export type ShaderKey = 'fluid' | 'monolith' | 'particles' | 'horizon' | 'fractured' | 'worlds';

export const VERTEX = `#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }
`;

const HEAD = `#version 300 es
precision highp float;
uniform vec2 uRes;
uniform float uTime;
out vec4 fragColor;
`;

const COMMON = `
float hash21(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
vec2 hash22(vec2 p){ vec3 a = fract(p.xyx * vec3(123.34, 234.34, 345.65)); a += dot(a, a + 34.45); return fract(vec2(a.x * a.y, a.y * a.z)); }
float vnoise(vec2 p){
  vec2 i = floor(p), f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  float a = hash21(i), b = hash21(i + vec2(1.0, 0.0)), c = hash21(i + vec2(0.0, 1.0)), d = hash21(i + vec2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(vec2 p){
  float v = 0.0, a = 0.5;
  mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
  for(int i = 0; i < 5; i++){ v += a * vnoise(p); p = m * p; a *= 0.5; }
  return v;
}
vec3 pal(float t, vec3 a, vec3 b, vec3 c, vec3 d){ return a + b * cos(6.28318 * (c * t + d)); }
`;

/** Fluid Dreams: перетекающий цветной fbm-поток. */
const FLUID = `
void main(){
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  float t = uTime * 0.09;
  vec2 p = uv * 1.35;
  vec2 q = vec2(fbm(p + vec2(0.0, t)), fbm(p + vec2(5.2, 1.3) - t * 0.7));
  vec2 r = vec2(fbm(p + 2.2 * q + vec2(1.7, 9.2) + 0.25 * t), fbm(p + 2.2 * q + vec2(8.3, 2.8) - 0.20 * t));
  float f = fbm(p + 2.4 * r);
  vec3 col = pal(f * 0.85 + 0.06 * t + 0.42, vec3(0.40, 0.34, 0.42), vec3(0.46, 0.42, 0.50), vec3(1.0), vec3(0.00, 0.16, 0.34));
  col = mix(vec3(0.012, 0.016, 0.05), col, smoothstep(0.08, 0.78, f + 0.35 * length(r)));
  col += vec3(1.0, 0.58, 0.32) * pow(max(f - 0.46, 0.0), 2.0) * 1.8;
  col += vec3(0.34, 0.54, 1.0) * pow(max(r.x - 0.56, 0.0), 2.0) * 1.3;
  col *= 0.30 + 0.85 * smoothstep(1.30, 0.20, length(uv * vec2(0.85, 1.0)));
  fragColor = vec4(col, 1.0);
}
`;

