package gallery.shader.app

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** One 24 px outline family, independent of the device's text/emoji font. */
internal class GalleryIcon(private val name:String,color:Int):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color;style=Paint.Style.STROKE;strokeWidth=1.65f
        strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
    }
    override fun draw(canvas:Canvas){
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        val path=Path()
        when(name){
            "brand"->{
                paint.style=Paint.Style.FILL
                canvas.rotate(-35f,12f,12f)
                paint.color=0xff284ced.toInt();canvas.drawRoundRect(2f,6f,13f,24f,5.5f,5.5f,paint)
                paint.color=0xffc2f64a.toInt();canvas.drawRoundRect(12f,0f,23f,18f,5.5f,5.5f,paint)
            }
            "back"->{path.moveTo(19f,12f);path.lineTo(5f,12f);path.moveTo(11f,5f);path.lineTo(4f,12f);path.lineTo(11f,19f)}
            "bookmark","saved"->{path.moveTo(6f,4f);path.lineTo(18f,4f);path.lineTo(18f,21f);path.lineTo(12f,17f);path.lineTo(6f,21f);path.close();if(name=="saved")paint.style=Paint.Style.FILL_AND_STROKE}
            "search"->{canvas.drawCircle(10.5f,10.5f,6.5f,paint);path.moveTo(16f,16f);path.lineTo(21f,21f)}
            "pause"->{path.moveTo(9f,5f);path.lineTo(9f,19f);path.moveTo(15f,5f);path.lineTo(15f,19f)}
            "play"->{path.moveTo(8f,4f);path.lineTo(20f,12f);path.lineTo(8f,20f);path.close()}
            "reset"->{canvas.drawArc(5f,5f,20f,20f,-90f,290f,false,paint);path.moveTo(4f,4f);path.lineTo(4f,10f);path.lineTo(10f,10f)}
            "more"->{paint.style=Paint.Style.FILL;listOf(5f,12f,19f).forEach {canvas.drawCircle(12f,it,1.5f,paint)}}
        }
        canvas.drawPath(path,paint);canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
