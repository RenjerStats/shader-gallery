export const shaders = {
chrome: `// Liquid chrome · procedural light on a soft sphere
float wave(vec3 p) {
  return sin(p.x*6.0 + sin(p.y*4.0+iTime*.3))*
         cos(p.z*5.0 - p.y*3.0 + iTime*.4);
}
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = (fragCoord*2.0-iResolution.xy)/iResolution.y;
  uv += vec2(0.0, 0.05);
  float angle = atan(uv.y, uv.x);
  float radius = 0.72 + .028*sin(angle*5.0+iTime*.4)
                     + .023*cos(angle*3.0-iTime*.3);
  float r = length(uv)/radius;
  vec3 col = vec3(.026,.023,.041);
  col += vec3(.15,.04,.23)*exp(-3.5*length(uv-vec2(.3,-.3)));
  col += vec3(.04,.06,.13)*exp(-3.0*length(uv+vec2(.6,.0)));
  if (r < 1.0) {
    vec3 n = normalize(vec3(uv/radius, sqrt(1.0-r*r)));
    float w = wave(n*1.3);
    float bands = sin(n.x*4.0 + n.y*5.0 + w*2.0+iTime*.35);
    vec3 base = .52 + .48*cos(6.28318*(vec3(.0,.22,.43)+bands*.22+n.z*.25));
    float stripe = smoothstep(.0,.12, sin(n.y*9.0+n.x*4.0+w*2.3));
    col = mix(vec3(.06,.04,.09), base, stripe*.75+.15);
    float shine = pow(max(0.0,dot(n,normalize(vec3(-.55,.8,1.0)))),16.0);
    col += vec3(.85,.92,1.0)*shine*.7;
    col += vec3(.9,.72,1.0)*pow(1.0-n.z,4.0)*.7;
    col *= .58 + .5*n.z;
  }
  col += vec3(.55,.3,.8)*.025/(abs(r-1.0)+.035)*(1.0-smoothstep(1.0,1.02,r));
  float grain = fract(sin(dot(fragCoord,vec2(12.9898,78.233)))*43758.5453);
  col += (grain-.5)*.018;
  fragColor = vec4(col,1.0);
}`,
silk: `// Electric silk · waves folded into light
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = (fragCoord*2.0-iResolution.xy)/iResolution.y;
  float t=iTime*.24;
  vec3 col=vec3(.025,.024,.05);
  for(int i=0;i<18;i++) {
    float f=float(i)/18.0;
    float y=uv.y+.37*sin(uv.x*1.7+t+f*2.0)+.2*sin(uv.x*3.0-t+f*2.4);
    float line=exp(-abs(y-(f-.5)*1.1)*85.0);
    vec3 c=mix(vec3(.32,.15,.95),vec3(.25,.85,.96),f);
    col+=c*line*.62;
    col+=c*.008/(abs(y-(f-.5)*1.1)+.07);
  }
  col*=1.0-.22*length(uv);
  fragColor=vec4(col,1.0);
}`,
aurora: `// Aurora drift · a curtain of northern light
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv=fragCoord/iResolution.xy;
  float t=iTime*.16;
  vec3 col=vec3(.016,.045,.055);
  for(int i=0;i<7;i++) {
    float f=float(i);
    float w=sin(uv.x*5.0+t+f*.1)*.14+sin(uv.x*12.0-t+f*.15)*.05;
    float y=uv.y-.5-w-f*.025;
    float glow=exp(-abs(y)*18.0)*(.55+.45*sin(uv.x*45.0+f+t));
    vec3 c=mix(vec3(.03,.48,.3),vec3(.22,.25,.68),uv.x);
    col+=c*glow*.3;
  }
  float star=fract(sin(dot(floor(uv*250.0),vec2(127.1,311.7)))*43758.54);
  col+=step(.998,star)*.5*smoothstep(.45,.85,uv.y);
  fragColor=vec4(col,1.0);
}`,
contour: `// Soft terrain · topography without an edge
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p=(fragCoord*2.0-iResolution.xy)/iResolution.y;
  float t=iTime*.15;
  p+=.25*vec2(sin(p.y*2.0+t),cos(p.x*2.0-t));
  float h=length(p*vec2(.75,1.0))+.16*sin(p.x*3.0+t)+.1*cos(p.y*5.0);
  float line=pow(.5+.5*cos(h*62.0),20.0);
  vec3 col=mix(vec3(.08,.045,.025),vec3(.42,.24,.11),exp(-h));
  col+=vec3(.78,.53,.29)*line*.65;
  fragColor=vec4(col,1.0);
}`,
portal: `// Event horizon · an impossible blue doorway
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p=(fragCoord*2.0-iResolution.xy)/iResolution.y;
  float r=length(p), a=atan(p.y,p.x), t=iTime*.3;
  float ring=.56+.025*sin(a*7.0+t)+.014*sin(a*13.0-t);
  float d=abs(r-ring);
  vec3 col=vec3(.015,.027,.065);
  col+=vec3(.08,.35,1.0)*.013/(d+.008);
  col+=vec3(.55,.2,1.0)*.005/(abs(r-.63-.025*sin(a*8.0-t))+.006);
  col*=smoothstep(.37,.54,r);
  float stars=fract(sin(dot(floor(p*170.0),vec2(127.1,311.7)))*43758.54);
  col+=step(.999,stars)*.55*smoothstep(.65,1.1,r);
  fragColor=vec4(col,1.0);
}`,
bloom: `// Coral bloom · a living interference field
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p=(fragCoord*2.0-iResolution.xy)/iResolution.y;
  float t=iTime*.18;
  float r=length(p),a=atan(p.y,p.x);
  float petals=sin(a*6.0+sin(r*5.0-t)*1.8);
  float fold=sin(r*23.0+petals*3.0-t*3.0);
  vec3 col=mix(vec3(.08,.019,.06),vec3(.88,.24,.31),pow(.5+.5*fold,3.0));
  col*=exp(-r*r*.8);
  col+=vec3(.7,.3,.16)*exp(-r*5.0)*.5;
  fragColor=vec4(col,1.0);
}`,
starter: `// Твой первый шейдер. Меняй числа — меняй настроение.
// iResolution — размер холста, iTime — время в секундах.
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float t = iTime * 0.35;
  float wave = sin(uv.x * 6.0 + t)
             + cos(uv.y * 5.0 - t);
  vec3 color = 0.5 + 0.5 * cos(
    wave + uv.xyx + vec3(0.0, 2.0, 4.0)
  );
  fragColor = vec4(color, 1.0);
}`
};

