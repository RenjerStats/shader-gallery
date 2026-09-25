import type {Parameter} from './types';
export const templates:{id:string;title:string;code:string;parameters:Parameter[]}[]=[
  {id:'aurora',title:'Северное сияние',parameters:[{name:'tint',label:'Цвет сияния',type:'color',default:'#6ce9c0'},{name:'speed',label:'Скорость',type:'float',min:0,max:3,default:1}],code:`// Цвет и скорость доступны зрителю как настройки.
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float t = iTime * speed * 0.25;
  vec3 col = vec3(0.018, 0.025, 0.075);
  for (int i = 0; i < 8; i++) {
    float f = float(i);
    float wave = sin(uv.x * 7.0 + t + f * 0.32) * 0.11;
    float y = uv.y - 0.46 - wave - f * 0.023;
    float glow = exp(-abs(y) * 22.0) * (0.5 + 0.5 * sin(uv.x * 35.0 + f + t));
    col += tint * glow * 0.09;
  }
  fragColor = vec4(col, 1.0);
}`},
  {id:'rings',title:'Цветные орбиты',parameters:[{name:'accent',label:'Акцент',type:'color',default:'#a893ff'},{name:'density',label:'Частота',type:'float',min:2,max:20,default:9}],code:`void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
  float radius = length(p);
  float pulse = 0.5 + 0.5 * sin(radius * density * 6.0 - iTime);
  float ring = pow(pulse, 20.0) * exp(-radius * 1.4);
  vec3 col = vec3(0.03, 0.025, 0.07) + accent * ring;
  fragColor = vec4(col, 1.0);
}`}
];
