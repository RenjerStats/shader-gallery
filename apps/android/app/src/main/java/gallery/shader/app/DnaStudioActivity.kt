package gallery.shader.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

/**
 * Native studio. Jobs live on the server; the activity only observes their state.
 * Three tabs keep a phone screen focused: the idea, the two variants, the history.
 */
class DnaStudioActivity:Activity() {
    private lateinit var ui:Ui
    private val handler=Handler(Looper.getMainLooper())
    private val previews=mutableListOf<GalleryPreview>()
    private val variantValues=mutableMapOf<String,MutableMap<String,String>>()
    private lateinit var root:FrameLayout
    private lateinit var tabs:Segmented
    private lateinit var notice:TextView
    private lateinit var noticeCard:View
    private lateinit var idea:EditText
    private lateinit var counter:TextView
    private lateinit var controls:EditText
    private lateinit var referenceList:LinearLayout
    private lateinit var resultList:LinearLayout
    private lateinit var historyList:LinearLayout
    private lateinit var generate:android.widget.Button
    private lateinit var bottomBar:LinearLayout
    private lateinit var pages:List<ScrollView>
    private var source=GalleryClient.SITE
    private var owner:String?=null
    private var enabled=false
    private var foreground=false
    private var busy=false
    private var tab=0
    private var selectedVariant=0
    private var job:JSONObject?=null
    private var refs=JSONArray()
    private var renderKey=""
    private var revision=0
    private var historyCursor:String?=null
    private var insets=Insets()
    private val poll=Runnable {refreshJob()}

    private fun key()="dna:$source:${owner ?: "guest"}"
    private fun preferences()=getSharedPreferences("dna_studio",MODE_PRIVATE)
    private fun JSONObject.text(name:String):String?=if(isNull(name))null else optString(name).takeIf {it.isNotBlank()}
    private fun running()=job?.optJSONArray("variants")?.let {a->(0 until a.length()).any {a.getJSONObject(it).optString("status") in listOf("queued","running")}} ?: false

    private fun updateButtons() {
        generate.isEnabled=enabled && owner!=null && !busy && !running()
        generate.text=if(running())"Варианты создаются…" else "Создать два варианта"
    }

    private fun say(message:String,warning:Boolean=false) {
        notice.text=message
        notice.setTextColor(if(warning)ui.p.danger else ui.p.ink)
        noticeCard.visibility=if(message.isBlank())View.GONE else View.VISIBLE
    }

