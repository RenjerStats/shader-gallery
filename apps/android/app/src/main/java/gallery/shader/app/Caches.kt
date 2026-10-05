package gallery.shader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** A size-capped folder under cacheDir. Android may wipe it at any time, so everything stored here can be refetched. */
class DiskCache(private val dir:File,private val maxBytes:Long) {
    private fun file(key:String)=File(dir,Caches.sha256(key))

    fun read(key:String):ByteArray? = try {file(key).takeIf {it.isFile}?.readBytes()} catch(_:Exception) {null}

    /** Milliseconds since [key] was stored, or null when absent. */
    fun age(key:String):Long? = file(key).takeIf {it.isFile}?.let {System.currentTimeMillis()-it.lastModified()}

    @Synchronized fun write(key:String,bytes:ByteArray) {
        try {
            dir.mkdirs()
            val target=file(key);val temp=File(dir,"${target.name}.tmp")
            temp.writeBytes(bytes)
            if(!temp.renameTo(target)){target.delete();temp.renameTo(target)}
            trim()
        } catch(_:Exception) {}
    }

    /** Drops the least recently written files until the folder fits. */
    private fun trim() {
        val files=dir.listFiles() ?: return
        var total=files.sumOf {it.length()}
        if(total<=maxBytes)return
        for(file in files.sortedBy {it.lastModified()}) {
            if(total<=maxBytes*3/4)break
            total-=file.length();file.delete()
        }
    }
}

object Caches {
    private val stores=HashMap<String,DiskCache>()

    @Synchronized private fun store(context:Context,name:String,maxBytes:Long)=
        stores.getOrPut(name) {DiskCache(File(context.applicationContext.cacheDir,name),maxBytes)}

    /** Feed pages and work details: small JSON, shown instantly while a fresh copy loads. */
    fun api(context:Context)=store(context,"api",16L shl 20)
    /** Downscaled artwork previews, keyed by revision id (a revision never changes). */
    fun thumbs(context:Context)=store(context,"thumbs",64L shl 20)
    /** Shader packages, keyed by revision id and verified by content hash when read. */
    fun packages(context:Context)=store(context,"packages",24L shl 20)

    fun sha256(text:String):String=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
}

/** Loads artwork previews on demand: memory, then disk, then one small download per revision. */
object Thumbs {
    private const val MAX_EDGE=480
    private val main=Handler(Looper.getMainLooper())
    private val memory=object:LruCache<String,Bitmap>(24*1024*1024) {override fun sizeOf(key:String,value:Bitmap)=value.byteCount}
    private val pool=Executors.newFixedThreadPool(3)
    private val waiting=HashMap<String,MutableList<(Bitmap?)->Unit>>()
    private val failedAt=ConcurrentHashMap<String,Long>()

    fun peek(card:GalleryCard):Bitmap?=memory.get(card.revisionId)

    /** Main thread only. [done] also runs on the main thread; it receives null when there is no image. */
    fun load(context:Context,source:String,card:GalleryCard,done:(Bitmap?)->Unit) {
        val key=card.revisionId
        memory.get(key)?.let {done(it);return}
        if(!card.hasPreview && card.preview==null){done(null);return}
        failedAt[key]?.let {if(System.currentTimeMillis()-it<30_000){done(null);return}}
        waiting[key]?.let {it+=done;return}
        waiting[key]=mutableListOf(done)
        val app=context.applicationContext
        pool.execute {
            val bitmap=try {fetch(app,source,card)} catch(_:Exception) {null}
            if(bitmap!=null){memory.put(key,bitmap);failedAt.remove(key)} else failedAt[key]=System.currentTimeMillis()
            main.post {waiting.remove(key)?.forEach {it(bitmap)}}
        }
    }

    private fun fetch(app:Context,source:String,card:GalleryCard):Bitmap? {
        val disk=Caches.thumbs(app)
        disk.read(card.revisionId)?.let {bytes->decode(bytes)?.let {return it}}
        // Older servers still inline the image in the feed; nothing to download then.
        if(!card.hasPreview)return card.thumbnail()
        val bitmap=decode(GalleryClient.download(app,source,GalleryClient.previewUrl(source,card.revisionId))) ?: return null
        encode(bitmap)?.let {disk.write(card.revisionId,it)}
        return bitmap
    }

    private fun decode(bytes:ByteArray):Bitmap? {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0)return null
        var sample=1
        while(bounds.outWidth/sample>MAX_EDGE || bounds.outHeight/sample>MAX_EDGE)sample*=2
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample})
    }

    @Suppress("DEPRECATION")
    private fun encode(bitmap:Bitmap):ByteArray? {
        val format=if(Build.VERSION.SDK_INT>=30)Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        return ByteArrayOutputStream().also {if(!bitmap.compress(format,85,it))return null}.toByteArray()
    }
}
