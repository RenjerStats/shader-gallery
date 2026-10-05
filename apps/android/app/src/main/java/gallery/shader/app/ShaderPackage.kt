package gallery.shader.app

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

data class ShaderParameter(val name:String,val label:String,val type:String,val min:Float,val max:Float,val defaultValue:String) {
    companion object {
        fun fromJson(json:JSONObject):ShaderParameter {
            val name=json.getString("name")
            require(Regex("[A-Za-z_][A-Za-z0-9_]{0,31}").matches(name))
            val type=json.getString("type")
            require(type=="float" || type=="color")
            val minimum=if(type=="float")json.getDouble("min").toFloat() else 0f
            val maximum=if(type=="float")json.getDouble("max").toFloat() else 1f
            val default=json.get("default").toString()
            if(type=="float")require(minimum<maximum && default.toFloat() in minimum..maximum)
            else require(Regex("#[0-9a-fA-F]{6}").matches(default))
            return ShaderParameter(name,json.getString("label"),type,minimum,maximum,default)
        }
    }
}

data class ShaderPackage(val raw:String,val workId:String,val revisionId:String,val title:String,val authorName:String,val code:String,val license:String,val parameters:List<ShaderParameter>) {
    companion object {
        fun parse(raw:String):ShaderPackage {
            require(raw.length<=1_100_000) { "Пакет слишком большой" }
            val json=JSONObject(raw)
            require(json.getInt("schemaVersion")==1 && json.getString("runtimeKind")=="shader") { "Неподдерживаемый пакет" }
            require(json.getString("renderProfile")=="webgl2-gles3-single-pass") { "Неподдерживаемый профиль рендера" }
            val workId=json.getString("workId");val revisionId=json.getString("revisionId")
            val authorName=json.getJSONObject("author").getString("name")
            UUID.fromString(workId);UUID.fromString(revisionId)
            val code=json.getString("code")
            require(code.length in 20..50000 && code.contains("mainImage")) { "Некорректный исходник" }
            val hash=MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            require(hash==json.getString("contentHash")) { "Контрольная сумма не совпадает" }
            val license=json.getString("licenseId")
            require(license=="MIT" || license=="CC0-1.0")
            val array=json.getJSONArray("parameters")
            require(array.length()<=12)
            val params=(0 until array.length()).map { ShaderParameter.fromJson(array.getJSONObject(it)) }
            require(params.map { it.name }.distinct().size==params.size)
            return ShaderPackage(raw,workId,revisionId,json.getString("title"),authorName,code,license,params)
        }
    }
    fun defaults():MutableMap<String,String> = parameters.associate { it.name to it.defaultValue }.toMutableMap()
}

object PackageStore {
    private const val PREFS="gallery"
    fun gallerySource(context:Context):String?=context.getSharedPreferences(PREFS,0).getString("gallery_source",null)
    fun setGallerySource(context:Context,source:String){context.getSharedPreferences(PREFS,0).edit().putString("gallery_source",GalleryClient.base(source)).apply()}
    fun selected(context:Context):ShaderPackage? {
        return try {
            val raw=context.getSharedPreferences(PREFS,0).getString("selected_package",null) ?: return null
            ShaderPackage.parse(raw)
        } catch (_:Exception) { null }
    }
    fun values(context:Context,shader:ShaderPackage):MutableMap<String,String> {
        val raw=context.getSharedPreferences(PREFS,0).getString("values_${shader.revisionId}",null) ?: return shader.defaults()
        return try { val json=JSONObject(raw);shader.defaults().also { map -> shader.parameters.forEach { p ->
            if(json.has(p.name)) {
                val value=json.getString(p.name)
                if((p.type=="float" && value.toFloatOrNull()?.let {it.isFinite() && it in p.min..p.max}==true) ||
                    (p.type=="color" && Regex("#[0-9a-fA-F]{6}").matches(value)))map[p.name]=value
            }
        } } } catch (_:Exception) {shader.defaults()}
    }
    fun saveValues(context:Context,shader:ShaderPackage,values:Map<String,String>) {
        val json=JSONObject();shader.parameters.forEach { p -> json.put(p.name,values[p.name] ?: p.defaultValue) }
        context.getSharedPreferences(PREFS,0).edit().putString("values_${shader.revisionId}",json.toString()).apply()
    }
    fun select(context:Context,shader:ShaderPackage) { context.getSharedPreferences(PREFS,0).edit().putString("selected_package",shader.raw).commit() }
    fun quality(context:Context):Float=context.getSharedPreferences(PREFS,0).getFloat("quality",0.75f)
    fun setQuality(context:Context,value:Float){context.getSharedPreferences(PREFS,0).edit().putFloat("quality",value).apply()}
    fun fps(context:Context):Int=context.getSharedPreferences(PREFS,0).getInt("fps",30)
    fun setFps(context:Context,value:Int){context.getSharedPreferences(PREFS,0).edit().putInt("fps",value).apply()}
}

