package gallery.shader.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * One work. The preview stays pinned on top while the tabs below change:
 * About (reactions, author), Settings (live parameters) and Discussion (chat-like comments).
 * The bottom bar is the primary action: install as wallpaper, or the comment composer.
 */
internal class WorkScreen(private val host:MainActivity) {
    private val ui=host.ui
    private val p=ui.p
    private val account get()=host.account
    val view=FrameLayout(host)

    private lateinit var title:TextView
    private lateinit var subtitle:TextView
    private val body=LinearLayout(host)
    private val previewFrame=FrameLayout(host)
    private val panel=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL}
    private val preview=GalleryPreview(host) {ok,message->
        ready=ok;previewError=if(ok)null else message
        updateApply();showPreviewError()
    }
    private lateinit var errorBox:TextView
    private lateinit var pause:ImageButton
    private lateinit var segmented:Segmented
    private val scroll=ScrollView(host).apply {isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER}
    private val content=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(28))}
    private val bottom=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL}
    private lateinit var primary:android.widget.Button
    private val composer=LinearLayout(host).apply {gravity=Gravity.BOTTOM}
    private lateinit var input:EditText
    private lateinit var send:ImageButton

    private var generation=0
    private var source=""
    private var workId=""
    private var requestedRevision:String?=null
    private var hint:GalleryCard?=null
    private var pkg:ShaderPackage?=null
    private var detail:JSONObject?=null
    private var loadError:String?=null
    private var values=mutableMapOf<String,String>()
    private var tab=0
    private var ready=false
    private var previewError:String?=null
    private var previewRunning=false
    private var busy=false
    private var sending=false
    private var pendingBody=""
    private var pendingId=""
    private var insets=Insets()
    private val visible get()=view.visibility==View.VISIBLE

    init {
        view.setBackgroundColor(p.paper);view.visibility=View.GONE
        val column=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL}
        view.fill(column)
        column.addView(buildTopBar())
        body.orientation=LinearLayout.VERTICAL
        column.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        buildPreview()
        buildPanel()
        body.addView(previewFrame);body.addView(panel)
        applyLayout()
        account.listen {if(visible && workId.isNotEmpty()){loadDetail(generation);updateBottom()}}
    }

    private fun buildTopBar():View=LinearLayout(host).apply {
        gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(4),0,ui.dp(4),0);minimumHeight=ui.dp(56)
        addView(ui.iconButton("back","Назад") {host.closeWork()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        val titles=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(4),0,ui.dp(8),0)}
        title=ui.title("",19f,maxLines=1);subtitle=ui.text("",13f,p.muted,ui.sansMedium,1)
        titles.addView(title);titles.addView(subtitle)
        addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        addView(ui.iconButton("more","Ещё") {showMenu()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
    }

    private fun buildPreview() {
        previewFrame.setBackgroundColor(p.fill)
        previewFrame.fill(preview)
        errorBox=ui.text("",12f,Color_WHITE,ui.sansMedium,5).apply {
            background=ui.shape(p.scrim,14f);setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));visibility=View.GONE
        }
        previewFrame.addView(errorBox,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply {setMargins(ui.dp(12),0,ui.dp(68),ui.dp(12))})
        pause=ui.iconButton(if(preview.isPaused)"play" else "pause",if(preview.isPaused)"Включить движение" else "Приостановить движение",Color_WHITE) {
            preview.setPaused(!preview.isPaused);updatePause()
        }
        pause.background=ui.ripple(ui.oval(p.scrim),circle=true)
        previewFrame.addView(pause,FrameLayout.LayoutParams(ui.dp(44),ui.dp(44),Gravity.BOTTOM or Gravity.END).apply {setMargins(0,0,ui.dp(12),ui.dp(12))})
        pause.setPadding(ui.dp(11),ui.dp(11),ui.dp(11),ui.dp(11))
    }

    private fun updatePause() {
        pause.setImageDrawable(ui.icon(if(preview.isPaused)"play" else "pause",Color_WHITE))
        pause.contentDescription=if(preview.isPaused)"Включить движение" else "Приостановить движение"
    }

    private fun showPreviewError() {
        val message=previewError
        errorBox.visibility=if(message==null)View.GONE else View.VISIBLE
        errorBox.text=message?.take(400) ?: ""
    }

    private fun buildPanel() {
        segmented=Segmented(ui,listOf("Работа","Настройки","Обсуждение"),0) {setTab(it)}
        panel.addView(segmented,LinearLayout.LayoutParams(-1,-2).apply {setMargins(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(4))})
        scroll.addView(content)
        panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        bottom.background=ui.shape(p.surface,0f)
        bottom.addView(ui.divider(),LinearLayout.LayoutParams(-1,1))
        primary=ui.button("Загружаем…","primary") {install()}
        bottom.addView(primary,LinearLayout.LayoutParams(-1,ui.dp(52)).apply {setMargins(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10))})
        buildComposer()
        bottom.addView(composer,LinearLayout.LayoutParams(-1,-2).apply {setMargins(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8))})
        panel.addView(bottom,LinearLayout.LayoutParams(-1,-2))
    }

    private fun buildComposer() {
        input=EditText(host).apply {
            hint="Комментарий";contentDescription="Ваш комментарий";textSize=16f;typeface=ui.sans;includeFontPadding=false
            setTextColor(p.ink);setHintTextColor(p.muted);background=ui.shape(p.fill,24f)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines=4;filters=arrayOf(android.text.InputFilter.LengthFilter(2000));minimumHeight=ui.dp(48);setPadding(ui.dp(18),ui.dp(12),ui.dp(18),ui.dp(12))
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){updateSend()}
                override fun afterTextChanged(s:Editable?){}
            })
        }
        send=ui.iconButton("send","Отправить комментарий",p.onAccent) {sendComment()}
        send.background=ui.ripple(ui.oval(p.accent),circle=true)
        composer.addView(input,LinearLayout.LayoutParams(0,-2,1f))
        composer.addView(send,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply {leftMargin=ui.dp(8)})
        updateSend()
    }

    private fun updateSend(){send.isEnabled=!sending && input.text.isNotBlank();send.alpha=if(send.isEnabled)1f else .5f}

    /** Portrait stacks preview over details; landscape puts them side by side. */
    fun applyLayout() {
        val landscape=host.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        val metrics=host.resources.displayMetrics
        body.orientation=if(landscape)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if(landscape) {
            previewFrame.layoutParams=LinearLayout.LayoutParams(0,-1,1f)
            panel.layoutParams=LinearLayout.LayoutParams(0,-1,1f)
        } else {
            previewFrame.layoutParams=LinearLayout.LayoutParams(-1,min(metrics.widthPixels,(metrics.heightPixels*.32f).toInt()))
            panel.layoutParams=LinearLayout.LayoutParams(-1,0,1f)
        }
        applyInsets(insets)
    }

    fun applyInsets(next:Insets) {
        insets=next
        view.setPadding(next.left,next.top,next.right,0)
        bottom.setPadding(0,0,0,next.bottomPad)
        val landscape=host.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        previewFrame.visibility=if(!landscape && next.ime>0)View.GONE else View.VISIBLE
    }

    fun open(newSource:String,id:String,revision:String?,card:GalleryCard?) {
        val current=++generation
        source=newSource;workId=id;requestedRevision=revision;hint=card
        pkg=null;detail=null;loadError=null;ready=false;previewError=null;busy=false;sending=false
        values=mutableMapOf();input.setText("");pendingBody=""
        title.text=card?.title ?: "Загружаем работу…";subtitle.text=card?.author ?: ""
        showPreviewError();segmented.select(0);tab=0
        updateApply();updateBottom();renderTab(true)
        val viewer=account.viewerId
        // Page data from an earlier visit shows at once; the fresh copy replaces it when it arrives.
        GalleryClient.cachedWork(host,newSource,id,revision,viewer)?.let {detail=it;renderTab(false)}
        host.background({PackageClient.open(host,newSource,id,revision,viewer)},{error->
            if(current==generation){loadError=friendly(error,"Не удалось открыть работу");updateApply();renderTab(true)}
        }) {opened->
            if(current==generation){
                opened.detail?.let {detail=it}
                show(opened.shader)
                if(opened.detail==null)loadDetail(current)
            }
        }
    }

    private fun show(shader:ShaderPackage) {
        pkg=shader;values=PackageStore.values(host,shader)
        title.text=shader.title;subtitle.text=shader.authorName
        ready=false;previewError=null;showPreviewError();updatePause()
        preview.setShader(shader,values.toMap())
        if(visible && !previewRunning && host.resumedNow){preview.start();previewRunning=true}
        updateApply();renderTab(false)
    }

    private fun loadDetail(current:Int) {
        val target=source;val id=workId;val revision=pkg?.revisionId ?: requestedRevision
        val viewer=account.viewerId
        host.background({GalleryClient.work(host,target,id,revision,viewer)},{}) {data->
            if(current==generation && target==source){detail=data;renderTab(false)}
        }
    }

    private fun work()=detail?.optJSONObject("work")

    private fun setTab(index:Int) {
        tab=index;updateBottom();renderTab(true)
        if(index!=2)(host.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
    }

    private fun updateBottom() {
        primary.visibility=if(tab==2)View.GONE else View.VISIBLE
        composer.visibility=if(tab==2 && account.signedIn)View.VISIBLE else View.GONE
        if(tab==2 && !account.signedIn) {
            primary.visibility=View.VISIBLE;primary.text="Войдите, чтобы комментировать";primary.isEnabled=true
            primary.setOnClickListener {AuthSheet.show(host,"Войдите, чтобы писать комментарии.")}
        } else {
            primary.setOnClickListener {install()}
            updateApply()
        }
    }

    private fun updateApply() {
        if(tab==2 && !account.signedIn)return
        when {
            pkg==null->{primary.text=if(loadError!=null)"Работа недоступна" else "Загружаем…";primary.isEnabled=false}
            previewError!=null->{primary.text="Шейдер не запустился";primary.isEnabled=false}
            !ready->{primary.text="Проверяем шейдер…";primary.isEnabled=false}
            else->{primary.text="Установить как обои";primary.isEnabled=true}
        }
    }

    private fun renderTab(reset:Boolean) {
        content.removeAllViews()
        val error=loadError
        if(error!=null && pkg==null) {
            content.addView(emptyState(ui,"info","Не удалось открыть работу",error,"Повторить") {open(source,workId,requestedRevision,hint)})
        } else when(tab) {
            0->renderAbout()
            1->renderSettings()
            else->renderComments()
        }
        if(reset)scroll.scrollTo(0,0)
        if(tab==2 && reset)scroll.post {scroll.fullScroll(View.FOCUS_DOWN)}
    }

    private fun gap(heightDp:Int)=ui.spacer(heightDp)

    private fun pill(icon:String,label:String,tint:Int,action:()->Unit)=LinearLayout(host).apply {
        gravity=Gravity.CENTER;background=ui.ripple(ui.shape(p.fill,100f),100f);isClickable=true;isFocusable=true;contentDescription=label
        addView(ui.iconView(icon,tint),LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)))
        addView(ui.text(label,14f,p.ink,ui.sansSemi,1).apply {setPadding(ui.dp(8),0,0,0)})
        setOnClickListener {action()}
    }

    private fun renderAbout() {
        val work=work()
        val shader=pkg
        content.add(ui.title(shader?.title ?: hint?.title ?: "",28f)) {bottomMargin=ui.dp(6)}
        val category=work?.optString("category")?.takeIf {it.isNotBlank()} ?: hint?.category ?: ""
        val created=shortDate(work?.optString("created_at") ?: hint?.createdAt ?: "")
        content.add(ui.text(listOf(category,created).filter {it.isNotBlank()}.joinToString(" · "),13f,p.muted,ui.sansMedium))

        val authorName=work?.optJSONObject("author")?.optString("display_name")?.takeIf {it.isNotBlank()} ?: shader?.authorName ?: hint?.author ?: ""
        val authorRow=LinearLayout(host).apply {gravity=Gravity.CENTER_VERTICAL}
        authorRow.addView(AvatarView(ui,authorName,40))
        val authorText=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(12),0,ui.dp(8),0)}
        authorText.addView(ui.text(authorName,16f,p.ink,ui.sansSemi,1))
        authorText.addView(ui.text("Автор",13f,p.muted))
        authorRow.addView(authorText,LinearLayout.LayoutParams(0,-2,1f))
        val authorId=work?.optString("author_id") ?: ""
        if(work!=null && authorId.isNotEmpty() && authorId!=account.viewerId) {
            val following=work.optJSONObject("author")?.optBoolean("is_following") ?: false
            authorRow.addView(ui.button(if(following)"Вы подписаны" else "Подписаться",if(following)"outline" else "tonal") {react("follow")},LinearLayout.LayoutParams(-2,ui.dp(44)))
        }
        content.add(authorRow) {topMargin=ui.dp(18)}

        val actions=LinearLayout(host)
        if(work==null) {
            actions.addView(View(host).apply {background=ui.shape(p.fill,100f)},LinearLayout.LayoutParams(-1,ui.dp(48)))
        } else {
            val liked=work.optBoolean("liked");val saved=work.optBoolean("saved")
            actions.addView(pill(if(liked)"heart_filled" else "heart","${work.optInt("likes_count")}",if(liked)p.danger else p.muted) {react("like")},LinearLayout.LayoutParams(0,ui.dp(48),1f))
            actions.addView(pill(if(saved)"saved" else "bookmark",if(saved)"Сохранено" else "Сохранить",if(saved)p.link else p.muted) {react("save")},LinearLayout.LayoutParams(0,ui.dp(48),1.5f).apply {leftMargin=ui.dp(8)})
            actions.addView(pill("share","Отправить",p.muted) {share()},LinearLayout.LayoutParams(0,ui.dp(48),1.5f).apply {leftMargin=ui.dp(8)})
        }
        content.add(actions) {topMargin=ui.dp(16)}

        val description=work?.optString("description")?.takeIf {it.isNotBlank()} ?: hint?.description?.takeIf {it.isNotBlank()}
        if(description!=null)content.add(ui.body(description,16f,p.ink)) {topMargin=ui.dp(16)}

        val tags=work?.optJSONArray("tags")?.let {array->(0 until array.length()).map {array.optString(it)}} ?: hint?.tags ?: emptyList()
        if(tags.isNotEmpty()) {
            val row=LinearLayout(host)
            tags.filter {it.isNotBlank()}.take(8).forEach {tag->
                row.addView(ui.chip("#$tag").apply {ui.styleChip(this,false);isClickable=false},LinearLayout.LayoutParams(-2,ui.dp(34)).apply {rightMargin=ui.dp(8)})
            }
            content.add(HorizontalScrollView(host).apply {isHorizontalScrollBarEnabled=false;addView(row)}) {topMargin=ui.dp(14)}
        }

        val parent=detail?.optJSONObject("parent")
        if(parent!=null) {
            content.add(linkRow("Ремикс на работу",parent.optString("title"),parent.optString("author")) {
                host.openWork(source,parent.getString("work_id"),parent.optString("revision_id").takeIf {it.isNotBlank()},null)
            }) {topMargin=ui.dp(18)}
        }
        val refs=work?.optJSONObject("revision")?.optJSONObject("dna_origin")?.optJSONArray("references")
        if(refs!=null && refs.length()>0) {
            val card=ui.card().apply {setPadding(0,ui.dp(14),0,ui.dp(6))}
            card.addView(ui.text("Создано в Shader DNA Studio",14f,p.ink,ui.sansSemi).apply {setPadding(ui.dp(16),0,ui.dp(16),ui.dp(2))})
            card.addView(ui.text("Референсы, на которых основана работа",13f,p.muted).apply {setPadding(ui.dp(16),0,ui.dp(16),ui.dp(8))})
            for(i in 0 until refs.length()) {
                val ref=refs.getJSONObject(i)
                card.addView(ui.divider(),LinearLayout.LayoutParams(-1,1).apply {leftMargin=ui.dp(16)})
                card.addView(refRow(ref.optString("title"),"${ref.optString("author")} · ${ref.optString("license")}") {
                    host.openWork(source,ref.getString("work_id"),ref.optString("revision_id").takeIf {it.isNotBlank()},null)
                })
            }
            content.add(card) {topMargin=ui.dp(18)}
        }
        content.add(ui.button("Использовать в DNA Studio","tonal") {host.openDna(pkg)},height=ui.dp(52)) {topMargin=ui.dp(18)}
        shader?.let {content.add(ui.text("Лицензия ${it.license}",13f,p.muted).apply {gravity=Gravity.CENTER}) {topMargin=ui.dp(18)}}
    }

    private fun refRow(titleText:String,meta:String,action:()->Unit)=LinearLayout(host).apply {
        gravity=Gravity.CENTER_VERTICAL;minimumHeight=ui.dp(56);setPadding(ui.dp(16),ui.dp(8),ui.dp(12),ui.dp(8))
        background=ui.ripple(null);isClickable=true;isFocusable=true
        val column=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL}
        column.addView(ui.text(titleText,15f,p.ink,ui.sansSemi,1))
        column.addView(ui.text(meta,13f,p.muted,ui.sans,1))
        addView(column,LinearLayout.LayoutParams(0,-2,1f))
        addView(ui.iconView("chevron",p.muted),LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)))
        setOnClickListener {action()}
    }

    private fun linkRow(label:String,titleText:String,author:String,action:()->Unit):View {
        val card=ui.card()
        card.addView(ui.text(label,13f,p.muted).apply {setPadding(ui.dp(16),ui.dp(12),ui.dp(16),0)})
        card.addView(refRow(titleText,author,action))
        return card
    }

    private fun renderSettings() {
        val shader=pkg
        if(shader==null){content.addView(ui.text("Настройки появятся после загрузки работы.",15f,p.muted));return}
        if(shader.parameters.isEmpty()) {
            content.addView(emptyState(ui,"tune","Настроек нет","Автор не добавил параметры. Работа выглядит так, как задумана."))
            return
        }
        content.add(ui.body("Изменения сразу видны на превью и применяются к обоям.",14f,p.muted)) {bottomMargin=ui.dp(8)}
        val parameters=ParameterPanel(ui,shader.parameters,values) {changed(shader)}
        content.add(parameters)
        content.add(ui.button("Сбросить все настройки","text") {parameters.resetAll();host.toast("Настройки сброшены")},height=ui.dp(48)) {topMargin=ui.dp(12)}
    }

    private fun changed(shader:ShaderPackage) {
        PackageStore.saveValues(host,shader,values);preview.setValues(values.toMap())
    }

    private fun renderComments() {
        val comments=detail?.optJSONArray("comments")
        val count=comments?.length() ?: 0
        content.add(ui.title(if(count>0)"Комментарии · $count" else "Комментарии",20f)) {bottomMargin=ui.dp(10)}
        if(comments==null){content.add(ui.text("Загружаем обсуждение…",15f,p.muted));return}
        if(count==0) {
            content.add(ui.text("Пока нет комментариев",16f,p.ink,ui.sansSemi).apply {gravity=Gravity.CENTER}) {topMargin=ui.dp(28)}
            content.add(ui.text("Напишите первым — автору будет приятно.",14f,p.muted).apply {gravity=Gravity.CENTER}) {topMargin=ui.dp(4)}
            return
        }
        for(index in max(0,count-100) until count) {
            val comment=comments.getJSONObject(index)
            val author=comment.optJSONObject("author")?.optString("display_name").orEmpty().ifBlank {"Автор"}
            val row=LinearLayout(host).apply {setPadding(0,ui.dp(8),0,ui.dp(8))}
            row.addView(AvatarView(ui,author,36),LinearLayout.LayoutParams(-2,-2))
            val bubble=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;background=ui.shape(p.fill,18f);setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(12))}
            val head=LinearLayout(host).apply {gravity=Gravity.CENTER_VERTICAL}
            head.addView(ui.text(author,14f,p.link,ui.sansSemi,1),LinearLayout.LayoutParams(0,-2,1f))
            head.addView(ui.text(shortDate(comment.optString("created_at")),12f,p.muted).apply {setPadding(ui.dp(8),0,0,0)})
            bubble.addView(head)
            bubble.addView(ui.body(comment.optString("body"),15f,p.ink).apply {setPadding(0,ui.dp(4),0,0)})
            row.addView(bubble,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=ui.dp(10)})
            content.addView(row)
        }
    }

    private fun react(action:String) {
        if(!account.signedIn){AuthSheet.show(host,"Войдите, чтобы ставить отметки, сохранять работы и подписываться на авторов.");return}
        val work=work() ?: return
        if(busy)return
        val snapshot=detail.toString()
        val payload=JSONObject()
        when(action) {
            "like"->{
                val on=!work.optBoolean("liked")
                work.put("liked",on).put("likes_count",max(0,work.optInt("likes_count")+if(on)1 else -1))
                payload.put("work_id",workId).put("active",on)
            }
            "save"->{
                val on=!work.optBoolean("saved");work.put("saved",on)
                payload.put("work_id",workId).put("active",on)
                host.toast(if(on)"Добавлено в сохранённое" else "Убрано из сохранённого")
            }
            "follow"->{
                val author=work.getJSONObject("author");val on=!author.optBoolean("is_following")
                author.put("is_following",on)
                payload.put("author_id",work.getString("author_id")).put("active",on)
                host.toast(if(on)"Вы подписались на ${author.optString("display_name")}" else "Подписка отменена")
            }
        }
        busy=true;renderTab(false)
        val current=generation
        host.background({GalleryClient.rpc(host,source,action,payload)},{error->
            busy=false
            if(current==generation){detail=JSONObject(snapshot);renderTab(false)}
            host.toast(friendly(error,"Не удалось сохранить"))
        }) {
            busy=false
            if(action!="like")host.libraryChanged()
        }
    }

    private fun sendComment() {
        val text=input.text.toString().trim()
        if(text.isEmpty() || sending)return
        if(text!=pendingBody){pendingBody=text;pendingId=UUID.randomUUID().toString()}
        sending=true;updateSend()
        val current=generation
        host.background({GalleryClient.rpc(host,source,"comment",JSONObject().put("work_id",workId).put("request_id",pendingId).put("body",text))},{error->
            sending=false;updateSend();host.toast(friendly(error,"Не удалось отправить"))
        }) {
            sending=false;pendingBody="";input.setText("");updateSend()
            if(current==generation){
                val target=source;val revision=pkg?.revisionId ?: requestedRevision
                val viewer=account.viewerId
                host.background({GalleryClient.work(host,target,workId,revision,viewer)},{}) {data->
                    if(current==generation){detail=data;renderTab(false);if(tab==2)scroll.post {scroll.fullScroll(View.FOCUS_DOWN)}}
                }
            }
        }
    }

    private fun link()="$source/works/$workId?revision=${pkg?.revisionId ?: requestedRevision ?: ""}"

    private fun share() {
        startChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,link())})
    }

    private fun startChooser(intent:Intent){host.startActivity(Intent.createChooser(intent,"Поделиться"))}

    private fun showMenu() {
        ui.sheet(pkg?.title ?: hint?.title) {content,dialog->
            content.addView(ui.actionRow("sparkle","Использовать в DNA Studio") {dialog.dismiss();host.openDna(pkg)})
            content.addView(ui.actionRow("share","Поделиться ссылкой") {dialog.dismiss();share()})
            content.addView(ui.actionRow("copy","Скопировать ссылку") {
                dialog.dismiss()
                (host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Shader Gallery",link()))
                host.toast("Ссылка скопирована")
            })
            content.addView(ui.actionRow("external","Открыть на сайте") {dialog.dismiss();host.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link())))})
        }.show()
    }

    private fun install() {
        val shader=pkg ?: return
        if(!ready)return
        PackageStore.saveValues(host,shader,values);PackageStore.select(host,shader)
        host.launchWallpaperPicker()
    }

    fun onShown() {
        host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(pkg!=null && !previewRunning && host.resumedNow){preview.start();previewRunning=true}
    }

    fun onHidden() {
        host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(previewRunning){preview.stop();previewRunning=false}
    }

    fun onResume(){if(visible)onShown()}
    fun onPause(){if(previewRunning){preview.stop();previewRunning=false};host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}

    val currentPackage get()=pkg

    private companion object {const val Color_WHITE=0xffffffff.toInt()}
}