/** Monoliths: тёмные вертикальные монолиты, луна и отражение в воде. */
const MONOLITH = `
vec3 scene(vec2 uv, float horizon, float ar){
  vec3 top = vec3(0.020, 0.035, 0.10);
  vec3 mid = vec3(0.19, 0.115, 0.21);
  vec3 warm = vec3(0.92, 0.46, 0.26);
  float sy = clamp((uv.y - horizon) / (1.0 - horizon), 0.0, 1.0);
  vec3 col = mix(warm, mid, smoothstep(0.0, 0.55, sy));
  col = mix(col, top, smoothstep(0.35, 1.0, sy));

  vec2 d = (uv - vec2(0.62, 0.74)) * vec2(ar, 1.0);
  float rr = length(d);
  float moon = smoothstep(0.150, 0.142, rr);
  float shadow = smoothstep(0.150, 0.142, length(d + vec2(0.052, -0.018)));
  float moonMask = clamp(moon - shadow * 0.94, 0.0, 1.0);
  col = mix(col, vec3(1.0, 0.72, 0.47), moonMask * 0.95);
  col += vec3(1.0, 0.56, 0.32) * smoothstep(0.62, 0.0, rr) * 0.38;

  float st = pow(vnoise(uv * 230.0), 32.0) * 1.8 * smoothstep(0.5, 1.0, sy);
  col += vec3(st);

  for(int i = 0; i < 8; i++){
    float fi = float(i);
    float cx = 0.045 + fi * 0.132 + 0.028 * sin(fi * 2.3);
    float w = 0.014 + 0.020 * fract(fi * 0.618);
    float h = 0.13 + 0.34 * fract(fi * 0.7549);
    float topY = horizon + h;
    float mx = smoothstep(w + 0.0022, w - 0.0022, abs(uv.x - cx));
    float my = smoothstep(0.0028, -0.0028, uv.y - topY) * smoothstep(-0.0022, 0.0022, uv.y - horizon);
    float m = mx * my;
    vec3 slab = vec3(0.014, 0.016, 0.038);
    float rim = smoothstep(w - 0.011, w - 0.0015, abs(uv.x - cx));
    float rim2 = smoothstep(w - 0.011, w - 0.0015, abs(uv.x - (cx - w * 1.7)));
    slab += vec3(1.0, 0.52, 0.28) * rim * 0.5 * (0.35 + 0.65 * smoothstep(horizon, topY, uv.y));
    slab += vec3(0.35, 0.30, 0.60) * rim2 * 0.16;
    slab += vec3(0.10, 0.12, 0.26) * smoothstep(horizon, topY, uv.y) * 0.22;
    col = mix(col, slab, m);
  }
  return col;
}
void main(){
  vec2 uv = gl_FragCoord.xy / uRes;
  float horizon = 0.44;
  float ar = uRes.x / uRes.y;
  vec3 col;
  if(uv.y >= horizon){
    col = scene(uv, horizon, ar);
  } else {
    float dpt = horizon - uv.y;
    float wob = (vnoise(vec2(uv.x * 26.0, uTime * 0.5 + dpt * 16.0)) - 0.5) * 0.020 * (0.22 + dpt * 1.6);
    vec2 muv = vec2(uv.x + wob, horizon + dpt * 1.12);
    col = scene(muv, horizon, ar) * (0.50 + 0.22 * smoothstep(0.35, 0.0, dpt));
    col = mix(col, vec3(0.045, 0.032, 0.075), 0.42);
    col += vec3(0.55, 0.30, 0.30) * smoothstep(0.0040, 0.0, abs(fract(uv.y * 42.0 + wob * 26.0) - 0.5) * 0.02 - 0.002) * 0.10;
    col += vec3(1.0, 0.55, 0.32) * pow(max(0.0, 1.0 - abs(uv.x - 0.62) * (2.6 + dpt * 7.0)), 2.0) * smoothstep(0.30, 0.0, dpt) * 0.35;
  }
  col *= 0.35 + 0.75 * smoothstep(1.15, 0.28, length((uv - 0.5) * vec2(1.05, 1.25)));
  fragColor = vec4(col, 1.0);
}
`;

/** Particle Bloom: спиральная пыль из частиц. */
const PARTICLES = `
void main(){
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  float t = uTime * 0.06;
  float r = length(uv);
  float a = atan(uv.y, uv.x);
  float sw = a + 2.7 * log(r + 0.05) + t * 2.4;
  float arms = 0.5 + 0.5 * sin(sw * 2.0);
  float cloud = fbm(uv * 2.6 + vec2(cos(sw), sin(sw)) * 1.25 + t * 0.3);
  float glow = exp(-r * 2.0);

  vec3 col = pal(cloud * 0.72 + r * 1.15 + 0.52, vec3(0.30, 0.18, 0.52), vec3(0.44, 0.34, 0.52), vec3(1.0), vec3(0.52, 0.62, 0.80));
  col *= glow * (0.30 + 1.05 * arms * (0.35 + cloud));
  col += vec3(0.82, 0.72, 1.0) * exp(-r * 8.0) * 1.35;
  col += vec3(0.36, 0.24, 0.72) * exp(-r * 3.4) * 0.35;

  vec2 sp = uv * 30.0;
  vec2 id = floor(sp), gv = fract(sp) - 0.5;
  float h = hash21(id);
  vec2 off = (hash22(id) - 0.5) * 0.72;
  float star = smoothstep(0.055 + 0.075 * h, 0.0, length(gv - off)) * step(0.945, h);
  star *= 0.55 + 0.45 * sin(uTime * (1.4 + 3.0 * h) + h * 24.0);
  col += star * vec3(0.85, 0.88, 1.0) * (0.7 + 1.6 * glow);

  col *= 0.30 + 0.85 * smoothstep(1.15, 0.18, length(uv));
  fragColor = vec4(col, 1.0);
}
`;

