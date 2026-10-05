package gallery.shader.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

/** Round initial on a colour derived from the name, like a messenger avatar. */
internal class AvatarView(private val ui:Ui,name:String,private val sizeDp:Int):View(ui.context) {
    private val letter=name.trim().take(1).uppercase().ifEmpty {"?"}
    private val back=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=colors[Math.floorMod(name.hashCode(),colors.size)]}
    private val front=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textAlign=Paint.Align.CENTER;typeface=ui.sansSemi;textSize=ui.dpf(sizeDp*.44f)}
    init {importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onMeasure(widthSpec:Int,heightSpec:Int){setMeasuredDimension(ui.dp(sizeDp),ui.dp(sizeDp))}
    override fun onDraw(canvas:Canvas) {
        canvas.drawCircle(width/2f,height/2f,width/2f,back)
        canvas.drawText(letter,width/2f,height/2f-(front.ascent()+front.descent())/2f,front)
    }
    private companion object {
        val colors=intArrayOf(0xff6f5be0.toInt(),0xffd9623f.toInt(),0xff3b8fd4.toInt(),0xffa855c7.toInt(),0xff3f9a74.toInt(),0xffb8832a.toInt())
    }
}

/** Pill-shaped switcher with equal segments (tabs, quality presets). */
internal class Segmented(private val ui:Ui,labels:List<String>,selected:Int,private val onSelect:(Int)->Unit):LinearLayout(ui.context) {
    private val items=labels.map {ui.text(it,14f,ui.p.muted,ui.sansSemi).apply {gravity=Gravity.CENTER;maxLines=1;isClickable=true;isFocusable=true}}
    var selectedIndex=selected
        private set
    init {
        orientation=HORIZONTAL;setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));background=ui.shape(ui.p.fill,100f)
        items.forEachIndexed {index,item->
            item.setOnClickListener {if(index!=selectedIndex){select(index);onSelect(index)}}
            addView(item,LayoutParams(0,ui.dp(40),1f))
        }
        select(selected)
    }
    fun select(index:Int) {
        selectedIndex=index
        items.forEachIndexed {i,item->
            val active=i==index
            item.isSelected=active
            item.setTextColor(if(active)ui.p.ink else ui.p.muted)
            item.background=if(active)ui.shape(ui.p.raised,100f,ui.p.line) else ui.ripple(null,100f)
        }
    }
    fun label(index:Int,value:String){items[index].text=value}
}