export const seedPosts = [
 {id:'liquid-chrome',title:'Liquid Chrome',author:'nora.studio',name:'Nora',avatar:'N',color:'#c8b3e9',category:'Жидкости',tags:['chrome','iridescent','organic'],likes:248,saves:64,shader:'chrome',description:'Свет, который можно почти потрогать. Исследование жидкого металла и переливающихся отражений — всё рождается из нескольких волн.',comments:[{name:'kai.wav',text:'Этот перламутр хочется рассматривать бесконечно.'}],featured:true},
 {id:'electric-silk',title:'Electric Silk',author:'kai.wav',name:'Kai',avatar:'K',color:'#86b9d9',category:'Абстракция',tags:['waves','generative','flow'],likes:186,saves:41,shader:'silk',description:'Тонкие линии собираются в ткань. Небольшой эксперимент с интерференцией, глубиной и медленным движением.',comments:[]},
 {id:'aurora-drift',title:'Aurora Drift',author:'luma',name:'Luma',avatar:'L',color:'#8dbea7',category:'Космос',tags:['aurora','ambient','light'],likes:324,saves:97,shader:'aurora',description:'Карманное северное сияние. Оставь на экране, сделай паузу и немного побудь здесь.',comments:[{name:'nora.studio',text:'Удивительно спокойная работа.'}]},
 {id:'soft-terrain',title:'Soft Terrain',author:'form.field',name:'Form',avatar:'F',color:'#d4b597',category:'Геометрия',tags:['contour','terrain','lines'],likes:129,saves:32,shader:'contour',description:'Воображаемый рельеф, у которого нет границ. Контурные линии медленно меняют форму, как дюны на ветру.',comments:[]},
 {id:'event-horizon',title:'Event Horizon',author:'void.exe',name:'Void',avatar:'V',color:'#96a8e2',category:'Космос',tags:['space','portal','glow'],likes:412,saves:118,shader:'portal',description:'Небольшое окно в неизвестность. Световые кольца дрейфуют вокруг тёмного центра.',comments:[]},
 {id:'coral-bloom',title:'Coral Bloom',author:'mika',name:'Mika',avatar:'M',color:'#da9ca9',category:'Абстракция',tags:['organic','bloom','color'],likes:207,saves:53,shader:'bloom',description:'Цветок из математики: радиальные волны, тёплый цвет и немного случайной красоты.',comments:[]}
];