/** Procedural Horizons: серебристый каркас ландшафта. */
const HORIZON = `
void main(){
  vec2 uv = gl_FragCoord.xy / uRes;
  float horizon = 0.55;
  float ar = uRes.x / uRes.y;
  float sy = clamp((uv.y - horizon) / (1.0 - horizon), 0.0, 1.0);
  vec3 col = mix(vec3(0.020, 0.022, 0.032), vec3(0.075, 0.085, 0.125), smoothstep(0.0, 1.0, sy));

  float st = pow(vnoise(uv * 250.0), 30.0) * 1.5 * smoothstep(0.10, 0.85, sy);
  col += vec3(st);

  vec2 d = (uv - vec2(0.60, 0.82)) * vec2(ar, 1.0);
  float moon = smoothstep(0.082, 0.077, length(d));
  float shade = smoothstep(-0.075, 0.055, d.x + 0.012);
  col = mix(col, vec3(0.86, 0.90, 1.0) * (0.22 + 0.80 * shade), moon * 0.92);
  col += vec3(0.42, 0.52, 0.78) * smoothstep(0.38, 0.0, length(d)) * 0.28;

  for(int k = 0; k < 13; k++){
    float fk = float(k) / 12.0;
    float depth = pow(fk, 1.55);
    float base = mix(0.015, horizon - 0.008, depth);
    float x = (uv.x - 0.5) * mix(2.8, 0.75, depth);
    float h = fbm(vec2(x * 1.5 + fk * 11.0, uTime * 0.11 + fk * 4.0)) * mix(0.34, 0.055, depth);
    float line = base + h;
    float thick = mix(0.0046, 0.0014, depth);
    float m = smoothstep(thick, thick * 0.22, abs(uv.y - line));
    col += vec3(0.80, 0.84, 0.98) * m * mix(1.35, 0.4, depth);
    float fill = smoothstep(0.0, -0.006, uv.y - line);
    col = mix(col, col * 0.35, fill * mix(0.55, 0.15, depth));
  }

  for(int j = -9; j <= 9; j++){
    float fj = float(j);
    float tt = clamp((horizon - uv.y) / horizon, 0.0, 1.0);
    float xline = 0.5 + fj * 0.165 * pow(tt, 1.4);
    float m = smoothstep(0.0022, 0.0004, abs(uv.x - xline)) * step(uv.y, horizon);
    col += vec3(0.58, 0.64, 0.84) * m * 0.3 * (0.3 + tt);
  }

  col *= 0.35 + 0.75 * smoothstep(1.15, 0.25, length((uv - 0.5) * vec2(1.05, 1.15)));
  fragColor = vec4(col, 1.0);
}
`;

/** Fractured Light: осколки стекла с иризацией. */
const FRACTURED = `
void main(){
  vec2 uv = (gl_FragCoord.xy - 0.5 * uRes) / uRes.y;
  float t = uTime * 0.07;
  vec2 p = uv * 2.1;
  vec2 ip = floor(p), fp = fract(p);
  float f1 = 8.0, f2 = 8.0;
  vec2 cellId = vec2(0.0);
  for(int y = -1; y <= 1; y++){
    for(int x = -1; x <= 1; x++){
      vec2 g = vec2(float(x), float(y));
      vec2 o = hash22(ip + g);
      o = 0.5 + 0.42 * sin(t * 2.1 + 6.2831 * o);
      float d = length(g + o - fp);
      if(d < f1){ f2 = f1; f1 = d; cellId = ip + g; }
      else if(d < f2){ f2 = d; }
    }
  }
  float edge = f2 - f1;
  float rnd = hash21(cellId);
  vec3 col = pal(rnd * 0.62 + 0.13 * fbm(uv * 3.0 + t * 1.4) + 0.20, vec3(0.50, 0.44, 0.56), vec3(0.50, 0.45, 0.50), vec3(1.0), vec3(0.00, 0.26, 0.56));
  col *= 0.42 + 0.95 * smoothstep(0.05, 0.95, 1.0 - f1 * 0.9);
  col += vec3(1.0) * pow(smoothstep(0.17, 0.0, edge), 1.8) * 0.60;
  col += vec3(1.0, 0.92, 0.86) * pow(max(0.0, 1.0 - abs(f1 - 0.26) * 4.2), 3.0) * 0.16;
  col += vec3(1.0, 0.95, 0.9) * pow(max(0.0, 1.0 - abs(dot(normalize(uv + 0.001), normalize(vec2(-0.62, 0.78)))) * 1.45), 4.0) * 0.10;
  col *= 0.30 + 0.85 * smoothstep(1.30, 0.18, length(uv));
  fragColor = vec4(col, 1.0);
}
`;

