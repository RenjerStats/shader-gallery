package gallery.shader.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import org.json.JSONObject
import kotlin.math.max

/** The main tab: search, filter chips and an endless list of works (rows or tiles). */
internal class FeedScreen(private val host:MainActivity) {
    private val ui=host.ui
    private val p=ui.p
    private val account get()=host.account
    val view=FrameLayout(host)

    private val modes=listOf("new" to "Новое","curated" to "Подборка","following" to "Подписки","saved" to "Сохранённое")
    private val categories=listOf("Все категории","Абстракция","Природа","Геометрия","Свет","Другое")
    private val handler=Handler(Looper.getMainLooper())
    private val cards=mutableListOf<GalleryCard>()

    private var mode="new"
    private var category=""
    private var query=""
    private var grid=Appearance.grid(host)
    private var cursor:JSONObject?=null
    private var generation=0
    private val FRESH_MS=30_000L
    private var loading=false
    private var moreFailed=false
    private var firstFailure:String?=null
    private var loadedSource=""
    private var rows=listOf<Row>()

    private lateinit var list:ListView
    private lateinit var pull:PullRefreshLayout
    private lateinit var states:FrameLayout
    private lateinit var skeleton:Skeleton
    private lateinit var search:EditText
    private lateinit var clear:View
    private lateinit var gridToggle:android.widget.ImageButton
    private lateinit var fab:LinearLayout
    private val chips=mutableMapOf<String,android.widget.TextView>()
    private lateinit var categoryChip:android.widget.TextView
    private val searchRun=Runnable {
        val next=search.text.toString().trim()
        if(next!=query){query=next;reload()}
    }

    private sealed class Row {
        class Works(val cards:List<GalleryCard>):Row()
        object Footer:Row()
    }

