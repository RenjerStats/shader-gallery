package gallery.shader.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** Theme and a few layout preferences, shared by every screen. */
object Appearance {
    const val SYSTEM="system"
    const val LIGHT="light"
    const val DARK="dark"
    private fun prefs(context:Context)=context.getSharedPreferences("gallery_visual",Context.MODE_PRIVATE)
    fun mode(context:Context):String {
        val saved=prefs(context)
        return saved.getString("theme",null) ?: if(saved.contains("dark"))(if(saved.getBoolean("dark",false))DARK else LIGHT) else SYSTEM
    }
    fun setMode(context:Context,mode:String){prefs(context).edit().putString("theme",mode).apply()}
    fun night(context:Context)=when(mode(context)) {
        DARK->true
        LIGHT->false
        else->(context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES
    }
    fun grid(context:Context)=prefs(context).getBoolean("grid",false)
    fun setGrid(context:Context,grid:Boolean){prefs(context).edit().putBoolean("grid",grid).apply()}
}

/** The web palette (paper / ink / blue / lime) with a dark counterpart. */
class Palette(val night:Boolean) {
    private fun pick(light:Long,dark:Long)=(if(night)dark else light).toInt()
    val paper=pick(0xfff8f6f2,0xff08080e)
    val surface=pick(0xfffffefa,0xff181921)
    val raised=pick(0xffffffff,0xff20222d)
    val fill=pick(0xffefede7,0xff1d1e28)
    val ink=pick(0xff222120,0xffeef0f8)
    val muted=pick(0xff67635f,0xff9fa4b8)
    val line=pick(0xffe3e2da,0xff2c2d39)
    val accent=pick(0xff284ced,0xff5568e8)
    val onAccent=Color.WHITE
    val link=pick(0xff284ced,0xff9ca9ff)
    val soft=pick(0xffe9edff,0xff252b48)
    val lime=0xffc2f64a.toInt()
    val onLime=0xff222120.toInt()
    val danger=pick(0xffb3261e,0xffff8a80)
    val inverse=pick(0xff2b2a29,0xffe6e8f2)
    val onInverse=pick(0xfff8f6f2,0xff14151d)
    val ripple=pick(0x1a000000,0x26ffffff)
    val scrim=0x99000000.toInt()
}

class Insets(val left:Int=0,val top:Int=0,val right:Int=0,val bottom:Int=0,val ime:Int=0) {
    val bottomPad get()=max(bottom,ime)
}

@Suppress("DEPRECATION")
fun readInsets(insets:WindowInsets):Insets {
    if(Build.VERSION.SDK_INT>=30) {
        val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return Insets(bars.left,bars.top,bars.right,bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom)
    }
    val system=insets.systemWindowInsetBottom
    val stable=insets.stableInsetBottom
    return Insets(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,stable,if(system>stable)system else 0)
}

/** Edge-to-edge window with transparent bars; every screen pads itself by the insets. */
@Suppress("DEPRECATION")
fun Activity.edgeToEdge(night:Boolean) {
    window.statusBarColor=Color.TRANSPARENT
    window.navigationBarColor=Color.TRANSPARENT
    if(Build.VERSION.SDK_INT>=28)window.attributes=window.attributes.apply {layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
    if(Build.VERSION.SDK_INT>=30) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            it.setSystemBarsAppearance(if(night)0 else mask,mask)
        }
    } else {
        var flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if(!night)flags=flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility=flags
    }
}

private val pool=Executors.newFixedThreadPool(4)

/** Runs [work] off the main thread and delivers the result only while the activity is alive. */
fun <T> Activity.background(work:()->T,fail:(Exception)->Unit={},ok:(T)->Unit) {
    pool.execute {
        try {
            val result=work()
            runOnUiThread {if(!isDestroyed && !isFinishing)ok(result)}
        } catch(e:Exception) {
            runOnUiThread {if(!isDestroyed && !isFinishing)fail(e)}
        }
    }
}

fun friendly(error:Throwable,fallback:String):String=when(error) {
    is SocketTimeoutException->"Время ожидания истекло. Проверьте соединение."
    is UnknownHostException,is ConnectException->"Нет соединения с интернетом"
    else->error.message?.takeIf {it.isNotBlank()} ?: fallback
}

