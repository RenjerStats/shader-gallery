import {useEffect,useRef,useState} from 'react';
import {rpc} from './api';
import {CanvasPreview,ParameterControls} from './ShaderPreview';
import type {ShaderRenderer} from './runtime';
import type {DnaReference,FeedWork,Parameter,User,WorkDetail} from './types';
import './dna.css';

type Variant={id:string;slot:number;model:string;status:'queued'|'running'|'ready'|'failed'|'cancelled';attempts:number;error_message:string|null;draft_id:string|null;work_id:string|null;result:{title:string;description:string;code:string;parameters:Parameter[]}|null};
type Job={id:string;prompt:string;controls:string;created_at:string;status:string;references:DnaReference[];variants:Variant[]};
type History={items:Pick<Job,'id'|'prompt'|'created_at'|'variants'>[];next_cursor:string|null};
type Config={enabled:boolean;max_references:number;daily_limit:number};
type Form={prompt:string;controls:string;references:DnaReference[]};
const message=(e:unknown)=>e instanceof Error?e.message:'Не удалось связаться с галереей';
const href=(path:string)=>import.meta.env.BASE_URL.replace(/\/$/,'')+path;
const go=(path:string)=>{history.pushState({},'',href(path));dispatchEvent(new PopStateEvent('popstate'));window.scrollTo(0,0)};
const read=<T,>(key:string,fallback:T):T=>{try{return JSON.parse(localStorage.getItem(key)||'null')??fallback}catch{return fallback}};
const write=(key:string,value:unknown)=>{try{localStorage.setItem(key,JSON.stringify(value))}catch{}};
const statuses={queued:'В очереди',running:'Создаём шейдер',ready:'Готов к просмотру',failed:'Не удалось создать',cancelled:'Остановлено'};

function VariantCard({variant,onRefresh,onError}:{variant:Variant;onRefresh:()=>void;onError:(error:string)=>void}) {
  const [values,setValues]=useState<Record<string,number|string>>({}),[compiled,setCompiled]=useState(false),[busy,setBusy]=useState(false),[saved,setSaved]=useState(variant.draft_id);
  const renderer=useRef<ShaderRenderer|null>(null),lock=useRef(false);
  useEffect(()=>setSaved(variant.draft_id),[variant.draft_id]);
  const r=variant.result;
  async function action(kind:'save'|'publish'|'retry') {
    if(lock.current)return;lock.current=true;setBusy(true);onError('');
    try {
      if(kind==='retry') {await rpc('dna_retry',{variant_id:variant.id,expected_attempts:variant.attempts});onRefresh();return}
      if(kind==='save') {const d=await rpc<{draft_id:string}>('dna_save',{variant_id:variant.id,values});setSaved(d.draft_id);onRefresh();return}
      if(!compiled)throw new Error('Сначала дождитесь рабочего превью на этом устройстве.');
      const preview=renderer.current?.snapshot();
      if(!preview)throw new Error('Не удалось сделать превью. Попробуйте снова.');
      const published=await rpc<{work_id:string}>('dna_publish',{variant_id:variant.id,values,preview});go(`/works/${published.work_id}`);
    } catch(e) {onError(message(e))} finally {lock.current=false;setBusy(false)}
  }
  return <article className="dna-variant">
    <div className="dna-variant-heading"><span className="dna-letter">{variant.slot===0?'A':'B'}</span><div><strong>Вариант {variant.slot===0?'A':'B'}</strong><span className="muted small">{variant.model.split('/').at(-1)}</span></div><span className={`dna-status ${variant.status}`} role="status">{statuses[variant.status]}</span></div>
    {r?<><CanvasPreview code={r.code} parameters={r.parameters} values={values} compileKey={variant.id} onResult={(result,instance)=>{renderer.current=instance;setCompiled(result.ok)}}/>
      <div className="dna-variant-body"><h3>{r.title}</h3><p className="muted">{r.description}</p><ParameterControls parameters={r.parameters} values={values} onChange={setValues}/>
        {!compiled&&<p className="muted small">Если превью не работает на устройстве, сохраните черновик и исправьте код в редакторе на компьютере.</p>}
        <div className="dna-actions">{variant.work_id?<a className="button primary" href={href(`/works/${variant.work_id}`)}>Открыть публикацию</a>:<button className="button primary" disabled={busy||!compiled} onClick={()=>action('publish')}>Опубликовать</button>}
          <button className="button" disabled={busy||!!saved} onClick={()=>action('save')}>{saved?'Черновик сохранён':'В черновики'}</button>
          {saved&&<a className="button dna-editor-link" href={href(`/editor?draft=${saved}`)}>Открыть в редакторе</a>}</div>
        {saved&&<p className="muted small">Сохранённый черновик доступен на всех устройствах. Редактор кода — на компьютере.</p>}
      </div></>:<div className={`dna-placeholder ${variant.status==='running'?'is-running':''}`}><span className="dna-orbit" aria-hidden="true"/><h3>{statuses[variant.status]}</h3><p>{variant.error_message||(variant.status==='cancelled'?'Готовые результаты остались в истории.':'У каждой модели своё прочтение вашей идеи. Можно закрыть страницу и вернуться позже.')}</p>
        {variant.status==='failed'&&variant.attempts<3&&<button className="button" disabled={busy} onClick={()=>action('retry')}>Повторить этот вариант</button>}
        {variant.status==='failed'&&variant.attempts>=3&&<p>Повторы исчерпаны. Начните новую генерацию.</p>}
      </div>}
  </article>;
}

