import {useEffect, useRef} from 'react';
import * as THREE from 'three';
import {RoomEnvironment} from 'three/examples/jsm/environments/RoomEnvironment.js';
import type {FeedWork} from './types';
import {wallTexture, floorTexture, ornamentTexture, plaqueTexture, signTexture, glowTexture, coneTexture, sheenTexture} from './virtualTextures';

type Props = {
  works: FeedWork[];
  title: string;
  mode: 'desktop' | 'mobile';
  /** Индекс картины в режиме рассмотрения (null — свободная прогулка). */
  focus: number | null;
  onFocusChange: (index: number | null) => void;
  onNearest?: (index: number) => void;
  /** Включённый гироскоп: взгляд от наклона телефона. */
  gyro?: boolean;
};

const SPACING = 2.35;
const FIRST_X = 2.8;
const SIGN_X = 0.55;
const CAM_Y = 1.55;
const CENTER_Y = 1.62;
const WALL_H = 3.1;
const SIZE_CYCLE = [1, 0.86, 0.95, 1.06];

const rect = (w: number, h: number) => {
  const p = new THREE.Path();
  p.moveTo(-w / 2, -h / 2);
  p.lineTo(w / 2, -h / 2);
  p.lineTo(w / 2, h / 2);
  p.lineTo(-w / 2, h / 2);
  p.closePath();
  return p;
};

const frameGeometry = (outW: number, outH: number, holeW: number, holeH: number) => {
  const s = new THREE.Shape(rect(outW, outH).getPoints());
  s.holes.push(rect(holeW, holeH));
  return new THREE.ExtrudeGeometry(s, {
    depth: 0.05,
    bevelEnabled: true,
    bevelThickness: 0.009,
    bevelSize: 0.009,
    bevelSegments: 2,
    curveSegments: 2
  });
};

