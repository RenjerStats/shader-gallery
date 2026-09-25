import type {Parameter} from './types';

const vertex=`#version 300 es
in vec2 position;
void main(){gl_Position=vec4(position,0.0,1.0);}`;
const color=(hex:string)=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255);

export class ShaderRenderer {
  private gl:WebGL2RenderingContext;
  private program:WebGLProgram|null=null;
  private buffer:WebGLBuffer;
  private parameters:Parameter[]=[];
  private values:Record<string,number|string>={};
  private raf=0;
  private base=performance.now();
  private elapsed=0;
  private last=performance.now();
  private userPaused=matchMedia('(prefers-reduced-motion: reduce)').matches;
  private hidden=document.hidden;
  private mouse=[0,0,0,0];
  private disposed=false;
  private quality=0.7;
  private readonly visibility=()=>{this.hidden=document.hidden;this.last=performance.now();this.schedule();if(!this.hidden)this.draw();};
  private readonly pointer=(event:PointerEvent)=>{const r=this.canvas.getBoundingClientRect();this.mouse=[(event.clientX-r.left)/r.width*this.canvas.width,(r.bottom-event.clientY)/r.height*this.canvas.height,event.buttons?1:0,0];this.draw();};
  constructor(private canvas:HTMLCanvasElement){
    const gl=canvas.getContext('webgl2',{preserveDrawingBuffer:true,antialias:false,alpha:false});
    if(!gl)throw new Error('WebGL 2 недоступен на этом устройстве');
    this.gl=gl;
    const buffer=gl.createBuffer();if(!buffer)throw new Error('Не удалось создать WebGL-буфер');this.buffer=buffer;
    gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
    document.addEventListener('visibilitychange',this.visibility);
    canvas.addEventListener('pointermove',this.pointer);
    canvas.addEventListener('pointerdown',this.pointer);
    canvas.addEventListener('webglcontextlost',this.contextLost);
  }
  private readonly contextLost=(event:Event)=>{event.preventDefault();cancelAnimationFrame(this.raf);this.program=null;};
  private shader(type:number,source:string){const gl=this.gl,s=gl.createShader(type);if(!s)throw new Error('Не удалось создать шейдер');gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const error=gl.getShaderInfoLog(s)||'Ошибка компиляции';gl.deleteShader(s);throw new Error(error);}return s;}
  compile(code:string,parameters:Parameter[]):{ok:boolean;error?:string;line?:number}{
    const gl=this.gl;
    const declarations=parameters.map(p=>`uniform ${p.type==='color'?'vec3':'float'} ${p.name};`);
    const header=['#version 300 es','precision highp float;','uniform float iTime;','uniform vec3 iResolution;','uniform vec4 iMouse;','uniform vec3 iTilt;',...declarations,'out vec4 fragColor;'];
    const source=`${header.join('\n')}\n${code}\nvoid main(){mainImage(fragColor,gl_FragCoord.xy);}`;
    let vs:WebGLShader|null=null,fs:WebGLShader|null=null,program:WebGLProgram|null=null;
    try{
      vs=this.shader(gl.VERTEX_SHADER,vertex);fs=this.shader(gl.FRAGMENT_SHADER,source);
      program=gl.createProgram();if(!program)throw new Error('Не удалось создать программу');
      gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);
      if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program)||'Ошибка линковки');
      if(this.program)gl.deleteProgram(this.program);this.program=program;program=null;
      this.parameters=parameters;this.values=Object.fromEntries(parameters.map(p=>[p.name,p.default]));this.elapsed=0;this.base=performance.now();this.last=this.base;this.draw();this.schedule();return {ok:true};
    }catch(e){const error=e instanceof Error?e.message:String(e);const match=error.match(/ERROR:\s*\d+:(\d+)/);return {ok:false,error,line:match?Math.max(1,Number(match[1])-header.length):undefined};}
    finally{if(vs)gl.deleteShader(vs);if(fs)gl.deleteShader(fs);if(program)gl.deleteProgram(program);}
  }
  setValues(values:Record<string,number|string>){this.values={...this.values,...values};this.draw();}
  setPaused(paused:boolean){this.userPaused=paused;this.last=performance.now();this.schedule();if(paused)this.draw();}
  snapshot(){this.draw();return this.canvas.toDataURL('image/png');}
  private schedule(){cancelAnimationFrame(this.raf);if(!this.disposed&&!this.hidden&&!this.userPaused&&this.program)this.raf=requestAnimationFrame(this.tick);}
  private readonly tick=(now:number)=>{this.elapsed+=(now-this.last)/1000;this.last=now;this.draw();this.schedule();};
  private draw(){
    const gl=this.gl,p=this.program;if(!p||this.disposed||this.hidden)return;
    const rect=this.canvas.getBoundingClientRect(),scale=Math.min(devicePixelRatio||1,2)*this.quality;
    const width=Math.max(1,Math.round(rect.width*scale)),height=Math.max(1,Math.round(rect.height*scale));
    if(this.canvas.width!==width||this.canvas.height!==height){this.canvas.width=width;this.canvas.height=height;}
    gl.viewport(0,0,width,height);gl.useProgram(p);gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
    const position=gl.getAttribLocation(p,'position');gl.enableVertexAttribArray(position);gl.vertexAttribPointer(position,2,gl.FLOAT,false,0,0);
    gl.uniform1f(gl.getUniformLocation(p,'iTime'),this.elapsed);
    gl.uniform3f(gl.getUniformLocation(p,'iResolution'),width,height,1);
    gl.uniform4fv(gl.getUniformLocation(p,'iMouse'),this.mouse);
    gl.uniform3f(gl.getUniformLocation(p,'iTilt'),0,0,0);
    for(const parameter of this.parameters){const value=this.values[parameter.name],location=gl.getUniformLocation(p,parameter.name);if(parameter.type==='float')gl.uniform1f(location,Number(value));else gl.uniform3fv(location,color(String(value)));}
    gl.drawArrays(gl.TRIANGLE_STRIP,0,4);
  }
  destroy(){this.disposed=true;cancelAnimationFrame(this.raf);document.removeEventListener('visibilitychange',this.visibility);this.canvas.removeEventListener('pointermove',this.pointer);this.canvas.removeEventListener('pointerdown',this.pointer);this.canvas.removeEventListener('webglcontextlost',this.contextLost);if(this.program)this.gl.deleteProgram(this.program);this.gl.deleteBuffer(this.buffer);}
}
