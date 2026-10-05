package gallery.shader.app

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

internal class GalleryPreview(context:Activity,private val onStatus:(Boolean,String)->Unit):GLSurfaceView(context),GLSurfaceView.Renderer,SensorEventListener {
    private val handler=Handler(Looper.getMainLooper())
    private val sensorManager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    @Volatile private var pending:ShaderPackage?=null
    @Volatile private var values:Map<String,String> = emptyMap()
    @Volatile private var tilt=floatArrayOf(0f,0f,0f)
    @Volatile private var mouse=floatArrayOf(0f,0f,0f,0f)
    private var program:ShaderProgram?=null
    private var active:ShaderPackage?=null
    private var running=false
    @Volatile var isPaused=!android.animation.ValueAnimator.areAnimatorsEnabled()
        private set
    private val tick=object:Runnable{override fun run(){if(running && !isPaused){requestRender();handler.postDelayed(this,33)}}}
    init{setEGLContextClientVersion(3);setRenderer(this);renderMode=RENDERMODE_WHEN_DIRTY}
    fun setShader(shader:ShaderPackage,values:Map<String,String>){this.values=values;pending=shader;requestRender()}
    fun setValues(values:Map<String,String>){this.values=values;requestRender()}
    fun snapshot(callback:(String?)->Unit){queueEvent {
        val image=try {
            val w=width;val h=height
            require(w>0 && h>0 && w.toLong()*h<=8_000_000)
            val currentProgram=program ?: error("Превью недоступно")
            currentProgram.draw(w,h,PackageStore.quality(context),values,tilt,mouse)
            val buffer=java.nio.ByteBuffer.allocateDirect(w*h*4)
            GLES30.glReadPixels(0,0,w,h,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,buffer)
            val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
            buffer.rewind();bitmap.copyPixelsFromBuffer(buffer)
            val edge=160f/maxOf(w,h);val scaled=android.graphics.Bitmap.createScaledBitmap(bitmap,maxOf(1,(w*edge).toInt()),maxOf(1,(h*edge).toInt()),true)
            val flipped=android.graphics.Bitmap.createBitmap(scaled,0,0,scaled.width,scaled.height,android.graphics.Matrix().apply {postScale(1f,-1f)},true)
            val output=java.io.ByteArrayOutputStream();flipped.compress(android.graphics.Bitmap.CompressFormat.JPEG,65,output)
            if(flipped!==scaled)flipped.recycle();if(scaled!==bitmap)scaled.recycle();bitmap.recycle()
            "data:image/jpeg;base64,"+android.util.Base64.encodeToString(output.toByteArray(),android.util.Base64.NO_WRAP)
        }catch(_:Exception){null}
        post{callback(image)}
    }}
    fun start(){onResume();running=true;handler.removeCallbacks(tick);handler.post(tick);if(sensor!=null && !isPaused)sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)}
    fun setPaused(paused:Boolean){isPaused=paused;handler.removeCallbacks(tick);sensorManager.unregisterListener(this);if(running && !paused){handler.post(tick);if(sensor!=null)sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)};requestRender()}
    fun stop(){running=false;handler.removeCallbacks(tick);sensorManager.unregisterListener(this);onPause()}
    override fun onSurfaceCreated(gl:javax.microedition.khronos.opengles.GL10?,config:javax.microedition.khronos.egl.EGLConfig?){program=null;active?.let{pending=it}}
    override fun onSurfaceChanged(gl:javax.microedition.khronos.opengles.GL10?,width:Int,height:Int){GLES30.glViewport(0,0,width,height)}
    override fun onDrawFrame(gl:javax.microedition.khronos.opengles.GL10?){
        pending?.let { shader ->
            pending=null
            try{val replacement=ShaderProgram(shader);replacement.create();program?.destroy();program=replacement;active=shader;post{onStatus(true,"Готово к установке")}}
            catch(e:Exception){post{onStatus(false,"Ошибка шейдера: ${e.message}")}}
        }
        GLES30.glClearColor(0.04f,0.03f,0.08f,1f);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if(isPaused)program?.pause() else program?.resume()
        program?.draw(width,height,PackageStore.quality(context),values,tilt,mouse)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{mouse=floatArrayOf(event.x/width,1f-event.y/height,if(event.action==MotionEvent.ACTION_DOWN||event.action==MotionEvent.ACTION_MOVE)1f else 0f,0f);requestRender();return true}
    override fun onSensorChanged(event:SensorEvent){val matrix=FloatArray(9);val angles=FloatArray(3);SensorManager.getRotationMatrixFromVector(matrix,event.values);SensorManager.getOrientation(matrix,angles);tilt=floatArrayOf(angles[2],angles[1],angles[0])}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
}
