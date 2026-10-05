package gallery.shader.app

import android.animation.ValueAnimator
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shell of the app: three tabs (gallery, wallpaper, profile) under a Telegram-style bottom bar,
 * with a single full-screen work page that slides over them.
 */
class MainActivity:Activity() {
    internal lateinit var ui:Ui
    lateinit var account:Account
    internal lateinit var feed:FeedScreen
    internal lateinit var work:WorkScreen
    private lateinit var wallpaper:WallpaperScreen
    private lateinit var profile:ProfileScreen
    private lateinit var root:FrameLayout
    private lateinit var shell:LinearLayout
    private lateinit var frame:FrameLayout
    private lateinit var nav:LinearLayout
    private lateinit var snack:LinearLayout
    private lateinit var snackText:TextView
    private lateinit var snackAction:TextView
    private val navIcons=mutableListOf<ImageView>()
    private val navPills=mutableListOf<View>()
    private val navLabels=mutableListOf<TextView>()
    private val navNames=listOf("gallery","wallpaper","account")
    private val navTitles=listOf("Галерея","Обои","Профиль")
    private val handler=Handler(Looper.getMainLooper())
    private val hideSnack=Runnable {snack.animate().alpha(0f).setDuration(160).withEndAction {snack.visibility=View.GONE}.start()}
    private var tab=0
    private var insets=Insets()
    var resumedNow=false
        private set

