import {useEffect,useRef,useState} from 'react';
import {Icon} from './Icon';
import {ShaderRenderer} from './runtime';
import type {Parameter} from './types';
const friendly=(e:unknown)=>e instanceof Error?e.message:'Не удалось показать работу';
const fingerprint=(v:unknown)=>JSON.stringify(v);
export function CanvasPreview({code,parameters,values,compileKey,onResult}:{code:string;parameters:Parameter[];values:Record<string,number|string>;compileKey:string|number;onResult?:(result:{ok:boolean;error?:string;line?:number},renderer:ShaderRenderer)=>void}){
  const canvas=useRef<HTMLCanvasElement>(null),frame=useRef<HTMLDivElement>(null),renderer=useRef<ShaderRenderer|null>(null),callback=useRef(onResult);callback.current=onResult;
  const input=useRef(''),lastGood=useRef('');input.current=fingerprint({code,parameters});
  const [error,setError]=useState('');const [paused,setPaused]=useState(matchMedia('(prefers-reduced-motion: reduce)').matches),[fullscreen,setFullscreen]=useState(false),[expanded,setExpanded]=useState(false);
  useEffect(()=>{try{renderer.current=new ShaderRenderer(canvas.current!,result=>{const checked=result.ok&&input.current!==lastGood.current?{ok:false,error:'Превью восстановлено. Проверьте изменённый код снова.'}:result;setError(checked.ok?'':checked.error||'Превью недоступно');if(renderer.current)callback.current?.(checked,renderer.current)});return()=>{renderer.current?.destroy();renderer.current=null}}catch(e){setError(friendly(e))}},[]);
  useEffect(()=>{if(!renderer.current)return;const result=renderer.current.compile(code,parameters);if(result.ok)lastGood.current=fingerprint({code,parameters});setError(result.ok?'':result.error||'Ошибка компиляции');callback.current?.(result,renderer.current)},[compileKey]);
  useEffect(()=>{renderer.current?.setValues(values)},[fingerprint(values)]);
  useEffect(()=>{renderer.current?.setPaused(paused)},[paused]);
  useEffect(()=>{const sync=()=>setFullscreen(document.fullscreenElement===frame.current);document.addEventListener('fullscreenchange',sync);return()=>document.removeEventListener('fullscreenchange',sync)},[]);
  useEffect(()=>{if(!expanded)return;const oldOverflow=document.body.style.overflow;document.body.style.overflow='hidden';const close=(event:KeyboardEvent)=>{if(event.key==='Escape')setExpanded(false)};document.addEventListener('keydown',close);return()=>{document.body.style.overflow=oldOverflow;document.removeEventListener('keydown',close)}},[expanded]);
  async function download(){
    if(!canvas.current)return;
    const blob=await new Promise<Blob|null>(resolve=>canvas.current?.toBlob(resolve,'image/png'));
    if(!blob){setError('Не удалось сохранить скриншот');return}
    const url=URL.createObjectURL(blob),link=document.createElement('a');
    link.href=url;link.download=`shader-gallery-${new Date().toISOString().slice(0,10)}.png`;link.click();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
  async function toggleFullscreen(){if(fullscreen){await document.exitFullscreen();return}if(expanded){setExpanded(false);return}if(document.fullscreenEnabled&&frame.current?.requestFullscreen){try{await frame.current.requestFullscreen();return}catch{}}setExpanded(true)}
  return <div className={`preview${expanded?' is-expanded':''}`} ref={frame}><canvas ref={canvas} aria-label="Живое изображение работы"/>{error&&<div className="preview-error" role="alert">{error}</div>}<div className="preview-tools"><button type="button" aria-label={paused?'Включить движение':'Приостановить движение'} title={paused?'Включить движение':'Пауза'} aria-pressed={paused} onClick={()=>setPaused(!paused)}><Icon name={paused?'play':'pause'}/></button><button type="button" aria-label="Сохранить скриншот" title="Сохранить скриншот" onClick={download}><Icon name="download"/></button><button type="button" aria-label={fullscreen||expanded?'Выйти из полноэкранного режима':'Открыть во весь экран'} title={fullscreen||expanded?'Выйти из полноэкранного режима':'Во весь экран'} onClick={toggleFullscreen}><Icon name={fullscreen||expanded?'shrink':'expand'}/></button></div></div>;
}
export function ParameterControls({parameters,values,onChange}:{parameters:Parameter[];values:Record<string,number|string>;onChange:(v:Record<string,number|string>)=>void}){
  if(!parameters.length)return <p className="muted">Автор не добавил настройки.</p>;
  return <div className="parameter-list">{parameters.map(p=><label key={p.name}><span>{p.label}</span>{p.type==='color'?<input type="color" value={String(values[p.name]??p.default)} onChange={e=>onChange({...values,[p.name]:e.target.value})}/>:<div className="range"><input type="range" min={p.min} max={p.max} step="any" value={Number(values[p.name]??p.default)} onChange={e=>onChange({...values,[p.name]:Number(e.target.value)})}/><output>{Number(values[p.name]??p.default).toFixed(2)}</output></div>}</label>)}</div>;
}
