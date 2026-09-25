package gallery.shader.app

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageButton
import android.widget.PopupMenu
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import java.net.URL
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity:Activity() {
    private lateinit var gallerySection:LinearLayout
    private lateinit var bottomBar:LinearLayout
    private lateinit var backButton:ImageButton
    private lateinit var brandMark:ImageView
    private lateinit var toolbarTitle:TextView
    private lateinit var saveButton:ImageButton
    private lateinit var previewFrame:FrameLayout
    private var detailOpen=false
    private var workSaved=false
    private var socialBusy=false
    private var galleryScrollY=0
    private lateinit var qualityPanel:LinearLayout
    private lateinit var address:EditText
    private lateinit var status:TextView
    private lateinit var title:TextView
    private lateinit var byline:TextView
    private lateinit var controls:LinearLayout
    private lateinit var rootScroll:ScrollView
    private lateinit var detailSection:LinearLayout
    private lateinit var galleryList:android.widget.GridLayout
    private lateinit var gallerySignIn:Button
    private lateinit var galleryStatus:TextView
    private lateinit var authStatus:TextView
    private lateinit var authButton:Button
    private lateinit var socialPanel:LinearLayout
    private lateinit var search:EditText
    private lateinit var category:Spinner
    private lateinit var more:Button
    private lateinit var newTab:Button
    private lateinit var curatedTab:Button
    private lateinit var followingTab:Button
    private lateinit var savedTab:Button
    private lateinit var preview:GalleryPreview
    private lateinit var apply:Button
    private var current:ShaderPackage?=null
    private var values:MutableMap<String,String> = mutableMapOf()
    private var ready=false
    private var resumed=false
    private var previewRunning=false
    private var loadGeneration=0
    private var gallerySource:String?=null
    private var galleryMode="new"
    private var galleryCursor:org.json.JSONObject?=null
    private var galleryGeneration=0
    private var galleryLoading=false
    private var viewerId:String?=null
    private var sessionGeneration=0
    private val categories=listOf("Все категории","Абстракция","Природа","Геометрия","Свет","Другое")
    private fun dp(n:Int)=(n*resources.displayMetrics.density).roundToInt()
    private val ink=Color.rgb(34,33,32)
    private val muted=Color.rgb(112,109,105)
    private val paper=Color.rgb(248,246,242)
    private val blue=Color.rgb(40,76,237)
    private val line=Color.rgb(229,228,220)
    private val displayFont by lazy {resources.getFont(R.font.lora)}
    private val bodyFont by lazy {resources.getFont(R.font.manrope)}
    private val mediumFont by lazy {resources.getFont(R.font.manrope_medium)}
    private fun rounded(fill:Int,stroke:Int=fill,radius:Int=18)=GradientDrawable().apply {shape=GradientDrawable.RECTANGLE;cornerRadius=dp(radius).toFloat();setColor(fill);setStroke(dp(1),stroke)}
    private fun colorSwatch(color:Int)=RippleDrawable(ColorStateList.valueOf(0x14000000),android.graphics.drawable.InsetDrawable(rounded(color,line,16),dp(8)),null)
    private fun text(value:String,size:Float=14f,color:Int=ink,serif:Boolean=false)=TextView(this).apply {text=value;textSize=size;setTextColor(color);typeface=if(serif)displayFont else bodyFont;setPadding(0,dp(7),0,dp(7));includeFontPadding=false}
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=Button(this).apply {
        text=value;textSize=14f;isAllCaps=false;typeface=mediumFont
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(muted,if(primary)Color.WHITE else ink)))
        val surface=android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),rounded(if(primary)Color.rgb(227,228,233) else Color.TRANSPARENT,line,28))
            addState(intArrayOf(),rounded(if(primary)blue else Color.TRANSPARENT,if(primary)blue else line,28))
        }
        background=RippleDrawable(ColorStateList.valueOf(0x14000000),surface,null)
        backgroundTintList=null;stateListAnimator=null;minHeight=dp(48);minimumWidth=0;minWidth=0
        setPadding(dp(16),0,dp(16),0);setOnClickListener{action()}
    }
    private fun icon(name:String,label:String,action:()->Unit)=ImageButton(this).apply {
        setImageDrawable(GalleryIcon(name,ink));contentDescription=label;setPadding(dp(13),dp(13),dp(13),dp(13))
        background=RippleDrawable(ColorStateList.valueOf(0x18000000),null,rounded(Color.WHITE));setOnClickListener{action()}
    }
    private fun row()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),dp(16))}
    private fun card()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun reveal(view:View){
        view.animate().cancel()
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){view.alpha=1f;view.translationY=0f;return}
        view.alpha=0f;view.translationY=dp(3).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(android.view.animation.PathInterpolator(.2f,.7f,.2f,1f)).start()
    }
    private fun styleSlider(slider:SeekBar){slider.progressTintList=ColorStateList.valueOf(blue);slider.thumbTintList=ColorStateList.valueOf(blue);slider.progressBackgroundTintList=ColorStateList.valueOf(line);slider.minimumHeight=dp(48)}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=paper;window.navigationBarColor=paper
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val shell=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(paper)}
        setContentView(shell)
        shell.setOnApplyWindowInsetsListener { _,insets ->
            val top=if(Build.VERSION.SDK_INT>=30)insets.getInsets(WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
            val bottom=if(Build.VERSION.SDK_INT>=30)insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom else insets.systemWindowInsetBottom
            shell.setPadding(0,top,0,bottom);insets
        }
        shell.requestApplyInsets()
        if(Build.VERSION.SDK_INT>=33)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){if(detailOpen)showGallery() else finish()}
        val toolbar=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(8),0)}
        backButton=icon("back","Назад в галерею"){showGallery()}.apply {visibility=View.GONE}
        toolbar.addView(backButton,LinearLayout.LayoutParams(dp(48),dp(48)))
        brandMark=ImageView(this).apply {setImageDrawable(GalleryIcon("brand",ink));setPadding(dp(6),dp(10),dp(8),dp(10));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        toolbar.addView(brandMark,LinearLayout.LayoutParams(dp(42),dp(48)))
        toolbarTitle=text("Shader Gallery",20f,ink,true).apply {setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END}
        toolbar.addView(toolbarTitle,LinearLayout.LayoutParams(0,-2,1f))
        saveButton=icon("bookmark","Сохранить работу"){current?.let {socialAction("save",org.json.JSONObject().put("work_id",it.workId).put("active",!workSaved),it)}}.apply {visibility=View.GONE}
        toolbar.addView(saveButton,LinearLayout.LayoutParams(dp(48),dp(48)))
        val menu=icon("more","Меню"){ }
        menu.setOnClickListener {showMenu(menu)}
        toolbar.addView(menu,LinearLayout.LayoutParams(dp(48),dp(48)))
        shell.addView(toolbar,LinearLayout.LayoutParams(-1,dp(56)))
        status=text("",13f,muted).apply {visibility=View.GONE;setPadding(dp(20),dp(6),dp(20),dp(6));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        status.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){status.visibility=if(s.isNullOrBlank())View.GONE else View.VISIBLE}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        shell.addView(status)
        rootScroll=ScrollView(this).apply {isFillViewport=true;isVerticalScrollBarEnabled=false}
        val content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        rootScroll.addView(content);shell.addView(rootScroll,LinearLayout.LayoutParams(-1,0,1f))
        address=EditText(this).apply {hint="Адрес галереи или ссылка";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI;textSize=14f}
        authStatus=text("Гость");authButton=button("Войти"){if(viewerId==null)showAuthDialog() else signOut()}
        gallerySection=row();content.addView(gallerySection);buildGallery(gallerySection)
        detailSection=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;visibility=View.GONE};content.addView(detailSection)
        previewFrame=FrameLayout(this).apply {background=rounded(paper,paper,24);clipToOutline=true}
        val previewHeight=(resources.configuration.screenHeightDp*.49f).roundToInt().coerceIn(250,440)
        detailSection.addView(previewFrame,LinearLayout.LayoutParams(-1,dp(previewHeight)))
        preview=GalleryPreview(this){ok,message->runOnUiThread{ready=ok;apply.isEnabled=ok;status.text=if(ok)"" else message}}
        previewFrame.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val pause=icon(if(preview.isPaused)"play" else "pause",if(preview.isPaused)"Включить движение" else "Приостановить движение"){}
        pause.background=RippleDrawable(ColorStateList.valueOf(0x14000000),android.graphics.drawable.InsetDrawable(rounded(0xeef8f6f2.toInt(),0xeef8f6f2.toInt(),18),dp(6)),null)
        pause.setPadding(dp(15),dp(15),dp(15),dp(15))
        pause.setOnClickListener {preview.setPaused(!preview.isPaused);pause.setImageDrawable(GalleryIcon(if(preview.isPaused)"play" else "pause",ink));pause.contentDescription=if(preview.isPaused)"Включить движение" else "Приостановить движение"}
        previewFrame.addView(pause,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END).apply {topMargin=dp(12);rightMargin=dp(12)})
        val caption=row().apply {setPadding(dp(20),dp(40),dp(20),dp(16));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x00f8f6f2,0xeef8f6f2.toInt(),paper))}
        title=text("",24f,ink,true).apply {maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,0,0,dp(5))};caption.addView(title)
        byline=text("",12f,muted).apply {setPadding(0,0,0,0)};caption.addView(byline);previewFrame.addView(caption,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        controls=row();detailSection.addView(controls)
        socialPanel=row();qualityPanel=row();buildQuality()
        bottomBar=row().apply {visibility=View.GONE;setPadding(dp(20),dp(10),dp(20),dp(12))}
        apply=button("Установить обои",true){installWallpaper()}.apply {isEnabled=false}
        bottomBar.addView(apply,LinearLayout.LayoutParams(-1,dp(54)));shell.addView(bottomBar)
        val savedSource=PackageStore.gallerySource(this)
        gallerySource=if(savedSource==null || savedSource=="http://127.0.0.1:4173")GalleryClient.SITE else savedSource
        if(savedSource!=gallerySource)PackageStore.setGallerySource(this,GalleryClient.SITE)
        gallerySource?.let {address.setText(it)}
        PackageStore.selected(this)?.let {showPackage(it,false)}
        handleIntent(intent)
        gallerySource?.let {loadSession(it);loadGallery(false)}
    }
    private fun showMenu(anchor:View){
        val popup=PopupMenu(this,anchor)
        val choices=if(detailOpen)listOf("Поделиться","Обсуждение","Качество обоев","Подключение",if(viewerId==null)"Войти" else "Аккаунт") else listOf("Подключение",if(viewerId==null)"Войти" else "Аккаунт")
        choices.forEach {popup.menu.add(it)}
        popup.setOnMenuItemClickListener {item ->
            when(item.title.toString()){
                "Поделиться"->shareWork()
                "Обсуждение"->showPanel("Обсуждение",socialPanel)
                "Качество обоев"->showPanel("Качество обоев",qualityPanel)
                "Подключение"->{(address.parent as? android.view.ViewGroup)?.removeView(address);AlertDialog.Builder(this).setTitle("Подключение").setView(address).setPositiveButton("Открыть"){_,_->loadFromAddress()}.setNegativeButton("Отмена",null).show()}
                "Войти"->showAuthDialog()
                "Аккаунт"->AlertDialog.Builder(this).setTitle(authStatus.text).setPositiveButton("Выйти"){_,_->signOut()}.setNegativeButton("Закрыть",null).show()
            };true
        };popup.show()
    }
    private fun showPanel(label:String,panel:LinearLayout){
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
        val scroll=ScrollView(this).apply {addView(panel)}
        AlertDialog.Builder(this).setTitle(label).setView(scroll).setPositiveButton("Готово",null).show()
    }
    private fun shareWork(){val shader=current ?: return;val source=gallerySource ?: return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"$source/works/${shader.workId}?revision=${shader.revisionId}")},"Поделиться"))
    }
    private fun showGallery(){
        brandMark.visibility=View.VISIBLE;toolbarTitle.textSize=20f
        loadGeneration++;detailOpen=false;detailSection.visibility=View.GONE;bottomBar.visibility=View.GONE;backButton.visibility=View.GONE;saveButton.visibility=View.GONE;gallerySection.visibility=View.VISIBLE;status.text=""
        if(previewRunning){preview.stop();previewRunning=false};window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rootScroll.post {rootScroll.scrollTo(0,galleryScrollY)}
    }
    @Deprecated("Platform back callback")
    override fun onBackPressed(){if(detailOpen)showGallery() else super.onBackPressed()}
    private fun buildQuality(){
        fun setting(label:String,options:List<String>,selected:Int,onSelect:(Int)->Unit){
            qualityPanel.addView(text(label,15f))
            val picker=Spinner(this).apply {adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options);setSelection(selected)}
            picker.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){onSelect(position)}
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            };qualityPanel.addView(picker,LinearLayout.LayoutParams(-1,dp(48)))
        }
        setting("Разрешение",listOf("50% · экономное","75% · сбалансированное","100% · полное"),when(PackageStore.quality(this)){0.5f->0;1f->2;else->1}){PackageStore.setQuality(this,floatArrayOf(.5f,.75f,1f)[it]);preview.requestRender()}
        setting("Частота кадров",listOf("15 FPS","30 FPS","60 FPS"),when(PackageStore.fps(this)){15->0;60->2;else->1}){PackageStore.setFps(this,intArrayOf(15,30,60)[it])}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent)}
    private fun buildGallery(content:LinearLayout){
        content.addView(text("Галерея",32f,ink,true).apply {setPadding(0,dp(14),0,dp(16))})
        val tabs=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        newTab=button("Новое"){galleryMode="new";updateTabs();loadGallery(false)}
        curatedTab=button("Подборка"){galleryMode="curated";updateTabs();loadGallery(false)}
        followingTab=button("Подписки"){galleryMode="following";updateTabs();loadGallery(false)}
        savedTab=button("Сохранённое"){galleryMode="saved";updateTabs();loadGallery(false)}
        listOf(newTab,curatedTab,followingTab,savedTab).forEach {tab->tab.textSize=13f;tab.typeface=bodyFont;tab.setPadding(dp(10),0,dp(10),0);tabs.addView(tab,LinearLayout.LayoutParams(-2,dp(48)))}
        content.addView(HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false;addView(tabs)});updateTabs()
        val filters=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        search=EditText(this).apply {hint="Поиск работ";contentDescription="Поиск по названию или тегу";setSingleLine(true);setTextColor(ink);setHintTextColor(muted);textSize=14f;background=rounded(Color.TRANSPARENT,line,24);setPadding(dp(16),0,dp(16),0);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;setOnEditorActionListener {_,_,_->submitSearch();true}}
        filters.addView(search,LinearLayout.LayoutParams(0,dp(48),1f))
        filters.addView(icon("search","Найти работы"){submitSearch()},LinearLayout.LayoutParams(dp(48),dp(48)))
        content.addView(filters,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(14)})
        category=Spinner(this).apply {adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,categories)}
        category.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){if(::galleryStatus.isInitialized && gallerySource!=null)loadGallery(false)}
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
        }
        content.addView(category,LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(4)})
        galleryStatus=text("Откройте подключение в меню, чтобы выбрать галерею.",13f,muted).apply {accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};content.addView(galleryStatus)
        gallerySignIn=button("Войти"){showAuthDialog()}.apply {visibility=View.GONE};content.addView(gallerySignIn,LinearLayout.LayoutParams(-1,dp(48)))
        galleryList=android.widget.GridLayout(this).apply {columnCount=if(resources.configuration.screenWidthDp>=340 && resources.configuration.fontScale<1.4f)2 else 1};content.addView(galleryList)
        more=button("Показать ещё"){loadGallery(true)}.apply {visibility=View.GONE};content.addView(more,LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(16)})
    }
    private fun submitSearch(){(getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(search.windowToken,0);search.clearFocus();loadGallery(false)}
    private fun updateTabs(){
        listOf("new" to newTab,"curated" to curatedTab,"following" to followingTab,"saved" to savedTab).forEach {(mode,tab)->
            val active=galleryMode==mode;tab.isSelected=active;tab.setTextColor(if(active)ink else muted)
            tab.background=RippleDrawable(ColorStateList.valueOf(0x18000000),object:Drawable(){
                override fun draw(canvas:Canvas){if(active){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(194,246,74)};canvas.drawRect(bounds.left+dp(10).toFloat(),bounds.bottom-dp(3).toFloat(),bounds.right-dp(10).toFloat(),bounds.bottom.toFloat(),p)}}
                override fun setAlpha(alpha:Int){};override fun setColorFilter(filter:android.graphics.ColorFilter?){};override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
            },rounded(Color.WHITE))
        }
    }
    private fun loadSession(source:String){
        val generation=++sessionGeneration
        viewerId=null
        authStatus.text="Проверяем аккаунт…"
        Thread {
            try {
                val id=GalleryClient.session(this,source)
                val name=if(id!=null)GalleryClient.rpc(this,source,"profile",org.json.JSONObject().put("id",id)).optString("display_name") else null
                runOnUiThread {
                    if(generation!=sessionGeneration || gallerySource!=source)return@runOnUiThread
                    viewerId=id;authStatus.text=name ?: "Гость";authButton.text=if(id==null)"Войти" else "Выйти"
                    if(galleryMode=="saved" || galleryMode=="following")loadGallery(false)
                    current?.let {loadDetail(it)}
                }
            }catch(e:Exception){runOnUiThread {if(generation==sessionGeneration){viewerId=null;authStatus.text="Гость";authButton.text="Войти"}}}
        }.start()
    }
    private fun showAuthDialog(){
        val source=gallerySource ?: GalleryClient.SITE
        gallerySource=source;PackageStore.setGallerySource(this,source)
        val fields=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(12))}
        val tabs=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        var signingUp=false
        val email=EditText(this).apply {hint="Электронная почта";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;setSingleLine(true);setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)}
        val password=EditText(this).apply {hint="Пароль";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setSingleLine(true);setAutofillHints(View.AUTOFILL_HINT_PASSWORD)}
        val name=EditText(this).apply {hint="Имя";setSingleLine(true);visibility=View.GONE;setAutofillHints(View.AUTOFILL_HINT_NAME)}
        lateinit var dialog:AlertDialog
        val submit=button("Войти по почте",true){
            val enteredName=if(signingUp)name.text.toString() else null
            dialog.dismiss();authenticate(source,email.text.toString(),password.text.toString(),enteredName)
        }
        val loginTab=button("Вход"){}
        val signupTab=button("Регистрация"){}
        fun update(){
            name.visibility=if(signingUp)View.VISIBLE else View.GONE
            submit.text=if(signingUp)"Зарегистрироваться" else "Войти по почте"
            loginTab.isSelected=!signingUp;signupTab.isSelected=signingUp
            loginTab.setTextColor(if(signingUp)muted else blue)
            signupTab.setTextColor(if(signingUp)blue else muted)
        }
        loginTab.setOnClickListener {signingUp=false;update()}
        signupTab.setOnClickListener {signingUp=true;update()}
        tabs.addView(loginTab,LinearLayout.LayoutParams(0,dp(48),1f))
        tabs.addView(signupTab,LinearLayout.LayoutParams(0,dp(48),1f))
        fields.addView(tabs);fields.addView(name);fields.addView(email);fields.addView(password)
        fields.addView(submit,LinearLayout.LayoutParams(-1,dp(50)).apply {topMargin=dp(12)})
        if(GalleryClient.isCloud(source)){
            fields.addView(text("или",13f,muted).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,dp(42)))
            fields.addView(button("Продолжить с Google"){
                try {val target=GalleryClient.googleUrl(this);dialog.dismiss();startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(target)))}
                catch(e:Exception){status.text=e.message ?: "Не удалось открыть Google"}
            },LinearLayout.LayoutParams(-1,dp(50)))
            fields.addView(text("При регистрации по почте подтвердите адрес по ссылке из письма.",12f,muted),LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        }
        update()
        dialog=AlertDialog.Builder(this).setTitle("Аккаунт Shader Gallery").setView(fields).setNegativeButton("Отмена",null).create()
        dialog.show()
    }
    private fun authenticate(source:String,email:String,password:String,name:String?){
        if(email.isBlank() || password.length<8 || (name!=null && name.isBlank())){status.text="Укажите почту и пароль от 8 символов";return}
        authStatus.text=if(name==null)"Входим…" else "Регистрируем…"
        Thread {
            try {
                val id=if(name==null)GalleryClient.signIn(this,source,email,password) else GalleryClient.signUp(this,source,email,password,name)
                runOnUiThread {if(gallerySource==source){
                    if(id==null){viewerId=null;authStatus.text="Гость";status.text="Проверьте почту и подтвердите регистрацию по ссылке из письма."}
                    else {viewerId=id;authStatus.text=if(name==null)email else name;authButton.text="Выйти";status.text="";loadGallery(false);current?.let{loadDetail(it)}}
                }}
            }catch(e:Exception){runOnUiThread{authStatus.text="Гость";status.text=e.message ?: "Не удалось войти"}}
        }.start()
    }
    private fun signOut(){
        val source=gallerySource ?: return
        Thread {
            try{GalleryClient.signOut(this,source)}catch(_:Exception){}
            runOnUiThread{viewerId=null;authStatus.text="Гость";authButton.text="Войти";if(galleryMode=="saved"||galleryMode=="following"){galleryMode="new";updateTabs()};loadGallery(false);current?.let{loadDetail(it)}}
        }.start()
    }
    private fun loadGallery(next:Boolean){
        val source=gallerySource ?: try{GalleryClient.base(address.text.toString()).also{gallerySource=it;PackageStore.setGallerySource(this,it)}}catch(e:Exception){galleryStatus.text=e.message ?: "Укажите адрес галереи";return}
        if(next && (galleryLoading || galleryCursor==null))return
        if(!next){galleryGeneration++;galleryCursor=null;galleryList.removeAllViews();more.visibility=View.GONE}
        gallerySignIn.visibility=View.GONE
        if((galleryMode=="following" || galleryMode=="saved") && viewerId==null){galleryLoading=false;galleryStatus.visibility=View.VISIBLE;galleryStatus.text="Войдите, чтобы увидеть эти работы.";gallerySignIn.visibility=View.VISIBLE;return}
        val generation=galleryGeneration
        val cursor=if(next)galleryCursor else null
        val mode=galleryMode
        val query=search.text.toString()
        val selectedCategory=category.selectedItem.toString().takeUnless{it==categories[0]} ?: ""
        galleryLoading=true
        galleryStatus.visibility=View.VISIBLE;galleryStatus.text="Загружаем работы…"
        more.isEnabled=false
        Thread {
            try {
                val page=GalleryClient.feed(this,source,mode,query,selectedCategory,cursor)
                runOnUiThread {
                    if(generation!=galleryGeneration)return@runOnUiThread
                    galleryLoading=false
                    galleryCursor=page.nextCursor
                    val existing=(0 until galleryList.childCount).mapNotNull { galleryList.getChildAt(it).tag as? String }.toSet()
                    page.items.filterNot {it.id in existing}.forEach {
                        val index=galleryList.childCount;val columns=galleryList.columnCount
                        galleryList.addView(galleryCard(it),android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(index/columns),android.widget.GridLayout.spec(index%columns)).apply {
                            width=dp((resources.configuration.screenWidthDp-40-(columns-1)*12)/columns);height=-2;topMargin=dp(16);if(index%columns>0)leftMargin=dp(12)
                        })
                    }
                    galleryStatus.text=if(galleryList.childCount==0)"Работ пока нет. Попробуйте другой запрос." else ""
                    galleryStatus.visibility=if(galleryList.childCount==0)View.VISIBLE else View.GONE
                    more.visibility=if(galleryCursor!=null)View.VISIBLE else View.GONE
                    more.isEnabled=true
                }
            } catch(e:Exception){runOnUiThread {
                if(generation!=galleryGeneration)return@runOnUiThread
                galleryLoading=false
                galleryStatus.visibility=View.VISIBLE;galleryStatus.text=e.message ?: "Не удалось загрузить галерею"
                more.visibility=if(next)View.VISIBLE else View.GONE
                more.isEnabled=true
            }}
        }.start()
    }
    private fun galleryCard(work:GalleryCard):View {
        val card=card().apply {tag=work.id;contentDescription="Открыть работу ${work.title}";isClickable=true;isFocusable=true;setOnClickListener {load(gallerySource ?: return@setOnClickListener,work.id,work.revisionId)}}
        val columns=galleryList.columnCount
        val artSize=(resources.configuration.screenWidthDp-40-(columns-1)*12)/columns
        card.addView(ImageView(this).apply {work.thumbnail()?.let {setImageBitmap(it)};scaleType=ImageView.ScaleType.CENTER_CROP;background=rounded(Color.rgb(231,232,236),Color.rgb(231,232,236),12);clipToOutline=true},LinearLayout.LayoutParams(-1,dp(artSize)).apply {bottomMargin=dp(9)})
        card.addView(text(work.title,16f,ink,true).apply {maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,0,0,dp(4))})
        card.addView(text(work.author,11f,muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,0,0,dp(4))})
        return card
    }
    private fun handleIntent(intent:Intent?){
        val uri=intent?.data ?: return
        if(uri.scheme=="shadergallery" && uri.host=="auth-callback"){
            val code=uri.getQueryParameter("code")
            val error=uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            if(code==null){status.text=error ?: "Не удалось завершить вход";return}
            gallerySource=GalleryClient.SITE;PackageStore.setGallerySource(this,GalleryClient.SITE);address.setText(GalleryClient.SITE)
            authStatus.text="Входим…"
            Thread {
                try {GalleryClient.completeGoogle(this,code);runOnUiThread {loadSession(GalleryClient.SITE);loadGallery(false)}}
                catch(e:Exception){runOnUiThread {authStatus.text="Гость";status.text=e.message ?: "Не удалось завершить вход"}}
            }.start()
            return
        }
        if(uri.scheme=="shadergallery" && uri.host=="work"){
            val id=uri.pathSegments.firstOrNull() ?: return
            val source=uri.getQueryParameter("source")?.let{if(it=="https://renjerstats.github.io")GalleryClient.SITE else it} ?: return
            load(source,id,uri.getQueryParameter("revision"));return
        }
        if(uri.scheme=="https" && uri.host=="renjerstats.github.io" && uri.pathSegments.size==3 && uri.pathSegments[0]=="shader-gallery" && uri.pathSegments[1]=="works"){
            load(GalleryClient.SITE,uri.pathSegments[2],uri.getQueryParameter("revision"))
        }
    }
    private fun loadFromAddress(){
        try {
            val url=URL(address.text.toString().trim());val uri=Uri.parse(url.toString())
            val cloud=url.host.equals("renjerstats.github.io",true)
            val source=GalleryClient.base(if(cloud)GalleryClient.SITE else "${url.protocol}://${url.authority}")
            val segments=if(cloud){require(uri.pathSegments.firstOrNull()=="shader-gallery"){"Некорректная ссылка"};uri.pathSegments.drop(1)}else uri.pathSegments
            if(segments.isEmpty()){
                val changed=gallerySource!=source
                gallerySource=source;PackageStore.setGallerySource(this,source);address.setText(source);if(changed)loadSession(source);loadGallery(false)
            }else{
                require(segments.size==2 && segments[0]=="works") { "Укажите адрес галереи или ссылку /works/…" }
                address.setText(source)
                load(source,segments[1],uri.getQueryParameter("revision"))
            }
        }catch(e:Exception){status.text=e.message ?: "Некорректная ссылка"}
    }
    private fun load(source:String,id:String,revision:String?){
        val base=try{GalleryClient.base(source)}catch(e:Exception){status.text=e.message ?: "Некорректный адрес";return}
        val sourceChanged=base!=gallerySource
        gallerySource=base;PackageStore.setGallerySource(this,base);address.setText(base)
        if(sourceChanged)loadSession(base)
        val request=++loadGeneration
        val previousReady=ready
        workSaved=false;saveButton.isEnabled=false
        ready=false;apply.isEnabled=false;status.text="Загружаем работу…"
        Thread {
            try {val shader=PackageClient.download(this,base,id,revision);runOnUiThread {if(request==loadGeneration){showPackage(shader,true);if(sourceChanged)loadGallery(false)}}}
            catch(e:Exception){runOnUiThread{if(request==loadGeneration){ready=previousReady;apply.isEnabled=previousReady;saveButton.isEnabled=current!=null;status.text=e.message ?: "Не удалось открыть работу"}}}
        }.start()
    }
    private fun showPackage(shader:ShaderPackage,fromNetwork:Boolean){
        brandMark.visibility=View.GONE;toolbarTitle.textSize=18f
        if(!detailOpen)galleryScrollY=rootScroll.scrollY
        detailOpen=true;gallerySection.visibility=View.GONE;detailSection.visibility=View.VISIBLE;bottomBar.visibility=View.VISIBLE;backButton.visibility=View.VISIBLE;saveButton.visibility=View.VISIBLE
        saveButton.isEnabled=false;saveButton.setImageDrawable(GalleryIcon("bookmark",ink));saveButton.contentDescription="Сохранить работу"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(resumed && !previewRunning){preview.start();previewRunning=true}
        current=shader;values=PackageStore.values(this,shader);title.text=shader.title;byline.text=shader.authorName
        buildControls(shader);reveal(controls);preview.setShader(shader,values.toMap());status.text="Загружаем превью…";loadDetail(shader)
        rootScroll.post {rootScroll.scrollTo(0,0)}
    }
    private fun buildControls(shader:ShaderPackage){
        controls.removeAllViews()
        for(parameter in shader.parameters){
            val heading=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
            val label=text(parameter.label,15f,ink,true);heading.addView(label,LinearLayout.LayoutParams(0,-2,1f))
            val output=text("",12f,muted)
            if(parameter.type=="float")heading.addView(output)
            heading.addView(icon("reset","Сбросить: ${parameter.label}"){values[parameter.name]=parameter.defaultValue;changed(shader);buildControls(shader)},LinearLayout.LayoutParams(dp(48),dp(48)))
            controls.addView(heading)
            if(parameter.type=="float"){
                val value=values[parameter.name]?.toFloatOrNull() ?: parameter.defaultValue.toFloat()
                output.text="%.2f".format(value)
                val slider=SeekBar(this).apply {max=100;progress=((value-parameter.min)/(parameter.max-parameter.min)*100).roundToInt().coerceIn(0,100);styleSlider(this);contentDescription=parameter.label}
                controls.addView(slider,LinearLayout.LayoutParams(-1,dp(44)))
                slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(view:SeekBar?,progress:Int,fromUser:Boolean){if(fromUser){val v=parameter.min+(parameter.max-parameter.min)*progress/100f;values[parameter.name]=v.toString();output.text="%.2f".format(v);changed(shader)}}
                    override fun onStartTrackingTouch(view:SeekBar?){};override fun onStopTrackingTouch(view:SeekBar?){}
                })
            }else{
                val color=Color.parseColor(values[parameter.name] ?: parameter.defaultValue)
                val hsv=FloatArray(3);Color.colorToHSV(color,hsv)
                val colorRow=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
                val swatch=button(""){editColor(shader,parameter.name,parameter.label)}.apply {
                    contentDescription="Выбрать точный цвет: ${parameter.label}"
                    background=colorSwatch(color)
                }
                val hue=SeekBar(this).apply {
                    max=360;progress=hsv[0].roundToInt();contentDescription="Оттенок: ${parameter.label}";splitTrack=false
                    setPadding(dp(12),0,dp(12),0)
                    progressTintList=null;progressBackgroundTintList=null;thumbTintList=null
                    progressDrawable=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xffff7777.toInt(),0xffffff77.toInt(),0xff77ff99.toInt(),0xff77ddff.toInt(),0xff7777ff.toInt(),0xffff77dd.toInt(),0xffff7777.toInt())).apply {cornerRadius=dp(9).toFloat();setSize(dp(200),dp(14))}
                    thumb=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(color);setStroke(dp(3),Color.WHITE);setSize(dp(24),dp(24))}
                    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                        override fun onProgressChanged(view:SeekBar?,progress:Int,fromUser:Boolean){if(fromUser){hsv[0]=progress.toFloat();val selected=Color.HSVToColor(hsv);values[parameter.name]="#%06x".format(selected and 0xffffff);(thumb as GradientDrawable).setColor(selected);swatch.background=colorSwatch(selected);changed(shader)}}
                        override fun onStartTrackingTouch(view:SeekBar?){};override fun onStopTrackingTouch(view:SeekBar?){}
                    })
                }
                colorRow.addView(hue,LinearLayout.LayoutParams(0,dp(48),1f));colorRow.addView(swatch,LinearLayout.LayoutParams(dp(48),dp(48)).apply {leftMargin=dp(8)})
                controls.addView(colorRow)
            }
        }
    }
    private fun editColor(shader:ShaderPackage,name:String,label:String){
        val original=Color.parseColor(values[name] ?: "#ffffff")
        val channels=intArrayOf(Color.red(original),Color.green(original),Color.blue(original))
        val panel=row();val sample=View(this).apply {background=rounded(original)};panel.addView(sample,LinearLayout.LayoutParams(-1,dp(54)))
        listOf("Красный","Зелёный","Синий").forEachIndexed {index,channel->
            panel.addView(text(channel,13f,muted));panel.addView(SeekBar(this).apply {max=255;progress=channels[index];styleSlider(this);contentDescription=channel;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(view:SeekBar?,progress:Int,fromUser:Boolean){channels[index]=progress;sample.background=rounded(Color.rgb(channels[0],channels[1],channels[2]))}
                override fun onStartTrackingTouch(view:SeekBar?){};override fun onStopTrackingTouch(view:SeekBar?){}
            })},LinearLayout.LayoutParams(-1,dp(48)))
        }
        AlertDialog.Builder(this).setTitle(label).setView(panel).setPositiveButton("Применить"){_,_->values[name]="#%02x%02x%02x".format(channels[0],channels[1],channels[2]);changed(shader);buildControls(shader)}.setNegativeButton("Отмена",null).show()
    }
    private fun loadDetail(shader:ShaderPackage){
        val source=gallerySource ?: return
        socialPanel.removeAllViews();socialPanel.addView(text("Загружаем обсуждение…",13f,muted))
        Thread {
            try {
                val detail=GalleryClient.rpc(this,source,"work",org.json.JSONObject().put("id",shader.workId).put("revision_id",shader.revisionId))
                runOnUiThread {if(current?.revisionId==shader.revisionId && gallerySource==source)renderSocial(detail,shader)}
            }catch(_:Exception){runOnUiThread{if(current?.revisionId==shader.revisionId){socialPanel.removeAllViews();socialPanel.addView(text("Обсуждение доступно при подключении к галерее.",13f,muted))}}}
        }.start()
    }
    private fun renderSocial(detail:org.json.JSONObject,shader:ShaderPackage){
        socialPanel.removeAllViews()
        val work=detail.getJSONObject("work")
        val author=work.getJSONObject("author")
        val reactions=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        val liked=work.optBoolean("liked")
        val saved=work.optBoolean("saved")
        workSaved=saved;saveButton.isEnabled=true;saveButton.setImageDrawable(GalleryIcon(if(saved)"saved" else "bookmark",ink));saveButton.contentDescription=if(saved)"Удалить из сохранённого" else "Сохранить работу"
        reactions.addView(button("${if(liked)"Нравится вам" else "Нравится"} · ${work.optInt("likes_count")}") {socialAction("like",org.json.JSONObject().put("work_id",shader.workId).put("active",!liked),shader)},LinearLayout.LayoutParams(0,dp(46),1f))
        reactions.addView(button(if(saved)"Сохранено" else "Сохранить") {socialAction("save",org.json.JSONObject().put("work_id",shader.workId).put("active",!saved),shader)},LinearLayout.LayoutParams(0,dp(46),1f).apply {leftMargin=dp(8)})
        socialPanel.addView(reactions,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        if(viewerId!=work.optString("author_id")){
            val following=author.optBoolean("is_following")
            socialPanel.addView(button(if(following)"Отписаться от автора" else "Подписаться на автора") {socialAction("follow",org.json.JSONObject().put("author_id",work.getString("author_id")).put("active",!following),shader)},LinearLayout.LayoutParams(-1,dp(46)).apply {topMargin=dp(9)})
        }
        val comments=detail.getJSONArray("comments")
        socialPanel.addView(text("Комментарии · ${comments.length()}",18f,ink,true).apply {setPadding(0,dp(18),0,dp(8))})
        if(comments.length()==0)socialPanel.addView(text("Пока нет комментариев.",13f,muted))
        for(index in maxOf(0,comments.length()-30) until comments.length()){
            val comment=comments.getJSONObject(index)
            socialPanel.addView(text(comment.getJSONObject("author").getString("display_name"),13f,ink).apply {typeface=Typeface.DEFAULT_BOLD})
            socialPanel.addView(text(comment.getString("body"),13f,muted).apply {setPadding(0,0,0,dp(10))})
        }
        val input=EditText(this).apply {hint="Ваш комментарий";minLines=2;maxLines=4;setTextColor(ink);textSize=14f;background=rounded(Color.WHITE,line,12);setPadding(dp(12),dp(9),dp(12),dp(9))}
        socialPanel.addView(input,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        socialPanel.addView(button("Отправить",true) {
            val body=input.text.toString().trim()
            if(body.isNotEmpty())socialAction("comment",org.json.JSONObject().put("work_id",shader.workId).put("request_id",UUID.randomUUID().toString()).put("body",body),shader)
        },LinearLayout.LayoutParams(-1,dp(46)).apply {topMargin=dp(8)})
    }
    private fun socialAction(action:String,payload:org.json.JSONObject,shader:ShaderPackage){
        if(viewerId==null){showAuthDialog();return}
        if(socialBusy)return
        socialBusy=true;saveButton.isEnabled=false
        val source=gallerySource ?: return
        status.text="Сохраняем…"
        Thread {
            try{GalleryClient.rpc(this,source,action,payload);runOnUiThread {socialBusy=false;status.text="";loadDetail(shader);if(action=="save" || action=="follow")loadGallery(false)}}
            catch(e:Exception){runOnUiThread{socialBusy=false;saveButton.isEnabled=true;status.text=e.message ?: "Не удалось сохранить"}}
        }.start()
    }
    private fun changed(shader:ShaderPackage){PackageStore.saveValues(this,shader,values);preview.setValues(values.toMap())}
    private fun installWallpaper(){val shader=current ?: return;if(!ready)return;PackageStore.saveValues(this,shader,values);PackageStore.select(this,shader);val intent=Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,ComponentName(this,GalleryWallpaperService::class.java));try{startActivity(intent)}catch(e:Exception){status.text="Не удалось открыть системный экран обоев: ${e.message}"}}
    override fun onResume(){super.onResume();resumed=true;if(current!=null && detailOpen){window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(::preview.isInitialized && !previewRunning){preview.start();previewRunning=true}}}
    override fun onPause(){resumed=false;if(previewRunning){preview.stop();previewRunning=false};window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause()}
}

