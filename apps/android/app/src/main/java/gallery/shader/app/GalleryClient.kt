package gallery.shader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class GalleryCard(
    val id:String,
    val title:String,
    val description:String,
    val tags:List<String>,
    val createdAt:String,
    val author:String,
    val category:String,
    val revisionId:String,
    /** Legacy servers inline the image here; current ones set [hasPreview] and serve it separately. */
    val preview:String?,
    val hasPreview:Boolean=false
) {
    companion object {
        fun fromJson(json:JSONObject):GalleryCard {
            val revision=json.getJSONObject("revision")
            return GalleryCard(
                UUID.fromString(json.getString("id")).toString(),
                json.getString("title"),
                json.optString("description"),
                json.optJSONArray("tags")?.let {array->(0 until array.length()).map {array.optString(it)}} ?: emptyList(),
                json.optString("created_at"),
                json.getJSONObject("author").getString("display_name"),
                json.getString("category"),
                UUID.fromString(revision.getString("id")).toString(),
                revision.optString("preview").takeIf { (it.startsWith("data:image/png;base64,") || it.startsWith("data:image/jpeg;base64,") || it.startsWith("data:image/webp;base64,")) && it.length<=400_000 },
                revision.optBoolean("has_preview",false)
            )
        }
    }

    fun thumbnail():Bitmap? {
        val encoded=preview?.substringAfter(',') ?: return null
        return try {
            val bytes=Base64.decode(encoded,Base64.DEFAULT)
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            var sample=1
            while(bounds.outWidth/sample>480 || bounds.outHeight/sample>480)sample*=2
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })
        } catch (_:Exception) { null }
    }
}

/** [signature] hashes the raw answer so an unchanged page can be recognised without comparing cards. */
data class GalleryPage(val items:List<GalleryCard>,val nextCursor:JSONObject?,val signature:String)
data class CachedPage(val page:GalleryPage,val ageMs:Long)

private class GalleryHttpException(val status:Int,message:String):Exception(message)

object GalleryClient {
    const val SITE="https://renjerstats.github.io/shader-gallery"
    private const val CLOUD="https://opjadrnmcghpwxrsryus.supabase.co"
    private const val KEY="sb_publishable_7BgpBbS6W-H7nWn4KZk5MQ_gLNeVe6T"
    private const val CALLBACK="shadergallery://auth-callback"

    fun base(raw:String):String {
        val url=URL(raw.trim().trimEnd('/'))
        require(url.protocol=="https" || (url.protocol=="http" && url.host=="127.0.0.1")) { "Нужен HTTPS-адрес галереи" }
        require(!url.authority.contains('@') && url.query==null && url.ref==null) { "Некорректный адрес галереи" }
        if(url.host.equals("renjerstats.github.io",true)) {
            require(url.protocol=="https" && (url.path=="/shader-gallery" || url.path=="/shader-gallery/")) { "Укажите адрес https://renjerstats.github.io/shader-gallery/" }
            return SITE
        }
        require(url.path.isEmpty() || url.path=="/") { "Укажите адрес галереи без пути" }
        return "${url.protocol}://${url.authority}"
    }

    fun isCloud(source:String)=base(source)==SITE

    private fun cloudRequest(path:String,body:JSONObject?=null,access:String?=null):JSONObject {
        val connection=URL(CLOUD+path).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects=false
        connection.requestMethod=if(body==null)"GET" else "POST"
        connection.connectTimeout=15000;connection.readTimeout=25000
        connection.setRequestProperty("apikey",KEY)
        if(access!=null)connection.setRequestProperty("Authorization","Bearer $access")
        if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json; charset=utf-8")}
        try {
            if(body!=null)connection.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=connection.responseCode
            val output=ByteArrayOutputStream()
            (if(code in 200..299)connection.inputStream else connection.errorStream)?.use {input ->
                val chunk=ByteArray(8192)
                while(output.size()<=6_000_000){val count=input.read(chunk);if(count<0)break;output.write(chunk,0,count)}
            }
            require(output.size()<=6_000_000) { "Ответ галереи слишком большой" }
            val response=try{JSONObject(output.toString("UTF-8"))}catch(_:Exception){JSONObject()}
            if(code !in 200..299)throw GalleryHttpException(code,response.optString("msg").ifBlank {response.optString("error_description").ifBlank {response.optString("error","Галерея недоступна: $code")}})
            require(!response.has("error")) {response.optString("error")}
            return response
        } catch(e:Exception) {connection.disconnect();throw e}
    }

