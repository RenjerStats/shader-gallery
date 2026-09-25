package gallery.shader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class GalleryCard(
    val id:String,
    val title:String,
    val author:String,
    val category:String,
    val revisionId:String,
    val preview:String?
) {
    companion object {
        fun fromJson(json:JSONObject):GalleryCard {
            val revision=json.getJSONObject("revision")
            return GalleryCard(
                UUID.fromString(json.getString("id")).toString(),
                json.getString("title"),
                json.getJSONObject("author").getString("display_name"),
                json.getString("category"),
                UUID.fromString(revision.getString("id")).toString(),
                revision.optString("preview").takeIf { it.startsWith("data:image/png;base64,") && it.length<=400_000 }
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

data class GalleryPage(val items:List<GalleryCard>,val nextCursor:JSONObject?)

object GalleryClient {
    fun base(raw:String):String {
        val url=URL(raw.trim().trimEnd('/'))
        require(url.protocol=="https" || (url.protocol=="http" && url.host=="127.0.0.1")) { "Нужен HTTPS-адрес галереи" }
        require(url.path.isEmpty() || url.path=="/") { "Укажите адрес галереи без пути" }
        require(!url.authority.contains('@') && url.query==null && url.ref==null) { "Некорректный адрес галереи" }
        return "${url.protocol}://${url.authority}"
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
        connection.connectTimeout=8000
        connection.readTimeout=8000
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
        } finally { connection.disconnect() }
    }

    fun rpc(context:Context,source:String,action:String,payload:JSONObject=JSONObject()):JSONObject =
        request(context,source,"/api/rpc",JSONObject().put("action",action).put("payload",payload)).getJSONObject("data")

    fun session(context:Context,source:String):String?=request(context,source,"/api/auth/session").optJSONObject("data")?.optString("id")
    fun signIn(context:Context,source:String,email:String,password:String):String=
        request(context,source,"/api/auth/login",JSONObject().put("email",email).put("password",password)).getJSONObject("data").getString("id")
    fun signUp(context:Context,source:String,email:String,password:String,name:String):String=
        request(context,source,"/api/auth/signup",JSONObject().put("email",email).put("password",password).put("display_name",name)).getJSONObject("data").getString("id")
    fun signOut(context:Context,source:String){try {request(context,source,"/api/auth/logout",JSONObject())} finally {setCookie(context,source,null)}}

    fun feed(context:Context,source:String,mode:String,query:String,category:String,cursor:JSONObject?):GalleryPage {
        require(mode in listOf("new","curated","following","saved"))
        val payload=JSONObject().put("mode",mode).put("limit",12)
        if(query.isNotBlank())payload.put("query",query.trim().take(100))
        if(category.isNotBlank())payload.put("category",category)
        if(cursor!=null)payload.put("cursor",cursor)
        val data=rpc(context,source,"feed",payload)
        val array=data.getJSONArray("items")
        val items=(0 until array.length()).map { GalleryCard.fromJson(array.getJSONObject(it)) }
        return GalleryPage(items,data.optJSONObject("next_cursor"))
    }
}