    private val feedAdapter=object:BaseAdapter() {
        override fun getCount()=rows.size
        override fun getItem(position:Int)=rows[position]
        override fun getItemId(position:Int)=position.toLong()
        override fun getViewTypeCount()=3
        override fun getItemViewType(position:Int)=when(rows[position]) {is Row.Works->if(grid)1 else 0;Row.Footer->2}
        override fun isEnabled(position:Int)=false
        override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
            return when(val row=rows[position]) {
                is Row.Works->if(grid) {
                    val tileColumns=columns()
                    val reusable=(convertView as? GridRow)?.takeIf {it.columns==tileColumns} ?: GridRow(tileColumns)
                    reusable.also {it.bind(row.cards)}
                } else ((convertView as? ListRow) ?: ListRow()).also {it.bind(row.cards[0])}
                Row.Footer->FooterRow()
            }
        }
    }

    init {
        view.setBackgroundColor(p.paper)
        view.isFocusableInTouchMode=true
        val column=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL}
        view.fill(column)
        column.addView(buildHeader())
        column.addView(buildSearch(),LinearLayout.LayoutParams(-1,ui.dp(48)).apply {setMargins(ui.dp(16),ui.dp(4),ui.dp(16),ui.dp(12))})
        column.addView(buildChips())
        column.addView(ui.divider(),LinearLayout.LayoutParams(-1,1).apply {topMargin=ui.dp(12)})
        list=buildList()
        states=FrameLayout(host)
        skeleton=Skeleton(ui,7)
        val body=FrameLayout(host).apply {fill(list);fill(states)}
        pull=PullRefreshLayout(ui,body,list).apply {onRefresh={reload(preserve=true)}}
        column.addView(pull,LinearLayout.LayoutParams(-1,0,1f))
        buildFab()
        account.listen {if(needsAccount() || account.source!=loadedSource)reload()}
        reload()
    }

    private fun needsAccount()=mode=="following" || mode=="saved"

    private fun buildHeader():View=LinearLayout(host).apply {
        gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),0,ui.dp(4),0)
        addView(ui.iconView("brand"),LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)))
        addView(ui.title("Shader Gallery",23f).apply {setPadding(ui.dp(10),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
        gridToggle=ui.iconButton("gallery","Показать плитками") {setGrid(!grid)}
        addView(gridToggle,LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)))
        minimumHeight=ui.dp(56)
        updateToggle()
    }

    private fun updateToggle() {
        gridToggle.setImageDrawable(ui.icon(if(grid)"list" else "gallery"))
        gridToggle.contentDescription=if(grid)"Показать списком" else "Показать плитками"
    }

    fun setGrid(value:Boolean) {
        grid=value;Appearance.setGrid(host,value);updateToggle()
        rebuildRows();list.setSelection(0)
    }

    private fun buildSearch():View=LinearLayout(host).apply {
        gravity=Gravity.CENTER_VERTICAL;background=ui.shape(p.fill,100f);setPadding(ui.dp(16),0,ui.dp(4),0)
        addView(ui.iconView("search",p.muted),LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)))
        search=EditText(host).apply {
            hint="Поиск работ, тегов и авторов";contentDescription="Поиск по названию, тегу или автору"
            textSize=16f;typeface=ui.sans;includeFontPadding=false;setTextColor(p.ink);setHintTextColor(p.muted)
            background=null;setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT;imeOptions=EditorInfo.IME_ACTION_SEARCH
            setPadding(ui.dp(12),0,ui.dp(8),0)
            setOnFocusChangeListener {_,focused->setFabVisible(!focused)}
            setOnEditorActionListener {_,_,_->hideKeyboard();handler.removeCallbacks(searchRun);searchRun.run();true}
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                    clear.visibility=if(s.isNullOrEmpty())View.GONE else View.VISIBLE
                    handler.removeCallbacks(searchRun);handler.postDelayed(searchRun,450)
                }
                override fun afterTextChanged(s:Editable?){}
            })
        }
        addView(search,LinearLayout.LayoutParams(0,-1,1f))
        clear=ui.iconButton("close","Очистить поиск",p.muted) {search.setText("");hideKeyboard()}.apply {visibility=View.GONE}
        addView(clear,LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)))
    }

    private fun hideKeyboard() {
        (host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken,0)
        search.clearFocus()
        view.requestFocus()
    }

    private fun buildChips():View {
        val row=LinearLayout(host).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),0,ui.dp(8),0)}
        modes.forEach {(key,label)->
            val chip=ui.chip(label).apply {setOnClickListener {if(mode!=key){mode=key;updateChips();reload()}}}
            chips[key]=chip
            row.addView(chip,LinearLayout.LayoutParams(-2,ui.dp(40)).apply {rightMargin=ui.dp(8)})
        }
        categoryChip=ui.chip("Категория").apply {setOnClickListener {pickCategory()}}
        row.addView(categoryChip,LinearLayout.LayoutParams(-2,ui.dp(40)).apply {rightMargin=ui.dp(8)})
        updateChips()
        return HorizontalScrollView(host).apply {isHorizontalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER;addView(row)}
    }

    private fun updateChips() {
        chips.forEach {(key,chip)->ui.styleChip(chip,key==mode)}
        categoryChip.text=if(category.isEmpty())"Категория" else category
        ui.styleChip(categoryChip,category.isNotEmpty())
    }

    private fun pickCategory() {
        ui.sheet("Категория") {content,dialog->
            categories.forEachIndexed {index,label->
                val value=if(index==0)"" else label
                content.add(ui.optionRow(label,value==category) {
                    dialog.dismiss()
                    if(value!=category){category=value;updateChips();reload()}
                })
            }
        }.show()
    }

    private fun buildFab() {
        fab=LinearLayout(host).apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(18),0,ui.dp(22),0);elevation=ui.dpf(6f)
            background=ui.ripple(ui.shape(p.lime,100f),100f);isClickable=true;isFocusable=true
            contentDescription="Создать с DNA Studio"
            addView(ui.iconView("sparkle",p.onLime),LinearLayout.LayoutParams(ui.dp(24),ui.dp(24)))
            addView(ui.text("Создать",15f,p.onLime,ui.sansSemi).apply {setPadding(ui.dp(8),0,0,0)})
            setOnClickListener {host.openDna(null)}
        }
        view.addView(fab,FrameLayout.LayoutParams(-2,ui.dp(56),Gravity.BOTTOM or Gravity.END).apply {setMargins(0,0,ui.dp(16),ui.dp(16))})
    }

    private fun setFabVisible(visible:Boolean) {
        if(!::fab.isInitialized)return
        val target=if(visible)0f else ui.dpf(96f)
        if(fab.translationY==target)return
        fab.animate().cancel();fab.animate().translationY(target).setDuration(180).start()
    }

    private fun buildList():ListView=ListView(host).apply {
        divider=null;dividerHeight=0;selector=ColorDrawable(Color.TRANSPARENT);cacheColorHint=Color.TRANSPARENT
        clipToPadding=false;setPadding(0,ui.dp(4),0,ui.dp(96));isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER
        setBackgroundColor(p.paper);adapter=feedAdapter
        var lastFirst=0;var lastTop=0
        val slop=ui.dp(3)
        setOnScrollListener(object:AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view:AbsListView?,state:Int){if(state==AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)hideKeyboard()}
            override fun onScroll(view:AbsListView,first:Int,visible:Int,total:Int) {
                val top=view.getChildAt(0)?.top ?: 0
                if(first==0 && top>=0)setFabVisible(true)
                else if(first>lastFirst || (first==lastFirst && top<lastTop-slop))setFabVisible(false)
                else if(first<lastFirst || top>lastTop+slop)setFabVisible(true)
                lastFirst=first;lastTop=top
                if(total>0 && first+visible>=total-2)loadMore()
            }
        })
    }

    fun scrollToTop(){list.smoothScrollToPosition(0)}

    /** Saved / following lists depend on actions taken elsewhere; refresh them quietly. */
    fun onLibraryChanged(){if(needsAccount())reload(preserve=true)}

    fun onConfigurationChanged(){rebuildRows()}

    fun showMode(newMode:String) {
        if(modes.none {it.first==newMode})return
        mode=newMode;updateChips();reload()
    }

    /** Fetches page one. [preserve] keeps the current rows on screen until the answer arrives (pull to refresh). */
    private fun reload(preserve:Boolean=false) {
        val current=++generation
        cursor=null;loading=false;moreFailed=false;firstFailure=null
        if(!preserve){cards.clear();rebuildRows()}
        if(needsAccount()) {
            if(account.checking){showSkeleton();pull.finish();return}
            if(!account.signedIn){cards.clear();rebuildRows();showState(signInState());pull.finish();return}
        }
        val requestMode=mode;val requestQuery=query;val requestCategory=category;val source=account.source;val viewer=account.viewerId
        loadedSource=source
        // Last known page first: it makes the feed usable instantly, offline included.
        val remembered=if(preserve)null else GalleryClient.cachedFeed(host,source,requestMode,requestQuery,requestCategory,viewer)
        if(remembered!=null && cards.isEmpty()){
            cards.addAll(remembered.page.items);cursor=remembered.page.nextCursor
            rebuildRows();showState(null)
            if(remembered.ageMs<FRESH_MS){pull.finish();return}
        }
        if(cards.isEmpty())showSkeleton() else states.removeAllViews()
        loading=true
        host.background({
            GalleryClient.feed(host,source,requestMode,requestQuery,requestCategory,null,viewer)
        },{error->
            if(current==generation){
                loading=false;pull.finish();firstFailure=friendly(error,"Не удалось загрузить галерею")
                if(cards.isEmpty())showState(errorState(firstFailure!!)) else host.toast(firstFailure!!,"Повторить") {reload(true)}
            }
        }) {page->
            if(current==generation){
                loading=false;pull.finish()
                // Same answer as the page already on screen: leave it (and the scroll position) alone.
                if(remembered!=null && remembered.page.signature==page.signature && cards.isNotEmpty()){rebuildRows();return@background}
                cards.clear();cards.addAll(page.items);cursor=page.nextCursor
                rebuildRows();showState(if(cards.isEmpty())emptyStateView() else null)
            }
        }
    }

    private fun loadMore() {
        if(loading || cursor==null || moreFailed || cards.isEmpty())return
        val current=generation;val next=cursor
        loading=true;rebuildRows()
        val requestMode=mode;val requestQuery=query;val requestCategory=category;val source=account.source
        host.background({
            GalleryClient.feed(host,source,requestMode,requestQuery,requestCategory,next)
        },{error->
            if(current==generation){loading=false;moreFailed=true;rebuildRows();host.toast(friendly(error,"Не удалось загрузить ещё"))}
        }) {page->
            if(current==generation){
                loading=false;cursor=page.nextCursor
                val known=cards.map {it.id}.toSet()
                cards.addAll(page.items.filterNot {it.id in known})
                rebuildRows()
            }
        }
    }

    private fun showSkeleton() {states.removeAllViews();states.addView(skeleton,FrameLayout.LayoutParams(-1,-2));states.setBackgroundColor(p.paper)}
    private fun showState(content:View?) {
        states.removeAllViews()
        if(content==null){states.setBackgroundColor(Color.TRANSPARENT);return}
        states.setBackgroundColor(p.paper)
        states.addView(android.widget.ScrollView(host).apply {isFillViewport=true;addView(content)},FrameLayout.LayoutParams(-1,-1))
    }

    private fun signInState():View=emptyState(ui,"account","Нужен вход",
        if(mode=="saved")"Войдите, чтобы увидеть сохранённые работы на любом устройстве." else "Войдите, чтобы читать ленту авторов, на которых вы подписаны.","Войти") {
        AuthSheet.show(host)
    }

    private fun errorState(message:String):View=emptyState(ui,"info","Не удалось загрузить",message,"Повторить") {reload()}

    private fun emptyStateView():View {
        if(query.isNotEmpty() || category.isNotEmpty())return emptyState(ui,"search","Ничего не найдено","Попробуйте другой запрос или сбросьте фильтры.","Сбросить") {
            search.setText("");category="";query="";updateChips();reload()
        }
        return when(mode) {
            "saved"->emptyState(ui,"bookmark","Пока пусто","Нажмите закладку на работе, и она появится здесь.","К ленте") {mode="new";updateChips();reload()}
            "following"->emptyState(ui,"heart","Лента авторов пуста","Подпишитесь на авторов, чтобы видеть их новые работы.","К ленте") {mode="new";updateChips();reload()}
            else->emptyState(ui,"sparkle","Работ пока нет","Станьте первым: создайте работу в DNA Studio.","Создать") {host.openDna(null)}
        }
    }

    private fun columns()=if(grid)max(2,(host.resources.configuration.screenWidthDp-8)/170) else 1

    private fun rebuildRows() {
        val perRow=columns()
        val built=cards.chunked(perRow).map {Row.Works(it)}.toMutableList<Row>()
        if(cards.isNotEmpty() && (loading || moreFailed))built.add(Row.Footer)
        rows=built;feedAdapter.notifyDataSetChanged()
    }

    private fun open(card:GalleryCard){host.openWork(account.source,card.id,card.revisionId,card)}

    private fun accent(category:String)=when(category) {
        "Природа"->Color.rgb(96,172,139)
        "Геометрия"->Color.rgb(108,139,238)
        "Свет"->Color.rgb(235,153,106)
        else->Color.rgb(140,112,230)
    }

    private fun artwork(image:ImageView,card:GalleryCard,radius:Int) {
        val base=accent(card.category)
        image.background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(255,(Color.red(base)+255*2)/3,(Color.green(base)+255*2)/3,(Color.blue(base)+255*2)/3),base))
        image.scaleType=ImageView.ScaleType.CENTER_CROP
        image.clipToOutline=true
        image.outlineProvider=object:ViewOutlineProvider() {
            override fun getOutline(target:View,outline:Outline){outline.setRoundRect(0,0,target.width,target.height,ui.dpf(radius.toFloat()))}
        }
        // Rows are recycled, so remember which revision this view is waiting for.
        image.tag=card.revisionId
        val ready=Thumbs.peek(card)
        image.setImageBitmap(ready)
        if(ready==null)Thumbs.load(host,account.source,card) {bitmap->
            if(bitmap!=null && image.tag==card.revisionId)image.setImageBitmap(bitmap)
        }
    }

    private inner class ListRow:LinearLayout(host) {
        private val art=ImageView(host)
        private val title=ui.text("",17f,p.ink,ui.serif,1)
        private val date=ui.text("",12f,p.muted)
        private val meta=ui.text("",14f,p.muted,ui.sansMedium,1)
        private val description=ui.text("",14f,p.muted,ui.sans,2).apply {setLineSpacing(0f,1.2f)}
        private var card:GalleryCard?=null
        init {
            orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10))
            background=ui.ripple(null);isClickable=true;isFocusable=true
            addView(art,LayoutParams(ui.dp(76),ui.dp(76)))
            val column=LinearLayout(host).apply {orientation=VERTICAL}
            val top=LinearLayout(host).apply {gravity=Gravity.CENTER_VERTICAL}
            top.addView(title,LayoutParams(0,-2,1f))
            top.addView(date,LayoutParams(-2,-2).apply {leftMargin=ui.dp(8)})
            column.addView(top)
            column.addView(meta,LayoutParams(-1,-2).apply {topMargin=ui.dp(3)})
            column.addView(description,LayoutParams(-1,-2).apply {topMargin=ui.dp(4)})
            addView(column,LayoutParams(0,-2,1f).apply {leftMargin=ui.dp(14)})
            setOnClickListener {card?.let {open(it)}}
        }
        fun bind(next:GalleryCard) {
            card=next
            artwork(art,next,16)
            title.text=next.title;date.text=shortDate(next.createdAt)
            meta.text="${next.author} · ${next.category}"
            description.text=next.description;description.visibility=if(next.description.isBlank())View.GONE else View.VISIBLE
        }
    }

    private inner class Tile:LinearLayout(host) {
        private val frame=SquareFrame(host)
        private val art=ImageView(host)
        private val title=ui.text("",16f,p.ink,ui.serif,1)
        private val author=ui.text("",13f,p.muted,ui.sansMedium,1)
        private var card:GalleryCard?=null
        init {
            orientation=VERTICAL;background=ui.ripple(null,18f);isClickable=true;isFocusable=true
            frame.fill(art);addView(frame,LayoutParams(-1,-2))
            addView(title,LayoutParams(-1,-2).apply {topMargin=ui.dp(8)})
            addView(author,LayoutParams(-1,-2).apply {topMargin=ui.dp(2)})
            setOnClickListener {card?.let {open(it)}}
        }
        fun bind(next:GalleryCard){card=next;artwork(art,next,18);title.text=next.title;author.text=next.author}
    }

    private inner class GridRow(val columns:Int):LinearLayout(host) {
        private val tiles=List(columns) {Tile()}
        init {
            orientation=HORIZONTAL;setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(10))
            tiles.forEach {addView(it,LayoutParams(0,-2,1f).apply {setMargins(ui.dp(6),0,ui.dp(6),0)})}
        }
        fun bind(items:List<GalleryCard>) {
            tiles.forEachIndexed {index,tile->
                if(index<items.size){tile.visibility=View.VISIBLE;tile.bind(items[index])} else tile.visibility=View.INVISIBLE
            }
        }
    }

    private inner class FooterRow:LinearLayout(host) {
        init {
            gravity=Gravity.CENTER;setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12))
            if(moreFailed) {
                addView(ui.button("Не удалось загрузить. Повторить","text") {moreFailed=false;rebuildRows();loadMore()},LayoutParams(-2,ui.dp(48)))
            } else {
                addView(ProgressBar(host).apply {isIndeterminate=true;indeterminateTintList=android.content.res.ColorStateList.valueOf(p.link)},LayoutParams(ui.dp(28),ui.dp(28)))
            }
        }
    }
}
