package gallery.shader.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the refresh token encrypted with a non-exportable Android Keystore key. */
object CloudAuth {
    private const val ALIAS="shader_gallery_session"
    private const val PREFS="gallery_cloud_auth"
    private const val SESSION="session"
    private const val VERIFIER="oauth_verifier"

    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun encrypt(value:String):String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes,Base64.NO_WRAP)
    }

    private fun decrypt(value:String):String {
        val bytes=Base64.decode(value,Base64.DEFAULT)
        require(bytes.size>12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
    }

    @Synchronized fun read(context:Context):JSONObject? {
        val saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(SESSION,null) ?: return null
        return try { JSONObject(decrypt(saved)) } catch (_:Exception) { clear(context);null }
    }

    @Synchronized fun save(context:Context,response:JSONObject) {
        val access=response.getString("access_token")
        val refresh=response.getString("refresh_token")
        val expires=response.optLong("expires_in",3600)
        val user=response.getJSONObject("user").getString("id")
        val session=JSONObject().put("access_token",access).put("refresh_token",refresh)
            .put("expires_at",System.currentTimeMillis()/1000+expires).put("user_id",user)
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(SESSION,encrypt(session.toString())).commit()
    }

    @Synchronized fun clear(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(SESSION).apply()
    }

    @Synchronized fun saveVerifier(context:Context,verifier:String) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(VERIFIER,encrypt(JSONObject().put("value",verifier).put("created",System.currentTimeMillis()).toString())).commit()
    }

    @Synchronized fun takeVerifier(context:Context):String? {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val saved=prefs.getString(VERIFIER,null) ?: return null
        prefs.edit().remove(VERIFIER).commit()
        return try {
            val data=JSONObject(decrypt(saved))
            if(System.currentTimeMillis()-data.getLong("created")>5*60*1000) null else data.getString("value")
        } catch (_:Exception) {null}
    }
}
