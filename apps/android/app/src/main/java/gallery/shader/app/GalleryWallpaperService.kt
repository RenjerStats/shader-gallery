package gallery.shader.app

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class GalleryWallpaperService:WallpaperService() {
    override fun onCreateEngine():Engine=GalleryEngine()

    private inner class GalleryEngine:Engine(),SensorEventListener {
        private val renderThread=HandlerThread("ShaderGalleryWallpaper").apply{start()}
        private val renderHandler=Handler(renderThread.looper)
        private val sensorManager=getSystemService(SENSOR_SERVICE) as SensorManager
        private val sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        private val shader=PackageStore.selected(this@GalleryWallpaperService)
        @Volatile private var values=shader?.let{PackageStore.values(this@GalleryWallpaperService,it)} ?: mutableMapOf()
        @Volatile private var tilt=floatArrayOf(0f,0f,0f)
        @Volatile private var mouse=floatArrayOf(0f,0f,0f,0f)
        @Volatile private var visible=false
        @Volatile private var surfaceReady=false
        private var currentHolder:SurfaceHolder?=null
        private var width=1
        private var height=1
        private var egl:EglSession?=null
        private var program:ShaderProgram?=null
        private val rebuild:Runnable=object:Runnable { override fun run() {
            if(!visible || !surfaceReady)return
            val holder=currentHolder ?: return
            try {
                releaseGl()
                egl=EglSession(holder)
                // A package compiled on another GPU can still fail here. Keep a safe static frame.
                program=try {shader?.let {ShaderProgram(it).also { p -> p.create() }}} catch (_:Exception) {null}
                renderHandler.removeCallbacks(frame)
                renderHandler.post(frame)
            } catch (_:Exception) {
                releaseGl()
                renderHandler.postDelayed(this,1000)
            }
        }}
        private val frame:Runnable=object:Runnable{override fun run(){
            if(!visible||!surfaceReady)return
            try{
                val session=egl ?: return
                GLES30.glViewport(0,0,width,height)
                GLES30.glClearColor(0.035f,0.03f,0.07f,1f);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                program?.draw(width,height,PackageStore.quality(this@GalleryWallpaperService),values,tilt,mouse)
                session.swap()
            }catch(_:Exception){releaseGl();renderHandler.postDelayed(rebuild,1000);return}
            renderHandler.postDelayed(this,(1000/PackageStore.fps(this@GalleryWallpaperService).coerceIn(15,60)).toLong())
        }}
        init{setTouchEventsEnabled(true)}
        override fun onSurfaceCreated(holder:SurfaceHolder){super.onSurfaceCreated(holder);currentHolder=holder;surfaceReady=true}
        override fun onSurfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){
            super.onSurfaceChanged(holder,format,width,height)
            this.width=width;this.height=height;currentHolder=holder;surfaceReady=true
            renderHandler.removeCallbacks(rebuild)
            renderHandler.removeCallbacks(frame)
            if(visible)renderHandler.post(rebuild)
        }
        override fun onSurfaceDestroyed(holder:SurfaceHolder){surfaceReady=false;currentHolder=null;renderHandler.removeCallbacks(rebuild);renderHandler.removeCallbacks(frame);renderHandler.post{releaseGl()};super.onSurfaceDestroyed(holder)}
        override fun onVisibilityChanged(visible:Boolean){
            this.visible=visible
            if(visible && sensor!=null && shader?.code?.contains("iTilt")==true)sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)
            else sensorManager.unregisterListener(this)
            renderHandler.removeCallbacks(frame)
            renderHandler.post { if(visible)program?.resume() else program?.pause() }
            if(visible && surfaceReady){if(egl==null)renderHandler.post(rebuild) else renderHandler.post(frame)}
        }
        override fun onTouchEvent(event:MotionEvent){mouse=floatArrayOf(event.x/width,1f-event.y/height,if(event.action==MotionEvent.ACTION_DOWN||event.action==MotionEvent.ACTION_MOVE)1f else 0f,0f)}
        override fun onSensorChanged(event:SensorEvent){val matrix=FloatArray(9);val angles=FloatArray(3);SensorManager.getRotationMatrixFromVector(matrix,event.values);SensorManager.getOrientation(matrix,angles);tilt=floatArrayOf(angles[2],angles[1],angles[0])}
        override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
        private fun releaseGl(){program?.destroy();program=null;egl?.destroy();egl=null}
        override fun onDestroy(){visible=false;surfaceReady=false;currentHolder=null;sensorManager.unregisterListener(this);renderHandler.removeCallbacks(frame);renderHandler.removeCallbacks(rebuild);renderHandler.post{releaseGl();renderThread.quitSafely()};super.onDestroy()}
    }
}

private class EglSession(holder:SurfaceHolder){
    private val display:EGLDisplay=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context:EGLContext
    private val surface:EGLSurface
    init{
        require(display!=EGL14.EGL_NO_DISPLAY)
        var createdContext:EGLContext=EGL14.EGL_NO_CONTEXT
        var createdSurface:EGLSurface=EGL14.EGL_NO_SURFACE
        try{
            require(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
            val configAttrs=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE)
            val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
            require(EGL14.eglChooseConfig(display,configAttrs,0,configs,0,1,count,0) && count[0]>0)
            val config=configs[0]!!
            createdContext=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
            require(createdContext!=EGL14.EGL_NO_CONTEXT)
            createdSurface=EGL14.eglCreateWindowSurface(display,config,holder,intArrayOf(EGL14.EGL_NONE),0)
            require(createdSurface!=EGL14.EGL_NO_SURFACE)
            require(EGL14.eglMakeCurrent(display,createdSurface,createdSurface,createdContext))
        }catch(e:Exception){
            if(createdSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,createdSurface)
            if(createdContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,createdContext)
            EGL14.eglTerminate(display)
            throw e
        }
        context=createdContext;surface=createdSurface
    }
    fun swap(){require(EGL14.eglSwapBuffers(display,surface)) { "EGL surface lost" }}
    fun destroy(){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)}
}
