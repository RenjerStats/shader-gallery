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
        connection.connectTimeout=10000;connection.readTimeout=10000
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
            require(code in 200..299) {response.optString("msg").ifBlank {response.optString("error_description").ifBlank {response.optString("error","Галерея недоступна: $code")}}}
            require(!response.has("error")) {response.optString("error")}
            return response
        } finally {connection.disconnect()}
    }

    @Synchronized private fun access(context:Context):String? {
        var session=CloudAuth.read(context) ?: return null
        if(session.optLong("expires_at")<=System.currentTimeMillis()/1000+60){
            try {
                val refreshed=cloudRequest("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",session.getString("refresh_token")))
                CloudAuth.save(context,refreshed)
                session=CloudAuth.read(context) ?: return null
            } catch(e:Exception){CloudAuth.clear(context);throw e}
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

    fun rpc(context:Context,source:String,action:String,payload:JSONObject=JSONObject()):JSONObject {
        val body=JSONObject().put("action",action).put("payload",payload)
        return if(isCloud(source))cloudRequest("/functions/v1/gallery",body,access(context)).getJSONObject("data")
        else request(context,source,"/api/rpc",body).getJSONObject("data")
    }

    fun session(context:Context,source:String):String? {
        if(!isCloud(source))return request(context,source,"/api/auth/session").optJSONObject("data")?.optString("id")
        val token=access(context) ?: return null
        return try {cloudRequest("/auth/v1/user",access=token).getString("id")}
        catch(e:Exception){CloudAuth.clear(context);throw e}
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
