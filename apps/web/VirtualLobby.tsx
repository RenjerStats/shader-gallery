import {useEffect,useRef,useState} from 'react';
import * as THREE from 'three';
import {floorTexture,ornamentTexture,wallTexture} from './virtualTextures';

export type Exhibition={id:string;title:string;subtitle:string;mode:string;category?:string};

const positions=[-8,-4.8,-1.6,1.6,4.8,8];

function labelTexture(title:string,subtitle:string){
  const canvas=document.createElement('canvas');canvas.width=512;canvas.height=192;
  const context=canvas.getContext('2d')!;
  context.fillStyle='#e6d3aa';context.fillRect(0,0,512,192);
  context.strokeStyle='#9d773b';context.lineWidth=7;context.strokeRect(8,8,496,176);
  context.fillStyle='#342714';context.textAlign='center';context.textBaseline='middle';
  context.font='600 38px Georgia, serif';context.fillText(title,256,78,460);
  context.font='24px Georgia, serif';context.fillText(subtitle,256,128,460);
  const texture=new THREE.CanvasTexture(canvas);texture.colorSpace=THREE.SRGBColorSpace;return texture;
}

export function VirtualLobby({exhibitions,onEnter}:{exhibitions:Exhibition[];onEnter:(id:string)=>void}){
  const host=useRef<HTMLDivElement>(null);
  const enterRef=useRef(onEnter);enterRef.current=onEnter;
  const [unavailable,setUnavailable]=useState(false);
  useEffect(()=>{
    const element=host.current;if(!element)return;
    let renderer:THREE.WebGLRenderer;
    try{renderer=new THREE.WebGLRenderer({antialias:true,powerPreference:'high-performance'});}catch{setUnavailable(true);return;}
    renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));
    renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.25;
    renderer.domElement.style.width='100%';renderer.domElement.style.height='100%';
    element.appendChild(renderer.domElement);
    const scene=new THREE.Scene();scene.background=new THREE.Color(0x100c09);scene.fog=new THREE.Fog(0x100c09,11,28);
    const camera=new THREE.PerspectiveCamera(52,1,.1,60);camera.position.set(0,1.65,14);
    const disposed:Array<{dispose:()=>void}>=[];
    const track=<T extends {dispose:()=>void}>(item:T)=>(disposed.push(item),item);
    const wallMap=track(wallTexture());wallMap.repeat.set(5,1);
    const floorMap=track(floorTexture());floorMap.repeat.set(5,7);
    const goldMap=track(ornamentTexture());
    const wall=track(new THREE.MeshStandardMaterial({map:wallMap,color:0x9b8568,roughness:1}));
    const floorMaterial=track(new THREE.MeshStandardMaterial({map:floorMap,roughness:.62}));
    const wood=track(new THREE.MeshStandardMaterial({color:0x2c170d,roughness:.6}));
    const gold=track(new THREE.MeshStandardMaterial({map:goldMap,color:0xe2be73,metalness:.75,roughness:.38}));
    const ceiling=track(new THREE.MeshStandardMaterial({color:0xd0bfa3,roughness:1}));
    function box(w:number,h:number,d:number,x:number,y:number,z:number,material:THREE.Material){
      const mesh=new THREE.Mesh(track(new THREE.BoxGeometry(w,h,d)),material);mesh.position.set(x,y,z);scene.add(mesh);return mesh;
    }
    box(24,4.4,.35,0,2.2,-.25,wall);
    box(24,.2,17,0,4.35,8,ceiling);
    const floorMesh=new THREE.Mesh(track(new THREE.PlaneGeometry(24,24)),floorMaterial);
    floorMesh.rotation.x=-Math.PI/2;floorMesh.position.set(0,0,9);scene.add(floorMesh);
    box(.2,4.4,24,-12,2.2,9,wall);box(.2,4.4,24,12,2.2,9,wall);
    box(24,.16,.12,0,.08,.02,wood);
    scene.add(new THREE.AmbientLight(0xffecd3,1.2));
    for(const x of [-8,-4,0,4,8]){
      const lamp=new THREE.PointLight(0xffd8a1,9,7,1.7);lamp.position.set(x,3.5,2.1);scene.add(lamp);
      box(.35,.08,.24,x,3.9,.05,gold);
    }
    const ray=new THREE.Raycaster(),mouse=new THREE.Vector2();
    const hitTargets:THREE.Object3D[]=[];
    const doorHinges:THREE.Group[]=[];
    exhibitions.forEach((exhibition,index)=>{
      const x=positions[index];
      box(2.52,.12,.17,x,3.03,.06,gold);
      box(.12,2.94,.17,x-1.25,1.5,.06,gold);
      box(.12,2.94,.17,x+1.25,1.5,.06,gold);
      const hinge=new THREE.Group();hinge.position.set(x-1.1,0,.18);scene.add(hinge);
      const door=new THREE.Mesh(track(new THREE.BoxGeometry(2.18,2.78,.12)),wood);door.position.set(1.09,1.39,0);door.userData.door=index;hinge.add(door);hitTargets.push(door);doorHinges.push(hinge);
      const moulding=new THREE.Mesh(track(new THREE.BoxGeometry(1.8,2.4,.025)),gold);moulding.position.set(1.09,1.4,.08);moulding.userData.door=index;hinge.add(moulding);hitTargets.push(moulding);
      const inner=new THREE.Mesh(track(new THREE.BoxGeometry(1.68,2.28,.028)),wood);inner.position.set(1.09,1.4,.1);inner.userData.door=index;hinge.add(inner);hitTargets.push(inner);
      const knob=new THREE.Mesh(track(new THREE.SphereGeometry(.045,12,12)),gold);knob.position.set(1.88,1.35,.15);hinge.add(knob);
      const label=track(labelTexture(exhibition.title,exhibition.subtitle));
      const sign=new THREE.Mesh(track(new THREE.PlaneGeometry(2.25,.55)),track(new THREE.MeshBasicMaterial({map:label})));
      sign.position.set(x,3.48,.14);sign.userData.door=index;scene.add(sign);hitTargets.push(sign);
    });
    const resize=()=>{const width=Math.max(2,element.clientWidth),height=Math.max(2,element.clientHeight);renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();};
    resize();const resizeObserver=new ResizeObserver(resize);resizeObserver.observe(element);
    const canvas=renderer.domElement;
    let desiredX=0,pressed=false,startX=0,lastX=0,moved=false,chosen=-1,entered=false;
    let pointerX=0,pointerY=0;
    function pick(clientX:number,clientY:number){
      const rect=canvas.getBoundingClientRect();mouse.set(((clientX-rect.left)/rect.width)*2-1,-((clientY-rect.top)/rect.height)*2+1);
      ray.setFromCamera(mouse,camera);const hit=ray.intersectObjects(hitTargets,false)[0];return hit?.object.userData.door as number|undefined;
    }
    const wheel=(event:WheelEvent)=>{event.preventDefault();if(chosen<0)desiredX=THREE.MathUtils.clamp(desiredX+(event.deltaY+event.deltaX)*.006,-6,6);};
    const down=(event:PointerEvent)=>{pressed=true;startX=lastX=event.clientX;moved=false;canvas.setPointerCapture(event.pointerId);};
    const move=(event:PointerEvent)=>{
      pointerX=event.clientX;pointerY=event.clientY;
      if(pressed&&chosen<0){const delta=event.clientX-lastX;if(Math.abs(event.clientX-startX)>7)moved=true;desiredX=THREE.MathUtils.clamp(desiredX-delta*.018,-6,6);lastX=event.clientX;}
      else canvas.style.cursor=pick(event.clientX,event.clientY)!=null?'pointer':'grab';
    };
    const up=(event:PointerEvent)=>{pressed=false;if(canvas.hasPointerCapture(event.pointerId))canvas.releasePointerCapture(event.pointerId);if(moved||chosen>=0)return;const target=pick(event.clientX,event.clientY);if(target!=null){chosen=target;desiredX=positions[target];}};
    const key=(event:KeyboardEvent)=>{if(chosen>=0)return;if(event.key==='ArrowLeft')desiredX=Math.max(-6,desiredX-2.3);if(event.key==='ArrowRight')desiredX=Math.min(6,desiredX+2.3);};
    canvas.addEventListener('wheel',wheel,{passive:false});canvas.addEventListener('pointerdown',down);canvas.addEventListener('pointermove',move);canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',up);window.addEventListener('keydown',key);
    let frame=0,last=performance.now(),elapsed=0;
    const animate=(now:number)=>{
      frame=requestAnimationFrame(animate);const dt=Math.min((now-last)/1000,.05);last=now;
      if(document.hidden)return;
      elapsed+=dt;
      const factor=1-Math.exp(-dt*2.8);
      camera.position.x+=(desiredX-camera.position.x)*factor;
      const destinationZ=chosen>=0?1.15:7.8;
      camera.position.z+=(destinationZ-camera.position.z)*factor;
      if(chosen>=0){doorHinges[chosen].rotation.y+=(Math.PI*.66-doorHinges[chosen].rotation.y)*factor*1.6;if(camera.position.z<1.5&&!entered){entered=true;enterRef.current(exhibitions[chosen].id);}}
      const lookAt=new THREE.Vector3(camera.position.x+((pointerX/canvas.clientWidth)-.5)*.3,1.7,0);
      camera.lookAt(lookAt);
      renderer.render(scene,camera);
    };
    frame=requestAnimationFrame(animate);
    return()=>{cancelAnimationFrame(frame);resizeObserver.disconnect();canvas.removeEventListener('wheel',wheel);canvas.removeEventListener('pointerdown',down);canvas.removeEventListener('pointermove',move);canvas.removeEventListener('pointerup',up);canvas.removeEventListener('pointercancel',up);window.removeEventListener('keydown',key);disposed.forEach(item=>item.dispose());renderer.dispose();canvas.remove();};
  },[exhibitions]);
  return <div ref={host} className="virtual-canvas" aria-label="Холл виртуальной галереи">{unavailable&&<p>3D не поддерживается в этом браузере. Выберите выставку ниже.</p>}</div>;
}