export function DnaStudio({user,requireAuth}:{user:User|null;requireAuth:()=>void}) {
  const storageKey=`shader-dna-form:${user?.id||'guest'}`;
  const [form,setForm]=useState<Form>(()=>read(storageKey,read('shader-dna-form:guest',{prompt:'',controls:'',references:[]})));
  const [config,setConfig]=useState<Config|null>(null),[job,setJob]=useState<Job|null>(null),[history,setHistory]=useState<History>({items:[],next_cursor:null});
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[picker,setPicker]=useState(false),[query,setQuery]=useState(''),[found,setFound]=useState<FeedWork[]>([]),[searching,setSearching]=useState(false);
  const selected=useRef<string|null>(null),serial=useRef(0),busyRef=useRef(false),searchSerial=useRef(0);
  const running=!!job?.variants.some(v=>v.status==='queued'||v.status==='running');
  const pendingKey=storageKey+':pending';
  const pendingRequest=useRef<{fingerprint:string;id:string}|null>(read(pendingKey,null));
  const pickerPanel=useRef<HTMLElement>(null);
  useEffect(()=>{write(storageKey,form)},[form,storageKey]);
  useEffect(()=>{if(picker)pickerPanel.current?.scrollIntoView({block:'start',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'})},[picker]);
  async function loadHistory(more=false) {
    if(!user)return;
    const data=await rpc<History>('dna_list',more&&history.next_cursor?{before:history.next_cursor}:{});
    setHistory(previous=>more?{...data,items:[...previous.items,...data.items.filter(j=>!previous.items.some(p=>p.id===j.id))]}:data);
    return data;
  }
  async function openJob(id:string,restore=false) {
    selected.current=id;const seq=++serial.current;
    try {const data=await rpc<Job>('dna_get',{id});if(seq!==serial.current)return;setJob(data);write(storageKey+':job',id);setError('');if(restore)setForm({prompt:data.prompt,controls:data.controls,references:data.references});}
    catch(e){if(seq===serial.current)setError(message(e))}
  }
  useEffect(()=>{
    let cancelled=false;
    rpc<Config>('dna_config').then(c=>{if(!cancelled)setConfig(c)}).catch(e=>{if(!cancelled)setError(message(e))});
    if(user)loadHistory().then(h=>{if(cancelled||selected.current)return;const active=h?.items.find(j=>j.variants.some(v=>v.status==='queued'||v.status==='running'));const id=active?.id||read<string|null>(storageKey+':job',null);if(id)openJob(id)}).catch(e=>{if(!cancelled)setError(message(e))});
    const params=new URLSearchParams(location.search),work=params.get('work'),revision=params.get('revision');
    if(work)rpc<WorkDetail>('work',{id:work,revision_id:revision||undefined}).then(({work:w})=>{if(cancelled)return;setForm(prev=>prev.references.some(r=>r.revision_id===w.revision.id)?prev:{...prev,references:[...prev.references.slice(0,2),{work_id:w.id,revision_id:w.revision.id,title:w.title,author:w.author.display_name,author_id:w.author_id,license:w.revision.license}]})}).catch(e=>{if(!cancelled)setError(message(e))});
    return()=>{cancelled=true;serial.current++;selected.current=null};
  },[user?.id]);
  useEffect(()=>{
    if(!running||!job)return;
    let cancelled=false,timer:ReturnType<typeof setTimeout>;
    const id=job.id;
    async function poll(){
      let delay=2500;
      const seq=serial.current;
      try {const updated=await rpc<Job>('dna_get',{id});if(cancelled||selected.current!==id)return;if(seq===serial.current){setJob(updated);if(!updated.variants.some(v=>v.status==='queued'||v.status==='running')){await loadHistory();return}}}
      catch(e){if(cancelled)return;setError(`Связь прервалась. Результаты остаются на сервере. ${message(e)}`);delay=8000}
      if(!cancelled)timer=setTimeout(poll,delay);
    }
    timer=setTimeout(poll,2500);return()=>{cancelled=true;clearTimeout(timer)};
  },[job?.id,running]);
  async function create(e:React.FormEvent) {
    e.preventDefault();if(!user){requireAuth();return}if(busyRef.current)return;
    busyRef.current=true;setBusy(true);setError('');
    const payload={prompt:form.prompt,controls:form.controls,reference_ids:form.references.map(r=>r.revision_id)},fingerprint=JSON.stringify(payload);
    let pending=pendingRequest.current;
    if(pending?.fingerprint!==fingerprint){pending={fingerprint,id:crypto.randomUUID()};pendingRequest.current=pending;write(pendingKey,pending)}
    const seq=++serial.current;
    try {const data=await rpc<Job>('dna_create',{...payload,request_id:pending!.id});write(pendingKey,null);pendingRequest.current=null;if(seq!==serial.current)return;selected.current=data.id;write(storageKey+':job',data.id);setJob(data);await loadHistory();}
    catch(e){setError(message(e))}finally{busyRef.current=false;setBusy(false)}
  }
  async function search(e?:React.FormEvent) {
    e?.preventDefault();const seq=++searchSerial.current;setSearching(true);
    try{const data=await rpc<{items:FeedWork[]}>('feed',{query,limit:12});if(seq===searchSerial.current)setFound(data.items)}catch(e){setError(message(e))}finally{if(seq===searchSerial.current)setSearching(false)}
  }
  async function addReference(w:FeedWork) {
    if(form.references.length>=3)return;
    try{const d=await rpc<WorkDetail>('work',{id:w.id,revision_id:w.revision.id});const r:DnaReference={work_id:w.id,revision_id:w.revision.id,title:w.title,author:w.author.display_name,author_id:w.author_id,license:d.work.revision.license};setForm(prev=>prev.references.length>=3||prev.references.some(x=>x.revision_id===r.revision_id)?prev:{...prev,references:[...prev.references,r]});setPicker(false)}catch(e){setError(message(e))}
  }
  return <main className="page dna-page"><div className="dna-intro"><div><p className="eyebrow">ИДЕЯ → ДВА ВАРИАНТА → ВАША РАБОТА</p><h1>Shader DNA <em>Studio</em></h1><p>Смешайте вдохновение. Опишите движение.<br/>Найдите своё прочтение цифрового искусства.</p></div><div className="dna-emblem" aria-hidden="true"><span>A</span><i>×</i><span>B</span></div></div>
    {error&&<div className="notice" role="alert">{error}<button onClick={()=>setError('')} aria-label="Закрыть сообщение">×</button></div>}
    {config&&!config.enabled&&<div className="dna-info" role="status">Генерация пока не подключена. Можно подготовить идею и референсы; они сохранятся на этом устройстве.</div>}
    <div className="dna-layout"><section className="dna-composer panel" aria-label="Новая генерация"><form onSubmit={create}>
      <div className="section-head compact"><h2>Ваша идея</h2><span className="muted small">01 / ЗАМЫСЕЛ</span></div>
      <label htmlFor="dna-prompt">Что хотите увидеть?</label><textarea id="dna-prompt" required maxLength={2000} rows={5} placeholder="Например: перламутровые волны с мягким свечением, которые медленно переливаются…" value={form.prompt} onChange={e=>setForm({...form,prompt:e.target.value})}/><div className="dna-counter">{form.prompt.length} / 2000</div>
      <div className="section-head compact"><h3>Референсы</h3><span className="muted small">{form.references.length} / 3 · необязательно</span></div>
      <div className="dna-references">{form.references.map((r,i)=><div className="dna-reference" key={r.revision_id}><span className="dna-ref-number">{i+1}</span><div><a href={href(`/works/${r.work_id}?revision=${r.revision_id}`)}>{r.title}</a><span>{r.author} · {r.license}</span></div><button type="button" className="text-button" aria-label={`Убрать ${r.title}`} onClick={()=>setForm({...form,references:form.references.filter(x=>x.revision_id!==r.revision_id)})}>×</button></div>)}</div>
      {form.references.length<3&&<button type="button" className="dna-add" onClick={()=>{setPicker(!picker);if(!picker)search()}}>+ Выбрать из галереи</button>}
      <label htmlFor="dna-controls">Настройки будущей работы <span className="muted small">необязательно</span></label><textarea id="dna-controls" rows={2} maxLength={1000} placeholder="Скорость, масштаб, цвет, сила свечения" value={form.controls} onChange={e=>setForm({...form,controls:e.target.value})}/>
      <button className="button primary full dna-generate" disabled={busy||running||(!config?.enabled&&!!user)}>{!user?'Войти и создать':busy?'Запускаем…':running?'Варианты создаются…':'Создать два варианта'}</button>
      <p className="muted small dna-note">Две независимые модели · до {config?.daily_limit||10} запусков за 24 часа. Результаты приватны до публикации. Идея и исходники референсов передаются моделям через OpenRouter.</p>
    </form></section>
    <section className="dna-workspace" aria-label="Результаты генерации"><div className="section-head"><div><span className="eyebrow">02 / ИССЛЕДОВАНИЕ</span><h2>{job?'Два взгляда на вашу идею':'Здесь встретятся две идеи'}</h2></div>{running&&<button className="text-button" disabled={busy} onClick={async()=>{if(!job)return;setBusy(true);try{setJob(await rpc<Job>('dna_cancel',{id:job.id}));await loadHistory()}catch(e){setError(message(e))}finally{setBusy(false)}}}>Остановить</button>}</div>
      {job?<><p className="dna-job-prompt">{job.prompt}</p><div className="dna-results">{job.variants.map(v=><VariantCard key={v.id} variant={v} onError={setError} onRefresh={()=>openJob(job.id)}/>)}</div>{job.references.length>0&&<div className="dna-lineage"><strong>ДНК этой работы</strong>{job.references.map(r=><a key={r.revision_id} href={href(`/works/${r.work_id}?revision=${r.revision_id}`)}>{r.title} · {r.author} · {r.license}</a>)}</div>}</>:<div className="dna-results">{['A','B'].map((label,i)=><div className="dna-empty-variant" key={label}><span className="dna-letter">{label}</span><div className={`dna-seed seed-${i}`} aria-hidden="true"/><h3>{i?'Другое прочтение':'Первое направление'}</h3><p>Живой шейдер с вашими настройками</p></div>)}</div>}
    </section></div>
    {picker&&<section ref={pickerPanel} className="panel dna-picker" aria-label="Выбор референсов"><div className="section-head"><h2>Вдохновение из галереи</h2><button className="button" onClick={()=>setPicker(false)}>Закрыть</button></div><form className="filters" onSubmit={search}><input autoFocus aria-label="Поиск референсов" placeholder="Название, тема или тег" value={query} onChange={e=>setQuery(e.target.value)}/><button className="button" disabled={searching}>Найти</button></form>{searching?<p role="status">Ищем работы…</p>:<div className="dna-pick-grid">{found.map(w=><button key={w.id} className="dna-pick-card" disabled={form.references.some(r=>r.revision_id===w.revision.id)} onClick={()=>addReference(w)}>{w.revision.preview&&<img src={w.revision.preview} alt=""/>}<strong>{w.title}</strong><span>{w.author.display_name}</span></button>)}{!found.length&&<p>Работ не найдено. Попробуйте другой запрос.</p>}</div>}</section>}
    <section className="dna-history"><div className="section-head"><div><p className="eyebrow">03 / КОЛЛЕКЦИЯ ИДЕЙ</p><h2>История генераций</h2></div>{user&&<button className="button" onClick={()=>loadHistory().catch(e=>setError(message(e)))}>Обновить</button>}</div>{!user?<p className="muted">Войдите, чтобы продолжать свои идеи на любом устройстве.</p>:history.items.length?<><div className="dna-history-grid">{history.items.map(j=><button key={j.id} disabled={busy} className={`dna-history-item ${job?.id===j.id?'selected':''}`} onClick={()=>openJob(j.id,true)}><span>{new Date(j.created_at).toLocaleDateString('ru-RU',{day:'numeric',month:'short'})}</span><strong>{j.prompt}</strong><span>{j.variants.filter(v=>v.status==='ready').length} из 2 готово{j.variants.some(v=>v.status==='running'||v.status==='queued')?' · создаётся':''}</span></button>)}</div>{history.next_cursor&&<button className="button" onClick={()=>loadHistory(true).catch(e=>setError(message(e)))}>Ранее</button>}</>:<p className="muted">Здесь сохранятся ваши идеи и оба варианта каждой генерации.</p>}</section>
  </main>;
}