    @Synchronized private fun access(context:Context):String? {
        var session=CloudAuth.read(context) ?: return null
        if(session.optLong("expires_at")<=System.currentTimeMillis()/1000+60){
            try {
                val refreshed=cloudRequest("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",session.getString("refresh_token")))
                CloudAuth.save(context,refreshed)
                session=CloudAuth.read(context) ?: return null
            } catch(e:Exception){if(e is GalleryHttpException && e.status in listOf(400,401,403))CloudAuth.clear(context);throw e}
        }
        return session.getString("access_token")
    }

    fun googleUrl(context:Context):String {
        val random=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val verifier=Base64.encodeToString(random,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        CloudAuth.saveVerifier(context,verifier)
        return Uri.parse("$CLOUD/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider","google")
            .appendQueryParameter("redirect_to",CALLBACK)
            .appendQueryParameter("code_challenge",challenge)
            .appendQueryParameter("code_challenge_method","s256").build().toString()
    }

    fun completeGoogle(context:Context,code:String):String {
        val verifier=CloudAuth.takeVerifier(context) ?: error("Время входа истекло. Попробуйте ещё раз.")
        val response=cloudRequest("/auth/v1/token?grant_type=pkce",JSONObject().put("auth_code",code).put("code_verifier",verifier))
        CloudAuth.save(context,response)
        return response.getJSONObject("user").getString("id")
    }

    private fun sessionKey(source:String)="session_${base(source)}"
    private fun cookie(context:Context,source:String)=context.getSharedPreferences("gallery_auth",Context.MODE_PRIVATE).getString(sessionKey(source),null)
    private fun setCookie(context:Context,source:String,value:String?){context.getSharedPreferences("gallery_auth",Context.MODE_PRIVATE).edit().apply {
        if(value==null)remove(sessionKey(source)) else putString(sessionKey(source),value)
    }.apply()}

    private fun request(context:Context,source:String,path:String,body:JSONObject?=null):JSONObject {
        val connection=URL(base(source)+path).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects=false
        connection.requestMethod=if(body==null)"GET" else "POST"
        connection.connectTimeout=15000
        connection.readTimeout=25000
        cookie(context,source)?.let {connection.setRequestProperty("Cookie",it)}
        if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json; charset=utf-8")}
        try {
            if(body!=null)connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code=connection.responseCode
            val output=ByteArrayOutputStream()
            (if(code in 200..299)connection.inputStream else connection.errorStream)?.use { input ->
                val chunk=ByteArray(8192)
                while(output.size()<=6_000_000) {
                    val count=input.read(chunk)
                    if(count<0)break
                    output.write(chunk,0,count)
                }
            }
            require(output.size()<=6_000_000) { "Ответ галереи слишком большой" }
            val response=try {JSONObject(output.toString("UTF-8"))}catch (_:Exception) {JSONObject()}
            require(code in 200..299) { response.optString("error","Галерея недоступна: $code") }
            connection.getHeaderField("Set-Cookie")?.substringBefore(';')?.takeIf {it.startsWith("sg_session=") }?.let {setCookie(context,source,it)}
            return response
        } catch(e:Exception) {connection.disconnect();throw e}
    }

    private val retriedActions=setOf("feed","work","profile","package","batch")

    /** Reads are safe to repeat: one more try covers a cold server or a dropped mobile connection. */
    private fun <T> retrying(block:()->T):T {
        try {return block()} catch(e:Exception) {
            val transient=e is java.io.IOException || (e is GalleryHttpException && e.status>=500)
            if(!transient)throw e
        }
        Thread.sleep(400)
        return block()
    }

    fun rpc(context:Context,source:String,action:String,payload:JSONObject=JSONObject()):JSONObject {
        // Image bytes are fetched separately (and cached), so keep them out of lists and details.
        if((action=="feed" || action=="work") && !payload.has("lite"))payload.put("lite",true)
        val body=JSONObject().put("action",action).put("payload",payload)
        val call={
            if(isCloud(source))cloudRequest("/functions/v1/gallery",body,access(context)).getJSONObject("data")
            else request(context,source,"/api/rpc",body).getJSONObject("data")
        }
        return if(action in retriedActions)retrying(call) else call()
    }

    /** Several reads in one round trip; each slot is the result or the failure. Needs a server that knows `batch`. */
    fun batch(context:Context,source:String,requests:List<Pair<String,JSONObject>>):List<Result<JSONObject>> {
        val list=org.json.JSONArray()
        requests.forEach {(action,payload)->
            if(action=="feed" || action=="work")payload.put("lite",true)
            list.put(JSONObject().put("action",action).put("payload",payload))
        }
        val results=rpc(context,source,"batch",JSONObject().put("requests",list)).getJSONArray("results")
        return (0 until results.length()).map {
            val item=results.getJSONObject(it)
            if(item.has("error"))Result.failure(IllegalStateException(item.optString("error"))) else Result.success(item.getJSONObject("data"))
        }
    }

    fun previewUrl(source:String,revisionId:String):String =
        if(isCloud(source))"$CLOUD/functions/v1/gallery/preview/$revisionId" else "${base(source)}/api/preview/$revisionId"

    /** Plain GET of a small public file (artwork preview). */
    fun download(context:Context,source:String,url:String):ByteArray = retrying {
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=20000
        if(isCloud(source))connection.setRequestProperty("apikey",KEY)
        try {
            val code=connection.responseCode
            require(code==200) {"Изображение недоступно: $code"}
            val output=ByteArrayOutputStream()
            connection.inputStream.use {input->
                val chunk=ByteArray(8192)
                while(output.size()<=2_000_000){val count=input.read(chunk);if(count<0)break;output.write(chunk,0,count)}
            }
            require(output.size()<=2_000_000) {"Изображение слишком большое"}
            output.toByteArray()
        } catch(e:Exception) {connection.disconnect();throw e}
    }

    fun session(context:Context,source:String):String? {
        if(!isCloud(source))return request(context,source,"/api/auth/session").optJSONObject("data")?.optString("id")
        val token=access(context) ?: return null
        return try {cloudRequest("/auth/v1/user",access=token).getString("id")}
        catch(e:Exception){if(e is GalleryHttpException && e.status in listOf(400,401,403))CloudAuth.clear(context);throw e}
    }
    fun signIn(context:Context,source:String,email:String,password:String):String {
        if(!isCloud(source))return request(context,source,"/api/auth/login",JSONObject().put("email",email).put("password",password)).getJSONObject("data").getString("id")
        val response=cloudRequest("/auth/v1/token?grant_type=password",JSONObject().put("email",email).put("password",password))
        CloudAuth.save(context,response)
        return response.getJSONObject("user").getString("id")
    }
    fun signUp(context:Context,source:String,email:String,password:String,name:String):String? {
        if(!isCloud(source))return request(context,source,"/api/auth/signup",JSONObject().put("email",email).put("password",password).put("display_name",name)).getJSONObject("data").getString("id")
        val target=Uri.encode("$SITE/")
        val response=cloudRequest("/auth/v1/signup?redirect_to=$target",JSONObject().put("email",email).put("password",password).put("data",JSONObject().put("display_name",name)))
        if(!response.has("access_token"))return null
        CloudAuth.save(context,response)
        return response.getJSONObject("user").getString("id")
    }
    fun signOut(context:Context,source:String){
        if(!isCloud(source)){try {request(context,source,"/api/auth/logout",JSONObject())} finally {setCookie(context,source,null)};return}
        try {access(context)?.let{cloudRequest("/auth/v1/logout",JSONObject(),it)}} finally {CloudAuth.clear(context)}
    }

    private fun feedKey(source:String,mode:String,query:String,category:String,viewer:String?)=
        "feed|${base(source)}|$mode|${query.trim()}|$category|${viewer ?: ""}"

    private fun parsePage(data:JSONObject):GalleryPage {
        val array=data.getJSONArray("items")
        return GalleryPage((0 until array.length()).map {GalleryCard.fromJson(array.getJSONObject(it))},data.optJSONObject("next_cursor"),Caches.sha256(data.toString()))
    }

    /** First page as last seen on this device, however old; the caller decides whether it is fresh enough. */
    fun cachedFeed(context:Context,source:String,mode:String,query:String,category:String,viewer:String?):CachedPage? {
        val cache=Caches.api(context);val key=feedKey(source,mode,query,category,viewer)
        val bytes=cache.read(key) ?: return null
        return try {CachedPage(parsePage(JSONObject(String(bytes,Charsets.UTF_8))),cache.age(key) ?: Long.MAX_VALUE)} catch(_:Exception) {null}
    }

    fun feed(context:Context,source:String,mode:String,query:String,category:String,cursor:JSONObject?,viewer:String?=null):GalleryPage {
        require(mode in listOf("new","curated","following","saved"))
        val payload=JSONObject().put("mode",mode).put("limit",12)
        if(query.isNotBlank())payload.put("query",query.trim().take(100))
        if(category.isNotBlank())payload.put("category",category)
        if(cursor!=null)payload.put("cursor",cursor)
        val data=rpc(context,source,"feed",payload)
        val page=parsePage(data)
        // Personal lists are only kept once we know whose they are.
        if(cursor==null && (viewer!=null || mode=="new" || mode=="curated"))
            Caches.api(context).write(feedKey(source,mode,query,category,viewer),data.toString().toByteArray(Charsets.UTF_8))
        return page
    }

    private fun workKey(source:String,id:String,revision:String?,viewer:String?)="work|${base(source)}|$id|${revision ?: ""}|${viewer ?: ""}"

    fun cachedWork(context:Context,source:String,id:String,revision:String?,viewer:String?):JSONObject? =
        try {Caches.api(context).read(workKey(source,id,revision,viewer))?.let {JSONObject(String(it,Charsets.UTF_8))}} catch(_:Exception) {null}

    fun storeWork(context:Context,source:String,id:String,revision:String?,viewer:String?,detail:JSONObject) =
        Caches.api(context).write(workKey(source,id,revision,viewer),detail.toString().toByteArray(Charsets.UTF_8))

    /** Work details (comments, counters, versions), remembered so the page can open before the network answers. */
    fun work(context:Context,source:String,id:String,revision:String?,viewer:String?):JSONObject {
        val data=rpc(context,source,"work",JSONObject().put("id",id).put("revision_id",revision))
        storeWork(context,source,id,revision,viewer,data)
        return data
    }
}
