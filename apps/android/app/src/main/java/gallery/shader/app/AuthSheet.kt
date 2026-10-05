package gallery.shader.app

import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Sign in / sign up as a bottom sheet; errors stay inside the sheet next to the fields. */
internal object AuthSheet {
    fun show(host:MainActivity,reason:String?=null,onSigned:()->Unit={}) {
        val ui=host.ui;val p=ui.p;val account=host.account
        var signingUp=false;var busy=false
        ui.sheet("Аккаунт Shader Gallery") {content,dialog->
            if(reason!=null)content.add(ui.body(reason,15f,p.muted)) {bottomMargin=ui.dp(12)}
            val name=ui.field("Имя",input=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS).apply {visibility=View.GONE;setAutofillHints(View.AUTOFILL_HINT_NAME)}
            val email=ui.field("Электронная почта",input=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply {setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)}
            val password=ui.field("Пароль · от 8 символов",input=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {setAutofillHints(View.AUTOFILL_HINT_PASSWORD)}
            val message=ui.text("",14f,p.danger,ui.sansMedium).apply {visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE;setLineSpacing(0f,1.2f)}
            lateinit var submit:TextView
            fun render() {
                name.visibility=if(signingUp)View.VISIBLE else View.GONE
                submit.text=if(busy)(if(signingUp)"Регистрируем…" else "Входим…") else if(signingUp)"Зарегистрироваться" else "Войти"
                submit.isEnabled=!busy
            }
            fun say(text:String,error:Boolean=true) {message.text=text;message.setTextColor(if(error)p.danger else p.link);message.visibility=if(text.isBlank())View.GONE else View.VISIBLE}
            content.add(Segmented(ui,listOf("Вход","Регистрация"),0) {signingUp=it==1;say("");render()})
            content.add(name) {topMargin=ui.dp(14)}
            content.add(email) {topMargin=ui.dp(10)}
            content.add(password) {topMargin=ui.dp(10)}
            content.add(message) {topMargin=ui.dp(12)}
            submit=ui.button("Войти","primary") {
                if(busy)return@button
                val enteredName=if(signingUp)name.text.toString().trim() else null
                if(email.text.isBlank() || password.text.length<8 || (signingUp && enteredName.isNullOrEmpty())){
                    say(if(signingUp)"Укажите имя, почту и пароль от 8 символов" else "Укажите почту и пароль от 8 символов");return@button
                }
                busy=true;say("");render()
                account.authenticate(email.text.toString().trim(),password.text.toString(),enteredName) {outcome->
                    busy=false;render()
                    when(outcome) {
                        is Account.Outcome.Signed->{dialog.dismiss();host.toast("Вы вошли в аккаунт");onSigned()}
                        is Account.Outcome.NeedsConfirmation->say("Подтвердите адрес по ссылке из письма, затем войдите.",false)
                        is Account.Outcome.Failed->say(outcome.message)
                    }
                }
            }
            content.add(submit,height=ui.dp(52)) {topMargin=ui.dp(14)}
            if(GalleryClient.isCloud(account.source)) {
                content.add(ui.text("или",13f,p.muted).apply {gravity=Gravity.CENTER},height=ui.dp(40))
                content.add(ui.button("Продолжить с Google","outline") {
                    try {
                        val target=GalleryClient.googleUrl(host)
                        dialog.dismiss();host.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(target)))
                    } catch(e:Exception) {say(friendly(e,"Не удалось открыть Google"))}
                },height=ui.dp(52))
            }
            render()
        }.show()
    }
}
