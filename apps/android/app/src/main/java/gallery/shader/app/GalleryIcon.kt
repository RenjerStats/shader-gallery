package gallery.shader.app

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** One 24 px outline family, independent of the device's text/emoji font. */
internal class GalleryIcon(private val name:String,private val tint:Int):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private fun Path.polyline(vararg points:Float) {
        moveTo(points[0],points[1])
        for(i in 2 until points.size step 2)lineTo(points[i],points[i+1])
    }
    private fun Path.square(left:Float,top:Float,right:Float,bottom:Float,radius:Float)=addRoundRect(RectF(left,top,right,bottom),radius,radius,Path.Direction.CW)

    override fun draw(canvas:Canvas){
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.8f;paint.color=tint
        val path=Path()
        when(name){
            "brand"->{
                paint.style=Paint.Style.FILL
                canvas.rotate(-35f,12f,12f)
                paint.color=0xff284ced.toInt();canvas.drawRoundRect(2f,6f,13f,24f,5.5f,5.5f,paint)
                paint.color=0xffc2f64a.toInt();canvas.drawRoundRect(12f,0f,23f,18f,5.5f,5.5f,paint)
            }
            "back"->path.polyline(19f,12f,5f,12f,11f,5f,5f,12f,11f,19f)
            "close"->{path.polyline(6f,6f,18f,18f);path.polyline(18f,6f,6f,18f)}
            "check"->path.polyline(5f,12.5f,10f,17.5f,19f,7.5f)
            "chevron"->path.polyline(9f,5f,16f,12f,9f,19f)
            "expand"->path.polyline(5f,9f,12f,16f,19f,9f)
            "plus"->{path.polyline(12f,5f,12f,19f);path.polyline(5f,12f,19f,12f)}
            "bookmark","saved"->{path.polyline(6f,4f,18f,4f,18f,21f,12f,17f,6f,21f);path.close();if(name=="saved")paint.style=Paint.Style.FILL_AND_STROKE}
            "heart","heart_filled"->{
                path.moveTo(12f,20.5f);path.cubicTo(5f,15.2f,2.8f,11.8f,2.8f,8.6f);path.cubicTo(2.8f,5.9f,4.8f,4f,7.2f,4f)
                path.cubicTo(9.2f,4f,11f,5.2f,12f,7f);path.cubicTo(13f,5.2f,14.8f,4f,16.8f,4f);path.cubicTo(19.2f,4f,21.2f,5.9f,21.2f,8.6f)
                path.cubicTo(21.2f,11.8f,19f,15.2f,12f,20.5f);path.close()
                if(name=="heart_filled")paint.style=Paint.Style.FILL_AND_STROKE
            }
            "comment"->{path.square(4f,4f,20f,16f,3.5f);path.polyline(9f,16f,9f,20.5f,13.5f,16f)}
            "share"->{
                canvas.drawCircle(18f,5.5f,2.6f,paint);canvas.drawCircle(6f,12f,2.6f,paint);canvas.drawCircle(18f,18.5f,2.6f,paint)
                path.polyline(8.3f,10.7f,15.7f,6.8f);path.polyline(8.3f,13.3f,15.7f,17.2f)
            }
            "send"->{path.polyline(4f,12f,20f,4f,15f,20f,12f,13f);path.close();path.polyline(12f,13f,20f,4f)}
            "sparkle"->{path.polyline(12f,3f,14.2f,9.8f,21f,12f,14.2f,14.2f,12f,21f,9.8f,14.2f,3f,12f,9.8f,9.8f);path.close()}
            "search"->{canvas.drawCircle(10.5f,10.5f,6.5f,paint);path.polyline(15.5f,15.5f,20.5f,20.5f)}
            "pause"->{path.polyline(9f,5f,9f,19f);path.polyline(15f,5f,15f,19f)}
            "play"->{path.polyline(8f,4.5f,19.5f,12f,8f,19.5f);path.close();paint.style=Paint.Style.FILL_AND_STROKE}
            "reset"->{canvas.drawArc(5f,5f,20f,20f,-90f,290f,false,paint);path.polyline(4f,4f,4f,10f,10f,10f)}
            "more"->{paint.style=Paint.Style.FILL;listOf(5f,12f,19f).forEach {canvas.drawCircle(12f,it,1.7f,paint)}}
            "account"->{canvas.drawCircle(12f,8f,3.6f,paint);canvas.drawArc(4.5f,13.5f,19.5f,24f,195f,150f,false,paint)}
            "settings"->{canvas.drawCircle(12f,12f,7f,paint);canvas.drawCircle(12f,12f,2.5f,paint);path.polyline(12f,2f,12f,5f);path.polyline(12f,19f,12f,22f);path.polyline(2f,12f,5f,12f);path.polyline(19f,12f,22f,12f)}
            "gallery"->{path.square(4f,4f,10.5f,10.5f,2f);path.square(13.5f,4f,20f,10.5f,2f);path.square(4f,13.5f,10.5f,20f,2f);path.square(13.5f,13.5f,20f,20f,2f)}
            "list"->{path.polyline(4f,7f,20f,7f);path.polyline(4f,12f,20f,12f);path.polyline(4f,17f,20f,17f)}
            "wallpaper"->{path.square(6.5f,2.5f,17.5f,21.5f,3f);path.polyline(10.5f,18.5f,13.5f,18.5f)}
            "theme"->{canvas.drawCircle(12f,12f,8f,paint);path.moveTo(12f,4f);path.arcTo(RectF(4f,4f,20f,20f),-90f,180f);path.close();paint.style=Paint.Style.FILL_AND_STROKE}
            "logout"->{path.polyline(9f,12f,21f,12f);path.polyline(17f,8f,21f,12f,17f,16f);path.polyline(10f,4f,4f,4f,4f,20f,10f,20f)}
            "external"->{path.polyline(7f,17f,17f,7f);path.polyline(9f,7f,17f,7f,17f,15f)}
            "info"->{canvas.drawCircle(12f,12f,9f,paint);path.polyline(12f,11f,12f,16.5f);paint.style=Paint.Style.FILL_AND_STROKE;canvas.drawCircle(12f,7.8f,.7f,paint);paint.style=Paint.Style.STROKE}
            "tune"->{path.polyline(4f,7f,7f,7f);path.polyline(13f,7f,20f,7f);path.polyline(4f,17f,11f,17f);path.polyline(17f,17f,20f,17f);canvas.drawCircle(10f,7f,2.6f,paint);canvas.drawCircle(14f,17f,2.6f,paint)}
            "copy"->{path.square(9f,9f,20f,20f,2.5f);path.polyline(15f,5.5f,15f,4.5f,6.5f,4.5f,4.5f,6.5f,4.5f,15f,5.5f,15f)}
        }
        canvas.drawPath(path,paint);canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
    @Deprecated("Deprecated in Java")
    override fun getOpacity()=PixelFormat.TRANSLUCENT
}