private val months=listOf("янв","фев","мар","апр","мая","июн","июл","авг","сен","окт","ноя","дек")

/** "25 сен" within the current year, "25.09.25" before it; empty when the date is unknown. */
fun shortDate(iso:String):String {
    val match=Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return ""
    val (year,month,day)=match.destructured
    val now=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return if(year.toInt()==now)"${day.toInt()} ${months[month.toInt().coerceIn(1,12)-1]}" else "$day.$month.${year.takeLast(2)}"
}

/** Factory for every view of the app, so screens never touch colours or fonts directly. */
internal class Ui(val context:Context) {
    val p=Palette(Appearance.night(context))
    private val density=context.resources.displayMetrics.density
    fun dp(n:Int)=(n*density).roundToInt()
    fun dpf(n:Float)=n*density
    val serif:Typeface by lazy {context.resources.getFont(R.font.lora_medium)}
    val serifRegular:Typeface by lazy {context.resources.getFont(R.font.lora)}
    val sans:Typeface by lazy {context.resources.getFont(R.font.manrope)}
    val sansMedium:Typeface by lazy {context.resources.getFont(R.font.manrope_medium)}
    val sansSemi:Typeface by lazy {context.resources.getFont(R.font.manrope_semibold)}

    fun shape(fill:Int,radius:Float,stroke:Int=0,strokeDp:Int=1)=GradientDrawable().apply {
        cornerRadius=dpf(radius);setColor(fill);if(stroke!=0)setStroke(dp(strokeDp),stroke)
    }
    fun oval(fill:Int)=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(fill)}
    fun ripple(content:Drawable?,radius:Float=0f,circle:Boolean=false)=RippleDrawable(
        ColorStateList.valueOf(p.ripple),content,
        GradientDrawable().apply {if(circle)shape=GradientDrawable.OVAL else cornerRadius=dpf(radius);setColor(Color.BLACK)}
    )

    fun text(value:CharSequence?,size:Float=15f,color:Int=p.ink,face:Typeface=sans,maxLines:Int=0)=TextView(context).apply {
        text=value;textSize=size;setTextColor(color);typeface=face;includeFontPadding=false
        if(maxLines>0){this.maxLines=maxLines;ellipsize=TextUtils.TruncateAt.END}
    }
    /** Reading text: a little more air between lines. */
    fun body(value:CharSequence?,size:Float=16f,color:Int=p.ink,maxLines:Int=0)=text(value,size,color,sans,maxLines).apply {setLineSpacing(0f,1.3f)}
    fun title(value:CharSequence?,size:Float=24f,color:Int=p.ink,maxLines:Int=0)=text(value,size,color,serif,maxLines).apply {setLineSpacing(0f,1.12f)}

    fun icon(name:String,color:Int=p.ink)=GalleryIcon(name,color)
    fun iconButton(name:String,label:String,tint:Int=p.ink,action:()->Unit)=ImageButton(context).apply {
        setImageDrawable(GalleryIcon(name,tint));contentDescription=label
        setPadding(dp(12),dp(12),dp(12),dp(12));background=ripple(null,circle=true)
        setOnClickListener {action()}
    }
    fun iconView(name:String,color:Int=p.ink)=ImageView(context).apply {setImageDrawable(GalleryIcon(name,color));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}

    fun button(label:String,kind:String="tonal",action:()->Unit):Button=DimButton(context).apply {
        text=label;textSize=15f;typeface=sansSemi;isAllCaps=false;includeFontPadding=false
        stateListAnimator=null;elevation=0f;minHeight=0;minimumHeight=dp(48);minWidth=0;minimumWidth=0
        setPadding(dp(20),0,dp(20),0)
        val (fill,ink)=when(kind) {
            "primary"->p.accent to p.onAccent
            "lime"->p.lime to p.onLime
            "outline"->0 to p.ink
            "text"->0 to p.link
            "danger"->0 to p.danger
            else->p.soft to p.link
        }
        setTextColor(ink)
        background=ripple(shape(fill,100f,if(kind=="outline")p.line else 0),100f)
        setOnClickListener {action()}
    }

    fun chip(label:String)=TextView(context).apply {
        text=label;textSize=14f;typeface=sansSemi;gravity=Gravity.CENTER;includeFontPadding=false;maxLines=1
        setPadding(dp(16),0,dp(16),0);isClickable=true;isFocusable=true
    }
    fun styleChip(chip:TextView,selected:Boolean) {
        chip.isSelected=selected
        chip.setTextColor(if(selected)p.link else p.muted)
        chip.background=ripple(shape(if(selected)p.soft else p.fill,100f),100f)
    }

    fun field(hint:String,multiline:Boolean=false,minLines:Int=1,maxLength:Int=0,input:Int=InputType.TYPE_CLASS_TEXT)=EditText(context).apply {
        this.hint=hint;contentDescription=hint;textSize=16f;typeface=sans;includeFontPadding=false
        setTextColor(p.ink);setHintTextColor(p.muted)
        if(multiline) {
            this.minLines=minLines;gravity=Gravity.TOP or Gravity.START
            inputType=input or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            setSingleLine(true);inputType=input
        }
        if(maxLength>0)filters=arrayOf(android.text.InputFilter.LengthFilter(maxLength))
        minimumHeight=dp(52);setPadding(dp(16),dp(14),dp(16),dp(14))
        background=StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),shape(p.fill,16f,p.link,2))
            addState(intArrayOf(),shape(p.fill,16f))
        }
    }

    fun divider()=View(context).apply {setBackgroundColor(p.line)}
    fun spacer(heightDp:Int)=View(context).apply {layoutParams=LinearLayout.LayoutParams(1,dp(heightDp))}
    fun card(radius:Float=20f)=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;background=shape(p.surface,radius,p.line)}

    /** A tappable row with a trailing check mark, used by pickers. */
    fun optionRow(label:String,selected:Boolean,hint:String?=null,action:()->Unit)=LinearLayout(context).apply {
        gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(56);setPadding(dp(0),dp(8),dp(0),dp(8))
        background=ripple(null,14f);isClickable=true;isFocusable=true
        val column=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
        column.addView(text(label,16f,p.ink,if(selected)sansSemi else sans))
        if(hint!=null)column.addView(text(hint,13f,p.muted).apply {setPadding(0,dp(2),0,0)})
        addView(column,LinearLayout.LayoutParams(0,-2,1f))
        if(selected)addView(iconView("check",p.link),LinearLayout.LayoutParams(dp(24),dp(24)))
        setOnClickListener {action()}
    }

    /** Icon + label row used in menus and settings lists. */
    fun actionRow(icon:String,label:String,value:String?=null,tint:Int=p.ink,chevron:Boolean=false,action:()->Unit)=LinearLayout(context).apply {
        gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(56);setPadding(dp(16),0,dp(12),0)
        background=ripple(null);isClickable=true;isFocusable=true
        addView(iconView(icon,if(tint==p.ink)p.muted else tint),LinearLayout.LayoutParams(dp(24),dp(24)))
        addView(text(label,16f,tint),LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(16)})
        if(value!=null)addView(text(value,15f,p.muted))
        if(chevron)addView(iconView("chevron",p.muted),LinearLayout.LayoutParams(dp(20),dp(20)).apply {leftMargin=dp(6)})
        setOnClickListener {action()}
    }

    /** A thin track: ProgressBar stretches its drawable to the full view height unless a layer pins it. */
    fun track(drawable:Drawable,heightDp:Int)=android.graphics.drawable.LayerDrawable(arrayOf(drawable)).apply {
        setLayerHeight(0,dp(heightDp));setLayerGravity(0,Gravity.CENTER_VERTICAL)
    }

    fun slider(max:Int,progress:Int,label:String,onChange:(Int)->Unit)=android.widget.SeekBar(context).apply {
        this.max=max;this.progress=progress;contentDescription=label;splitTrack=false
        setPadding(dp(14),0,dp(14),0);minimumHeight=dp(48)
        progressDrawable=android.graphics.drawable.LayerDrawable(arrayOf(
            shape(p.line,4f).apply {setSize(dp(200),dp(6))},
            android.graphics.drawable.ClipDrawable(shape(p.accent,4f).apply {setSize(dp(200),dp(6))},Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL)
        )).apply {
            setId(0,android.R.id.background);setId(1,android.R.id.progress)
            for(layer in 0..1){setLayerHeight(layer,dp(6));setLayerGravity(layer,Gravity.CENTER_VERTICAL)}
        }
        thumb=oval(p.accent).apply {setStroke(dp(3),p.surface);setSize(dp(26),dp(26))}
        thumbOffset=0
        setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:android.widget.SeekBar?,value:Int,fromUser:Boolean){if(fromUser)onChange(value)}
            override fun onStartTrackingTouch(bar:android.widget.SeekBar?){}
            override fun onStopTrackingTouch(bar:android.widget.SeekBar?){}
        })
    }

    /** Bottom sheet in the Telegram manner: drag handle, rounded top, dimmed backdrop. */
    fun sheet(title:String?=null,build:(content:LinearLayout,dialog:Dialog)->Unit):Dialog {
        val dialog=Dialog(context,if(p.night)R.style.SheetDialogDark else R.style.SheetDialog)
        val column=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
        column.addView(View(context).apply {background=shape((p.muted and 0x00ffffff) or 0x66000000,2f)},
            LinearLayout.LayoutParams(dp(36),dp(4)).apply {gravity=Gravity.CENTER_HORIZONTAL;topMargin=dp(10);bottomMargin=dp(6)})
        if(title!=null)column.addView(title(title,21f).apply {setPadding(dp(16),dp(8),dp(16),dp(10))})
        val content=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),dp(16))}
        column.addView(MaxHeightScroll(context,(context.resources.displayMetrics.heightPixels*.8f).toInt()).apply {addView(content)})
        val radius=dpf(26f)
        column.background=GradientDrawable().apply {setColor(p.raised);cornerRadii=floatArrayOf(radius,radius,radius,radius,0f,0f,0f,0f)}
        column.setOnApplyWindowInsetsListener {view,insets->view.setPadding(0,0,0,readInsets(insets).bottomPad);insets}
        build(content,dialog)
        dialog.setContentView(column)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM);setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return dialog
    }

    /** Hue / saturation / value picker. [onChange] fires live with a "#rrggbb" string. */
    fun colorSheet(title:String,initial:String,onChange:(String)->Unit) {
        val hsv=FloatArray(3);Color.colorToHSV(Color.parseColor(initial),hsv)
        sheet(title) {content,dialog->
            val swatch=View(context)
            val hex=text("",14f,p.muted,sansMedium).apply {gravity=Gravity.CENTER}
            fun update() {
                val color=Color.HSVToColor(hsv)
                swatch.background=shape(color,22f,p.line)
                val value="#%06x".format(color and 0xffffff)
                hex.text=value;onChange(value)
            }
            content.addView(swatch,LinearLayout.LayoutParams(-1,dp(88)))
            content.addView(hex,LinearLayout.LayoutParams(-1,dp(36)))
            fun channel(name:String,max:Int,current:Int,apply:(Int)->Unit) {
                content.addView(text(name,13f,p.muted,sansMedium).apply {setPadding(dp(4),dp(6),0,0)})
                content.addView(slider(max,current,name) {apply(it);update()},LinearLayout.LayoutParams(-1,dp(48)))
            }
            channel("Оттенок",360,hsv[0].roundToInt()) {hsv[0]=it.toFloat()}
            channel("Насыщенность",100,(hsv[1]*100).roundToInt()) {hsv[1]=it/100f}
            channel("Яркость",100,(hsv[2]*100).roundToInt()) {hsv[2]=it/100f}
            content.addView(button("Готово","primary") {dialog.dismiss()},LinearLayout.LayoutParams(-1,dp(52)).apply {topMargin=dp(14)})
            update()
        }.show()
    }
}

private class DimButton(context:Context):Button(context) {
    override fun setEnabled(enabled:Boolean) {super.setEnabled(enabled);alpha=if(enabled)1f else .45f}
}

internal class MaxHeightScroll(context:Context,private val maxPx:Int):android.widget.ScrollView(context) {
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(maxPx,View.MeasureSpec.AT_MOST))
    }
}

internal fun LinearLayout.add(view:View,width:Int=-1,height:Int=-2,configure:LinearLayout.LayoutParams.()->Unit={}) {
    addView(view,LinearLayout.LayoutParams(width,height).apply(configure))
}

internal fun FrameLayout.fill(view:View) {addView(view,FrameLayout.LayoutParams(-1,-1))}
