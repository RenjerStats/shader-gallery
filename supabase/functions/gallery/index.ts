import postgres from 'npm:postgres@3.4.7';
import {createClient} from 'npm:@supabase/supabase-js@2.57.4';
import {createStore} from '../_shared/gallery-core.mjs';
import {makePackage} from '../_shared/package.mjs';

const dbUrl=Deno.env.get('SUPABASE_DB_URL');
const supabaseUrl=Deno.env.get('SUPABASE_URL');
const publishableKey=Deno.env.get('SUPABASE_PUBLISHABLE_KEY')||Deno.env.get('SUPABASE_ANON_KEY');
if(!dbUrl||!supabaseUrl||!publishableKey)throw new Error('Supabase configuration is missing');

const sql=postgres(dbUrl,{prepare:false,max:3,connect_timeout:10});
const adapter=(client:typeof sql)=>({
  query:async (query:string,args:unknown[]=[])=>({rows:await client.unsafe(query,args)}),
  transaction:async <T>(run:(tx:ReturnType<typeof adapter>)=>Promise<T>)=>client.begin(async tx=>run(adapter(tx as typeof sql))) as Promise<T>
});
const store=createStore(adapter(sql));
const auth=createClient(supabaseUrl,publishableKey,{auth:{persistSession:false,autoRefreshToken:false}});
const allowed=new Set(['https://renjerstats.github.io','http://localhost:4173','http://127.0.0.1:4173']);

Deno.serve(async request=>{
  const origin=request.headers.get('origin');
  const cors={
    'Access-Control-Allow-Origin':origin&&allowed.has(origin)?origin:'https://renjerstats.github.io',
    'Access-Control-Allow-Headers':'authorization, apikey, content-type, x-client-info',
    'Access-Control-Allow-Methods':'POST, OPTIONS',
    'Vary':'Origin'
  };
  const respond=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{...cors,'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'}});
  if(request.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
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
      const {data,error}=await auth.auth.getUser(bearer);
      if(error||!data.user)return respond(401,{error:'Нужно войти в аккаунт'});
      userId=data.user.id;
    }
    const data=body.action==='package'
      ?makePackage(await store.rpc(null,'work',{id:body.payload.id,revision_id:body.payload.revision_id}),
        'https://renjerstats.github.io/shader-gallery')
      :await store.rpc(userId,body.action,body.payload);
    return respond(200,{data});
  }catch(error){
    console.error('Gallery request failed',error);
    const message=error instanceof Error?error.message:'Ошибка сервера';
    return respond(/duplicate key|unique constraint/.test(message)?409:400,{error:/duplicate key|unique constraint/.test(message)?'Такая запись уже существует':message});
  }
});
