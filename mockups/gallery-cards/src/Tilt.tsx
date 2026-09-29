import {useEffect, useRef, type CSSProperties, type ReactNode} from 'react';

type TiltProps = {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  /** Максимальный наклон в градусах. */
  max?: number;
  /** Включать ли гироскоп устройства (мобильные макеты). */
  gyro?: boolean;
  /** Насколько сильно «блики» смещаются при повороте. */
  glare?: boolean;
};

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

/**
 * Обёртка «картинку можно покрутить в руках»: плавный 3D-наклон от указателя
 * или гироскопа телефона с инерцией и мягким возвратом.
 * Углы отдаются в CSS-переменные --rx/--ry/--mx/--my.
 */
export function Tilt({children, className, style, max = 15, gyro = false, glare = true}: TiltProps) {
  const root = useRef<HTMLDivElement>(null);
  const state = useRef({rx: 0, ry: 0, trx: 0, try: 0, mx: 0.5, my: 0.5, tmx: 0.5, tmy: 0.5, active: false, hover: 0, thover: 0});

  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const s = state.current;
    let raf = 0;

    const tick = () => {
      raf = requestAnimationFrame(tick);
      const k = s.active ? 0.22 : 0.10;
      s.rx += (s.trx - s.rx) * k;
      s.ry += (s.try - s.ry) * k;
      s.mx += (s.tmx - s.mx) * 0.14;
      s.my += (s.tmy - s.my) * 0.14;
      s.hover += (s.thover - s.hover) * 0.12;
      el.style.setProperty('--rx', s.rx.toFixed(3) + 'deg');
      el.style.setProperty('--ry', s.ry.toFixed(3) + 'deg');
      el.style.setProperty('--mx', s.mx.toFixed(3));
      el.style.setProperty('--my', s.my.toFixed(3));
      el.style.setProperty('--lift', (1 + s.hover * 0.035).toFixed(4));
      el.style.setProperty('--shadow', (1 + s.hover * 0.45).toFixed(3));
    };
    raf = requestAnimationFrame(tick);

    const rectCenter = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      const px = clamp((e.clientX - r.left) / r.width, 0, 1);
      const py = clamp((e.clientY - r.top) / r.height, 0, 1);
      s.tmx = px;
      s.tmy = py;
      s.try = (px - 0.5) * 2 * max;
      s.trx = -(py - 0.5) * 2 * max;
    };
    const down = (e: PointerEvent) => {
      if (e.button !== undefined && e.button !== 0) return;
      s.active = true;
      s.thover = 1;
      el.setPointerCapture(e.pointerId);
      el.dataset.grabbing = 'true';
      rectCenter(e);
    };
    const move = (e: PointerEvent) => {
      if (s.active) rectCenter(e);
      else {
        const r = el.getBoundingClientRect();
        s.tmx = clamp((e.clientX - r.left) / r.width, 0, 1);
        s.tmy = clamp((e.clientY - r.top) / r.height, 0, 1);
      }
    };
    const up = (e: PointerEvent) => {
      if (!s.active) return;
      s.active = false;
      s.thover = 0;
      s.trx = 0;
      s.try = 0;
      delete el.dataset.grabbing;
      if (el.hasPointerCapture(e.pointerId)) el.releasePointerCapture(e.pointerId);
    };
    const enter = () => {
      s.thover = 1;
    };
    const leave = () => {
      if (s.active) return;
      s.thover = 0;
      s.trx = 0;
      s.try = 0;
      s.tmx = 0.5;
      s.tmy = 0.5;
    };

    el.addEventListener('pointerdown', down);
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
    el.addEventListener('pointerenter', enter);
    el.addEventListener('pointerleave', leave);

    // Гироскоп: настоящее «покручивание в руках» на телефоне.
    const orient = (e: DeviceOrientationEvent) => {
      if (s.active || e.gamma == null || e.beta == null) return;
      s.try = clamp(e.gamma / 45, -1, 1) * max;
      s.trx = clamp((e.beta - 45) / 60, -1, 1) * max;
    };
    if (gyro && 'DeviceOrientationEvent' in window) {
      window.addEventListener('deviceorientation', orient);
    }

    return () => {
      cancelAnimationFrame(raf);
      el.removeEventListener('pointerdown', down);
      el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerup', up);
      el.removeEventListener('pointercancel', up);
      el.removeEventListener('pointerenter', enter);
      el.removeEventListener('pointerleave', leave);
      if (gyro && 'DeviceOrientationEvent' in window) window.removeEventListener('deviceorientation', orient);
    };
  }, [max, gyro]);

  return (
    <div ref={root} className={className ? `tilt ${className}` : 'tilt'} style={style} data-glare={glare ? 'true' : undefined}>
      {children}
    </div>
  );
}

/** Запрос разрешения на гироскоп (нужен на iOS 13+). */
export async function requestGyro(): Promise<boolean> {
  const D = window.DeviceOrientationEvent as unknown as {requestPermission?: () => Promise<string>};
  if (!D) return false;
  if (typeof D.requestPermission === 'function') {
    try {
      return (await D.requestPermission()) === 'granted';
    } catch {
      return false;
    }
  }
  return true;
}