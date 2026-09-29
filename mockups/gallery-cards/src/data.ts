import type {ShaderKey} from './shaders';

export type Work = {
  id: string;
  title: string;
  author: string;
  year: string;
  description: string;
  tags: string[];
  views: string;
  likes: string;
  shader: ShaderKey;
  /** Акцент для свечения карточки (версия A). */
  accent: string;
  /** Вариация «живой» скорости шейдера. */
  speed?: number;
};

export const WORKS: Work[] = [
  {
    id: 'fluid-dreams',
    title: 'Fluid Dreams',
    author: 'Mira Chen',
    year: '2024',
    description: 'A colorful fluid simulation blending light, motion and organic forms in real time.',
    tags: ['Fluid', 'Generative', 'Color'],
    views: '12.4K',
    likes: '982',
    shader: 'fluid',
    accent: '#ff9d5c',
    speed: 0.9
  },
  {
    id: 'monoliths',
    title: 'Monoliths',
    author: 'Daniel Kovac',
    year: '2024',
    description: 'A raymarched scene exploring light, scale and atmospheric depth.',
    tags: ['Raymarching', 'Geometry', 'Atmosphere'],
    views: '28.1K',
    likes: '2.4K',
    shader: 'monolith',
    accent: '#ff8a4c',
    speed: 0.75
  },
  {
    id: 'particle-bloom',
    title: 'Particle Bloom',
    author: 'Aiko Tanaka',
    year: '2024',
    description: 'Procedural particles dancing through a field of forces and color.',
    tags: ['Particles', 'Generative', 'Abstract'],
    views: '18.7K',
    likes: '1.6K',
    shader: 'particles',
    accent: '#a78bfa',
    speed: 1.1
  },
  {
    id: 'procedural-horizons',
    title: 'Procedural Horizons',
    author: 'Lena Ortiz',
    year: '2023',
    description: 'An infinite landscape shaped by noise, time and a shifting sky.',
    tags: ['Landscape', 'Procedural', 'Atmosphere'],
    views: '16.2K',
    likes: '1.1K',
    shader: 'horizon',
    accent: '#9fb4ff',
    speed: 0.8
  },
  {
    id: 'fractured-light',
    title: 'Fractured Light',
    author: 'Noah Reid',
    year: '2024',
    description: 'Geometric forms, refractive materials and dynamic illumination.',
    tags: ['Geometry', 'Refraction', 'Generative'],
    views: '14.9K',
    likes: '1.2K',
    shader: 'fractured',
    accent: '#7dd3fc',
    speed: 1
  },
  {
    id: 'other-worlds',
    title: 'Other Worlds',
    author: 'Lucas Morel',
    year: '2023',
    description: 'Procedural terrain and volumetric skies for imagined places.',
    tags: ['Landscape', 'Volumetric', 'Fantasy'],
    views: '22.6K',
    likes: '1.9K',
    shader: 'worlds',
    accent: '#ff9bb0',
    speed: 0.85
  },
  {
    id: 'luminous-current',
    title: 'Luminous Current',
    author: 'Mira Chen',
    year: '2024',
    description: 'Volumetric filaments of light drift through a slow electric tide.',
    tags: ['Volumetric', 'GLSL', 'Interactive'],
    views: '9.8K',
    likes: '764',
    shader: 'particles',
    accent: '#8ab4ff',
    speed: 1.35
  },
  {
    id: 'aurora-drift',
    title: 'Aurora Drift',
    author: 'Sofia Lind',
    year: '2025',
    description: 'Soft ribbons of color fold and unfold over a quiet procedural sky.',
    tags: ['Color', 'Motion', 'Generative'],
    views: '7.3K',
    likes: '612',
    shader: 'fluid',
    accent: '#c084fc',
    speed: 1.2
  }
];