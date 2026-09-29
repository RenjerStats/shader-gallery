import {useEffect,useState} from 'react';
import {rpc} from './api';
import {VirtualLobby,type Exhibition} from './VirtualLobby';
import {VirtualRoom} from './VirtualRoom';
import type {FeedWork} from './types';
import './virtual-gallery.css';

const exhibitions:Exhibition[]=[
  {id:'editorial',title:'Выбор редакции',subtitle:'Кураторская коллекция',mode:'curated'},
  {id:'community',title:'Выбор пользователей',subtitle:'Любимые работы сообщества',mode:'popular'},
  {id:'discussed',title:'Самые обсуждаемые',subtitle:'Работы, о которых говорят',mode:'discussed'},
  {id:'abstract',title:'Абстракция',subtitle:'Тематическая выставка',mode:'new',category:'Абстракция'},
  {id:'nature',title:'Природа',subtitle:'Тематическая выставка',mode:'new',category:'Природа'},
  {id:'light',title:'Свет',subtitle:'Тематическая выставка',mode:'new',category:'Свет'}
];

type FeedResult={items:FeedWork[]};
export function VirtualGallery({openWork}:{openWork:(id:string)=>void}){
  const initial=new URLSearchParams(location.search).get('room');
  const [room,setRoom]=useState<string|null>(exhibitions.some(item=>item.id===initial)?initial:null);
  const [works,setWorks]=useState<FeedWork[]>([]),[loading,setLoading]=useState(false),[error,setError]=useState('');
  const [focus,setFocus]=useState<number|null>(null),[nearest,setNearest]=useState(0),[gyro,setGyro]=useState(false);
  const [mobile,setMobile]=useState(()=>innerWidth<700);
  const exhibition=exhibitions.find(item=>item.id===room);
  useEffect(()=>{const resize=()=>setMobile(innerWidth<700);addEventListener('resize',resize);return()=>removeEventListener('resize',resize)},[]);
  useEffect(()=>{
    if(!exhibition){setWorks([]);return}
    let active=true;setLoading(true);setError('');setWorks([]);setFocus(null);
    rpc<FeedResult>('feed',{mode:exhibition.mode,category:exhibition.category,limit:24}).then(result=>{if(active)setWorks(result.items)}).catch(cause=>{if(active)setError(cause instanceof Error?cause.message:'Не удалось открыть зал')}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[exhibition?.id]);
  useEffect(()=>{const sync=()=>{const id=new URLSearchParams(location.search).get('room');setRoom(exhibitions.some(item=>item.id===id)?id:null)};addEventListener('popstate',sync);return()=>removeEventListener('popstate',sync)},[]);
  function enter(id:string|null){setRoom(id);setFocus(null);const url=new URL(location.href);if(id)url.searchParams.set('room',id);else url.searchParams.delete('room');history.pushState({},'',url);}
  const active=works[focus??nearest];
  return <main className="virtual-page">
    <div className="virtual-stage">
      {!exhibition?<>
        <VirtualLobby exhibitions={exhibitions} onEnter={enter}/>
        <div className="virtual-heading"><p>SHADER GALLERY · ВИРТУАЛЬНЫЙ МУЗЕЙ</p><h1>Войдите в галерею</h1><span>Выберите дверь, чтобы открыть выставку. Колесо мыши или перетаскивание — прогулка вдоль холла.</span></div>
        <div className="virtual-room-list" aria-label="Выставки">{exhibitions.map(item=><button key={item.id} onClick={()=>enter(item.id)}>{item.title}<span>↗</span></button>)}</div>
      </>:<>
        {works.length>0?<VirtualRoom key={`${room}:${mobile}:${works.map(item=>item.id).join(',')}`} works={works} title={exhibition.title} mode={mobile?'mobile':'desktop'} focus={focus} onFocusChange={setFocus} onNearest={setNearest} gyro={gyro}/>:<div className="virtual-empty">{loading?'Открываем зал…':error||'В этом зале пока нет работ.'}</div>}
        <div className="virtual-room-header"><button onClick={()=>enter(null)}>← В холл</button><div><p>ВЫСТАВКА</p><h1>{exhibition.title}</h1></div><span>{works.length} работ</span></div>
        {active&&<aside className="virtual-caption"><span>РАБОТА {String((focus??nearest)+1).padStart(2,'0')} / {String(works.length).padStart(2,'0')}</span><h2>{active.title}</h2><p>by {active.author.display_name}</p>{focus!=null&&<><p>{active.description}</p><div className="virtual-tags">{(active.tags||[]).join(' · ')}</div><button className="virtual-open" onClick={()=>openWork(active.id)}>Открыть работу ↗</button><button className="virtual-unfocus" onClick={()=>setFocus(null)}>Отдалиться</button></>}</aside>}
        <div className="virtual-room-hint"><span>Колесо мыши / перетаскивание — прогулка · нажмите на картину — рассмотреть</span><button onClick={async()=>{if(gyro){setGyro(false);return}const orientation=window.DeviceOrientationEvent as typeof DeviceOrientationEvent & {requestPermission?:()=>Promise<string>};if(!orientation)return;if(orientation.requestPermission&&await orientation.requestPermission()!=='granted')return;setGyro(true)}}>{gyro?'Выключить гироскоп':'Осмотреться гироскопом'}</button></div>
      </>}
    </div>
  </main>;
}
