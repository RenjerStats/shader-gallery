import {useEffect, useRef} from 'react';
import {SHADERS, VERTEX, type ShaderKey} from './shaders';

type Props = {shader: ShaderKey; className?: string; timeScale?: number; style?: React.CSSProperties};

const compile = (gl: WebGL2RenderingContext, type: number, source: string) => {
  const s = gl.createShader(type)!;
  gl.shaderSource(s, source);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
    const info = gl.getShaderInfoLog(s);
    gl.deleteShader(s);
    throw new Error(info || 'Ошибка компиляции шейдера');
  }
  return s;
};

/** Живое GLSL-превью работы: один fragment pass, WebGL 2, пауза вне экрана. */
export function ShaderCanvas({shader, className, style, timeScale = 1}: Props) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const scale = useRef(timeScale);
  scale.current = timeScale;

  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    let gl: WebGL2RenderingContext | null = null;
    let program: WebGLProgram | null = null;
    let frame = 0;
    let raf = 0;
    let start = performance.now();
    let time = 0;
    let visible = true;

    try {
      gl = el.getContext('webgl2', {antialias: false, alpha: false, powerPreference: 'low-power'});
      if (!gl) throw new Error('WebGL 2 недоступен');
      const vs = compile(gl, gl.VERTEX_SHADER, VERTEX);
      const fs = compile(gl, gl.FRAGMENT_SHADER, SHADERS[shader]);
      program = gl.createProgram()!;
      gl.attachShader(program, vs);
      gl.attachShader(program, fs);
      gl.linkProgram(program);
      gl.deleteShader(vs);
      gl.deleteShader(fs);
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program) || 'Ошибка линковки');
      gl.useProgram(program);
      const buffer = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    } catch (e) {
      console.warn('[ShaderCanvas]', shader, e);
      el.dataset.fallback = 'true';
      return;
    }

    const uRes = gl.getUniformLocation(program, 'uRes');
    const uTime = gl.getUniformLocation(program, 'uTime');
    const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

    const draw = (now: number) => {
      raf = requestAnimationFrame(draw);
      if (!gl || !program || gl.isContextLost()) return;
      if (!visible || document.hidden) {
        start = now - time * 1000;
        return;
      }
      time = reduced ? 8 : ((now - start) / 1000) * scale.current;
      const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      const w = Math.max(2, Math.round(el.clientWidth * dpr));
      const h = Math.max(2, Math.round(el.clientHeight * dpr));
      if (el.width !== w || el.height !== h) {
        el.width = w;
        el.height = h;
        gl.viewport(0, 0, w, h);
      }
      gl.uniform2f(uRes, w, h);
      gl.uniform1f(uTime, time);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      frame++;
    };
    raf = requestAnimationFrame(draw);

    const io = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
    }, {rootMargin: '120px'});
    io.observe(el);

    return () => {
      cancelAnimationFrame(raf);
      io.disconnect();
    };
  }, [shader]);

  return <canvas ref={canvas} className={className} style={style} aria-label="Живое изображение работы" />;
}