/** Draws [content] over a spinner and calls [onRefresh] when [scroller] is pulled down from its top. */
internal class PullRefreshLayout(private val ui:Ui,content:View,private val scroller:View):FrameLayout(ui.context) {
    var onRefresh:(()->Unit)?=null
    var refreshing=false
        private set
    private val spinner=ProgressBar(context).apply {isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(ui.p.link);alpha=0f}
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private val threshold=ui.dp(72)
    private val hold=ui.dp(56)
    private var startX=0f
    private var startY=0f
    private var pulling=false
    private var drag=0f
    private var settle:ValueAnimator?=null
    init {
        addView(content,LayoutParams(-1,-1))
        addView(spinner,LayoutParams(ui.dp(32),ui.dp(32),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        spinner.translationY=-ui.dpf(40f)
    }
    private fun render() {
        val content=getChildAt(0)
        content.translationY=drag
        spinner.translationY=drag/2f-ui.dpf(16f)
        spinner.alpha=min(1f,drag/(threshold*.6f))
    }
    private fun animateTo(target:Float,end:()->Unit={}) {
        settle?.cancel()
        settle=ValueAnimator.ofFloat(drag,target).apply {
            duration=if(ValueAnimator.areAnimatorsEnabled())200 else 0
            addUpdateListener {drag=it.animatedValue as Float;render()}
            addListener(object:android.animation.AnimatorListenerAdapter() {override fun onAnimationEnd(animation:android.animation.Animator){end()}})
            start()
        }
    }
    fun finish() {
        if(!refreshing)return
        refreshing=false;animateTo(0f)
    }
    override fun onInterceptTouchEvent(event:MotionEvent):Boolean {
        if(refreshing)return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN->{startX=event.x;startY=event.y;pulling=false;settle?.cancel()}
            MotionEvent.ACTION_MOVE->{
                val dy=event.y-startY
                if(!pulling && dy>slop && dy>abs(event.x-startX) && !scroller.canScrollVertically(-1)){pulling=true;startY=event.y}
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->pulling=false
        }
        return pulling
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!pulling)return false
        when(event.actionMasked) {
            MotionEvent.ACTION_MOVE->{drag=((event.y-startY)*.5f).coerceIn(0f,threshold*1.5f);render()}
            MotionEvent.ACTION_UP->{
                pulling=false
                if(drag>=threshold){refreshing=true;animateTo(hold.toFloat());onRefresh?.invoke()} else animateTo(0f)
            }
            MotionEvent.ACTION_CANCEL->{pulling=false;animateTo(0f)}
        }
        return true
    }
}

/** Square box whose height follows its width (artwork thumbnails). */
internal class SquareFrame(context:Context):FrameLayout(context) {
    override fun onMeasure(widthSpec:Int,heightSpec:Int){super.onMeasure(widthSpec,widthSpec)}
}

/** Placeholder rows shown while the first page loads. */
internal class Skeleton(private val ui:Ui,rows:Int):LinearLayout(ui.context) {
    private var pulse:ObjectAnimator?=null
    init {
        orientation=VERTICAL;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        repeat(rows) {
            val row=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10))}
            row.addView(View(context).apply {background=ui.shape(ui.p.fill,16f)},LayoutParams(ui.dp(76),ui.dp(76)))
            val lines=LinearLayout(context).apply {orientation=VERTICAL}
            fun bar(widthPercent:Int,heightDp:Int)=lines.addView(View(context).apply {background=ui.shape(ui.p.fill,6f)},
                LayoutParams(ui.dp(widthPercent*2),ui.dp(heightDp)).apply {bottomMargin=ui.dp(9)})
            bar(110,16);bar(70,12);bar(95,12)
            row.addView(lines,LayoutParams(0,-2,1f).apply {leftMargin=ui.dp(14)})
            addView(row)
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if(ValueAnimator.areAnimatorsEnabled())pulse=ObjectAnimator.ofFloat(this,ALPHA,1f,.45f).apply {duration=800;repeatMode=ValueAnimator.REVERSE;repeatCount=ValueAnimator.INFINITE;start()}
    }
    override fun onDetachedFromWindow() {pulse?.cancel();pulse=null;alpha=1f;super.onDetachedFromWindow()}
}

/** Centered illustration-less empty/error state with an optional action. */
internal fun emptyState(ui:Ui,icon:String,title:String,message:String,action:String?=null,onAction:()->Unit={}):LinearLayout=LinearLayout(ui.context).apply {
    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(ui.dp(32),ui.dp(48),ui.dp(32),ui.dp(32))
    addView(FrameLayout(context).apply {
        background=ui.oval(ui.p.soft)
        addView(ui.iconView(icon,ui.p.link),FrameLayout.LayoutParams(ui.dp(34),ui.dp(34),Gravity.CENTER))
    },LinearLayout.LayoutParams(ui.dp(84),ui.dp(84)))
    addView(ui.title(title,22f).apply {gravity=Gravity.CENTER;setPadding(0,ui.dp(20),0,ui.dp(8))})
    addView(ui.body(message,15f,ui.p.muted).apply {gravity=Gravity.CENTER})
    if(action!=null)addView(ui.button(action,"primary",onAction),LinearLayout.LayoutParams(-2,ui.dp(52)).apply {topMargin=ui.dp(22)})
}

