package gallery.shader.app

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** A single-pass GLES 3 runtime. GL calls must stay on one thread/context. */
class ShaderProgram(private val shader:ShaderPackage) {
    private var program=0
    private var vertices=0
    private var framebuffer=0
    private var texture=0
    private var targetWidth=0
    private var targetHeight=0
    private var started=System.nanoTime()
    private var pausedAt:Long?=null
    private val vertexSource="""#version 300 es
        in vec2 position;
        void main(){gl_Position=vec4(position,0.0,1.0);}
    """.trimIndent()

    private fun compile(type:Int,source:String):Int {
        val id=GLES30.glCreateShader(type)
        require(id!=0) { "Не удалось создать шейдер" }
        GLES30.glShaderSource(id,source);GLES30.glCompileShader(id)
        val status=IntArray(1);GLES30.glGetShaderiv(id,GLES30.GL_COMPILE_STATUS,status,0)
        if(status[0]==0){val log=GLES30.glGetShaderInfoLog(id);GLES30.glDeleteShader(id);throw IllegalArgumentException(log)}
        return id
    }
    fun create(){
        val uniforms=shader.parameters.joinToString("\n") { "uniform ${if(it.type=="color")"vec3" else "float"} ${it.name};" }
        val fragment="""#version 300 es
            precision highp float;
            uniform float iTime;
            uniform vec3 iResolution;
            uniform vec4 iMouse;
            uniform vec3 iTilt;
            $uniforms
            out vec4 fragColor;
            ${shader.code}
            void main(){ mainImage(fragColor,gl_FragCoord.xy); }
        """.trimIndent()
        val vs=compile(GLES30.GL_VERTEX_SHADER,vertexSource)
        val fs=try{compile(GLES30.GL_FRAGMENT_SHADER,fragment)}catch(e:Exception){GLES30.glDeleteShader(vs);throw e}
        val linked=GLES30.glCreateProgram()
        GLES30.glAttachShader(linked,vs);GLES30.glAttachShader(linked,fs);GLES30.glLinkProgram(linked)
        GLES30.glDeleteShader(vs);GLES30.glDeleteShader(fs)
        val status=IntArray(1);GLES30.glGetProgramiv(linked,GLES30.GL_LINK_STATUS,status,0)
        if(status[0]==0){val log=GLES30.glGetProgramInfoLog(linked);GLES30.glDeleteProgram(linked);throw IllegalArgumentException(log)}
        program=linked
        val data=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));data.position(0)
        val ids=IntArray(1);GLES30.glGenBuffers(1,ids,0);vertices=ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vertices)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,8*4,data,GLES30.GL_STATIC_DRAW)
        started=System.nanoTime()
        pausedAt=null
    }
    fun pause(){if(pausedAt==null)pausedAt=System.nanoTime()}
    fun resume(){pausedAt?.let{started+=System.nanoTime()-it;pausedAt=null}}
    private fun target(width:Int,height:Int,quality:Float){
        val w=max(1,(width*quality).toInt());val h=max(1,(height*quality).toInt())
        if(w==targetWidth && h==targetHeight)return
        releaseTarget();targetWidth=w;targetHeight=h
        if(w==width && h==height)return
        val ids=IntArray(1)
        GLES30.glGenTextures(1,ids,0);texture=ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA8,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null)
        GLES30.glGenFramebuffers(1,ids,0);framebuffer=ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,texture,0)
        require(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)==GLES30.GL_FRAMEBUFFER_COMPLETE) { "Не удалось подготовить рендер" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0)
    }
    fun draw(width:Int,height:Int,quality:Float,values:Map<String,String>,tilt:FloatArray,mouse:FloatArray){
        if(program==0)return
        target(width,height,quality)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer)
        GLES30.glViewport(0,0,targetWidth,targetHeight)
        GLES30.glUseProgram(program)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vertices)
        val position=GLES30.glGetAttribLocation(program,"position")
        GLES30.glEnableVertexAttribArray(position)
        GLES30.glVertexAttribPointer(position,2,GLES30.GL_FLOAT,false,0,0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"iTime"),((pausedAt?:System.nanoTime())-started)/1_000_000_000f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program,"iResolution"),targetWidth.toFloat(),targetHeight.toFloat(),1f)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(program,"iMouse"),mouse[0]*targetWidth,mouse[1]*targetHeight,mouse[2],0f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program,"iTilt"),tilt[0],tilt[1],tilt[2])
        for(p in shader.parameters){
            val value=values[p.name] ?: p.defaultValue
            val location=GLES30.glGetUniformLocation(program,p.name)
            if(p.type=="float")GLES30.glUniform1f(location,value.toFloatOrNull()?.coerceIn(p.min,p.max) ?: p.defaultValue.toFloat())
            else {val hex=if(Regex("#[0-9a-fA-F]{6}").matches(value))value else p.defaultValue
                GLES30.glUniform3f(location,hex.substring(1,3).toInt(16)/255f,hex.substring(3,5).toInt(16)/255f,hex.substring(5,7).toInt(16)/255f)}
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4)
        if(framebuffer!=0){
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER,framebuffer)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,0)
            GLES30.glBlitFramebuffer(0,0,targetWidth,targetHeight,0,0,width,height,GLES30.GL_COLOR_BUFFER_BIT,GLES30.GL_LINEAR)
        }
    }
    private fun releaseTarget(){if(framebuffer!=0)GLES30.glDeleteFramebuffers(1,intArrayOf(framebuffer),0);if(texture!=0)GLES30.glDeleteTextures(1,intArrayOf(texture),0);framebuffer=0;texture=0;targetWidth=0;targetHeight=0}
    fun destroy(){releaseTarget();if(vertices!=0)GLES30.glDeleteBuffers(1,intArrayOf(vertices),0);if(program!=0)GLES30.glDeleteProgram(program);vertices=0;program=0}
}