private class GalleryPreview(context:Activity,private val onStatus:(Boolean,String)->Unit):GLSurfaceView(context),GLSurfaceView.Renderer,SensorEventListener {
    private val handler=Handler(Looper.getMainLooper())
    private val sensorManager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    @Volatile private var pending:ShaderPackage?=null
    @Volatile private var values:Map<String,String> = emptyMap()
    @Volatile private var tilt=floatArrayOf(0f,0f,0f)
    @Volatile private var mouse=floatArrayOf(0f,0f,0f,0f)
    private var program:ShaderProgram?=null
    private var active:ShaderPackage?=null
    private var running=false
    @Volatile var isPaused=!android.animation.ValueAnimator.areAnimatorsEnabled()
        private set
    private val tick=object:Runnable{override fun run(){if(running && !isPaused){requestRender();handler.postDelayed(this,33)}}}
    init{setEGLContextClientVersion(3);setRenderer(this);renderMode=RENDERMODE_WHEN_DIRTY}
    fun setShader(shader:ShaderPackage,values:Map<String,String>){this.values=values;pending=shader;requestRender()}
    fun setValues(values:Map<String,String>){this.values=values;requestRender()}
    fun start(){onResume();running=true;handler.removeCallbacks(tick);handler.post(tick);if(sensor!=null && !isPaused)sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)}
    fun setPaused(paused:Boolean){isPaused=paused;handler.removeCallbacks(tick);sensorManager.unregisterListener(this);if(running && !paused){handler.post(tick);if(sensor!=null)sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)};requestRender()}
    fun stop(){running=false;handler.removeCallbacks(tick);sensorManager.unregisterListener(this);onPause()}
    override fun onSurfaceCreated(gl:javax.microedition.khronos.opengles.GL10?,config:javax.microedition.khronos.egl.EGLConfig?){program=null;active?.let{pending=it}}
    override fun onSurfaceChanged(gl:javax.microedition.khronos.opengles.GL10?,width:Int,height:Int){GLES30.glViewport(0,0,width,height)}
    override fun onDrawFrame(gl:javax.microedition.khronos.opengles.GL10?){
        pending?.let { shader ->
            pending=null
            try{val replacement=ShaderProgram(shader);replacement.create();program?.destroy();program=replacement;active=shader;post{onStatus(true,"Готово к установке")}}
            catch(e:Exception){post{onStatus(false,"Ошибка шейдера: ${e.message}")}}
        }
        GLES30.glClearColor(0.04f,0.03f,0.08f,1f);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if(isPaused)program?.pause() else program?.resume()
        program?.draw(width,height,PackageStore.quality(context),values,tilt,mouse)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{mouse=floatArrayOf(event.x/width,1f-event.y/height,if(event.action==MotionEvent.ACTION_DOWN||event.action==MotionEvent.ACTION_MOVE)1f else 0f,0f);requestRender();return true}
    override fun onSensorChanged(event:SensorEvent){val matrix=FloatArray(9);val angles=FloatArray(3);SensorManager.getRotationMatrixFromVector(matrix,event.values);SensorManager.getOrientation(matrix,angles);tilt=floatArrayOf(angles[2],angles[1],angles[0])}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
}
