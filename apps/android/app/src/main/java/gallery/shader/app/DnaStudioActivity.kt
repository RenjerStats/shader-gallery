package gallery.shader.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Native studio. Jobs live on the server; the activity only observes their state. */
class DnaStudioActivity:Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executors.newFixedThreadPool(2)
    private val previews=mutableListOf<GalleryPreview>()
    private val variantValues=mutableMapOf<String,MutableMap<String,String>>()
    private lateinit var idea:EditText
    private lateinit var controls:EditText
    private lateinit var referenceList:LinearLayout
    private lateinit var resultList:LinearLayout
    private lateinit var historyList:LinearLayout
    private lateinit var notice:TextView
    private lateinit var generate:Button
    private lateinit var stop:Button
    private var source=GalleryClient.SITE
    private var owner:String?=null
    private var enabled=false
    private var foreground=false
    private var busy=false
    private var job:JSONObject?=null
    private var refs=JSONArray()
    private var renderKey=""
    private var revision=0
    private var historyCursor:String?=null
    private var historyMore:Button?=null
    private val paper=Color.rgb(248,246,242)
    private val ink=Color.rgb(34,33,32)
    private val blue=Color.rgb(40,76,237)
    private val muted=Color.rgb(112,109,105)
    private val poll=Runnable {refreshJob()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
    private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun label(value:String,size:Float=14f)=TextView(this).apply {
        text=value;textSize=size;setTextColor(if(size<14)muted else ink)
        typeface=resources.getFont(if(size>=22)R.font.lora else R.font.manrope)
        setPadding(0,dp(9),0,dp(9));includeFontPadding=false
    }
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=Button(this).apply {
        text=value;isAllCaps=false;textSize=14f;minHeight=dp(48);typeface=resources.getFont(R.font.manrope)
        setTextColor(if(primary)Color.WHITE else ink)
        background=GradientDrawable().apply {cornerRadius=dp(24).toFloat();setColor(if(primary)blue else Color.TRANSPARENT);setStroke(dp(1),if(primary)blue else Color.rgb(220,220,214))}
        setPadding(dp(16),dp(8),dp(16),dp(8));setOnClickListener{action()}
    }
    private fun field(hintText:String,max:Int,lines:Int)=EditText(this).apply {
        hint=hintText;contentDescription=hintText;minLines=lines;gravity=android.view.Gravity.TOP
        filters=arrayOf(InputFilter.LengthFilter(max));textSize=15f;setTextColor(ink)
        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(14),dp(12),dp(14),dp(12));background=GradientDrawable().apply{cornerRadius=dp(14).toFloat();setColor(Color.WHITE);setStroke(dp(1),Color.rgb(226,225,218))}
    }
    private fun key()="dna:$source:${owner ?: "guest"}"
    private fun preferences()=getSharedPreferences("dna_studio",MODE_PRIVATE)
    private fun running()=job?.optJSONArray("variants")?.let {a->(0 until a.length()).any {a.getJSONObject(it).optString("status") in listOf("queued","running")}} ?: false
    private fun updateButtons(){generate.isEnabled=enabled && owner!=null && !busy && !running();stop.visibility=if(running())View.VISIBLE else View.GONE;stop.isEnabled=!busy}
    private fun request(action:String,payload:JSONObject=JSONObject(),failed:(()->Unit)?=null,done:(JSONObject)->Unit){
        val account=owner
        executor.execute {
            try{val data=GalleryClient.rpc(this,source,action,payload);runOnUiThread {if(!isDestroyed && !isFinishing && owner==account)done(data)}}
            catch(e:Exception){runOnUiThread {if(!isDestroyed && !isFinishing){busy=false;failed?.invoke();notice.text=e.message ?: "Не удалось связаться с галереей";updateButtons();handler.removeCallbacks(poll);if(running() && foreground)handler.postDelayed(poll,8000)}}}
        }
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        source=try{GalleryClient.base(intent.getStringExtra("source") ?: GalleryClient.SITE)}catch(_:Exception){GalleryClient.SITE}
        window.statusBarColor=paper;window.navigationBarColor=paper
        val shell=column().apply {setBackgroundColor(paper);setPadding(dp(20),dp(12),dp(20),dp(20))}
        if(android.os.Build.VERSION.SDK_INT>=35)shell.setOnApplyWindowInsetsListener {v,insets->val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(20),bars.top+dp(12),dp(20),bars.bottom+dp(20));insets}
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(shell)};setContentView(scroll)
        shell.addView(button("‹  Галерея"){finish()})
        shell.addView(label("Shader DNA Studio",30f));shell.addView(label("Ваша идея. Два независимых прочтения. Живое искусство без кода."))
        notice=label("Подключаем студию…",13f).apply {accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};shell.addView(notice)
        shell.addView(label("Что хотите увидеть?",18f));idea=field("Например: перламутровые волны с мягким свечением",2000,4);shell.addView(idea)
        shell.addView(label("Референсы · до 3 работ",18f));referenceList=column();shell.addView(referenceList)
        shell.addView(button("+ Выбрать из галереи"){chooseReference()})
        shell.addView(label("Настройки будущей работы",18f));controls=field("Скорость, масштаб, цвет — необязательно",1000,2);shell.addView(controls)
        generate=button("Создать два варианта",true){createJob()}.apply {isEnabled=false};shell.addView(generate,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(20)})
        shell.addView(label("До 10 запусков за 24 часа. Идея и исходники референсов передаются моделям через OpenRouter. Результаты приватны до публикации.",12f))
        stop=button("Остановить генерацию"){val current=job ?: return@button;if(busy)return@button;busy=true;updateButtons();request("dna_cancel",JSONObject().put("id",current.getString("id"))){busy=false;showJob(it);loadHistory()}}.apply {visibility=View.GONE};shell.addView(stop)
        shell.addView(label("Два варианта",26f));resultList=column();shell.addView(resultList);resultList.addView(label("Результаты появятся здесь. Можно закрыть приложение и вернуться позже.",13f))
        shell.addView(label("История идей",26f));shell.addView(button("Обновить историю"){loadHistory();refreshJob()});historyList=column();shell.addView(historyList)
        executor.execute {
            try {
                val id=GalleryClient.session(this,source)
                runOnUiThread {
                    if(isDestroyed)return@runOnUiThread
                    owner=id
                    if(id==null){notice.text="Вернитесь в галерею и войдите в аккаунт, чтобы создавать работы.";return@runOnUiThread}
                    restoreForm();addIntentReference()
                    request("dna_config"){enabled=it.optBoolean("enabled");notice.text=if(enabled)"Идея готова к исследованию." else "Генерация пока не подключена. Идея сохранится на устройстве.";updateButtons()}
                    loadHistory(true)
                }
            }catch(e:Exception){runOnUiThread{if(!isDestroyed)notice.text=e.message}}
        }
    }
    private fun saveForm(){if(owner==null)return;preferences().edit().putString(key()+":form",JSONObject().put("prompt",idea.text.toString()).put("controls",controls.text.toString()).put("references",refs).toString()).apply()}
    private fun restoreForm(){try{val f=JSONObject(preferences().getString(key()+":form","{}")!!);idea.setText(f.optString("prompt"));controls.setText(f.optString("controls"));refs=f.optJSONArray("references") ?: JSONArray()}catch(_:Exception){refs=JSONArray()};renderReferences()}
    private fun renderReferences(){
        referenceList.removeAllViews()
        for(i in 0 until refs.length()){
            val ref=refs.getJSONObject(i)
            referenceList.addView(button("${i+1}. ${ref.getString("title")}  ×"){
                refs.remove(i);renderReferences();saveForm()
            })
        }
    }
    private fun addIntentReference(){
        val id=intent.getStringExtra("work_id") ?: return
        val revisionId=intent.getStringExtra("revision_id")
        intent.removeExtra("work_id")
        request("work",JSONObject().put("id",id).put("revision_id",revisionId)){addReference(it.getJSONObject("work"))}
    }
    private fun addReference(work:JSONObject){
        val r=work.getJSONObject("revision");val id=r.getString("id")
        if((0 until refs.length()).any {refs.getJSONObject(it).getString("revision_id")==id})return
        if(refs.length()>=3){notice.text="Можно выбрать не более трёх референсов.";return}
        refs.put(JSONObject().put("work_id",work.getString("id")).put("revision_id",id).put("title",work.getString("title"))
            .put("author",work.getJSONObject("author").getString("display_name")).put("license",r.getString("license")))
        renderReferences();saveForm()
    }
    private fun chooseReference(){
        if(refs.length()>=3){notice.text="Уберите один референс, чтобы добавить другой.";return}
        val panel=column().apply{setPadding(dp(20),0,dp(20),dp(16))};val search=field("Поиск по названию или тегу",100,1);panel.addView(search)
        val items=column();val dialog=AlertDialog.Builder(this).setTitle("Референсы из галереи").setView(ScrollView(this).apply {addView(panel)}).setNegativeButton("Закрыть",null).create()
        var searchVersion=0
        fun find(){val version=++searchVersion;items.removeAllViews();items.addView(label("Ищем работы…",13f));request("feed",JSONObject().put("query",search.text.toString()).put("limit",12)){data->
            if(!dialog.isShowing || version!=searchVersion)return@request
            items.removeAllViews();val found=data.getJSONArray("items")
            if(found.length()==0)items.addView(label("Работ не найдено.",13f))
            for(i in 0 until found.length()){val card=found.getJSONObject(i)
                items.addView(button("${card.getString("title")} · ${card.getJSONObject("author").getString("display_name")}"){
                    request("work",JSONObject().put("id",card.getString("id")).put("revision_id",card.getJSONObject("revision").getString("id"))){addReference(it.getJSONObject("work"));dialog.dismiss()}
                })
            }
        }}
        panel.addView(button("Найти"){find()});panel.addView(items);dialog.show();find()
    }
    private fun createJob(){
        if(busy||running())return
        if(idea.text.isBlank()){notice.text="Опишите, что хотите увидеть.";idea.requestFocus();return}
        saveForm()
        val ids=JSONArray();for(i in 0 until refs.length())ids.put(refs.getJSONObject(i).getString("revision_id"))
        val payload=JSONObject().put("prompt",idea.text.toString()).put("controls",controls.text.toString()).put("reference_ids",ids)
        val fingerprint=payload.toString();val pending=try{JSONObject(preferences().getString(key()+":pending","{}")!!)}catch(_:Exception){JSONObject()}
        val requestId=if(pending.optString("fingerprint")==fingerprint)pending.getString("id") else UUID.randomUUID().toString()
        // Persist before dispatch so a lost response / activity restart reuses the same paid request.
        preferences().edit().putString(key()+":pending",JSONObject().put("fingerprint",fingerprint).put("id",requestId).toString()).commit()
        busy=true;updateButtons();notice.text="Запускаем две модели…";revision++
        request("dna_create",payload.put("request_id",requestId)){data->
            busy=false;preferences().edit().remove(key()+":pending").apply();showJob(data);loadHistory()
        }
    }
    private fun showJob(data:JSONObject){
        job=data;preferences().edit().putString(key()+":job",data.getString("id")).apply()
        notice.text=when(data.optString("status")){"running"->"Варианты создаются. Можно вернуться позже.";"partial"->"Один вариант готов. Для второго доступна отдельная попытка.";"ready"->"Оба варианта готовы. Сравните их и настройте под себя.";else->"Генерация завершена. Результаты сохранены в истории."}
        updateButtons()
        val variants=data.getJSONArray("variants")
        val signature=data.getString("id")+(0 until variants.length()).joinToString {val v=variants.getJSONObject(it);v.getString("id")+v.getString("status")+v.optString("draft_id")+v.optString("work_id")}
        if(signature!=renderKey){renderKey=signature;renderVariants(variants,data)}
        handler.removeCallbacks(poll);if(running() && foreground)handler.postDelayed(poll,2500)
    }
    private fun refreshJob(){
        val id=job?.optString("id") ?: return
        val seq=revision
        request("dna_get",JSONObject().put("id",id)){if(seq==revision && job?.optString("id")==id)showJob(it)}
    }
    private fun openJob(id:String,restore:Boolean=false){
        handler.removeCallbacks(poll);val seq=++revision
        request("dna_get",JSONObject().put("id",id)){data->if(seq!=revision)return@request
            if(restore){idea.setText(data.getString("prompt"));controls.setText(data.getString("controls"));refs=data.getJSONArray("references");renderReferences();saveForm()}
            showJob(data)
        }
    }
    private fun loadHistory(resume:Boolean=false,more:Boolean=false){
        val payload=JSONObject();if(more && historyCursor!=null)payload.put("before",historyCursor)
        request("dna_list",payload){data->
            historyMore?.let{historyList.removeView(it)};historyMore=null
            if(!more)historyList.removeAllViews()
            val items=data.getJSONArray("items");historyCursor=data.optString("next_cursor").takeIf{it.isNotBlank()&&it!="null"}
            if(items.length()==0 && !more)historyList.addView(label("Пока нет генераций.",13f))
            for(i in 0 until items.length()){val item=items.getJSONObject(i)
                historyList.addView(button(item.getString("prompt")){if(!busy)openJob(item.getString("id"),true)},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
            }
            historyCursor?.let {historyMore=button("Более ранние идеи"){historyMore?.isEnabled=false;loadHistory(more=true)};historyList.addView(historyMore)}
            if(resume && job==null){
                val active=(0 until items.length()).map{items.getJSONObject(it)}.firstOrNull{item->val variants=item.getJSONArray("variants");(0 until variants.length()).any{variants.getJSONObject(it).getString("status") in listOf("queued","running")}}
                val id=active?.getString("id") ?: preferences().getString(key()+":job",null) ?: if(items.length()>0)items.getJSONObject(0).getString("id") else null
                if(id!=null)openJob(id)
            }
        }
    }
    private fun renderVariants(variants:JSONArray,data:JSONObject){
        previews.forEach{it.stop()};previews.clear();resultList.removeAllViews()
        resultList.addView(label(data.getString("prompt"),16f))
        for(i in 0 until variants.length()){
            val v=variants.getJSONObject(i);val id=v.getString("id");val status=v.getString("status")
            val box=column().apply{setPadding(0,dp(16),0,dp(24))};resultList.addView(box)
            box.addView(label("Вариант ${if(v.getInt("slot")==0)"A" else "B"}",24f));box.addView(label(v.getString("model").substringAfter('/'),12f))
            val r=v.optJSONObject("result")
            if(r==null){
                box.addView(label(when(status){"failed"->v.optString("error_message","Не удалось создать вариант");"cancelled"->"Остановлено";else->"Модель создаёт шейдер…"}))
                if(status=="failed" && v.getInt("attempts")<3)box.addView(button("Повторить этот вариант"){
                    if(busy)return@button;busy=true;updateButtons();request("dna_retry",JSONObject().put("variant_id",id).put("expected_attempts",v.getInt("attempts"))){busy=false;showJob(it)}
                })
                continue
            }
            val array=r.getJSONArray("parameters");val parameters=(0 until array.length()).map{ShaderParameter.fromJson(array.getJSONObject(it))}
            val shader=ShaderPackage("",data.getString("id"),id,r.getString("title"),"Shader DNA",r.getString("code"),"MIT",parameters)
            val values=variantValues.getOrPut(id){shader.defaults()}
            var compiled=false;var publishing=false
            val compileStatus=label("Проверяем шейдер на устройстве…",12f)
            val publish=button(if(v.optString("work_id").let{it.isNotBlank()&&it!="null"})"Открыть публикацию" else "Опубликовать",true){}
            val preview=GalleryPreview(this){ok,error->runOnUiThread{if(!isDestroyed){compiled=ok;publish.isEnabled=ok;compileStatus.text=if(ok)"Превью проверено на устройстве" else "$error. Сохраните черновик для исправления на компьютере."}}}
            previews.add(preview);box.addView(preview,LinearLayout.LayoutParams(-1,dp(290)));preview.setShader(shader,values.toMap());if(foreground)preview.start()
            box.addView(button("Движение / пауза"){preview.setPaused(!preview.isPaused)})
            box.addView(compileStatus);box.addView(label(r.getString("title"),22f));box.addView(label(r.optString("description"),13f))
            parameters.forEach {p->
                box.addView(label(p.label,14f))
                if(p.type=="float"){
                    val output=label(values[p.name] ?: p.defaultValue,12f);box.addView(output)
                    box.addView(SeekBar(this).apply {max=1000;progress=(((values[p.name] ?: p.defaultValue).toFloat()-p.min)/(p.max-p.min)*1000).roundToInt()
                        setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                            override fun onProgressChanged(bar:SeekBar?,n:Int,fromUser:Boolean){if(fromUser){val value=p.min+(p.max-p.min)*n/1000;values[p.name]=value.toString();output.text="%.2f".format(value);preview.setValues(values.toMap())}}
                            override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
                        })},LinearLayout.LayoutParams(-1,dp(48)))
                }else box.addView(button(values[p.name] ?: p.defaultValue){colorPicker(p.label,values[p.name] ?: p.defaultValue){hex->values[p.name]=hex;preview.setValues(values.toMap())}})
            }
            fun payload():JSONObject{val vals=JSONObject();parameters.forEach{p->vals.put(p.name,if(p.type=="float")(values[p.name] ?: p.defaultValue).toDouble() else values[p.name] ?: p.defaultValue)};return JSONObject().put("variant_id",id).put("values",vals)}
            val hasDraft=v.optString("draft_id").let{it.isNotBlank()&&it!="null"}
            val save=button(if(hasDraft)"Черновик сохранён" else "Сохранить черновик"){}
            save.isEnabled=!hasDraft
            save.setOnClickListener{if(busy)return@setOnClickListener;busy=true;save.isEnabled=false;updateButtons();request("dna_save",payload(),failed={save.isEnabled=true}){busy=false;notice.text="Черновик сохранён. Его можно открыть в редакторе на компьютере.";refreshJob();updateButtons()}}
            box.addView(save,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
            publish.isEnabled=false
            publish.setOnClickListener{
                val workId=v.optString("work_id").takeIf{it.isNotBlank()&&it!="null"}
                if(workId!=null){openWork(workId);return@setOnClickListener}
                if(!compiled||busy||publishing)return@setOnClickListener
                publishing=true;publish.isEnabled=false
                preview.snapshot {image->
                    if(isDestroyed)return@snapshot
                    publishing=false
                    if(image==null){notice.text="Не удалось создать превью. Попробуйте снова.";publish.isEnabled=true;return@snapshot}
                    busy=true;updateButtons();request("dna_publish",payload().put("preview",image),failed={publish.isEnabled=compiled}){published->busy=false;updateButtons();openWork(published.getString("work_id"))}
                }
            }
            box.addView(publish,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        }
        val references=data.getJSONArray("references")
        if(references.length()>0){resultList.addView(label("ДНК этой работы",18f));for(i in 0 until references.length()){val ref=references.getJSONObject(i);resultList.addView(button("${ref.getString("title")} · ${ref.getString("author")} · ${ref.getString("license")}"){openWork(ref.getString("work_id"),ref.getString("revision_id"))})}}
    }
    private fun colorPicker(title:String,initial:String,selected:(String)->Unit){
        val panel=column().apply{setPadding(dp(20),0,dp(20),dp(16))};val color=Color.parseColor(initial);val channels=intArrayOf(Color.red(color),Color.green(color),Color.blue(color))
        val swatch=View(this).apply{setBackgroundColor(color)};panel.addView(swatch,LinearLayout.LayoutParams(-1,dp(60)))
        listOf("Красный","Зелёный","Синий").forEachIndexed{i,name->panel.addView(label(name,13f));panel.addView(SeekBar(this).apply{max=255;progress=channels[i];contentDescription=name;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(bar:SeekBar?,value:Int,user:Boolean){channels[i]=value;swatch.setBackgroundColor(Color.rgb(channels[0],channels[1],channels[2]))}
            override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
        })},LinearLayout.LayoutParams(-1,dp(48)))}
        AlertDialog.Builder(this).setTitle(title).setView(panel).setPositiveButton("Применить"){_,_->selected("#%02x%02x%02x".format(channels[0],channels[1],channels[2]))}.setNegativeButton("Отмена",null).show()
    }
    private fun openWork(id:String,revisionId:String?=null){
        val uri=Uri.parse("shadergallery://work/$id").buildUpon().appendQueryParameter("source",source).apply {if(revisionId!=null)appendQueryParameter("revision",revisionId)}.build()
        startActivity(Intent(this,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish()
    }
    override fun onResume(){super.onResume();foreground=true;previews.forEach{it.start()};handler.removeCallbacks(poll);if(job!=null)handler.post(poll)}
    override fun onPause(){foreground=false;saveForm();handler.removeCallbacks(poll);previews.forEach{it.stop()};super.onPause()}
    override fun onDestroy(){revision++;handler.removeCallbacksAndMessages(null);executor.shutdown();super.onDestroy()}
}