    override fun onCreate(savedInstanceState:Bundle?) {
        val night=Appearance.night(this)
        setTheme(if(night)R.style.GalleryThemeDark else R.style.GalleryTheme)
        super.onCreate(savedInstanceState)
        ui=Ui(this)
        account=Account(this)
        root=FrameLayout(this).apply {setBackgroundColor(ui.p.paper)}
        setContentView(root)
        edgeToEdge(night)
        root.setOnApplyWindowInsetsListener {_,windowInsets->insets=readInsets(windowInsets);applyInsets();windowInsets}

        val saved=PackageStore.gallerySource(this)
        account.use(if(saved==null || saved=="http://127.0.0.1:4173")GalleryClient.SITE else saved)

        feed=FeedScreen(this);wallpaper=WallpaperScreen(this);profile=ProfileScreen(this);work=WorkScreen(this)
        shell=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        frame=FrameLayout(this).apply {addView(feed.view,-1,-1);addView(wallpaper.view,-1,-1);addView(profile.view,-1,-1)}
        shell.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        shell.addView(buildNav(),LinearLayout.LayoutParams(-1,-2))
        root.addView(shell,FrameLayout.LayoutParams(-1,-1))
        root.addView(work.view,FrameLayout.LayoutParams(-1,-1))
        buildSnack()
        selectTab(0)
        if(savedInstanceState==null)handleIntent(intent)
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT){goBack()}
        root.requestApplyInsets()
    }

    private fun buildNav():View {
        val p=ui.p
        nav=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(p.surface)}
        nav.addView(ui.divider(),LinearLayout.LayoutParams(-1,1))
        val row=LinearLayout(this)
        navNames.forEachIndexed {index,name->
            val item=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=ui.ripple(null);isClickable=true;isFocusable=true
                contentDescription=navTitles[index];setPadding(0,ui.dp(8),0,ui.dp(8))
            }
            val pill=FrameLayout(this)
            val icon=ImageView(this)
            pill.addView(icon,FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER))
            val label=ui.text(navTitles[index],12f,p.muted,ui.sansSemi,1)
            item.addView(pill,LinearLayout.LayoutParams(ui.dp(64),ui.dp(32)))
            item.addView(label,LinearLayout.LayoutParams(-2,-2).apply {topMargin=ui.dp(3)})
            item.setOnClickListener {if(index==tab && index==0)feed.scrollToTop() else selectTab(index)}
            navIcons+=icon;navPills+=pill;navLabels+=label
            row.addView(item,LinearLayout.LayoutParams(0,ui.dp(68),1f))
        }
        nav.addView(row)
        return nav
    }

    fun selectTab(index:Int) {
        tab=index
        val p=ui.p
        feed.view.visibility=if(index==0)View.VISIBLE else View.GONE
        wallpaper.view.visibility=if(index==1)View.VISIBLE else View.GONE
        profile.view.visibility=if(index==2)View.VISIBLE else View.GONE
        navNames.forEachIndexed {i,name->
            val active=i==index
            navIcons[i].setImageDrawable(ui.icon(name,if(active)p.link else p.muted))
            navPills[i].background=if(active)ui.shape(p.soft,100f) else null
            navLabels[i].setTextColor(if(active)p.link else p.muted)
            navLabels[i].isSelected=active
        }
        if(index==1)wallpaper.refresh()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
    }

    fun showFeed(mode:String) {selectTab(0);feed.showMode(mode)}
    fun setFeedGrid(grid:Boolean){feed.setGrid(grid)}
    fun libraryChanged(){feed.onLibraryChanged()}

    private fun buildSnack() {
        val p=ui.p
        snackText=ui.text("",14f,p.onInverse,ui.sansMedium).apply {setLineSpacing(0f,1.15f)}
        snackAction=ui.text("",14f,if(p.night)0xff3d4fd0.toInt() else 0xffb7c2ff.toInt(),ui.sansSemi).apply {setPadding(ui.dp(16),ui.dp(12),0,ui.dp(12));isClickable=true}
        snack=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL;background=ui.shape(p.inverse,16f);setPadding(ui.dp(16),ui.dp(6),ui.dp(12),ui.dp(6));elevation=ui.dpf(8f);visibility=View.GONE
            minimumHeight=ui.dp(48)
            addView(snackText,LinearLayout.LayoutParams(0,-2,1f));addView(snackAction)
        }
        root.addView(snack,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply {setMargins(ui.dp(12),0,ui.dp(12),0)})
    }

    /** A short message above the bottom bar; [action] adds a button such as "Повторить". */
    fun toast(message:String,action:String?=null,onAction:()->Unit={}) {
        handler.removeCallbacks(hideSnack)
        snackText.text=message
        snackAction.visibility=if(action==null)View.GONE else View.VISIBLE
        snackAction.text=action ?: ""
        snackAction.setOnClickListener {handler.removeCallbacks(hideSnack);hideSnack.run();onAction()}
        snack.animate().cancel()
        snack.visibility=View.VISIBLE;snack.alpha=0f;snack.translationY=ui.dpf(12f)
        snack.animate().alpha(1f).translationY(0f).setDuration(if(ValueAnimator.areAnimatorsEnabled())180 else 0).start()
        snack.announceForAccessibility(message)
        handler.postDelayed(hideSnack,if(action!=null)5500 else 3200)
    }

    private fun applyInsets() {
        shell.setPadding(insets.left,insets.top,insets.right,0)
        val keyboard=insets.ime>0
        nav.setPadding(0,0,0,insets.bottom)
        nav.visibility=if(keyboard)View.GONE else View.VISIBLE
        frame.setPadding(0,0,0,insets.ime)
        work.applyInsets(insets)
        val above=when {
            work.view.visibility==View.VISIBLE->ui.dp(84)+insets.bottomPad
            keyboard->insets.ime+ui.dp(12)
            else->ui.dp(68)+insets.bottom+ui.dp(12)
        }
        (snack.layoutParams as FrameLayout.LayoutParams).bottomMargin=above
        snack.requestLayout()
    }

    fun openWork(source:String,id:String,revision:String?,card:GalleryCard?) {
        val base=try{GalleryClient.base(source)}catch(e:Exception){toast(friendly(e,"Некорректный адрес"));return}
        if(base!=account.source)account.use(base)
        work.open(base,id,revision,card)
        if(work.view.visibility!=View.VISIBLE) {
            work.view.visibility=View.VISIBLE
            if(ValueAnimator.areAnimatorsEnabled()) {
                work.view.alpha=0f;work.view.translationX=ui.dpf(28f)
                work.view.animate().alpha(1f).translationX(0f).setDuration(220).start()
            }
            work.onShown()
        }
        applyInsets()
    }

    fun closeWork() {
        if(work.view.visibility!=View.VISIBLE)return
        work.view.animate().cancel()
        work.view.visibility=View.GONE;work.view.alpha=1f;work.view.translationX=0f
        work.onHidden();applyInsets()
    }

    fun openDna(shader:ShaderPackage?) {
        if(account.checking){toast("Проверяем аккаунт. Попробуйте ещё раз через секунду.");return}
        if(!account.signedIn){AuthSheet.show(this,"Войдите, чтобы создавать работы в DNA Studio.") {openDna(shader)};return}
        startActivity(Intent(this,DnaStudioActivity::class.java).putExtra("source",account.source)
            .putExtra("work_id",shader?.workId).putExtra("revision_id",shader?.revisionId))
    }

    fun launchWallpaperPicker() {
        val intent=Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(this,GalleryWallpaperService::class.java))
        try{startActivity(intent)} catch(e:Exception){toast("Не удалось открыть экран обоев: ${e.message}")}
    }

    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);handleIntent(intent)}

    private fun handleIntent(intent:Intent?) {
        val uri=intent?.data ?: return
        if(uri.scheme=="shadergallery" && uri.host=="auth-callback") {
            val code=uri.getQueryParameter("code")
            val error=uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            if(code==null){toast(error ?: "Не удалось завершить вход");return}
            account.use(GalleryClient.SITE)
            account.completeGoogle(code) {failure->toast(failure ?: "Вы вошли в аккаунт")}
            return
        }
        if(uri.scheme=="shadergallery" && uri.host=="work") {
            val id=uri.pathSegments.firstOrNull() ?: return
            val source=uri.getQueryParameter("source")?.let {if(it=="https://renjerstats.github.io")GalleryClient.SITE else it} ?: return
            openWork(source,id,uri.getQueryParameter("revision"),null);return
        }
        if(uri.scheme=="https" && uri.host=="renjerstats.github.io" && uri.pathSegments.size==3 && uri.pathSegments[0]=="shader-gallery" && uri.pathSegments[1]=="works") {
            openWork(GalleryClient.SITE,uri.pathSegments[2],uri.getQueryParameter("revision"),null)
        }
    }

    override fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig)
        feed.onConfigurationChanged();work.applyLayout();root.requestApplyInsets()
    }

    private fun goBack() {
        when {
            work.view.visibility==View.VISIBLE->closeWork()
            tab!=0->selectTab(0)
            else->finish()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION","DEPRECATION")
    override fun onBackPressed(){goBack()}

    override fun onResume(){super.onResume();resumedNow=true;work.onResume()}
    override fun onPause(){resumedNow=false;work.onPause();super.onPause()}
}