/** Other Worlds: горы, огромная луна и отражение в воде. */
const WORLDS = `
float ridgeY(float x, float freq, float amp, float base, float seed){
  return base + amp * (fbm(vec2(x * freq + seed, seed * 1.7)) - 0.5);
}
vec3 scene(vec2 uv, float horizon, float ar){
  vec3 top = vec3(0.20, 0.15, 0.38);
  vec3 mid = vec3(0.78, 0.42, 0.56);
  vec3 warm = vec3(1.0, 0.66, 0.55);
  float sy = clamp((uv.y - horizon) / (1.0 - horizon), 0.0, 1.0);
  vec3 col = mix(warm, mid, smoothstep(0.0, 0.52, sy));
  col = mix(col, top, smoothstep(0.34, 1.0, sy));

  float cl = fbm(vec2(uv.x * 2.3 + uTime * 0.022, uv.y * 3.4 + 2.0));
  col = mix(col, vec3(1.0, 0.74, 0.70), smoothstep(0.48, 0.88, cl) * smoothstep(0.12, 0.78, sy) * 0.55);

  vec2 d = (uv - vec2(0.615, 0.775)) * vec2(ar, 1.0);
  float rr = length(d);
  float moon = smoothstep(0.185, 0.180, rr);
  float tex = fbm(d * 7.0 + 3.0);
  col = mix(col, vec3(0.99, 0.93, 0.90) * (0.80 + 0.28 * tex), moon * 0.96);
  col += vec3(1.0, 0.78, 0.72) * smoothstep(0.62, 0.0, rr) * 0.30;

  float y1 = ridgeY(uv.x, 1.55, 0.40, horizon + 0.185, 1.0);
  float y2 = ridgeY(uv.x, 2.60, 0.30, horizon + 0.115, 7.0);
  float y3 = ridgeY(uv.x, 4.30, 0.22, horizon + 0.055, 13.0);
  col = mix(col, vec3(0.46, 0.29, 0.46), smoothstep(0.0035, -0.0035, uv.y - y1));
  col = mix(col, vec3(0.95, 0.80, 0.82), smoothstep(0.028, 0.0, y1 - uv.y) * 0.22);
  col = mix(col, vec3(0.26, 0.155, 0.30), smoothstep(0.0030, -0.0030, uv.y - y2));
  col = mix(col, vec3(0.92, 0.74, 0.78), smoothstep(0.022, 0.0, y2 - uv.y) * 0.18);
  col = mix(col, vec3(0.085, 0.05, 0.135), smoothstep(0.0026, -0.0026, uv.y - y3));
  return col;
}
void main(){
  vec2 uv = gl_FragCoord.xy / uRes;
  float horizon = 0.40;
  float ar = uRes.x / uRes.y;
  vec3 col;
  if(uv.y >= horizon){
    col = scene(uv, horizon, ar);
  } else {
    float dpt = horizon - uv.y;
    float wob = (vnoise(vec2(uv.x * 15.0, uTime * 0.22 + dpt * 20.0)) - 0.5) * 0.018 * (0.18 + dpt * 2.2);
    vec3 refl = scene(vec2(uv.x + wob, horizon + dpt * 1.22), horizon, ar);
    col = mix(refl * 0.80, vec3(0.06, 0.035, 0.11), smoothstep(0.0, 0.52, dpt) * 0.60);
    col += vec3(1.0, 0.62, 0.52) * pow(max(0.0, 1.0 - abs(uv.x - 0.615) * (2.0 + dpt * 6.5)), 2.0) * smoothstep(0.30, 0.0, dpt) * 0.42;
    col += vec3(1.0, 0.72, 0.62) * smoothstep(0.0045, 0.0, abs(fract(uv.y * 34.0 + wob * 22.0) - 0.5) * 0.018 - 0.0015) * 0.10;
  }
  col *= 0.35 + 0.75 * smoothstep(1.18, 0.26, length((uv - 0.5) * vec2(1.05, 1.18)));
  fragColor = vec4(col, 1.0);
}
`;

export const SHADERS: Record<ShaderKey, string> = {
  fluid: HEAD + COMMON + FLUID,
  monolith: HEAD + COMMON + MONOLITH,
  particles: HEAD + COMMON + PARTICLES,
  horizon: HEAD + COMMON + HORIZON,
  fractured: HEAD + COMMON + FRACTURED,
  worlds: HEAD + COMMON + WORLDS
};