import http from 'node:http';
import {readFile, stat, mkdir} from 'node:fs/promises';
import {join, extname, resolve, sep} from 'node:path';
import {gzipSync, brotliCompressSync, constants as zlib} from 'node:zlib';
import {createHash} from 'node:crypto';
import {createServer as createViteServer} from 'vite';
import {createDatabase} from './db.mjs';
import {makePackage} from './package.mjs';

const production=process.argv.includes('--production');
const port=Number(process.env.PORT)||4173;
await mkdir('data',{recursive:true});
const store=await createDatabase({path:process.env.DATA_DIR||'data/pglite',dna:{apiKey:process.env.OPENROUTER_API_KEY,
  models:process.env.DNA_MODELS?.split(',').map(s=>s.trim())}});
await store.dna.recover();
const dnaRecovery=setInterval(()=>store.dna.recover().catch(()=>console.error('DNA recovery failed')),30000);
dnaRecovery.unref();
const vite=production?null:await createViteServer({configFile:'vite.config.ts',server:{middlewareMode:true},appType:'custom'});
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'};
const cookies=req=>Object.fromEntries((req.headers.cookie||'').split(';').map(x=>x.trim().split('=').map(decodeURIComponent)).filter(x=>x.length===2));
const compressible=/^(text\/|application\/json|image\/svg|text\/javascript)/;
const accepted=(req,coding)=>new RegExp(`(^|,\s*)${coding}(\s*[;,]|$)`).test(String(req.headers['accept-encoding']||''));
/** Sends [content] compressed when the client allows it. Static files memoize the compressed copy in [memo]. */
function send(req,res,status,headers,content,memo){
  const body=typeof content==='string'?Buffer.from(content):content;
  const type=String(headers['content-type']||'');
  let coding=null,out=body;
  if(body.length>1024&&compressible.test(type)){
    coding=accepted(req,'br')?'br':accepted(req,'gzip')?'gzip':null;
    if(coding){
      const cached=memo?.get(coding);
      out=cached||(coding==='br'?brotliCompressSync(body,{params:{[zlib.BROTLI_PARAM_QUALITY]:memo?9:4}}):gzipSync(body));
      if(!cached&&memo)memo.set(coding,out);
    }
  }
  res.writeHead(status,{...headers,vary:'Accept-Encoding',...(coding?{'content-encoding':coding}:{}),'content-length':out.length});
  res.end(req.method==='HEAD'?undefined:out);
}
const json=(res,status,data,req)=>{
  const headers={'content-type':'application/json; charset=utf-8','cache-control':'no-store'};
  if(req)send(req,res,status,headers,JSON.stringify(data));else{res.writeHead(status,headers);res.end(JSON.stringify(data));}
};
const strongEtag=content=>`"${createHash('sha256').update(content).digest('base64url').slice(0,22)}"`;
const fileCache=new Map();
const tokenCookie=(token)=>`sg_session=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${token?60*60*24*30:0}${production?'; Secure':''}`;
const body=async req=>{let s='';for await(const chunk of req){s+=chunk;if(s.length>1100000)throw new Error('Запрос слишком большой');}try{return JSON.parse(s||'{}');}catch{throw new Error('Некорректный JSON');}};
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const origin=req=>process.env.PUBLIC_ORIGIN||`${production?'https':'http'}://${req.headers.host||'localhost:4173'}`;
const authAttempts=new Map();
function checkAuthRate(req){const key=req.socket.remoteAddress||'unknown',now=Date.now();const recent=(authAttempts.get(key)||[]).filter(t=>now-t<15*60*1000);if(recent.length>=10)throw new Error('Слишком много попыток. Повторите позже.');recent.push(now);authAttempts.set(key,recent);}

