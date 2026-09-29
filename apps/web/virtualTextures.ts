import * as THREE from 'three';
import type {FeedWork} from './types';

const cv = (w: number, h: number) => {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return [c, c.getContext('2d')!] as const;
};

const finish = (c: HTMLCanvasElement, srgb = true) => {
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  return t;
};

/** Тёплые обои с тонкими вертикальными полосами — как в залах галереи. */
export function wallTexture() {
  const [c, g] = cv(512, 512);
  g.fillStyle = '#6d5a44';
  g.fillRect(0, 0, 512, 512);
  for (let x = 0; x < 512; x += 26) {
    g.fillStyle = x % 52 === 0 ? 'rgba(255,238,214,0.028)' : 'rgba(30,20,10,0.035)';
    g.fillRect(x, 0, 13, 512);
  }
  for (let i = 0; i < 5200; i++) {
    g.fillStyle = `rgba(${Math.random() > 0.5 ? '255,240,216' : '30,20,10'},${Math.random() * 0.05})`;
    g.fillRect(Math.random() * 512, Math.random() * 512, 1.5, 1.5);
  }
  return finish(c);
}

/** Тёмный паркет: доски идут вдоль стены, с прожилками и швами. */
export function floorTexture() {
  const [c, g] = cv(512, 512);
  g.fillStyle = '#3a2417';
  g.fillRect(0, 0, 512, 512);
  for (let y = 0; y < 512; y += 64) {
    const l = 8 + Math.random() * 10;
    g.fillStyle = `hsl(${22 + Math.random() * 8}, ${34 + Math.random() * 10}%, ${l}%)`;
    g.fillRect(0, y + 1, 512, 62);
    g.strokeStyle = 'rgba(12,6,2,0.55)';
    g.strokeRect(-1, y + 0.5, 514, 63);
    for (let s = 0; s < 26; s++) {
      g.strokeStyle = `rgba(${Math.random() > 0.5 ? '255,214,160' : '10,5,2'},${0.04 + Math.random() * 0.05})`;
      g.beginPath();
      const yy = y + 4 + Math.random() * 56;
      g.moveTo(0, yy);
      g.bezierCurveTo(170, yy + Math.random() * 5 - 2.5, 340, yy + Math.random() * 5 - 2.5, 512, yy + Math.random() * 4 - 2);
      g.stroke();
    }
  }
  return finish(c);
}

/** Плетёное золото для багета (map + roughnessMap). */
export function ornamentTexture() {
  const [c, g] = cv(256, 256);
  g.fillStyle = '#c19a4b';
  g.fillRect(0, 0, 256, 256);
  g.save();
  g.rotate(-Math.PI / 4);
  for (let i = -300; i < 300; i += 11) {
    const grad = g.createLinearGradient(i, 0, i + 11, 0);
    grad.addColorStop(0, 'rgba(84,58,16,0.5)');
    grad.addColorStop(0.35, 'rgba(255,236,186,0.42)');
    grad.addColorStop(0.7, 'rgba(202,164,84,0.25)');
    grad.addColorStop(1, 'rgba(84,58,16,0.5)');
    g.fillStyle = grad;
    g.fillRect(i, -300, 11, 600);
  }
  g.restore();
  for (let i = 0; i < 2600; i++) {
    g.fillStyle = `rgba(255,240,200,${Math.random() * 0.08})`;
    g.fillRect(Math.random() * 256, Math.random() * 256, 1.4, 1.4);
  }
  return finish(c);
}