const vertex = 'attribute vec2 aPosition; void main(){ gl_Position=vec4(aPosition,0.0,1.0); }';
const header = 'precision highp float;\nuniform vec3 iResolution;\nuniform float iTime;\nuniform vec4 iMouse;\n';
const footer = '\nvoid main(){mainImage(gl_FragColor,gl_FragCoord.xy); }';
export class ShaderView {
  constructor(canvas, code, options={}) {
    this.canvas=canvas; this.options=options; this.visible=true; this.paused=options.paused||false; this.time=0; this.speed=1; this.last=0; this.mouse=[0,0,0,0];
    this.gl=canvas.getContext('webgl',{alpha:false,antialias:false,preserveDrawingBuffer:true,powerPreference:'low-power'});
    if(!this.gl) { this.error='WebGL недоступен. Открой сайт в браузере с аппаратным ускорением.'; canvas.dataset.error=this.error; return; }
    const gl=this.gl;
    this.buffer=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
    gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);
    this.compile(code);
    this.observer=new IntersectionObserver(entries=>{this.visible=entries[0].isIntersecting;}); this.observer.observe(canvas);
    this.pointer=e=>{const r=canvas.getBoundingClientRect();this.mouse=[(e.clientX-r.left)*canvas.width/r.width,(r.bottom-e.clientY)*canvas.height/r.height,e.buttons?1:0,0];};
    canvas.addEventListener('pointermove',this.pointer);
    this.tick=this.tick.bind(this);this.frame=requestAnimationFrame(this.tick);
  }
  compile(code) {
    if(!this.gl)return {ok:false,error:this.error};
    const gl=this.gl;
    const vs=gl.createShader(gl.VERTEX_SHADER), fs=gl.createShader(gl.FRAGMENT_SHADER);
    gl.shaderSource(vs,vertex);gl.compileShader(vs);
    gl.shaderSource(fs,header+code+footer);gl.compileShader(fs);
    if(!gl.getShaderParameter(fs,gl.COMPILE_STATUS)) {
      const error=gl.getShaderInfoLog(fs)||'Не удалось скомпилировать шейдер.';
      gl.deleteShader(vs);gl.deleteShader(fs);this.error=error;
      return {ok:false,error};
    }
    const program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);
    gl.deleteShader(vs);gl.deleteShader(fs);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS)) {const error=gl.getProgramInfoLog(program);gl.deleteProgram(program);this.error=error;return {ok:false,error};}
    if(this.program)gl.deleteProgram(this.program);
    this.program=program;gl.useProgram(program);
    const a=gl.getAttribLocation(program,'aPosition');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,2,gl.FLOAT,false,0,0);
    this.uniforms={res:gl.getUniformLocation(program,'iResolution'),time:gl.getUniformLocation(program,'iTime'),mouse:gl.getUniformLocation(program,'iMouse')};
    this.error=null;this.code=code;this.draw();return {ok:true};
  }
  draw() {
    if(!this.gl||!this.program)return;
    const gl=this.gl, c=this.canvas;
    const dpr=Math.min(devicePixelRatio||1,this.options.dpr||1.35);
    const width=Math.max(1,Math.round(c.clientWidth*dpr)),height=Math.max(1,Math.round(c.clientHeight*dpr));
    if(c.width!==width||c.height!==height){c.width=width;c.height=height;}
    gl.viewport(0,0,width,height);gl.useProgram(this.program);
    gl.uniform3f(this.uniforms.res,width,height,1);gl.uniform1f(this.uniforms.time,this.time);gl.uniform4fv(this.uniforms.mouse,this.mouse);
    gl.drawArrays(gl.TRIANGLES,0,6);
  }
  tick(ts) {
    const delta=this.last?Math.min((ts-this.last)/1000,.1):0;
    if(ts-this.last>1000/(this.options.fps||30)){
      this.last=ts;
      if(this.visible&&!document.hidden){if(!this.paused)this.time+=delta*this.speed;this.draw();}
    }
    this.frame=requestAnimationFrame(this.tick);
  }
  destroy() {cancelAnimationFrame(this.frame);this.observer?.disconnect();this.canvas.removeEventListener('pointermove',this.pointer);if(this.gl){if(this.program)this.gl.deleteProgram(this.program);this.gl.deleteBuffer(this.buffer);this.gl.getExtension('WEBGL_lose_context')?.loseContext();}}
}
