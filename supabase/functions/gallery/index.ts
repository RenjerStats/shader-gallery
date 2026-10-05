import postgres from 'npm:postgres@3.4.7';
import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
import {createStore} from '../_shared/gallery-core.mjs';

const dbUrl=Deno.env.get('SUPABASE_DB_URL');
const supabaseUrl=Deno.env.get('SUPABASE_URL');
const publishableKey=Deno.env.get('SUPABASE_PUBLISHABLE_KEY')||Deno.env.get('SUPABASE_ANON_KEY');
if(!dbUrl||!supabaseUrl||!publishableKey)throw new Error('Supabase configuration is missing');

const sql=postgres(dbUrl,{prepare:false,max:3,connect_timeout:10});
type DatabaseAdapter={
  query:(query:string,args?:postgres.ParameterOrJSON<never>[])=>Promise<{rows:Record<string,unknown>[]}>,
  transaction:<T>(run:(tx:DatabaseAdapter)=>Promise<T>)=>Promise<T>
};
declare const EdgeRuntime:{waitUntil(task:Promise<unknown>):void};
const adapter=(client:typeof sql):DatabaseAdapter=>({
  query:async (query,args=[])=>({rows:await client.unsafe(query,args)}),
  transaction:async <T>(run:(tx:DatabaseAdapter)=>Promise<T>)=>client.begin(async tx=>run(adapter(tx as typeof sql))) as Promise<T>
});
const SITE='https://renjerstats.github.io/shader-gallery';
const store=createStore(adapter(sql),{origin:SITE,dna:{apiKey:Deno.env.get('OPENROUTER_API_KEY'),
  models:Deno.env.get('DNA_MODELS')?.split(',').map(s=>s.trim()),
  schedule:(task:Promise<unknown>)=>EdgeRuntime.waitUntil(task)}});
const auth=createClient(supabaseUrl,publishableKey,{auth:{persistSession:false,autoRefreshToken:false}});
// Verifying a token costs a round trip to the auth service; a revoked session may live this long.
const tokenCache=new Map<string,{id:string;until:number}>();
const TOKEN_TTL=30_000;
async function userFor(bearer:string):Promise<string|null>{
  const hit=tokenCache.get(bearer);
  if(hit&&hit.until>Date.now())return hit.id;
  const {data,error}=await auth.auth.getUser(bearer);
  if(error||!data.user)return null;
  if(tokenCache.size>=200)tokenCache.delete(tokenCache.keys().next().value!);
  tokenCache.set(bearer,{id:data.user.id,until:Date.now()+TOKEN_TTL});
  return data.user.id;
}
const allowed=new Set(['https://renjerstats.github.io','http://localhost:4173','http://127.0.0.1:4173']);

Deno.serve(async request=>{
  const origin=request.headers.get('origin');
  const cors={
    'Access-Control-Allow-Origin':origin&&allowed.has(origin)?origin:'https://renjerstats.github.io',
    'Access-Control-Allow-Headers':'authorization, apikey, content-type, x-client-info',
    'Access-Control-Allow-Methods':'GET, POST, OPTIONS',
    'Vary':'Origin'
  };
  const respond=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{...cors,'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'}});
  if(request.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
  if(request.method==='GET'){
    const match=new URL(request.url).pathname.match(/\/preview\/([0-9a-f-]{36})$/i);
    if(!match)return respond(404,{error:'Не найдено'});
    // Previews are public and immutable per revision: let browsers, apps and the CDN keep them for a year.
    const publicHeaders={'Access-Control-Allow-Origin':'*','Cross-Origin-Resource-Policy':'cross-origin','Cache-Control':'public, max-age=31536000, immutable'};
    try{
      const image=await store.previewImage(match[1]);
      if(!image)return new Response(null,{status:404,headers:{...publicHeaders,'Cache-Control':'public, max-age=60'}});
      if(request.headers.get('if-none-match')===image.etag)return new Response(null,{status:304,headers:{...publicHeaders,ETag:image.etag}});
      return new Response(image.bytes,{status:200,headers:{...publicHeaders,ETag:image.etag,'Content-Type':image.type,'Content-Length':String(image.bytes.length)}});
    }catch(error){console.error('Preview request failed',error);return new Response(null,{status:500,headers:{'Access-Control-Allow-Origin':'*','Cache-Control':'no-store'}});}
  }
  if(request.method!=='POST')return respond(405,{error:'Метод не поддерживается'});
  if(origin&&!allowed.has(origin))return respond(403,{error:'Недопустимый источник запроса'});
  try{
    const raw=await request.text();
    if(raw.length>1_100_000)return respond(413,{error:'Запрос слишком большой'});
    const body=JSON.parse(raw);
    if(typeof body?.action!=='string'||!body.payload||typeof body.payload!=='object'||Array.isArray(body.payload))return respond(400,{error:'Некорректный запрос'});
    const bearer=request.headers.get('authorization')?.match(/^Bearer (.+)$/i)?.[1];
    let userId:string|null=null;
    if(bearer&&!bearer.startsWith('sb_publishable_')&&bearer!==publishableKey){
      userId=await userFor(bearer);
      if(!userId)return respond(401,{error:'Нужно войти в аккаунт'});
    }
    const data=await store.rpc(userId,body.action,body.payload);
    return respond(200,{data});
  }catch(error){
    console.error('Gallery request failed',error);
    const message=error instanceof Error?error.message:'Ошибка сервера';
    return respond(/duplicate key|unique constraint/.test(message)?409:400,{error:/duplicate key|unique constraint/.test(message)?'Такая запись уже существует':message});
  }
});
