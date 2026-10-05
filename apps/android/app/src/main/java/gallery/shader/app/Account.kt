package gallery.shader.app

import android.app.Activity
import org.json.JSONObject

/** Who is signed in and against which gallery; screens subscribe to changes. */
class Account(private val activity:Activity) {
    var source=GalleryClient.SITE
        private set
    var viewerId:String?=null
        private set
    var profile:JSONObject?=null
        private set
    var checking=false
        private set
    private var generation=0
    private val listeners=mutableListOf<()->Unit>()

    val signedIn get()=viewerId!=null
    val name:String? get()=profile?.optString("display_name")?.takeIf {it.isNotBlank()}
    val handle:String? get()=profile?.optString("username")?.takeIf {it.isNotBlank()}
    val bio:String? get()=profile?.optString("bio")?.takeIf {it.isNotBlank()}

    fun listen(listener:()->Unit){listeners+=listener}
    private fun changed(){listeners.toList().forEach {it()}}

    fun use(newSource:String) {
        val base=GalleryClient.base(newSource)
        if(base==source && generation>0)return
        source=base;PackageStore.setGallerySource(activity,base)
        refresh()
    }

    /** Re-reads the session and profile. A network failure keeps what was known before. */
    fun refresh() {
        val current=++generation;val target=source
        checking=true;changed()
        activity.background({
            val id=GalleryClient.session(activity,target)
            id to if(id!=null)GalleryClient.rpc(activity,target,"profile",JSONObject().put("id",id)) else null
        },{
            if(current==generation){checking=false;changed()}
        }) {(id,loaded)->
            if(current==generation && target==source){viewerId=id;profile=loaded;checking=false;changed()}
        }
    }

    sealed class Outcome {
        object Signed:Outcome()
        object NeedsConfirmation:Outcome()
        class Failed(val message:String):Outcome()
    }

    fun authenticate(email:String,password:String,name:String?,done:(Outcome)->Unit) {
        val target=source
        activity.background({
            if(name==null)GalleryClient.signIn(activity,target,email,password) else GalleryClient.signUp(activity,target,email,password,name)
        },{error->
            done(Outcome.Failed(friendly(error,"Не удалось войти")))
        }) {id->
            if(target!=source){done(Outcome.Failed("Адрес галереи изменился. Повторите вход."));return@background}
            if(id==null){done(Outcome.NeedsConfirmation);return@background}
            viewerId=id;changed();refresh();done(Outcome.Signed)
        }
    }

    fun completeGoogle(code:String,done:(String?)->Unit) {
        activity.background({GalleryClient.completeGoogle(activity,code)},{done(friendly(it,"Не удалось завершить вход"))}) {
            viewerId=it;refresh();done(null)
        }
    }

    fun signOut(done:()->Unit) {
        val target=source
        activity.background({try{GalleryClient.signOut(activity,target)}catch(_:Exception){}},{}) {
            ++generation;viewerId=null;profile=null;checking=false;changed();done()
        }
    }
}
