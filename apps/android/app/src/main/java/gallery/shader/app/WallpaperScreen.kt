package gallery.shader.app

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Second tab: what is on the home screen now and how hungry it may be. */
internal class WallpaperScreen(private val host:MainActivity) {
    private val ui=host.ui
    private val p=ui.p
    val view=FrameLayout(host)
    private val content=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(16),0,ui.dp(16),ui.dp(32))}

    init {
        view.setBackgroundColor(p.paper)
        view.fill(ScrollView(host).apply {isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER;addView(content)})
        refresh()
    }

    fun refresh() {
        content.removeAllViews()
        content.add(ui.title("Обои",30f)) {topMargin=ui.dp(16)}
        content.add(ui.body("Живая графика на экране вашего телефона.",15f,p.muted)) {topMargin=ui.dp(4);bottomMargin=ui.dp(18)}
        val shader=PackageStore.selected(host)
        if(shader==null) {
            content.add(emptyState(ui,"wallpaper","Обои пока не выбраны","Откройте любую работу в галерее и нажмите «Установить как обои».","К галерее") {host.selectTab(0)})
        } else {
            val card=ui.card(24f).apply {setPadding(ui.dp(20),ui.dp(18),ui.dp(20),ui.dp(20))}
            card.addView(ui.text("СЕЙЧАС НА ЭКРАНЕ",11f,p.muted,ui.sansSemi).apply {letterSpacing=.12f})
            card.addView(ui.title(shader.title,25f,maxLines=2).apply {setPadding(0,ui.dp(8),0,ui.dp(4))})
            card.addView(ui.text("${shader.authorName} · лицензия ${shader.license}",14f,p.muted))
            val actions=LinearLayout(host)
            actions.addView(ui.button("Открыть","tonal") {host.openWork(host.account.source,shader.workId,shader.revisionId,null)},LinearLayout.LayoutParams(0,ui.dp(48),1f))
            actions.addView(ui.button("Установить","primary") {host.launchWallpaperPicker()},LinearLayout.LayoutParams(0,ui.dp(48),1f).apply {leftMargin=ui.dp(8)})
            card.addView(actions,LinearLayout.LayoutParams(-1,-2).apply {topMargin=ui.dp(18)})
            content.add(card)
        }

        content.add(ui.title("Качество и батарея",20f)) {topMargin=ui.dp(28);bottomMargin=ui.dp(4)}
        setting("Разрешение рендера",listOf("50%","75%","100%"),when(PackageStore.quality(host)) {0.5f->0;1f->2;else->1},
            listOf("Экономно: картинка чуть мягче, батарея служит дольше.","Сбалансированно: хорошая картинка без лишней нагрузки.","Максимальная чёткость. Телефон будет нагреваться сильнее.")) {
            PackageStore.setQuality(host,floatArrayOf(.5f,.75f,1f)[it])
        }
        setting("Частота кадров",listOf("15","30","60"),when(PackageStore.fps(host)) {15->0;60->2;else->1},
            listOf("15 кадров в секунду: минимум энергии, подходит для медленных работ.","30 кадров в секунду: плавно и экономно.","60 кадров в секунду: максимально плавно, но быстрее садит батарею.")) {
            PackageStore.setFps(host,intArrayOf(15,30,60)[it])
        }

        val note=LinearLayout(host).apply {gravity=Gravity.CENTER_VERTICAL;background=ui.shape(p.soft,18f);setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14))}
        note.addView(ui.iconView("info",p.link),LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)))
        note.addView(ui.body("После загрузки работы обои идут без интернета.",14f,p.ink),LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=ui.dp(12)})
        content.add(note) {topMargin=ui.dp(24)}
    }

    private fun setting(label:String,options:List<String>,selected:Int,hints:List<String>,onSelect:(Int)->Unit) {
        content.add(ui.text(label,16f,p.ink,ui.sansSemi)) {topMargin=ui.dp(16)}
        val hint=ui.body(hints[selected],13f,p.muted)
        content.add(Segmented(ui,options,selected) {onSelect(it);hint.text=hints[it]}) {topMargin=ui.dp(10)}
        content.add(hint) {topMargin=ui.dp(8)}
    }
}
