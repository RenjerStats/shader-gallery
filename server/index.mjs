import http from 'node:http';
import {readFile, stat, mkdir} from 'node:fs/promises';
import {join, extname, resolve, sep} from 'node:path';
import {createServer as createViteServer} from 'vite';
import {createDatabase} from './db.mjs';
import {makePackage} from './package.mjs';

const production=process.argv.includes('--production');
const port=Number(process.env.PORT)||4173;
await mkdir('data',{recursive:true});
const store=await createDatabase({path:process.env.DATA_DIR||'data/pglite'});
const vite=production?null:await createViteServer({configFile:'vite.config.ts',server:{middlewareMode:true},appType:'custom'});
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'};
const cookies=req=>Object.fromEntries((req.headers.cookie||'').split(';').map(x=>x.trim().split('=').map(decodeURIComponent)).filter(x=>x.length===2));
const json=(res,status,data)=>{res.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store'});res.end(JSON.stringify(data));};
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
      if(url.pathname==='/api/config'&&req.method==='GET'){json(res,200,{mode:'local'});return;}
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
        const data=await store.rpc(user?.id,p.action,p.payload);json(res,200,{data});return;
      }
      const packageMatch=url.pathname.match(/^\/api\/packages\/([0-9a-f-]{36})\/([0-9a-f-]{36})$/i);
      if(packageMatch && req.method==='GET'){
        const detail=await store.rpc(null,'work',{id:packageMatch[1],revision_id:packageMatch[2]});
        json(res,200,{data:makePackage(detail,origin(req))});return;
      }
      json(res,404,{error:'Не найдено'});return;
    }
    if(url.pathname.startsWith('/og/')){
      const id=url.pathname.slice(4).replace(/\.png$/,'');
      const revision=url.searchParams.get('revision');
      const detail=await store.rpc(null,'work',{id,revision_id:revision||undefined});
      const preview=detail.work.revision.preview;
      if(preview){res.writeHead(200,{'content-type':'image/png','cache-control':'public, max-age=300'});res.end(Buffer.from(preview.split(',')[1],'base64'));return;}
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
        if(match){try{const revision=url.searchParams.get('revision');const d=await store.rpc(null,'work',{id:match[1],revision_id:revision||undefined});const title=`${d.work.title} — Shader Gallery`;const description=d.work.description||'Живое цифровое искусство';const imageUrl=`${origin(req)}/og/${d.work.id}.png?revision=${d.work.revision.id}`;html=html.replace('</head>',`<meta property="og:type" content="article"><meta property="og:url" content="${esc(origin(req)+url.pathname+url.search)}"><meta property="og:title" content="${esc(title)}"><meta property="og:description" content="${esc(description)}"><meta property="og:image" content="${esc(imageUrl)}"></head>`);}catch{}}
        res.writeHead(200,{'content-type':'text/html; charset=utf-8'});res.end(html);return;
      }
      try{const content=await readFile(file);res.writeHead(200,{'content-type':mime[extname(file)]||'application/octet-stream'});res.end(content);}catch{res.writeHead(404);res.end();}return;
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
