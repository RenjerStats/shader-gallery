import {useEffect, useRef, useState, type CSSProperties} from 'react';
import {Icon} from './Icon';
import type {FeedWork} from './types';

const accents = ['#7865f5', '#f08061', '#61a9e8', '#bd76e4', '#78bc9a', '#e4aa61'];
const clamp = (value:number) => Math.max(0, Math.min(1, value));

export function GalleryCard({work,index,onOpen,gyro}:{work:FeedWork;index:number;onOpen:()=>void;gyro:boolean}) {
  const ref=useRef<HTMLElement>(null);
  const [dragging,setDragging]=useState(false);
  const target=useRef({x:0,y:0,mx:.5,my:.5});
  const current=useRef({x:0,y:0,mx:.5,my:.5});
  const touch=useRef({x:0,y:0,moved:false});
  useEffect(()=>{
    const element=ref.current;
    if(!element)return;
    const reduced=matchMedia('(prefers-reduced-motion: reduce)');
    let frame=0;
    function draw(){
      if(element && !reduced.matches){
        const a=current.current,b=target.current;
        a.x+=(b.x-a.x)*.15;a.y+=(b.y-a.y)*.15;
        a.mx+=(b.mx-a.mx)*.15;a.my+=(b.my-a.my)*.15;
        element.style.setProperty('--rx',`${a.x.toFixed(2)}deg`);
        element.style.setProperty('--ry',`${a.y.toFixed(2)}deg`);
        element.style.setProperty('--mx',String(a.mx));
        element.style.setProperty('--my',String(a.my));
      }
      frame=requestAnimationFrame(draw);
    }
    frame=requestAnimationFrame(draw);
    return()=>cancelAnimationFrame(frame);
  },[]);
  useEffect(()=>{
    if(!gyro)return;
    const orient=(event:DeviceOrientationEvent)=>{
      if(event.beta==null||event.gamma==null||dragging)return;
      target.current.x=Math.max(-12,Math.min(12,(event.beta-45)*.25));
      target.current.y=Math.max(-12,Math.min(12,event.gamma*.3));
    };
    addEventListener('deviceorientation',orient);
    return()=>removeEventListener('deviceorientation',orient);
  },[gyro,dragging]);
  function point(clientX:number,clientY:number){
    const box=ref.current?.getBoundingClientRect();if(!box)return;
    const x=clamp((clientX-box.left)/box.width),y=clamp((clientY-box.top)/box.height);
    target.current={x:(.5-y)*24,y:(x-.5)*24,mx:x,my:y};
  }
  function reset(){setDragging(false);if(!gyro)target.current={x:0,y:0,mx:.5,my:.5}}
  return <article ref={ref} className="gallery-card" style={{'--accent':accents[index%accents.length]} as CSSProperties}
    data-dragging={dragging} onPointerMove={e=>{if(e.pointerType==='mouse'||dragging)point(e.clientX,e.clientY)}}
    onPointerLeave={reset} onPointerUp={reset} onPointerCancel={reset}>
    <div className="gallery-card-surface">
    <div className="gallery-card-glow" aria-hidden="true"/>
    <div className="gallery-card-art" role="link" tabIndex={0} aria-label={`Открыть работу ${work.title}`}
      onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();onOpen()}}}
      onClick={e=>{if(e.detail>0 && !touch.current.moved)onOpen()}}
      onPointerDown={e=>{touch.current={x:e.clientX,y:e.clientY,moved:false};if(e.pointerType!=='mouse'&&e.isPrimary){setDragging(true);e.currentTarget.setPointerCapture(e.pointerId);point(e.clientX,e.clientY)}}}
      onPointerMove={e=>{if(dragging && Math.hypot(e.clientX-touch.current.x,e.clientY-touch.current.y)>8)touch.current.moved=true}}
      >
      {work.revision.preview?<img src={work.revision.preview} alt="" loading="lazy"/>:<div className="card-fallback"/>}
      <div className="gallery-card-art-fade"/><span className="gallery-card-category">{work.category}</span>
    </div>
    <div className="gallery-card-body"><div className="gallery-card-title"><h3>{work.title}</h3><span>{work.created_at?new Date(work.created_at).getFullYear():`№ ${String(index+1).padStart(2,'0')}`}</span></div>
      <p>{work.description || `Работа в категории «${work.category}»`}</p>
      <div className="gallery-card-footer"><div className="gallery-card-tags">{(work.tags?.length?work.tags:[work.category]).slice(0,3).map(tag=><span className="gallery-card-tag" key={tag}>{tag}</span>)}</div><button className="gallery-card-open" onClick={onOpen}><Icon name="arrow"/> Открыть</button></div>
      <div className="gallery-card-author">by {work.author.display_name}</div>
    </div><div className="gallery-card-glare" aria-hidden="true"/>
    </div>
  </article>;
}