    private fun request(action:String,payload:JSONObject=JSONObject(),failed:(()->Unit)?=null,done:(JSONObject)->Unit) {
        val account=owner
        background({GalleryClient.rpc(this,source,action,payload)},{error->
            busy=false;failed?.invoke();say(friendly(error,"Не удалось связаться с галереей"),true);updateButtons()
            handler.removeCallbacks(poll);if(running() && foreground)handler.postDelayed(poll,8000)
        }) {data->
            if(owner==account)done(data)
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        val night=Appearance.night(this)
        setTheme(if(night)R.style.GalleryThemeDark else R.style.GalleryTheme)
        super.onCreate(savedInstanceState)
        ui=Ui(this)
        source=try{GalleryClient.base(intent.getStringExtra("source") ?: GalleryClient.SITE)}catch(_:Exception){GalleryClient.SITE}
        root=FrameLayout(this).apply {setBackgroundColor(ui.p.paper)}
        setContentView(root)
        edgeToEdge(night)
        buildScreen()
        root.setOnApplyWindowInsetsListener {_,windowInsets->insets=readInsets(windowInsets);applyInsets();windowInsets}
        root.requestApplyInsets()
        background({GalleryClient.session(this,source)},{error->say(friendly(error,"Нет связи с галереей"),true)}) {id->
            owner=id
            if(id==null){say("Вернитесь в галерею и войдите в аккаунт, чтобы создавать работы.");return@background}
            restoreForm();addIntentReference()
            request("dna_config") {
                enabled=it.optBoolean("enabled")
                say(if(enabled)"" else "Генерация пока не подключена. Идея сохранится на устройстве.")
                updateButtons()
            }
            loadHistory(true)
        }
    }

    private fun applyInsets() {
        root.setPadding(insets.left,insets.top,insets.right,0)
        bottomBar.setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10)+insets.bottomPad)
        pages.forEach {it.setPadding(0,0,0,if(tab==0)0 else insets.bottomPad)}
    }

    private fun buildScreen() {
        val p=ui.p
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        root.fill(column)
        column.addView(LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(4),0,ui.dp(16),0);minimumHeight=ui.dp(56)
            addView(ui.iconButton("back","Назад в галерею") {finish()},LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
            val titles=LinearLayout(this@DnaStudioActivity).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(4),0,0,0)}
            titles.addView(ui.title("DNA Studio",21f))
            titles.addView(ui.text("Ваша идея — два прочтения",13f,p.muted,ui.sansMedium))
            addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        })
        notice=ui.body("Подключаем студию…",14f,p.ink)
        noticeCard=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL;background=ui.shape(p.soft,16f);setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12))
            addView(ui.iconView("info",p.link),LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)))
            addView(notice,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=ui.dp(12)})
        }
        notice.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        column.addView(noticeCard,LinearLayout.LayoutParams(-1,-2).apply {setMargins(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(8))})
        tabs=Segmented(ui,listOf("Идея","Варианты","История"),0) {showTab(it)}
        column.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply {setMargins(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(8))})

        val body=FrameLayout(this)
        column.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        pages=listOf(buildIdeaPage(),buildResultsPage(),buildHistoryPage())
        pages.forEach {body.fill(it)}
        bottomBar=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(p.surface)}
        bottomBar.addView(ui.divider(),LinearLayout.LayoutParams(-1,1).apply {bottomMargin=ui.dp(0)})
        generate=ui.button("Создать два варианта","primary") {createJob()}.apply {isEnabled=false}
        bottomBar.addView(generate,LinearLayout.LayoutParams(-1,ui.dp(52)).apply {topMargin=ui.dp(10)})
        column.addView(bottomBar,LinearLayout.LayoutParams(-1,-2))
        showTab(0)
        say("Подключаем студию…")
    }

    private fun page(content:LinearLayout)=ScrollView(this).apply {
        isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER;clipToPadding=false
        content.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(28));addView(content)
    }

    private fun label(value:String,hint:String?=null,top:Int=22):View=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(0,ui.dp(top),0,ui.dp(8))
        addView(ui.text(value,16f,ui.p.ink,ui.sansSemi))
        if(hint!=null)addView(ui.text(hint,13f,ui.p.muted).apply {setPadding(0,ui.dp(2),0,0)})
    }

    private fun buildIdeaPage():ScrollView {
        val p=ui.p
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        column.addView(label("Что хотите увидеть?",top=8))
        idea=ui.field("Например: перламутровые волны с мягким свечением",true,5,2000)
        counter=ui.text("0 / 2000",12f,p.muted).apply {gravity=Gravity.END;setPadding(0,ui.dp(6),ui.dp(4),0)}
        idea.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){counter.text="${s?.length ?: 0} / 2000"}
            override fun afterTextChanged(s:Editable?){}
        })
        column.addView(idea,LinearLayout.LayoutParams(-1,-2));column.addView(counter)
        column.addView(label("Референсы","До 3 работ из галереи: модели опираются на их стиль"))
        referenceList=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        column.addView(referenceList)
        column.addView(ui.button("Выбрать из галереи","outline") {chooseReference()},LinearLayout.LayoutParams(-1,ui.dp(48)).apply {topMargin=ui.dp(8)})
        column.addView(label("Настройки будущей работы","Скорость, масштаб, цвет — необязательно"))
        controls=ui.field("Например: медленное движение, холодные цвета",true,2,1000)
        column.addView(controls,LinearLayout.LayoutParams(-1,-2))
        column.addView(ui.body("До 10 запусков за 24 часа. Идея и исходники референсов передаются моделям через OpenRouter. Результаты приватны до публикации.",12f,p.muted).apply {setPadding(0,ui.dp(22),0,0)})
        return page(column)
    }

    private fun buildResultsPage():ScrollView {
        resultList=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        resultList.addView(resultsEmpty())
        return page(resultList)
    }

    private fun resultsEmpty():View=emptyState(ui,"sparkle","Здесь появятся варианты","Опишите идею — две модели независимо предложат живой шейдер. Приложение можно закрыть и вернуться позже.")

    private fun buildHistoryPage():ScrollView {
        historyList=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        return page(historyList)
    }

    private fun showTab(index:Int) {
        tab=index;tabs.select(index)
        pages.forEachIndexed {i,page->page.visibility=if(i==index)View.VISIBLE else View.GONE}
        bottomBar.visibility=if(index==0)View.VISIBLE else View.GONE
        applyInsets()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
        previews.forEachIndexed {i,preview->if(index==1 && i==selectedVariant && foreground)preview.start() else preview.stop()}
    }

    private fun saveForm() {
        if(owner==null)return
        preferences().edit().putString(key()+":form",JSONObject().put("prompt",idea.text.toString()).put("controls",controls.text.toString()).put("references",refs).toString()).apply()
    }

    private fun restoreForm() {
        try {
            val form=JSONObject(preferences().getString(key()+":form","{}")!!)
            idea.setText(form.optString("prompt"));controls.setText(form.optString("controls"));refs=form.optJSONArray("references") ?: JSONArray()
        } catch(_:Exception){refs=JSONArray()}
        renderReferences()
    }

    private fun renderReferences() {
        referenceList.removeAllViews()
        for(i in 0 until refs.length()) {
            val ref=refs.getJSONObject(i)
            val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=ui.shape(ui.p.surface,16f,ui.p.line);setPadding(ui.dp(12),ui.dp(8),ui.dp(4),ui.dp(8))}
            row.addView(TextView(this).apply {
                text="${i+1}";textSize=13f;typeface=ui.sansSemi;gravity=Gravity.CENTER;setTextColor(ui.p.link);background=ui.oval(ui.p.soft)
            },LinearLayout.LayoutParams(ui.dp(30),ui.dp(30)))
            val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(12),0,ui.dp(8),0)}
            column.addView(ui.text(ref.getString("title"),15f,ui.p.ink,ui.sansSemi,1))
            column.addView(ui.text("${ref.optString("author")} · ${ref.optString("license")}",13f,ui.p.muted,ui.sans,1))
            row.addView(column,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(ui.iconButton("close","Убрать референс: ${ref.getString("title")}",ui.p.muted) {refs.remove(i);renderReferences();saveForm()},LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
            referenceList.addView(row,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=ui.dp(8)})
        }
    }

    private fun addIntentReference() {
        val id=intent.getStringExtra("work_id") ?: return
        val revisionId=intent.getStringExtra("revision_id")
        intent.removeExtra("work_id")
        request("work",JSONObject().put("id",id).put("revision_id",revisionId)) {addReference(it.getJSONObject("work"))}
    }

    private fun addReference(work:JSONObject) {
        val revisionData=work.getJSONObject("revision");val id=revisionData.getString("id")
        if((0 until refs.length()).any {refs.getJSONObject(it).getString("revision_id")==id})return
        if(refs.length()>=3){say("Можно выбрать не более трёх референсов.",true);return}
        refs.put(JSONObject().put("work_id",work.getString("id")).put("revision_id",id).put("title",work.getString("title"))
            .put("author",work.getJSONObject("author").getString("display_name")).put("license",revisionData.getString("license")))
        renderReferences();saveForm()
    }

    private fun chooseReference() {
        if(refs.length()>=3){say("Уберите один референс, чтобы добавить другой.",true);return}
        var version=0
        lateinit var find:Runnable
        ui.sheet("Референсы из галереи") {content,dialog->
            val search=ui.field("Поиск по названию или тегу").apply {imeOptions=EditorInfo.IME_ACTION_SEARCH}
            val items=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
            find=Runnable {
                val current=++version
                items.removeAllViews();items.addView(ui.text("Ищем работы…",14f,ui.p.muted).apply {setPadding(ui.dp(8),ui.dp(16),0,0)})
                request("feed",JSONObject().put("query",search.text.toString().trim()).put("limit",12)) {data->
                    if(!dialog.isShowing || current!=version)return@request
                    items.removeAllViews();val found=data.getJSONArray("items")
                    if(found.length()==0)items.addView(ui.text("Работ не найдено.",14f,ui.p.muted).apply {setPadding(ui.dp(8),ui.dp(16),0,0)})
                    for(i in 0 until found.length()) {
                        val card=found.getJSONObject(i)
                        val row=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;minimumHeight=ui.dp(56);gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));background=ui.ripple(null,14f);isClickable=true;isFocusable=true}
                        row.addView(ui.text(card.getString("title"),16f,ui.p.ink,ui.sansSemi,1))
                        row.addView(ui.text(card.getJSONObject("author").getString("display_name"),13f,ui.p.muted,ui.sans,1))
                        row.setOnClickListener {
                            request("work",JSONObject().put("id",card.getString("id")).put("revision_id",card.getJSONObject("revision").getString("id"))) {work->addReference(work.getJSONObject("work"));dialog.dismiss()}
                        }
                        items.addView(row)
                    }
                }
            }
            search.addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){handler.removeCallbacks(find);handler.postDelayed(find,400)}
                override fun afterTextChanged(s:Editable?){}
            })
            search.setOnEditorActionListener {_,_,_->handler.removeCallbacks(find);find.run();true}
            dialog.setOnDismissListener {handler.removeCallbacks(find)}
            content.add(search)
            content.add(items) {topMargin=ui.dp(8)}
        }.show()
        find.run()
    }

    private fun createJob() {
        if(busy || running())return
        if(idea.text.isBlank()){say("Опишите, что хотите увидеть.",true);idea.requestFocus();return}
        saveForm()
        val ids=JSONArray();for(i in 0 until refs.length())ids.put(refs.getJSONObject(i).getString("revision_id"))
        val payload=JSONObject().put("prompt",idea.text.toString()).put("controls",controls.text.toString()).put("reference_ids",ids)
        val fingerprint=payload.toString()
        val pending=try{JSONObject(preferences().getString(key()+":pending","{}")!!)}catch(_:Exception){JSONObject()}
        val requestId=if(pending.optString("fingerprint")==fingerprint)pending.getString("id") else UUID.randomUUID().toString()
        // Persist before dispatch so a lost response / activity restart reuses the same paid request.
        preferences().edit().putString(key()+":pending",JSONObject().put("fingerprint",fingerprint).put("id",requestId).toString()).commit()
        busy=true;updateButtons();say("Запускаем две модели…");revision++
        request("dna_create",payload.put("request_id",requestId)) {data->
            busy=false;preferences().edit().remove(key()+":pending").apply();selectedVariant=0;showJob(data);showTab(1);loadHistory()
        }
    }

    private fun showJob(data:JSONObject) {
        job=data;preferences().edit().putString(key()+":job",data.getString("id")).apply()
        say(when(data.optString("status")) {
            "running"->"Варианты создаются. Можно вернуться позже."
            "partial"->"Один вариант готов. Для второго доступна отдельная попытка."
            "ready"->"Оба варианта готовы. Сравните их и настройте под себя."
            else->"Генерация завершена. Результаты сохранены в истории."
        })
        updateButtons()
        val variants=data.getJSONArray("variants")
        val signature=data.getString("id")+(0 until variants.length()).joinToString {val v=variants.getJSONObject(it);v.getString("id")+v.getString("status")+v.optString("draft_id")+v.optString("work_id")}
        if(signature!=renderKey){renderKey=signature;renderVariants(variants,data)}
        handler.removeCallbacks(poll);if(running() && foreground)handler.postDelayed(poll,2500)
    }

    private fun refreshJob() {
        val id=job?.optString("id") ?: return
        val seq=revision
        request("dna_get",JSONObject().put("id",id)) {if(seq==revision && job?.optString("id")==id)showJob(it)}
    }

    private fun openJob(id:String,restore:Boolean=false) {
        handler.removeCallbacks(poll);val seq=++revision
        request("dna_get",JSONObject().put("id",id)) {data->
            if(seq!=revision)return@request
            if(restore){idea.setText(data.getString("prompt"));controls.setText(data.getString("controls"));refs=data.getJSONArray("references");renderReferences();saveForm()}
            selectedVariant=0;renderKey="";showJob(data)
            if(restore)showTab(1)
        }
    }

    private fun statusLabel(status:String)=when(status) {"queued","running"->"создаётся";"ready"->"готов";"failed"->"ошибка";"cancelled"->"остановлен";else->status}

    private fun loadHistory(resume:Boolean=false,more:Boolean=false) {
        val payload=JSONObject();if(more && historyCursor!=null)payload.put("before",historyCursor)
        request("dna_list",payload) {data->
            if(!more)historyList.removeAllViews() else historyList.removeViewAt(historyList.childCount-1)
            val items=data.getJSONArray("items");historyCursor=data.text("next_cursor")
            if(items.length()==0 && !more)historyList.addView(emptyState(ui,"list","Пока нет генераций","Здесь сохранится каждая ваша идея вместе с вариантами."))
            for(i in 0 until items.length()) {
                val item=items.getJSONObject(i)
                val variants=item.getJSONArray("variants")
                val states=(0 until variants.length()).joinToString(" · ") {statusLabel(variants.getJSONObject(it).optString("status"))}
                val row=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL;background=ui.ripple(ui.shape(ui.p.surface,18f,ui.p.line),18f);setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));isClickable=true;isFocusable=true
                }
                row.addView(ui.text(item.getString("prompt"),16f,ui.p.ink,ui.sansSemi,2).apply {setLineSpacing(0f,1.15f)})
                row.addView(ui.text("${shortDate(item.optString("created_at"))} ${if(states.isBlank())"" else "· $states"}".trim().trimStart('·',' '),13f,ui.p.muted).apply {setPadding(0,ui.dp(6),0,0)})
                row.setOnClickListener {if(!busy)openJob(item.getString("id"),true)}
                historyList.addView(row,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=ui.dp(10)})
            }
            historyCursor?.let {historyList.addView(ui.button("Более ранние идеи","tonal") {loadHistory(more=true)},LinearLayout.LayoutParams(-1,ui.dp(48)))}
            if(resume && job==null) {
                val active=(0 until items.length()).map {items.getJSONObject(it)}.firstOrNull {item->
                    val variants=item.getJSONArray("variants");(0 until variants.length()).any {variants.getJSONObject(it).getString("status") in listOf("queued","running")}
                }
                val id=active?.getString("id") ?: preferences().getString(key()+":job",null) ?: if(items.length()>0)items.getJSONObject(0).getString("id") else null
                if(id!=null)openJob(id)
            }
        }
    }

    private fun renderVariants(variants:JSONArray,data:JSONObject) {
        previews.forEach {it.stop()};previews.clear();resultList.removeAllViews()
        val p=ui.p
        resultList.addView(ui.text("ВАША ИДЕЯ",11f,p.muted,ui.sansSemi).apply {letterSpacing=.12f;setPadding(0,ui.dp(8),0,ui.dp(4))})
        resultList.addView(ui.body(data.getString("prompt"),15f,p.ink).apply {setPadding(0,0,0,ui.dp(14))})
        if(running()) {
            resultList.addView(ui.button("Остановить генерацию","outline") {
                val current=job ?: return@button;if(busy)return@button
                busy=true;updateButtons();request("dna_cancel",JSONObject().put("id",current.getString("id"))) {busy=false;showJob(it);loadHistory()}
            },LinearLayout.LayoutParams(-1,ui.dp(48)).apply {bottomMargin=ui.dp(12)})
        }
        val host=FrameLayout(this)
        val views=mutableListOf<View>()
        if(selectedVariant>=variants.length())selectedVariant=0
        if(variants.length()>1) {
            resultList.addView(Segmented(ui,(0 until variants.length()).map {"Вариант ${if(variants.getJSONObject(it).getInt("slot")==0)"A" else "B"}"},selectedVariant) {index->
                selectedVariant=index
                views.forEachIndexed {i,view->view.visibility=if(i==index)View.VISIBLE else View.GONE}
                previews.forEachIndexed {i,preview->if(i==index && foreground && tab==1)preview.start() else preview.stop()}
            },LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=ui.dp(12)})
        }
        for(i in 0 until variants.length()) {
            val variantView=buildVariant(variants.getJSONObject(i),data)
            variantView.visibility=if(i==selectedVariant)View.VISIBLE else View.GONE
            views+=variantView;host.addView(variantView,FrameLayout.LayoutParams(-1,-2))
        }
        resultList.addView(host)
        val references=data.getJSONArray("references")
        if(references.length()>0) {
            resultList.addView(ui.title("ДНК этой работы",20f).apply {setPadding(0,ui.dp(24),0,ui.dp(8))})
            val card=ui.card()
            for(i in 0 until references.length()) {
                val ref=references.getJSONObject(i)
                if(i>0)card.addView(ui.divider(),LinearLayout.LayoutParams(-1,1).apply {leftMargin=ui.dp(16)})
                card.addView(ui.actionRow("sparkle",ref.getString("title"),null,p.ink,true) {openWork(ref.getString("work_id"),ref.getString("revision_id"))})
            }
            resultList.addView(card)
        }
    }

    private fun buildVariant(v:JSONObject,data:JSONObject):View {
        val p=ui.p
        val id=v.getString("id");val status=v.getString("status")
        val box=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val head=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        head.addView(TextView(this).apply {
            text=if(v.getInt("slot")==0)"A" else "B";textSize=17f;typeface=ui.serif;gravity=Gravity.CENTER;setTextColor(p.onAccent);background=ui.oval(p.accent)
        },LinearLayout.LayoutParams(ui.dp(36),ui.dp(36)))
        val names=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(12),0,0,0)}
        names.addView(ui.text(v.getString("model").substringAfter('/'),14f,p.ink,ui.sansSemi,1))
        names.addView(ui.text(statusLabel(status).replaceFirstChar {it.uppercase()},13f,if(status=="failed")p.danger else p.muted))
        head.addView(names,LinearLayout.LayoutParams(0,-2,1f))
        box.addView(head,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=ui.dp(12)})

        val result=v.optJSONObject("result")
        if(result==null) {
            val card=LinearLayout(this).apply {gravity=Gravity.CENTER;orientation=LinearLayout.VERTICAL;background=ui.shape(p.fill,24f);setPadding(ui.dp(24),ui.dp(40),ui.dp(24),ui.dp(40))}
            if(status=="queued" || status=="running")card.addView(ProgressBar(this).apply {isIndeterminate=true;indeterminateTintList=android.content.res.ColorStateList.valueOf(p.link)},LinearLayout.LayoutParams(ui.dp(36),ui.dp(36)))
            card.addView(ui.body(when(status) {"failed"->v.optString("error_message","Не удалось создать вариант");"cancelled"->"Остановлено";else->"Модель создаёт шейдер…"},15f,p.ink).apply {gravity=Gravity.CENTER;setPadding(0,ui.dp(12),0,0)})
            if(status=="failed" && v.getInt("attempts")<3)card.addView(ui.button("Повторить этот вариант","primary") {
                if(busy)return@button;busy=true;updateButtons()
                request("dna_retry",JSONObject().put("variant_id",id).put("expected_attempts",v.getInt("attempts"))) {busy=false;showJob(it)}
            },LinearLayout.LayoutParams(-2,ui.dp(48)).apply {topMargin=ui.dp(16)})
            box.addView(card)
            return box
        }

        val array=result.getJSONArray("parameters");val parameters=(0 until array.length()).map {ShaderParameter.fromJson(array.getJSONObject(it))}
        val shader=ShaderPackage("",data.getString("id"),id,result.getString("title"),"Shader DNA",result.getString("code"),"MIT",parameters)
        val values=variantValues.getOrPut(id) {shader.defaults()}
        var compiled=false;var publishing=false
        val compileStatus=ui.text("Проверяем шейдер на устройстве…",13f,p.muted)
        val publish=ui.button(if(v.text("work_id")!=null)"Открыть публикацию" else "Опубликовать","primary") {}
        val frame=FrameLayout(this).apply {setBackgroundColor(p.fill)}
        val preview=GalleryPreview(this) {ok,error->
            compiled=ok;publish.isEnabled=ok || v.text("work_id")!=null
            compileStatus.text=if(ok)"Превью проверено на устройстве" else "$error. Сохраните черновик для исправления на компьютере."
            compileStatus.setTextColor(if(ok)p.muted else p.danger)
        }
        previews.add(preview)
        frame.fill(preview)
        val pause=ui.iconButton(if(preview.isPaused)"play" else "pause","Движение / пауза",0xffffffff.toInt()) {}
        pause.background=ui.ripple(ui.oval(p.scrim),circle=true)
        pause.setOnClickListener {
            preview.setPaused(!preview.isPaused)
            pause.setImageDrawable(ui.icon(if(preview.isPaused)"play" else "pause",0xffffffff.toInt()))
        }
        frame.addView(pause,FrameLayout.LayoutParams(ui.dp(44),ui.dp(44),Gravity.BOTTOM or Gravity.END).apply {setMargins(0,0,ui.dp(12),ui.dp(12))})
        val metrics=resources.displayMetrics
        box.addView(frame,LinearLayout.LayoutParams(-1,min(metrics.widthPixels-ui.dp(32),(metrics.heightPixels*.5f).toInt())))
        preview.setShader(shader,values.toMap())
        if(foreground && tab==1 && previews.size-1==selectedVariant)preview.start()
        box.addView(compileStatus,LinearLayout.LayoutParams(-1,-2).apply {topMargin=ui.dp(10)})
        box.addView(ui.title(result.getString("title"),24f).apply {setPadding(0,ui.dp(10),0,ui.dp(4))})
        result.text("description")?.let {box.addView(ui.body(it,15f,p.muted))}
        if(parameters.isNotEmpty()) {
            box.addView(ui.text("Настройки",16f,p.ink,ui.sansSemi).apply {setPadding(0,ui.dp(20),0,ui.dp(4))})
            box.addView(ParameterPanel(ui,parameters,values) {preview.setValues(values.toMap())})
        }
        fun payload():JSONObject {
            val vals=JSONObject()
            parameters.forEach {parameter->vals.put(parameter.name,if(parameter.type=="float")(values[parameter.name] ?: parameter.defaultValue).toDouble() else values[parameter.name] ?: parameter.defaultValue)}
            return JSONObject().put("variant_id",id).put("values",vals)
        }
        val hasDraft=v.text("draft_id")!=null
        val save=ui.button(if(hasDraft)"Черновик сохранён" else "Сохранить черновик","outline") {}
        save.isEnabled=!hasDraft
        save.setOnClickListener {
            if(busy)return@setOnClickListener
            busy=true;save.isEnabled=false;updateButtons()
            request("dna_save",payload(),failed={save.isEnabled=true}) {busy=false;say("Черновик сохранён. Его можно открыть в редакторе на компьютере.");refreshJob();updateButtons()}
        }
        box.addView(save,LinearLayout.LayoutParams(-1,ui.dp(48)).apply {topMargin=ui.dp(20)})
        publish.isEnabled=v.text("work_id")!=null
        publish.setOnClickListener {
            val workId=v.text("work_id")
            if(workId!=null){openWork(workId);return@setOnClickListener}
            if(!compiled || busy || publishing)return@setOnClickListener
            publishing=true;publish.isEnabled=false
            preview.snapshot {image->
                if(isDestroyed)return@snapshot
                publishing=false
                if(image==null){say("Не удалось создать превью. Попробуйте снова.",true);publish.isEnabled=true;return@snapshot}
                busy=true;updateButtons()
                request("dna_publish",payload().put("preview",image),failed={publish.isEnabled=compiled}) {published->busy=false;updateButtons();openWork(published.getString("work_id"))}
            }
        }
        box.addView(publish,LinearLayout.LayoutParams(-1,ui.dp(52)).apply {topMargin=ui.dp(10)})
        return box
    }

    private fun openWork(id:String,revisionId:String?=null) {
        val uri=Uri.parse("shadergallery://work/$id").buildUpon().appendQueryParameter("source",source).apply {if(revisionId!=null)appendQueryParameter("revision",revisionId)}.build()
        startActivity(Intent(this,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish()
    }

    override fun onResume() {
        super.onResume();foreground=true
        previews.forEachIndexed {i,preview->if(tab==1 && i==selectedVariant)preview.start()}
        handler.removeCallbacks(poll);if(job!=null)handler.post(poll)
    }

    override fun onPause() {
        foreground=false;saveForm();handler.removeCallbacks(poll);previews.forEach {it.stop()};super.onPause()
    }

    override fun onDestroy() {revision++;handler.removeCallbacksAndMessages(null);super.onDestroy()}
}