/** Латунная табличка с гравировкой. */
export function plaqueTexture(work: FeedWork) {
  const [c, g] = cv(512, 224);
  const grad = g.createLinearGradient(0, 0, 0, 224);
  grad.addColorStop(0, '#e6c579');
  grad.addColorStop(0.45, '#c39c4c');
  grad.addColorStop(1, '#8e6c2c');
  g.fillStyle = grad;
  g.fillRect(0, 0, 512, 224);
  g.strokeStyle = 'rgba(64,42,8,0.7)';
  g.lineWidth = 6;
  g.strokeRect(12, 12, 488, 200);
  g.strokeStyle = 'rgba(255,238,190,0.5)';
  g.lineWidth = 2;
  g.strokeRect(22, 22, 468, 180);

  g.fillStyle = '#3d2a08';
  g.textBaseline = 'top';
  g.font = '600 54px Georgia, serif';
  g.fillText(work.title, 44, 44);
  g.textAlign = 'right';
  g.font = '400 34px Georgia, serif';
  g.fillText(work.created_at?.slice(0, 4) || '', 468, 52);
  g.textAlign = 'left';
  g.font = 'italic 32px Georgia, serif';
  g.fillText(`by ${work.author.display_name}`, 44, 112);
  g.font = '500 22px Georgia, serif';
  g.fillStyle = 'rgba(61,42,8,0.8)';
  g.fillText((work.tags || []).join(' • '), 44, 162);
  return finish(c);
}

/** Надпись на стене у входа — музейный текст зала. */
export function signTexture(title = 'Shader Art') {
  const [c, g] = cv(768, 448);
  g.clearRect(0, 0, 768, 448);
  g.textBaseline = 'top';
  g.fillStyle = 'rgba(52,36,18,0.95)';
  g.font = '500 30px Georgia, serif';
  g.fillText('A CURATED GALLERY OF', 36, 42);
  g.font = '700 108px Georgia, serif';
  g.fillText(title, 32, 86);
  g.font = 'italic 46px Georgia, serif';
  g.fillText('Code Beautiful Worlds.', 36, 214);
  g.fillStyle = 'rgba(52,36,18,0.8)';
  g.font = '400 27px Georgia, serif';
  const line = 'Real-time worlds, rendered as fine art. Восемь живых';
  g.fillText(line, 36, 292);
  g.fillText('работ — прокрутите зал, чтобы пройти вдоль стены.', 36, 330);
  g.strokeStyle = 'rgba(52,36,18,0.55)';
  g.lineWidth = 3;
  g.beginPath();
  g.moveTo(36, 268);
  g.lineTo(300, 268);
  g.stroke();
  return finish(c);
}

/** Мягкое радиальное свечение (пятно света на стене, ореол лампы). */
export function glowTexture() {
  const [c, g] = cv(256, 256);
  const grad = g.createRadialGradient(128, 128, 8, 128, 128, 126);
  grad.addColorStop(0, 'rgba(255,238,206,0.9)');
  grad.addColorStop(0.45, 'rgba(255,226,180,0.35)');
  grad.addColorStop(1, 'rgba(255,220,170,0)');
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 256);
  return finish(c);
}

/** Градиент светового конуса: ярко у лампы, гаснет вниз. */
export function coneTexture() {
  const [c, g] = cv(64, 256);
  const grad = g.createLinearGradient(0, 0, 0, 256);
  grad.addColorStop(0, 'rgba(255,236,198,0.5)');
  grad.addColorStop(0.55, 'rgba(255,228,182,0.16)');
  grad.addColorStop(1, 'rgba(255,224,176,0)');
  g.fillStyle = grad;
  g.fillRect(0, 0, 64, 256);
  const soft = g.createLinearGradient(0, 0, 64, 0);
  soft.addColorStop(0, 'rgba(0,0,0,1)');
  soft.addColorStop(0.2, 'rgba(0,0,0,0)');
  soft.addColorStop(0.8, 'rgba(0,0,0,0)');
  soft.addColorStop(1, 'rgba(0,0,0,1)');
  g.globalCompositeOperation = 'destination-out';
  g.fillStyle = soft;
  g.fillRect(0, 0, 64, 256);
  return finish(c);
}

/** Блик «стекла» на картине диагональной полосой. */
export function sheenTexture() {
  const [c, g] = cv(256, 256);
  g.clearRect(0, 0, 256, 256);
  const grad = g.createLinearGradient(0, 256, 256, 0);
  grad.addColorStop(0.3, 'rgba(255,255,255,0)');
  grad.addColorStop(0.46, 'rgba(255,255,255,0.10)');
  grad.addColorStop(0.52, 'rgba(255,255,255,0.16)');
  grad.addColorStop(0.6, 'rgba(255,255,255,0.03)');
  grad.addColorStop(0.75, 'rgba(255,255,255,0)');
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 256);
  return finish(c);
}