/** Вариант B: длинный зал с работами из настоящей ленты. */
export function VirtualRoom({works, title, mode, focus, onFocusChange, onNearest, gyro}: Props) {
  const host = useRef<HTMLDivElement>(null);
  const props = useRef({works, title, mode, focus, onFocusChange, onNearest, gyro});
  props.current = {works, title, mode, focus, onFocusChange, onNearest, gyro};

  useEffect(() => {
    const el = host.current;
    if (!el) return;

    // ---------- сцена ----------
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x0e0906);
    scene.fog = new THREE.Fog(0x0e0906, 4.5, 13);

    const renderer = new THREE.WebGLRenderer({antialias: true, powerPreference: 'high-performance'});
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.0;
    renderer.domElement.style.width = '100%';
    renderer.domElement.style.height = '100%';
    el.appendChild(renderer.domElement);

    try {
      const pmrem = new THREE.PMREMGenerator(renderer);
      scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.06).texture;
    } catch {
      /* без окружения просто темнее металл */
    }

    const dispose: Array<{dispose(): void}> = [];
    const track = <T extends {dispose(): void}>(x: T) => (dispose.push(x), x);

    // ---------- материалы ----------
    const wallMap = track(wallTexture());
    wallMap.repeat.set(7.5, 1);
    const floorMap = track(floorTexture());
    floorMap.repeat.set(9, 5);
    const ornMap = track(ornamentTexture());
    ornMap.repeat.set(3, 3);
    const glowMap = track(glowTexture());
    const coneMap = track(coneTexture());
    const sheenMap = track(sheenTexture());

    const goldMat = track(new THREE.MeshStandardMaterial({map: ornMap, color: 0xe2c075, metalness: 0.94, roughness: 0.3, envMapIntensity: 1.3}));
    const brassMat = track(new THREE.MeshStandardMaterial({color: 0xb08d3e, metalness: 0.9, roughness: 0.42, envMapIntensity: 1.1}));
    const matMat = track(new THREE.MeshStandardMaterial({color: 0xd9cdb0, roughness: 0.95}));
    const wallMat = track(new THREE.MeshStandardMaterial({map: wallMap, color: 0x93826b, roughness: 0.96}));
    const floorMat = track(new THREE.MeshStandardMaterial({map: floorMap, roughness: 0.5, metalness: 0.12, envMapIntensity: 0.6}));
    const ceilMat = track(new THREE.MeshStandardMaterial({color: 0xd9cdb8, roughness: 1}));
    const darkWood = track(new THREE.MeshStandardMaterial({color: 0x241408, roughness: 0.6}));
    const glowMat = track(new THREE.MeshBasicMaterial({map: glowMap, transparent: true, opacity: 0.28, blending: THREE.AdditiveBlending, depthWrite: false, color: 0xffd9a6}));
    const coneMat = track(new THREE.MeshBasicMaterial({map: coneMap, transparent: true, opacity: 0.16, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide}));

    // ---------- зал ----------
    const xs = works.map((_, i) => FIRST_X + i * SPACING);
    const lastX = xs[xs.length - 1] ?? FIRST_X;
    const wallLen = lastX + 4;
    const wallCx = wallLen / 2 - 1.5;

    const wall = track(new THREE.PlaneGeometry(wallLen, WALL_H));
    const wallMesh = new THREE.Mesh(wall, wallMat);
    wallMesh.position.set(wallCx, WALL_H / 2, 0);
    scene.add(wallMesh);

    const floorGeo = track(new THREE.PlaneGeometry(wallLen, 14));
    const floor = new THREE.Mesh(floorGeo, floorMat);
    floor.rotation.x = -Math.PI / 2;
    floor.position.set(wallCx, 0, 7);
    scene.add(floor);

    const ceil = new THREE.Mesh(floorGeo, ceilMat);
    ceil.rotation.x = Math.PI / 2;
    ceil.position.set(wallCx, WALL_H, 7);
    scene.add(ceil);

    const endGeo = track(new THREE.PlaneGeometry(14, WALL_H));
    for (const x of [wallCx - wallLen / 2, wallCx + wallLen / 2]) {
      const end = new THREE.Mesh(endGeo, wallMat);
      end.rotation.y = x < wallCx ? Math.PI / 2 : -Math.PI / 2;
      end.position.set(x, WALL_H / 2, 7);
      scene.add(end);
    }
    const back = new THREE.Mesh(endGeo, wallMat);
    back.rotation.y = Math.PI;
    back.position.set(wallCx, WALL_H / 2, 14);
    scene.add(back);

    const skirting = new THREE.Mesh(track(new THREE.BoxGeometry(wallLen, 0.14, 0.04)), darkWood);
    skirting.position.set(wallCx, 0.07, 0.02);
    scene.add(skirting);

    // ---------- надпись у входа ----------
    const signTex = track(signTexture(title));
    const sign = new THREE.Mesh(
      track(new THREE.PlaneGeometry(1.5, 0.88)),
      track(new THREE.MeshStandardMaterial({map: signTex, transparent: true, roughness: 1}))
    );
    sign.position.set(SIGN_X, CENTER_Y + 0.1, 0.006);
    scene.add(sign);

    // ---------- картины ----------
    type Built = {group: THREE.Group; mat: THREE.MeshBasicMaterial; glow: THREE.Mesh; cone: THREE.Mesh};
    const built: Built[] = [];
    const hitTargets: THREE.Object3D[] = [];
    const plaqueGeo = track(new THREE.BoxGeometry(0.36, 0.16, 0.012));

    works.forEach((work, i) => {
      const s = SIZE_CYCLE[i % SIZE_CYCLE.length];
      const artH = 1.0 * s;
      const artW = 0.75 * s;
      const matM = 0.05;
      const holeW = artW + matM * 2 - 0.01;
      const holeH = artH + matM * 2 - 0.01;
      const fw = 0.095;
      const outW = holeW + fw * 2;
      const outH = holeH + fw * 2;
      const x = xs[i];

      const group = new THREE.Group();
      group.position.set(x, CENTER_Y, 0);
      group.userData.index = i;

      const glow = new THREE.Mesh(track(new THREE.PlaneGeometry(outW * 1.9, outH * 1.5)), glowMat);
      glow.position.set(0, 0.05, 0.003);
      group.add(glow);

      const frameGeo = track(frameGeometry(outW, outH, holeW, holeH));
      const frame = new THREE.Mesh(frameGeo, goldMat);
      frame.position.z = 0.008;
      frame.userData.index = i;
      group.add(frame);
      hitTargets.push(frame);

      const matBoard = new THREE.Mesh(track(new THREE.PlaneGeometry(holeW + 0.02, holeH + 0.02)), matMat);
      matBoard.position.z = 0.042;
      matBoard.userData.index = i;
      group.add(matBoard);

      const preview = work.revision.preview;
      const fallback = document.createElement('canvas');fallback.width=8;fallback.height=8;
      const fallbackContext=fallback.getContext('2d')!;
      const fallbackShade=fallbackContext.createLinearGradient(0,0,8,8);fallbackShade.addColorStop(0,'#6357c9');fallbackShade.addColorStop(1,'#10243b');fallbackContext.fillStyle=fallbackShade;fallbackContext.fillRect(0,0,8,8);
      const artTexture=track(preview?new THREE.TextureLoader().load(preview):new THREE.CanvasTexture(fallback));
      artTexture.colorSpace=THREE.SRGBColorSpace;
      const artMat = track(new THREE.MeshBasicMaterial({map:artTexture}));
      const art = new THREE.Mesh(track(new THREE.PlaneGeometry(artW, artH)), artMat);
      art.position.z = 0.048;
      art.userData.index = i;
      group.add(art);
      hitTargets.push(art);

      const sheen = new THREE.Mesh(
        track(new THREE.PlaneGeometry(holeW + 0.02, holeH + 0.02)),
        track(new THREE.MeshBasicMaterial({map: sheenMap, transparent: true, opacity: 0.3, depthWrite: false}))
      );
      sheen.position.z = 0.055;
      group.add(sheen);

      const plaqueTex = track(plaqueTexture(work));
      const plaque = new THREE.Mesh(plaqueGeo, [brassMat, brassMat, brassMat, brassMat, track(new THREE.MeshStandardMaterial({map: plaqueTex, metalness: 0.75, roughness: 0.45})), brassMat]);
      plaque.position.set(0, -outH / 2 - 0.15, 0.012);
      group.add(plaque);

      // лампа над картиной + световой конус
      const lampY = outH / 2 + 0.14;
      const arm = new THREE.Mesh(track(new THREE.BoxGeometry(0.022, 0.022, 0.17)), brassMat);
      arm.position.set(0, lampY + 0.05, 0.085);
      group.add(arm);
      const shade = new THREE.Mesh(track(new THREE.CylinderGeometry(0.034, 0.05, 0.19, 14)), track(new THREE.MeshStandardMaterial({color: 0x6f5522, metalness: 0.9, roughness: 0.5, emissive: 0xffd9a0, emissiveIntensity: 0.28})));
      shade.rotation.z = Math.PI / 2;
      shade.position.set(0, lampY + 0.02, 0.17);
      group.add(shade);
      const bulb = new THREE.Mesh(track(new THREE.PlaneGeometry(0.22, 0.22)), glowMat);
      bulb.position.set(0, lampY, 0.15);
      group.add(bulb);

      const coneLen = outH * 1.05;
      const cone = new THREE.Mesh(track(new THREE.ConeGeometry(0.6, coneLen, 24, 1, true)), coneMat);
      cone.position.set(0, lampY - coneLen / 2 + 0.02, 0.12);
      group.add(cone);

      scene.add(group);
      built.push({group, mat: artMat, glow, cone});
    });

    // ---------- скамейки ----------
    const seatGeo = track(new THREE.BoxGeometry(1.5, 0.07, 0.42));
    const legGeo = track(new THREE.BoxGeometry(0.05, 0.42, 0.32));
    for (let i = 1; i < works.length; i += 3) {
      const bench = new THREE.Group();
      const seat = new THREE.Mesh(seatGeo, darkWood);
      seat.position.y = 0.45;
      bench.add(seat);
      for (const dx of [-0.58, 0.58]) {
        const leg = new THREE.Mesh(legGeo, brassMat);
        leg.position.set(dx, 0.21, 0);
        bench.add(leg);
      }
      bench.position.set(xs[i] - SPACING / 2, 0, 1.9);
      scene.add(bench);
    }

    // ---------- свет ----------
    scene.add(new THREE.AmbientLight(0x4a3a2a, 0.45));
    const lamps = [-2.8, 0, 2.8].map((dx) => {
      const l = new THREE.PointLight(0xffd9a8, 6, 6, 1.7);
      l.position.set(0, 2.7, 1.5);
      scene.add(l);
      return {l, dx};
    });

    // ---------- камера и ввод ----------
    const camera = new THREE.PerspectiveCamera(mode === 'mobile' ? 58 : 46, 1, 0.05, 60);
    const camZ = mode === 'mobile' ? 2.25 : 2.6;
    const focusZ = mode === 'mobile' ? 1.3 : 1.5;
    const gaze = mode === 'mobile' ? 1.15 : 1.7;
    const minX = -0.4;
    const maxX = lastX + 0.5;
    let scrollX = mode === 'mobile' ? 1.55 : 0.6;
    let vel = 0;
    let lookX = 0, lookY = 0, lookTX = 0, lookTY = 0;
    const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

    camera.position.set(scrollX, CAM_Y, camZ);

    const ray = new THREE.Raycaster();
    const ndc = new THREE.Vector2();
    let hover = -1;
    let dragging = false;
    let dragMoved = 0;
    let lastPx = 0;
    let lastT = 0;
    let tapX = 0, tapY = 0;

    const pick = (e: PointerEvent) => {
      const r = renderer.domElement.getBoundingClientRect();
      ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
      ray.setFromCamera(ndc, camera);
      const hit = ray.intersectObjects(hitTargets, false)[0];
      return hit ? (hit.object.userData.index as number) : -1;
    };

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      if (props.current.focus != null) props.current.onFocusChange(null);
      scrollX = THREE.MathUtils.clamp(scrollX + (e.deltaY + e.deltaX) * 0.0032, minX, maxX);
    };
    const onDown = (e: PointerEvent) => {
      dragging = true;
      dragMoved = 0;
      lastPx = e.clientX;
      lastT = performance.now();
      tapX = e.clientX;
      tapY = e.clientY;
      vel = 0;
    };
    const onMove = (e: PointerEvent) => {
      const r = renderer.domElement.getBoundingClientRect();
      if (dragging) {
        const dx = e.clientX - lastPx;
        const now = performance.now();
        dragMoved += Math.abs(dx);
        const meters = -dx * 0.0035;
        if (props.current.focus != null && Math.abs(dragMoved) > 6) props.current.onFocusChange(null);
        scrollX = THREE.MathUtils.clamp(scrollX + meters, minX, maxX);
        vel = meters / Math.max((now - lastT) / 1000, 0.008);
        lastPx = e.clientX;
        lastT = now;
      } else if (props.current.mode === 'desktop') {
        hover = pick(e);
        renderer.domElement.style.cursor = hover >= 0 ? 'pointer' : 'grab';
        lookTX = ((e.clientX - r.left) / r.width - 0.5) * 1.1;
        lookTY = -((e.clientY - r.top) / r.height - 0.5) * 0.55;
      }
    };
    const onUp = (e: PointerEvent) => {
      const wasDrag = dragMoved > 7;
      dragging = false;
      if (wasDrag) return;
      if (Math.abs(e.clientX - tapX) + Math.abs(e.clientY - tapY) > 9) return;
      const idx = pick(e);
      const f = props.current.focus;
      if (idx >= 0) props.current.onFocusChange(idx === f ? null : idx);
      else if (f != null) props.current.onFocusChange(null);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') props.current.onFocusChange(null);
      if (e.key === 'ArrowRight') scrollX = THREE.MathUtils.clamp(scrollX + 1.4, minX, maxX);
      if (e.key === 'ArrowLeft') scrollX = THREE.MathUtils.clamp(scrollX - 1.4, minX, maxX);
    };
    const onOrient = (e: DeviceOrientationEvent) => {
      if (!props.current.gyro) return;
      lookTX = THREE.MathUtils.clamp((e.gamma ?? 0) / 32, -1, 1) * 0.8;
      lookTY = -THREE.MathUtils.clamp(((e.beta ?? 45) - 45) / 32, -1, 1) * 0.45;
    };

    const canvas = renderer.domElement;
    canvas.addEventListener('wheel', onWheel, {passive: false});
    canvas.addEventListener('pointerdown', onDown);
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('keydown', onKey);
    window.addEventListener('deviceorientation', onOrient);
    canvas.style.cursor = 'grab';

    // ---------- размеры ----------
    const resize = () => {
      const w = Math.max(2, el.clientWidth);
      const h = Math.max(2, el.clientHeight);
      renderer.setSize(w, h, false);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(el);

    let visible = true;
    const io = new IntersectionObserver(([en]) => (visible = en.isIntersecting), {rootMargin: '80px'});
    io.observe(el);

    // ---------- цикл ----------
    let raf = 0;
    let t0 = performance.now();
    let clock = 0;
    let nearest = -1;
    const camTarget = new THREE.Vector3();
    const dims = built.map(() => 1);

    const frame = (now: number) => {
      raf = requestAnimationFrame(frame);
      const dt = Math.min((now - t0) / 1000, 0.05);
      t0 = now;
      if (!visible || document.hidden) return;
      clock += reduced ? 0 : dt;

      // инерция прокрутки
      if (!dragging && Math.abs(vel) > 0.001) {
        scrollX = THREE.MathUtils.clamp(scrollX + vel * dt, minX, maxX);
        vel *= Math.exp(-dt * 3.2);
      }

      const f = props.current.focus;
      const k = 1 - Math.exp(-dt * 3.4);
      const tx = f != null ? xs[f] + 0.55 : scrollX;
      const tz = f != null ? focusZ : camZ;
      camera.position.x += (tx - camera.position.x) * k;
      camera.position.y += (CAM_Y - camera.position.y) * k;
      camera.position.z += (tz - camera.position.z) * k;

      lookX += (lookTX - lookX) * k;
      lookY += (lookTY - lookY) * k;
      if (f != null) camTarget.set(xs[f] - 0.1, CENTER_Y - 0.02, 0);
      else camTarget.set(camera.position.x + gaze + lookX, CAM_Y - 0.06 + lookY, 0);
      camera.lookAt(camTarget);

      lamps.forEach(({l, dx}) => l.position.x = camera.position.x + dx);

      built.forEach((b, i) => {
        const target = f != null ? (i === f ? 1.06 : 0.42) : i === hover ? 1.1 : 1;
        dims[i] += (target - dims[i]) * k;
        b.mat.color.setScalar(dims[i]);
        (b.cone.material as THREE.MeshBasicMaterial).opacity = f != null && i !== f ? 0.05 : 0.16;
      });

      const near = xs.reduce((best, x, i) => (Math.abs(x - camera.position.x) < Math.abs(xs[best] - camera.position.x) ? i : best), 0);
      if (near !== nearest) {
        nearest = near;
        props.current.onNearest?.(near);
      }

      renderer.render(scene, camera);
    };
    raf = requestAnimationFrame(frame);

    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      io.disconnect();
      canvas.removeEventListener('wheel', onWheel);
      canvas.removeEventListener('pointerdown', onDown);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('deviceorientation', onOrient);
      dispose.forEach((d) => d.dispose());
      renderer.dispose();
      canvas.remove();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <div ref={host} className="gw-canvas" aria-label="3D-галерея: картины шейдеров на стене" />;
}