/** Slider and colour rows for a shader's parameters, shared by the artwork screen and DNA Studio. */
internal class ParameterPanel(
    private val ui:Ui,
    private val parameters:List<ShaderParameter>,
    private val values:MutableMap<String,String>,
    private val onChange:()->Unit
):LinearLayout(ui.context) {
    init {orientation=VERTICAL;clipChildren=false;build()}

    fun changed()=parameters.any {!same(it,values[it.name] ?: it.defaultValue)}
    private fun same(parameter:ShaderParameter,value:String):Boolean=
        if(parameter.type=="float")(value.toFloatOrNull()?.let {abs(it-parameter.defaultValue.toFloat())<1e-4} ?: false) else value.equals(parameter.defaultValue,true)

    fun resetAll() {
        parameters.forEach {values[it.name]=it.defaultValue}
        build();onChange()
    }

    private fun format(parameter:ShaderParameter,value:String)=value.toFloatOrNull()?.let {"%.2f".format(it)} ?: value

    private fun build() {
        removeAllViews()
        parameters.forEachIndexed {index,parameter->
            if(index>0)addView(ui.divider(),LayoutParams(-1,1).apply {topMargin=ui.dp(4);bottomMargin=ui.dp(4)})
            val value=values[parameter.name] ?: parameter.defaultValue
            val heading=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL}
            heading.addView(ui.text(parameter.label,16f,ui.p.ink,ui.sansSemi),LayoutParams(0,-2,1f))
            val output=ui.text(if(parameter.type=="float")format(parameter,value) else "",14f,ui.p.muted,ui.sansMedium)
            heading.addView(output)
            if(!same(parameter,value)) heading.addView(ui.iconButton("reset","Сбросить: ${parameter.label}",ui.p.muted) {
                values[parameter.name]=parameter.defaultValue;build();onChange()
            },LayoutParams(ui.dp(48),ui.dp(48)).apply {leftMargin=ui.dp(4);rightMargin=-ui.dp(8)})
            else heading.minimumHeight=ui.dp(48)
            addView(heading)
            if(parameter.type=="float")addFloat(parameter,value,output) else addColor(parameter,value)
        }
    }

    private fun addFloat(parameter:ShaderParameter,value:String,output:TextView) {
        val current=value.toFloatOrNull() ?: parameter.defaultValue.toFloat()
        val progress=((current-parameter.min)/(parameter.max-parameter.min)*1000).toInt().coerceIn(0,1000)
        addView(ui.slider(1000,progress,parameter.label) {
            val next=parameter.min+(parameter.max-parameter.min)*it/1000f
            values[parameter.name]=next.toString();output.text=format(parameter,next.toString());onChange()
        },LayoutParams(-1,ui.dp(48)).apply {leftMargin=-ui.dp(14);rightMargin=-ui.dp(14)})
    }

    private fun addColor(parameter:ShaderParameter,value:String) {
        val color=Color.parseColor(value)
        val hsv=FloatArray(3);Color.colorToHSV(color,hsv)
        val row=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL;clipChildren=false}
        val swatch=View(context).apply {
            contentDescription="Выбрать цвет: ${parameter.label}";isClickable=true;isFocusable=true
            background=ui.ripple(ui.shape(color,14f,ui.p.line),14f)
            setOnClickListener {ui.colorSheet(parameter.label,values[parameter.name] ?: parameter.defaultValue) {hex->values[parameter.name]=hex;onChange()
                background=ui.ripple(ui.shape(Color.parseColor(hex),14f,ui.p.line),14f)}}
        }
        val hue=SeekBar(context).apply {
            max=360;progress=hsv[0].toInt();contentDescription="Оттенок: ${parameter.label}";splitTrack=false;thumbOffset=0
            setPadding(ui.dp(14),0,ui.dp(14),0)
            progressTintList=null;progressBackgroundTintList=null;thumbTintList=null
            progressDrawable=ui.track(android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xffff7777.toInt(),0xffffff77.toInt(),0xff77ff99.toInt(),0xff77ddff.toInt(),0xff7777ff.toInt(),0xffff77dd.toInt(),0xffff7777.toInt()))
                .apply {cornerRadius=ui.dpf(5f)},10)
            thumb=ui.oval(color).apply {setStroke(ui.dp(3),ui.p.surface);setSize(ui.dp(26),ui.dp(26))}
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {
                    if(!fromUser)return
                    hsv[0]=progress.toFloat()
                    val selected=Color.HSVToColor(hsv)
                    values[parameter.name]="#%06x".format(selected and 0xffffff)
                    (thumb as android.graphics.drawable.GradientDrawable).setColor(selected)
                    swatch.background=ui.ripple(ui.shape(selected,14f,ui.p.line),14f)
                    onChange()
                }
                override fun onStartTrackingTouch(bar:SeekBar?){}
                override fun onStopTrackingTouch(bar:SeekBar?){}
            })
        }
        row.addView(hue,LayoutParams(0,ui.dp(48),1f).apply {leftMargin=-ui.dp(14)})
        row.addView(swatch,LayoutParams(ui.dp(44),ui.dp(36)).apply {leftMargin=ui.dp(4)})
        addView(row)
    }
}