const server=http.createServer(async(req,res)=>{
  try {
    const url=new URL(req.url||'/',origin(req));
    if(url.pathname.startsWith('/api/')){
      if(req.method==='POST'){
        const requestOrigin=req.headers.origin;
        if(requestOrigin && requestOrigin!==origin(req)){json(res,403,{error:'Недопустимый источник запроса'});return;}
      }
      const token=cookies(req).sg_session;
      const user=await store.sessionUser(token);
      if(url.pathname==='/api/config'&&req.method==='GET'){json(res,200,{mode:'local'},req);return;}
      if(url.pathname==='/api/auth/session'&&req.method==='GET'){json(res,200,{data:user});return;}
      if(url.pathname==='/api/auth/signup'&&req.method==='POST'){
        checkAuthRate(req);
        const p=await body(req);const created=await store.addUser({email:p.email,password:p.password,display_name:p.display_name});
        res.setHeader('set-cookie',tokenCookie(await store.createSession(created.id)));json(res,200,{data:created});return;
      }
      if(url.pathname==='/api/auth/login'&&req.method==='POST'){
        checkAuthRate(req);
        const p=await body(req);const signed=await store.authenticate(p.email,p.password);
        res.setHeader('set-cookie',tokenCookie(await store.createSession(signed.id)));json(res,200,{data:signed});return;
      }
      if(url.pathname==='/api/auth/logout'&&req.method==='POST'){
        await store.revokeSession(token);res.setHeader('set-cookie',tokenCookie(''));json(res,200,{data:null});return;
      }
      if(url.pathname==='/api/rpc'&&req.method==='POST'){
        const p=await body(req);if(typeof p.action!=='string'||!p.payload||typeof p.payload!=='object'){json(res,400,{error:'Некорректный запрос'});return;}
        const data=await store.rpc(user?.id,p.action,p.payload);json(res,200,{data},req);return;
      }
      const previewMatch=url.pathname.match(/^\/api\/preview\/([0-9a-f-]{36})$/i);
      if(previewMatch && (req.method==='GET'||req.method==='HEAD')){
        const image=await store.previewImage(previewMatch[1]);
        if(!image){res.writeHead(404,{'cache-control':'public, max-age=60'});res.end();return;}
        // A revision never changes, so its preview can be kept forever.
        const headers={'content-type':image.type,'cache-control':'public, max-age=31536000, immutable',etag:image.etag,'cross-origin-resource-policy':'same-origin'};
        if(req.headers['if-none-match']===image.etag){res.writeHead(304,headers);res.end();return;}
        res.writeHead(200,{...headers,'content-length':image.bytes.length});res.end(req.method==='HEAD'?undefined:image.bytes);return;
      }
      const packageMatch=url.pathname.match(/^\/api\/packages\/([0-9a-f-]{36})\/([0-9a-f-]{36})$/i);
      if(packageMatch && req.method==='GET'){
        const detail=await store.rpc(null,'work',{id:packageMatch[1],revision_id:packageMatch[2],lite:true});
        const text=JSON.stringify({data:makePackage(detail,origin(req))}),etag=strongEtag(text);
        const headers={'content-type':'application/json; charset=utf-8','cache-control':'public, max-age=31536000, immutable',etag};
        if(req.headers['if-none-match']===etag){res.writeHead(304,headers);res.end();return;}
        send(req,res,200,headers,text);return;
      }
      json(res,404,{error:'Не найдено'});return;
    }
    if(url.pathname.startsWith('/og/')){
      const id=url.pathname.slice(4).replace(/\.(png|jpg|webp)$/,'');
      const revision=url.searchParams.get('revision');
      const detail=await store.rpc(null,'work',{id,revision_id:revision||undefined});
      const preview=detail.work.revision.preview;
      if(preview){res.writeHead(200,{'content-type':preview.startsWith('data:image/webp;')?'image/webp':preview.startsWith('data:image/jpeg;')?'image/jpeg':'image/png','cache-control':revision?'public, max-age=31536000, immutable':'public, max-age=300'});res.end(Buffer.from(preview.split(',')[1],'base64'));return;}
      const fallback=await readFile(production?'build/hero-opal-ribbon.png':'apps/web/public/hero-opal-ribbon.png');
      res.writeHead(200,{'content-type':'image/png','cache-control':'public, max-age=300'});res.end(fallback);return;
    }
    if(production){
      const root=resolve('build');
      let file=resolve(root,'.'+decodeURIComponent(url.pathname));
      if(file!==root&&!file.startsWith(root+sep)){res.writeHead(403);res.end();return;}
      try{if((await stat(file)).isDirectory())file=join(file,'index.html');}catch{file=join(root,'index.html');}
      if(extname(file)==='.html'){
        let html=await readFile(join(root,'index.html'),'utf8');
        const match=url.pathname.match(/^\/works\/([0-9a-f-]{36})$/i);
        if(match){try{const revision=url.searchParams.get('revision');const d=await store.rpc(null,'work',{id:match[1],revision_id:revision||undefined});const title=`${d.work.title} — Shader Gallery`;const description=d.work.description||'Живое цифровое искусство';const extension=d.work.revision.preview?.startsWith('data:image/webp;')?'webp':d.work.revision.preview?.startsWith('data:image/jpeg;')?'jpg':'png';const imageUrl=`${origin(req)}/og/${d.work.id}.${extension}?revision=${d.work.revision.id}`;html=html.replace('</head>',`<meta property="og:type" content="article"><meta property="og:url" content="${esc(origin(req)+url.pathname+url.search)}"><meta property="og:title" content="${esc(title)}"><meta property="og:description" content="${esc(description)}"><meta property="og:image" content="${esc(imageUrl)}"></head>`);}catch{}}
        const etag=strongEtag(html),headers={'content-type':'text/html; charset=utf-8','cache-control':'no-cache',etag};
        if(req.headers['if-none-match']===etag){res.writeHead(304,headers);res.end();return;}
        send(req,res,200,headers,html);return;
      }
      try{
        // Vite emits content-hashed names under /assets/, so those never change; everything else is revalidated daily.
        const info=await stat(file),etag=`W/"${info.size.toString(16)}-${Math.floor(info.mtimeMs).toString(16)}"`;
        const headers={'content-type':mime[extname(file)]||'application/octet-stream','cache-control':url.pathname.startsWith('/assets/')?'public, max-age=31536000, immutable':'public, max-age=86400',etag};
        if(req.headers['if-none-match']===etag){res.writeHead(304,headers);res.end();return;}
        let memo=fileCache.get(file);if(!memo||memo.etag!==etag){memo={etag,content:await readFile(file),encoded:new Map()};fileCache.set(file,memo);}
        send(req,res,200,headers,memo.content,memo.encoded);
      }catch{res.writeHead(404);res.end();}return;
    }
    vite.middlewares(req,res,async()=>{
      try{
        if(!extname(url.pathname)){
          const template=await readFile('apps/web/index.html','utf8');
          const html=await vite.transformIndexHtml(url.pathname,template);
          res.writeHead(200,{'content-type':'text/html; charset=utf-8'});res.end(html);return;
        }
        res.writeHead(404);res.end();
      }catch(error){res.writeHead(500);res.end(error instanceof Error?error.message:'Ошибка сервера')}
    });
  }catch(error){const message=error instanceof Error?error.message:'Ошибка сервера';const status=/duplicate key|unique constraint/.test(message)?409:400;json(res,status,{error:/duplicate key|unique constraint/.test(message)?'Такая запись уже существует':message});}
});
server.listen(port,production?'0.0.0.0':'127.0.0.1',()=>console.log(`Shader Gallery: http://localhost:${port}`));
