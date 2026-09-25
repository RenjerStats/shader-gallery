import {shaders,seedPosts,ShaderView} from './shaders.js';

const $=(s,root=document)=>root.querySelector(s);
const $$=(s,root=document)=>[...root.querySelectorAll(s)];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths={
 home:'<path d="M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/>',
 compass:'<circle cx="12" cy="12" r="9"/><path d="m16 8-2.5 5.5L8 16l2.5-5.5Z"/>',
 bookmark:'<path d="M6 3h12v18l-6-4-6 4Z"/>',
 plus:'<path d="M12 5v14M5 12h14"/>',
 user:'<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
 search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/>',
 arrow:'<path d="M4 12h15m-6-6 6 6-6 6"/>',
 chevron:'<path d="m9 6 6 6-6 6"/>',
 back:'<path d="M20 12H5m6-6-6 6 6 6"/>',
 heart:'<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/>',
 spark:'<path d="m12 3 2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4ZM20 2v4m-2-2h4"/>',
 lock:'<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3"/>',
 close:'<path d="m6 6 12 12M6 18 18 6"/>',
 play:'<path d="m8 4 13 8-13 8Z"/>',
 pause:'<path d="M9 5v14M15 5v14"/>',
 expand:'<path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>',
 copy:'<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>',
 code:'<path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-14-2 16"/>',
 check:'<path d="m5 12 4 4L19 6"/>',
 send:'<path d="m22 2-7 20-4-9-9-4 20-7ZM11 13 22 2"/>',
 repeat:'<path d="m17 2 4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4m14-1v2a3 3 0 0 1-3 3H3"/>',
 info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.01"/>',
 grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>'
};
const icon=name=>`<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name]||paths.spark}</svg>`;
const categories=['Все','Абстракция','Жидкости','Космос','Геометрия'];
const initial={posts:[],likes:[],saved:[],following:[],comments:{}};
let storageAvailable=true;
function readStore(key,fallback){try{const data=JSON.parse(localStorage.getItem(key));return data??fallback;}catch{return fallback;}}
const raw=readStore('shader-gallery-v1',initial);
const state={...initial,...raw};
for(const k of ['posts','likes','saved','following'])if(!Array.isArray(state[k]))state[k]=[];
if(!state.comments||typeof state.comments!=='object')state.comments={};
let page='feed',category='Все',sort='curated',query='',pageViews=[],modalViews=[],detailPost=null,editor=null,returnFocus=null,previousHash='',toastTimer;
function setHash(hash){if(location.hash!=='#'+hash)history.pushState(null,'','#'+hash);}
const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
const posts=()=>[...state.posts,...seedPosts].map(p=>({...p,code:p.code||shaders[p.shader]}));
const findPost=id=>posts().find(p=>p.id===id);
function persist(){try{localStorage.setItem('shader-gallery-v1',JSON.stringify(state));return true;}catch{storageAvailable=false;toast('Память браузера заполнена. Изменения сохранены только до закрытия страницы.');return false;}}
function toast(message){const t=$('#toast');t.textContent=message;t.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>t.classList.remove('show'),3400);}
function avatar(p,large=false){return `<span class="avatar ${large?'large':''}" style="--avatar:${esc(p.color||'#c4a2ff')}">${esc(p.avatar||'R')}</span>`;}
function navItem(id,label,symbol){return `<a class="nav-item ${page===id?'active':''}" ${page===id?'aria-current="page"':''} href="#${id}">${icon(symbol)}<span>${label}</span></a>`;}
function shell(){
 $('#app').innerHTML=`<aside class="sidebar"><a href="#feed" class="brand"><img src="/favicon.svg" alt=""/><div>Shader Gallery<small>Made of possibilities</small></div></a><nav class="nav-list" aria-label="Основная навигация">${navItem('feed','Лента','home')}${navItem('explore','Исследовать','compass')}${navItem('saved','Сохранённое','bookmark')}<div class="nav-label">Твоё пространство</div>${navItem('profile','Мои работы','grid')}<button class="nav-item" data-action="create">${icon('plus')}<span>Создать шейдер</span></button></nav><div class="sidebar-note">${icon('spark')}<strong>Красота — в движении.</strong><p>Несколько строк кода.<br/>Бесконечность возможностей.</p></div><a class="account" href="#profile"><span class="avatar me">R</span><div><strong>Рейнджер</strong><small>Творческий режим</small></div><span class="spacer"></span>${icon('chevron')}</a></aside>
 <div class="content"><header class="topbar"><a href="#feed" class="brand"><img src="/favicon.svg" alt=""/>Shader Gallery</a><div class="topbar-title">${({feed:'Лента',explore:'Исследовать',saved:'Сохранённое',profile:'Мои работы'})[page]}<span>/ пространство вдохновения</span></div><label class="search">${icon('search')}<input id="global-search" type="search" placeholder="Шейдер, автор или настроение…" aria-label="Поиск шейдеров" value="${esc(query)}"/></label><button class="primary" data-action="create">${icon('plus')}<span>Создать</span></button><a class="avatar me" href="#profile" aria-label="Мой профиль">R</a></header><main class="page" id="main-content"></main></div>
 <nav class="bottom-nav" aria-label="Мобильная навигация"><a href="#feed" class="${page==='feed'?'active':''}" aria-label="Лента">${icon('home')}<span>Лента</span></a><a href="#explore" class="${page==='explore'?'active':''}" aria-label="Исследовать">${icon('compass')}<span>Поиск</span></a><button class="create-nav" data-action="create" aria-label="Создать шейдер">${icon('plus')}</button><a href="#saved" class="${page==='saved'?'active':''}" aria-label="Сохранённое">${icon('bookmark')}<span>Коллекция</span></a><a href="#profile" class="${page==='profile'?'active':''}" aria-label="Мои работы">${icon('user')}<span>Профиль</span></a></nav>`;
 $('#global-search')?.addEventListener('input',e=>{query=e.target.value;renderGallery();});
 renderPage();
}
function renderPage(){
 pageViews.forEach(v=>v.destroy());pageViews=[];
 let intro='';
 if(page==='feed')intro=`<section class="hero" aria-label="Работа недели"><div class="hero-copy"><div class="eyebrow">${icon('spark')} МЕСТО ДЛЯ ЦИФРОВОГО ИСКУССТВА</div><h1>Искусство,<br/>которое <em>живёт.</em></h1><p>Исследуй шейдеры. Находи свой ритм.<br/>Создавай то, чего ещё не было.</p><button class="text-button" data-action="open" data-id="liquid-chrome">Открыть работу недели ${icon('arrow')}</button></div><div class="hero-art"><canvas data-hero aria-label="Живой шейдер Liquid Chrome"></canvas><span class="pill"><span class="live-dot"></span> LIVE RENDER</span><div class="hero-credit"><strong>Liquid Chrome</strong>by nora.studio</div></div><div class="hero-dots" aria-hidden="true"><span></span><span></span><span></span></div></section>`;
 if(page==='explore')intro=`<section class="section-intro"><div class="eyebrow">ОТКРОЙ ЧТО-ТО НОВОЕ</div><h1>Следуй за любопытством.</h1><p>Найди работу по названию, автору, тегу или настроению.</p></section><label class="field"><span class="sr-only">Поиск в галерее</span><input id="explore-search" type="search" placeholder="Попробуй: chrome, космос, waves…" value="${esc(query)}"/></label>`;
 if(page==='saved')intro=`<section class="section-intro"><div class="eyebrow">ЛИЧНАЯ КОЛЛЕКЦИЯ</div><h1>Хочется вернуться.</h1><p>Все шейдеры, которые ты сохранил для вдохновения.</p></section>`;
 if(page==='profile')intro=`<section class="profile-hero"><span class="avatar me large">R</span><div><h1>Рейнджер</h1><p>@ranger · твоя территория экспериментов</p><div class="profile-stats"><span><strong>${state.posts.length}</strong> работ</span><span><strong>${state.saved.length}</strong> сохранено</span><span><strong>${state.following.length}</strong> подписок</span></div></div><button class="primary" data-action="create">${icon('plus')}Новая работа</button></section>`;
 $('#main-content').innerHTML=intro+`<section aria-label="Галерея шейдеров"><div class="feed-header"><h2>${({feed:'На твоей волне',explore:'Галерея',saved:'Твоя коллекция',profile:'Мои шейдеры'})[page]}</h2><span class="muted">Вдохновение в реальном времени</span><div class="feed-tabs" aria-label="Сортировка"><button data-action="sort" data-sort="curated" class="${sort==='curated'?'active':''}" aria-pressed="${sort==='curated'}">Для тебя</button><button data-action="sort" data-sort="new" class="${sort==='new'?'active':''}" aria-pressed="${sort==='new'}">Новое</button></div></div><div class="filters" aria-label="Категории">${categories.map(c=>`<button class="chip ${category===c?'active':''}" aria-pressed="${category===c}" data-action="category" data-category="${c}">${c}</button>`).join('')}</div><div id="gallery" class="gallery"></div><div class="gallery-end">Сделай свою волну. Создай шейдер.</div></section><footer class="page-footer"><span>Shader Gallery · искусство в движении</span><span>${icon('lock')} Приватный прототип</span></footer>`;
 const hero=$('[data-hero]');if(hero)mountShader(hero,shaders.chrome,pageViews,{fps:24});
 $('#explore-search')?.addEventListener('input',e=>{query=e.target.value;$('#global-search').value=query;renderGallery();});
 renderGallery();
}
function card(p){return `<article class="shader-card" data-post="${esc(p.id)}"><button class="art-button" data-action="open" data-id="${esc(p.id)}" aria-label="Открыть ${esc(p.title)}"><canvas data-shader="${esc(p.id)}" aria-label="Превью ${esc(p.title)}"></canvas><span class="pill"><span class="live-dot"></span> ${reduced?'GLSL':'LIVE'}</span><span class="format">FRAGMENT / GLSL</span><span class="open-icon">${icon('expand')}</span></button><div class="card-title-row"><button class="card-title" data-action="open" data-id="${esc(p.id)}">${esc(p.title)}</button><button class="icon-button ${state.saved.includes(p.id)?'is-saved':''}" data-action="save" data-id="${esc(p.id)}" aria-label="${state.saved.includes(p.id)?'Убрать из сохранённого':'Сохранить'} ${esc(p.title)}" aria-pressed="${state.saved.includes(p.id)}">${icon('bookmark')}</button></div><div class="card-meta">${avatar(p)}<span>${esc(p.author)}</span><button class="like ${state.likes.includes(p.id)?'liked':''}" data-action="like" data-id="${esc(p.id)}" aria-label="Нравится ${esc(p.title)}" aria-pressed="${state.likes.includes(p.id)}">${icon('heart')}<span>${p.likes+Number(state.likes.includes(p.id))}</span></button></div></article>`;}
function renderGallery(){
 pageViews=pageViews.filter(v=>{if(v.canvas.hasAttribute('data-shader')){v.destroy();return false;}return true;});
 let list=posts();
 if(page==='saved')list=list.filter(p=>state.saved.includes(p.id));
 if(page==='profile')list=list.filter(p=>p.own);
 if(category!=='Все')list=list.filter(p=>p.category===category);
 const q=query.trim().toLowerCase();if(q)list=list.filter(p=>[p.title,p.author,p.category,...p.tags].join(' ').toLowerCase().includes(q));
 if(sort==='new')list.sort((a,b)=>(b.createdAt||0)-(a.createdAt||0));
 const gallery=$('#gallery');if(!gallery)return;
 gallery.innerHTML=list.length?list.map(card).join(''):`<div class="empty">${icon(page==='saved'?'bookmark':page==='profile'?'code':'search')}<h3>${query||category!=='Все'?'Пока ничего не нашлось':page==='saved'?'Собери свою коллекцию':page==='profile'?'Твоя первая работа — впереди':'Здесь появятся шейдеры'}</h3><p>${query||category!=='Все'?'Попробуй другой запрос или сбрось фильтры.':page==='saved'?'Нажми на закладку у работы — и она останется здесь.':'Открой редактор, измени шаблон и поделись результатом.'}</p><button class="secondary" data-action="${query||category!=='Все'?'reset-filters':page==='saved'?'go-feed':'create'}">${query||category!=='Все'?'Сбросить фильтры':page==='saved'?'Перейти в ленту':'Создать шейдер'} ${icon('arrow')}</button></div>`;
 $$('[data-shader]',gallery).forEach(c=>mountShader(c,findPost(c.dataset.shader).code,pageViews,{fps:24,dpr:1}));
}
function mountShader(canvas,code,list,options={}){
 const v=new ShaderView(canvas,code,{paused:reduced,...options});list.push(v);
 if(v.error){const f=document.createElement('div');f.className='webgl-fallback';f.textContent=v.error;canvas.parentElement.append(f);}
 return v;
}
function updateReactions(id){
 const p=findPost(id),liked=state.likes.includes(id),saved=state.saved.includes(id);
 $$(`[data-action="like"][data-id="${CSS.escape(id)}"]`).forEach(b=>{b.classList.toggle('liked',liked);b.setAttribute('aria-pressed',liked);b.innerHTML=icon('heart')+`<span>${p.likes+Number(liked)}</span>`;});
 $$(`[data-action="save"][data-id="${CSS.escape(id)}"]`).forEach(b=>{b.classList.toggle('is-saved',saved);b.setAttribute('aria-pressed',saved);b.setAttribute('aria-label',`${saved?'Убрать из сохранённого':'Сохранить'} ${p.title}`);b.innerHTML=icon('bookmark')+(b.classList.contains('secondary')?`<span>${saved?'Сохранено':'Сохранить'}</span>`:'');});
}
function setModal(html){
 if(!document.body.classList.contains('dialog-open'))returnFocus=document.activeElement;
 modalViews.forEach(v=>v.destroy());modalViews=[];
 pageViews.forEach(v=>v.paused=true);
 $('#overlay-root').innerHTML=`<div class="modal-backdrop"><div class="modal ${editor?'editor-modal':''}" role="dialog" aria-modal="true" aria-labelledby="dialog-title" tabindex="-1">${html}</div></div>`;
 document.body.classList.add('dialog-open');
 requestAnimationFrame(()=>$('.modal').focus({preventScroll:true}));
}
function closeModal(updateHash=true){
 if(editor)saveDraft();
 modalViews.forEach(v=>v.destroy());modalViews=[];pageViews.forEach(v=>v.paused=reduced);
 $('#overlay-root').innerHTML='';document.body.classList.remove('dialog-open');detailPost=null;editor=null;
 if(updateHash)setHash(previousHash||page);
 if(returnFocus?.isConnected)returnFocus.focus({preventScroll:true});
}
function openPost(id,route=true){
 const p=findPost(id);if(!p){toast('Работа недоступна в этом браузере.');location.hash=page;return;}
 if(editor)saveDraft();editor=null;detailPost=p;
 if(route){previousHash=page;setHash(`work/${id}`);}
 setModal(`<header class="modal-header"><h2 id="dialog-title">Просмотр работы</h2><span class="pill">${p.own?'ТВОЯ ПУБЛИКАЦИЯ':'ДЕМО-РАБОТА'}</span><button class="icon-button" data-action="close-modal" aria-label="Закрыть просмотр">${icon('close')}</button></header><div class="detail-grid"><div class="detail-stage"><div class="detail-art" id="detail-art"><canvas id="detail-canvas" aria-label="Шейдер ${esc(p.title)}"></canvas><span class="pill"><span class="live-dot"></span> LIVE SHADER</span><button class="icon-button fullscreen-exit" data-action="fullscreen" aria-label="Выйти из полного экрана">${icon('close')}</button></div><div class="playback"><button class="icon-button" data-action="pause" aria-label="${reduced?'Продолжить анимацию':'Приостановить анимацию'}">${icon(reduced?'play':'pause')}</button><span class="mono">GLSL / WEBGL</span><span class="spacer"></span><label class="sr-only" for="detail-speed">Скорость анимации</label><select id="detail-speed"><option value="0.5">0.5×</option><option value="1" selected>1×</option><option value="1.5">1.5×</option><option value="2">2×</option></select><button class="icon-button" data-action="fullscreen" aria-label="Полный экран">${icon('expand')}</button></div></div><div class="detail-info"><h1>${esc(p.title)}</h1><div class="author-line">${avatar(p)}<div>${esc(p.author)}<small>${p.own?'Твоя работа':'Автор демо-работы'}</small></div>${!p.own?`<button class="secondary" data-action="follow" data-author="${esc(p.author)}">${state.following.includes(p.author)?'Подписка ✓':'Подписаться'}</button>`:''}</div><p class="description">${esc(p.description)}</p><div class="tags">${p.tags.map(t=>`<span class="tag">#${esc(t)}</span>`).join('')}</div><div class="detail-actions"><button class="secondary ${state.likes.includes(id)?'liked':''}" data-action="like" data-id="${esc(id)}" aria-label="Нравится ${esc(p.title)}" aria-pressed="${state.likes.includes(id)}">${icon('heart')}<span>${p.likes+Number(state.likes.includes(id))}</span></button><button class="secondary ${state.saved.includes(id)?'is-saved':''}" data-action="save" data-id="${esc(id)}" aria-pressed="${state.saved.includes(id)}">${icon('bookmark')}<span>${state.saved.includes(id)?'Сохранено':'Сохранить'}</span></button></div><div class="detail-tabs"><button class="active" data-action="detail-tab" data-tab="comments">Обсуждение</button><button data-action="detail-tab" data-tab="code">Исходный код</button></div><div class="tab-panel" id="detail-panel"></div><div class="demo-caption">${p.own?'Публикация сохранена в этом браузере.':'Демонстрационный автор. Реакции и комментарии сохраняются в этом браузере.'}</div></div></div>`);
 const view=mountShader($('#detail-canvas'),p.code,modalViews,{fps:30,dpr:1.5});
 $('#detail-speed').addEventListener('change',e=>view.speed=Number(e.target.value));
 renderDetailTab('comments');
}
function renderDetailTab(tab){
 const p=detailPost;if(!p)return;
 $$('.detail-tabs button').forEach(b=>b.classList.toggle('active',b.dataset.tab===tab));
 if(tab==='code')$('#detail-panel').innerHTML=`<pre class="code-box">${esc(p.code)}</pre><div class="code-actions"><button class="secondary small" data-action="copy-code">${icon('copy')} Копировать</button><button class="text-button" data-action="remix" data-id="${esc(p.id)}">${icon('repeat')} Сделать ремикс</button></div>`;
 else{
  const comments=[...(p.comments||[]),...(state.comments[p.id]||[])];
  $('#detail-panel').innerHTML=comments.length?comments.map(c=>`<div class="comment"><strong>${esc(c.name)}</strong><p>${esc(c.text)}</p></div>`).join(''):'<p class="muted" style="font-size:11px">Пока тихо. Начни обсуждение этой работы.</p>';
  $('#detail-panel').insertAdjacentHTML('beforeend',`<form class="comment-form"><input aria-label="Твой комментарий" name="comment" required maxlength="500" placeholder="Что ты чувствуешь, глядя на это?"/><button class="icon-button" type="submit" aria-label="Добавить комментарий">${icon('send')}</button></form>`);
  $('.comment-form').addEventListener('submit',e=>{e.preventDefault();const input=$('input',e.target),text=input.value.trim();if(!text)return;(state.comments[p.id]??=[]).push({name:'ranger',text});persist();renderDetailTab('comments');toast('Комментарий добавлен');});
 }
}
function defaultDraft(){return {code:shaders.starter,title:'',description:'',tags:'',category:'Абстракция',step:1,template:'starter'};}
function saveDraft(){if(!editor)return;const {code,title,description,tags,category,step,template}=editor;try{localStorage.setItem('shader-gallery-draft',JSON.stringify({code,title,description,tags,category,step,template}));}catch{storageAvailable=false;}}
function openEditor(remix){
 previousHash=page;setHash('create');detailPost=null;
 const draft=remix?{...defaultDraft(),code:remix.code,title:`${remix.title} / remix`,description:`Мой эксперимент на основе ${remix.title} от @${remix.author}.`,category:remix.category,tags:remix.tags.join(', '),template:'custom'}:{...defaultDraft(),...readStore('shader-gallery-draft',{})};
 editor={...draft,step:1,compiledCode:null,error:null};renderEditor();
}
function renderEditor(){
 const e=editor;
 setModal(`<header class="modal-header"><h2 id="dialog-title">${icon('code')} Создать шейдер</h2><div class="editor-steps">${['Шейдер','О работе','Предпросмотр'].map((s,i)=>`<div class="editor-step ${e.step===i+1?'active':e.step>i+1?'done':''}"><b>${e.step>i+1?'✓':i+1}</b>${s}</div>`).join('')}</div><button class="icon-button" data-action="close-modal" aria-label="Закрыть редактор">${icon('close')}</button></header><div class="editor-body"><div class="editor-preview"><div class="preview-label"><span>ТВОЯ РАБОТА В ДВИЖЕНИИ</span><span class="pill"><span class="live-dot"></span> LIVE PREVIEW</span></div><div class="editor-canvas-wrap"><canvas id="editor-canvas" aria-label="Предпросмотр твоего шейдера"></canvas></div><div class="playback"><button class="icon-button" data-action="pause" aria-label="${reduced?'Продолжить анимацию':'Приостановить анимацию'}">${icon(reduced?'play':'pause')}</button><span>Проведи по превью — передаётся iMouse</span><span class="spacer"></span><span class="mono">WEBGL</span></div></div><div class="editor-panel" id="editor-panel">${editorPanel()}</div></div><footer class="editor-footer">${e.step>1?`<button class="secondary" data-action="editor-back">${icon('back')}Назад</button>`:''}<span class="draft-note">${icon('check')}Черновик в этом браузере</span><button class="primary" data-action="${e.step===3?'publish':'editor-next'}">${e.step===3?'Опубликовать':e.step===1?'Далее: о работе':'К предпросмотру'} ${icon(e.step===3?'check':'arrow')}</button></footer>`);
 const view=mountShader($('#editor-canvas'),e.compiledCode||e.code,modalViews,{fps:30,dpr:1.35});
 if(view.error){e.error=view.error;e.compiledCode=null;}else if(e.step===1&&!e.compiledCode)e.compiledCode=e.code;
 if(e.step===1){
  const code=$('#shader-code');
  code.addEventListener('input',()=>{e.code=code.value;e.error=null;saveDraft();updateCompileState();});
  code.addEventListener('keydown',event=>{if(event.key==='Tab'){event.preventDefault();const start=code.selectionStart,end=code.selectionEnd;code.setRangeText('  ',start,end,'end');code.dispatchEvent(new Event('input'));}if((event.ctrlKey||event.metaKey)&&event.key==='Enter'){event.preventDefault();compileEditor();}});
  $('#shader-template').addEventListener('change',ev=>{e.template=ev.target.value;if(shaders[e.template]){e.code=shaders[e.template];code.value=e.code;saveDraft();compileEditor();}});
  updateCompileState();
 }
 if(e.step===2){
  for(const key of ['title','description','tags','category'])$(`#post-${key}`).addEventListener('input',ev=>{e[key]=ev.target.value;saveDraft();if(key==='title')$('#title-count').textContent=`${e.title.length}/60`;$('#metadata-error').textContent='';});
 }
}
function editorPanel(){
 const e=editor;
 if(e.step===1)return `<h3>Начни с искры.</h3><p class="lead">Измени готовый шаблон или вставь свой fragment shader.<br/>Запусти код, чтобы увидеть результат.</p><div class="template-row"><label for="shader-template">Шаблон</label><select id="shader-template">${[['starter','Цветовые волны'],['chrome','Liquid Chrome'],['silk','Electric Silk'],['aurora','Aurora Drift'],['custom','Свой код']].map(([v,n])=>`<option value="${v}" ${e.template===v?'selected':''}>${n}</option>`).join('')}</select></div><label class="sr-only" for="shader-code">GLSL-код шейдера</label><textarea id="shader-code" class="code-editor" spellcheck="false" autocapitalize="off" autocomplete="off" autocorrect="off" maxlength="40000">${esc(e.code)}</textarea><div class="compile-row"><button class="secondary" data-action="compile">${icon('play')}Запустить</button><span id="compile-state" class="compile-state"></span></div><div id="compile-errors" role="alert"></div><details class="code-help"><summary>Формат GLSL и доступные переменные</summary><p>WebGL 1 / GLSL ES 1.00. Используй mainImage(out vec4 fragColor, in vec2 fragCoord). Доступны iTime (float), iResolution (vec3) и iMouse (vec4). Объявления uniform добавляются автоматически. Текстуры и iChannel не поддерживаются. Запуск: Ctrl/⌘ + Enter. Строки ошибок включают 4 служебные строки перед твоим кодом.</p></details>`;
 if(e.step===2)return `<h3>Дай работе имя.</h3><p class="lead">Расскажи, что ты исследовал. Хорошее описание помогает почувствовать работу.</p><label class="field"><span>Название <small id="title-count">${e.title.length}/60</small></span><input id="post-title" required maxlength="60" placeholder="Например, Midnight Flow" value="${esc(e.title)}"/></label><label class="field"><span>Описание <small>Необязательно</small></span><textarea id="post-description" maxlength="700" placeholder="Идея, настроение или история эксперимента…">${esc(e.description)}</textarea></label><label class="field"><span>Категория</span><select id="post-category">${categories.slice(1).map(c=>`<option ${e.category===c?'selected':''}>${c}</option>`).join('')}</select></label><label class="field"><span>Теги <small>До 5, через запятую</small></span><input id="post-tags" maxlength="150" placeholder="ambient, waves, generative" value="${esc(e.tags)}"/></label><div id="metadata-error" class="field-error" role="alert"></div>`;
 return `<h3>Всё готово к первому взгляду.</h3><p class="lead">Так твоя работа появится в ленте. Можно вернуться и что-нибудь изменить.</p><article class="review-post"><div class="author-line"><span class="avatar me">R</span><div>ranger<small>Твоя новая работа</small></div></div><h3>${esc(e.title)}</h3><p>${esc(e.description||'Без описания — пусть говорит шейдер.')}</p><div class="tags"><span class="tag">${esc(e.category)}</span>${parseTags(e.tags).map(t=>`<span class="tag">#${esc(t)}</span>`).join('')}</div></article><div class="review-note">${icon('lock')}<span>Это приватный прототип. Работа появится в твоей ленте и сохранится в этом браузере. Общей ленты между устройствами пока нет.</span></div>`;
}
function parseTags(value){return [...new Set(value.split(/[,#]/).map(s=>s.trim().replace(/\s+/g,'-').slice(0,25)).filter(Boolean))].slice(0,5);}
function updateCompileState(){
 if(!editor||!$('#compile-state'))return;
 const dirty=editor.compiledCode!==editor.code;
 $('#compile-state').textContent=editor.error?'В коде есть ошибка':dirty?'Код изменён — нажми «Запустить»':'Скомпилировано · готово';
 $('#compile-state').classList.toggle('dirty',dirty||!!editor.error);
 $('#compile-errors').innerHTML=editor.error?`<pre class="compile-error">${esc(editor.error)}</pre>`:'';
 $('[data-action="editor-next"]').disabled=dirty||!!editor.error;
}
function compileEditor(){
 const e=editor;if(!e)return;
 const result=modalViews[0].compile(e.code);
 if(result.ok){e.compiledCode=e.code;e.error=null;$('.webgl-fallback',$('.editor-canvas-wrap'))?.remove();}
 else {e.error=result.error;e.compiledCode=null;}
 updateCompileState();saveDraft();
}
function nextEditor(){
 const e=editor;
 if(e.step===1&&(!e.compiledCode||e.code!==e.compiledCode||e.error)){updateCompileState();return;}
 if(e.step===2){e.title=e.title.trim();if(!e.title){$('#metadata-error').textContent='Добавь название, чтобы работу было легко найти.';$('#post-title').focus();return;}}
 e.step++;saveDraft();renderEditor();
}
function publish(){
 const e=editor;if(!e||e.step!==3||!e.title.trim()||!e.compiledCode||e.error)return;
 const p={id:'work-'+crypto.randomUUID(),title:e.title.trim(),description:e.description.trim(),tags:parseTags(e.tags),category:e.category,code:e.compiledCode,author:'ranger',name:'Рейнджер',avatar:'R',color:'#c4a2ff',likes:0,saves:0,comments:[],own:true,createdAt:Date.now()};
 state.posts.unshift(p);const durable=persist();
 try{localStorage.removeItem('shader-gallery-draft');}catch{}
 editor=null;closeModal(false);page='feed';category='Все';query='';sort='new';setHash('feed');shell();window.scrollTo({top:0,behavior:'smooth'});
 toast(durable?'Работа опубликована! Она уже в твоей ленте.':'Работа в ленте, но память браузера заполнена: она не переживёт перезагрузку.');
}
async function handleAction(b){
 const action=b.dataset.action,id=b.dataset.id;
 if(action==='open')openPost(id);
 if(action==='create')openEditor();
 if(action==='close-modal')closeModal();
 if(action==='category'){category=b.dataset.category;$$('.chip').forEach(c=>{c.classList.toggle('active',c.dataset.category===category);c.setAttribute('aria-pressed',c.dataset.category===category);});renderGallery();}
 if(action==='sort'){sort=b.dataset.sort;$$('[data-action="sort"]').forEach(c=>{c.classList.toggle('active',c.dataset.sort===sort);c.setAttribute('aria-pressed',c.dataset.sort===sort);});renderGallery();}
 if(action==='reset-filters'){query='';category='Все';renderPage();$('#global-search').value='';}
 if(action==='go-feed')location.hash='feed';
 if(action==='like'||action==='save'){
  const arr=state[action==='like'?'likes':'saved'],index=arr.indexOf(id);if(index>=0)arr.splice(index,1);else arr.push(id);persist();updateReactions(id);
  if(action==='save'){toast(index>=0?'Работа удалена из коллекции':'Сохранено в твою коллекцию');if(page==='saved')renderGallery();}
 }
 if(action==='follow'){const a=b.dataset.author,index=state.following.indexOf(a);if(index>=0)state.following.splice(index,1);else state.following.push(a);persist();b.textContent=index>=0?'Подписаться':'Подписка ✓';toast(index>=0?'Подписка отменена':'Автор добавлен в твои подписки');}
 if(action==='detail-tab')renderDetailTab(b.dataset.tab);
 if(action==='copy-code'){try{await navigator.clipboard.writeText(detailPost.code);toast('GLSL-код скопирован');}catch{toast('Копирование недоступно. Выдели код и скопируй вручную.');}}
 if(action==='remix')openEditor(findPost(id));
 if(action==='pause'){const v=modalViews[0];if(v){v.paused=!v.paused;b.innerHTML=icon(v.paused?'play':'pause');b.setAttribute('aria-label',v.paused?'Продолжить анимацию':'Приостановить анимацию');}}
 if(action==='fullscreen'){
  try{if(document.fullscreenElement)await document.exitFullscreen();else if($('#detail-art').requestFullscreen)await $('#detail-art').requestFullscreen();else toast('Полный экран не поддерживается этим браузером.');}catch{toast('Браузер не разрешил полный экран.');}
 }
 if(action==='compile')compileEditor();
 if(action==='editor-next')nextEditor();
 if(action==='editor-back'){editor.step--;saveDraft();renderEditor();}
 if(action==='publish')publish();
}
document.addEventListener('click',event=>{const b=event.target.closest('[data-action]');if(b&&!b.disabled)handleAction(b);});
document.addEventListener('keydown',event=>{
 if(!document.body.classList.contains('dialog-open'))return;
 if(event.key==='Escape'&&!document.fullscreenElement){event.preventDefault();closeModal();return;}
 if(event.key==='Tab'&&event.target.id!=='shader-code'){
  const focusable=$$('button:not(:disabled),[href],input,select,textarea,summary,[tabindex="0"]',$('.modal')).filter(el=>el.offsetParent!==null);
  const first=focusable[0],last=focusable.at(-1);
  if(event.shiftKey&&(document.activeElement===first||document.activeElement===$('.modal'))){event.preventDefault();last?.focus();}
  else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
 }
});
function route(){
 const hash=location.hash.slice(1)||'feed';
 if(hash.startsWith('work/')){if(!$('#main-content'))shell();previousHash=page;openPost(hash.slice(5),false);return;}
 if(hash==='create'){if(!$('#main-content'))shell();openEditor();return;}
 if(document.body.classList.contains('dialog-open'))closeModal(false);
 page=['feed','explore','saved','profile'].includes(hash)?hash:'feed';category='Все';query='';shell();window.scrollTo(0,0);
}
window.addEventListener('hashchange',route);
window.addEventListener('beforeunload',()=>{if(editor)saveDraft();});
route();