/** A shader ready to run, plus the work page data when the server could send both at once. */
class OpenedWork(val shader:ShaderPackage,val detail:JSONObject?)

object PackageClient {
    /** A revision never changes, so a package seen before is valid forever; parse() re-checks its content hash. */
    private fun cached(context:Context,workId:String,revisionId:String?):ShaderPackage? {
        if(revisionId==null)return null
        val bytes=Caches.packages(context).read(revisionId) ?: return null
        return try {ShaderPackage.parse(String(bytes,Charsets.UTF_8)).takeIf {it.workId==workId && it.revisionId==revisionId}} catch(_:Exception) {null}
    }

    private fun remember(context:Context,shader:ShaderPackage):ShaderPackage {
        Caches.packages(context).write(shader.revisionId,shader.raw.toByteArray(Charsets.UTF_8))
        return shader
    }

    /** Package and page details in one round trip when the package is not on the device yet. */
    fun open(context:Context,source:String,workId:String,revisionId:String?,viewer:String?):OpenedWork {
        UUID.fromString(workId);if(revisionId!=null)UUID.fromString(revisionId)
        cached(context,workId,revisionId)?.let {return OpenedWork(it,null)}
        if(GalleryClient.isCloud(source)) {
            try {
                val package_=JSONObject().put("id",workId);val work=JSONObject().put("id",workId)
                if(revisionId!=null){package_.put("revision_id",revisionId);work.put("revision_id",revisionId)}
                val (packed,detailed)=GalleryClient.batch(context,source,listOf("package" to package_,"work" to work))
                val shader=ShaderPackage.parse(packed.getOrThrow().toString())
                require(shader.workId==workId && (revisionId==null || shader.revisionId==revisionId)) { "Получена другая версия" }
                val detail=detailed.getOrNull()
                if(detail!=null)GalleryClient.storeWork(context,source,workId,revisionId,viewer,detail)
                return OpenedWork(remember(context,shader),detail)
            } catch(e:Exception) {
                // A server without `batch` (older deployment) still works through the single-call path below.
                if(e is java.io.IOException)throw e
            }
        }
        return OpenedWork(download(context,source,workId,revisionId),null)
    }

    fun download(context:Context,source:String,workId:String,revisionId:String?):ShaderPackage {
        UUID.fromString(workId);if(revisionId!=null)UUID.fromString(revisionId)
        cached(context,workId,revisionId)?.let {return it}
        if(GalleryClient.isCloud(source)) {
            val payload=JSONObject().put("id",workId)
            if(revisionId!=null)payload.put("revision_id",revisionId)
            val raw=GalleryClient.rpc(context,source,"package",payload).toString()
            val shader=ShaderPackage.parse(raw)
            require(shader.workId==workId && (revisionId==null || shader.revisionId==revisionId)) { "Получена другая версия" }
            return remember(context,shader)
        }
        val base=URL(source.trimEnd('/'))
        require(base.protocol=="https" || (base.protocol=="http" && base.host=="127.0.0.1")) { "Нужен HTTPS-адрес галереи" }
        require(base.path.isEmpty() || base.path=="/") { "Некорректный адрес галереи" }
        val resolved=revisionId ?: run {
            val lookup=URL(base,"/api/rpc").openConnection() as HttpURLConnection
            lookup.requestMethod="POST";lookup.doOutput=true;lookup.setRequestProperty("Content-Type","application/json")
            lookup.connectTimeout=8000;lookup.readTimeout=8000
            try {
                lookup.outputStream.use { it.write(JSONObject().put("action","work").put("payload",JSONObject().put("id",workId)).toString().toByteArray()) }
                require(lookup.responseCode==200) { "Работа недоступна" }
                JSONObject(lookup.inputStream.bufferedReader().use { it.readText() }).getJSONObject("data").getJSONObject("work").getString("current_revision_id")
            } finally { lookup.disconnect() }
        }
        val url=URL(base,"/api/packages/$workId/$resolved")
        val connection=url.openConnection() as HttpURLConnection
        connection.connectTimeout=8000;connection.readTimeout=8000;connection.instanceFollowRedirects=false
        try {
            require(connection.responseCode==200) { "Работа недоступна: ${connection.responseCode}" }
            require((connection.contentLengthLong in -1..1_100_000)) { "Пакет слишком большой" }
            val bytes=connection.inputStream.use { input ->
                val result=ByteArrayOutputStream();val chunk=ByteArray(8192)
                while(result.size()<=1_100_000){val read=input.read(chunk);if(read<0)break;result.write(chunk,0,read)}
                result.toByteArray()
            }
            require(bytes.size<=1_100_000) { "Пакет слишком большой" }
            val json=JSONObject(bytes.toString(Charsets.UTF_8))
            val shader=ShaderPackage.parse(json.getJSONObject("data").toString())
            require(shader.workId==workId && shader.revisionId==resolved) { "Получена другая версия" }
            return remember(context,shader)
        } finally { connection.disconnect() }
    }
}
