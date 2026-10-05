package gallery.shader.app

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Third tab: account, shortcuts and appearance, laid out like a messenger's settings list. */
internal class ProfileScreen(private val host:MainActivity) {
    private val ui=host.ui
    private val p=ui.p
    private val account get()=host.account
    val view=FrameLayout(host)
    private val content=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(16),0,ui.dp(16),ui.dp(32))}

    init {
        view.setBackgroundColor(p.paper)
        view.fill(ScrollView(host).apply {isVerticalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER;addView(content)})
        account.listen {refresh()}
        refresh()
    }

    private fun group(vararg rows:View):View {
        val card=ui.card(20f)
        rows.forEachIndexed {index,row->
            if(index>0)card.addView(ui.divider(),LinearLayout.LayoutParams(-1,1).apply {leftMargin=ui.dp(56)})
            card.addView(row)
        }
        return card
    }

    private fun themeLabel()=when(Appearance.mode(host)) {Appearance.LIGHT->"Светлая";Appearance.DARK->"Тёмная";else->"Системная"}

    fun refresh() {
        content.removeAllViews()
        content.add(ui.title("Профиль",30f)) {topMargin=ui.dp(16);bottomMargin=ui.dp(18)}
        val head=LinearLayout(host).apply {gravity=Gravity.CENTER_VERTICAL}
        if(account.signedIn) {
            val name=account.name ?: if(account.checking)"Загружаем…" else "Без имени"
            head.addView(AvatarView(ui,name,72))
            val column=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(16),0,0,0)}
            column.addView(ui.title(name,24f,maxLines=2))
            account.handle?.let {column.addView(ui.text("@$it",14f,p.muted,ui.sansMedium).apply {setPadding(0,ui.dp(4),0,0)})}
            head.addView(column,LinearLayout.LayoutParams(0,-2,1f))
            content.add(head)
            account.bio?.let {content.add(ui.body(it,15f,p.ink)) {topMargin=ui.dp(14)}}
        } else {
            head.addView(FrameLayout(host).apply {
                background=ui.oval(p.soft)
                addView(ui.iconView("account",p.link),FrameLayout.LayoutParams(ui.dp(34),ui.dp(34),Gravity.CENTER))
            },LinearLayout.LayoutParams(ui.dp(72),ui.dp(72)))
            val column=LinearLayout(host).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(16),0,0,0)}
            column.addView(ui.title(if(account.checking)"Проверяем аккаунт…" else "Вы не вошли",24f))
            head.addView(column,LinearLayout.LayoutParams(0,-2,1f))
            content.add(head)
            content.add(ui.body("Войдите, чтобы сохранять работы, подписываться на авторов, комментировать и создавать в DNA Studio.",15f,p.muted)) {topMargin=ui.dp(14)}
            content.add(ui.button("Войти или зарегистрироваться","primary") {AuthSheet.show(host)},height=ui.dp(52)) {topMargin=ui.dp(16)}
        }

        val shortcuts=mutableListOf<View>()
        if(account.signedIn) {
            shortcuts+=ui.actionRow("bookmark","Сохранённое",chevron=true) {host.showFeed("saved")}
            shortcuts+=ui.actionRow("heart","Подписки",chevron=true) {host.showFeed("following")}
        }
        shortcuts+=ui.actionRow("sparkle","DNA Studio",chevron=true) {host.openDna(null)}
        if(account.signedIn)account.viewerId?.let {id->
            shortcuts+=ui.actionRow("external","Профиль на сайте",chevron=true) {host.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("${account.source}/profile/$id")))}
        }
        content.add(group(*shortcuts.toTypedArray())) {topMargin=ui.dp(24)}

        content.add(group(
            ui.actionRow("theme","Тема",themeLabel(),chevron=true) {pickTheme()},
            ui.actionRow(if(Appearance.grid(host))"gallery" else "list","Вид ленты",if(Appearance.grid(host))"Плитки" else "Список",chevron=true) {pickFeedView()},
            ui.actionRow("info","О приложении",chevron=true) {about()}
        )) {topMargin=ui.dp(16)}

        if(account.signedIn)content.add(group(ui.actionRow("logout","Выйти из аккаунта",tint=p.danger) {confirmSignOut()})) {topMargin=ui.dp(16)}
    }

    private fun pickTheme() {
        ui.sheet("Тема") {content,dialog->
            val current=Appearance.mode(host)
            listOf(Appearance.SYSTEM to "Как в системе",Appearance.LIGHT to "Светлая",Appearance.DARK to "Тёмная").forEach {(mode,label)->
                content.add(ui.optionRow(label,mode==current) {
                    dialog.dismiss()
                    if(mode!=current){Appearance.setMode(host,mode);host.recreate()}
                })
            }
        }.show()
    }

    private fun pickFeedView() {
        ui.sheet("Вид ленты") {content,dialog->
            val grid=Appearance.grid(host)
            content.add(ui.optionRow("Список",!grid,"Миниатюра, название и описание в строке") {dialog.dismiss();host.setFeedGrid(false);refresh()})
            content.add(ui.optionRow("Плитки",grid,"Крупные картинки по две в ряд") {dialog.dismiss();host.setFeedGrid(true);refresh()})
        }.show()
    }

    private fun about() {
        val version=try{host.packageManager.getPackageInfo(host.packageName,0).versionName}catch(_:Exception){""}
        ui.sheet("Shader Gallery") {content,dialog->
            content.add(ui.body("Версия $version",15f,p.muted))
            content.add(ui.body("Галерея живой графики: смотрите работы, настраивайте их и ставьте как обои. Адрес галереи:",15f,p.ink)) {topMargin=ui.dp(10)}
            content.add(ui.text(account.source,14f,p.link,ui.sansMedium)) {topMargin=ui.dp(4)}
            content.add(ui.body("Шрифты Lora и Manrope распространяются по лицензии SIL OFL.",13f,p.muted)) {topMargin=ui.dp(14)}
            content.add(ui.button("Закрыть","tonal") {dialog.dismiss()},height=ui.dp(52)) {topMargin=ui.dp(18)}
        }.show()
    }

    private fun confirmSignOut() {
        ui.sheet("Выйти из аккаунта?") {content,dialog->
            content.add(ui.body("Сохранённое и подписки останутся в аккаунте. Войти снова можно в любой момент.",15f,p.muted))
            content.add(ui.button("Выйти","danger") {dialog.dismiss();account.signOut {host.toast("Вы вышли из аккаунта");host.libraryChanged()}},height=ui.dp(52)) {topMargin=ui.dp(16)}
            content.add(ui.button("Отмена","text") {dialog.dismiss()},height=ui.dp(48)) {topMargin=ui.dp(4)}
        }.show()
    }
}
