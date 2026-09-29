import * as THREE from 'three';
import {SHADERS, type ShaderKey} from '../shaders';

/**
 * Картины в 3D-зале — те же живые GLSL-сцены, что и в ShaderCanvas.
 * Шейдеры написаны под WebGL 2 (gl_FragCoord), здесь их фрагменты
 * переносятся в плоскость картины: fragCoord = vUv * uRes.
 * RawShaderMaterial + GLSL3: полный контроль над исходником.
 */
const VERT = `
in vec3 position;
in vec2 uv;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
out vec2 vUv;
void main(){
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const adapt = (src: string) =>
  src
    .replace('#version 300 es', '')
    .replace('out vec4 fragColor;', 'in vec2 vUv;\nout vec4 fragColor;')
    .replace('uniform float uTime;', 'uniform float uTime;\nuniform float uDim;')
    .replace(/gl_FragCoord\.xy/g, '(vUv * uRes)')
    .replace(/fragColor = vec4\(col, 1\.0\);/g, 'fragColor = vec4(col * uDim, 1.0);');

export function makeArtMaterial(shader: ShaderKey, w: number, h: number) {
  return new THREE.RawShaderMaterial({
    glslVersion: THREE.GLSL3,
    vertexShader: VERT,
    fragmentShader: adapt(SHADERS[shader]),
    uniforms: {
      uRes: {value: new THREE.Vector2(w, h)},
      uTime: {value: 0},
      uDim: {value: 1}
    }
  });
}
