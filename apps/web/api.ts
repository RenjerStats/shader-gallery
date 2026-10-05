import {createClient} from '@supabase/supabase-js';
import type {User} from './types';
const url=import.meta.env.VITE_SUPABASE_URL as string|undefined;
const key=import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string|undefined;
const config=url&&key?{mode:'supabase' as const,url,key}:await fetch('/api/config').then(r=>{if(!r.ok)throw new Error('Сервер недоступен');return r.json()}) as {mode:'local'|'supabase';url?:string;key?:string};
export const authMode=config.mode;
const supabase=config.mode==='supabase'?createClient(config.url!,config.key!):null;
async function request<T>(url:string,body?:unknown):Promise<T>{
 const res=await fetch(url,{method:body===undefined?'GET':'POST',signal:AbortSignal.timeout(25000),credentials:'same-origin',headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 const result=await res.json();if(!res.ok||result.error)throw new Error(result.error||'Ошибка запроса');return result.data as T;
}
const previewBase=supabase?`${config.url}/functions/v1/gallery/preview/`:'/api/preview/';
/** Image address for a revision. Previews are immutable per revision, so the browser may cache them for good. */
export function previewSrc(revision:{id:string;preview?:string|null;has_preview?:boolean}):string|null{
 return revision.preview||(revision.has_preview?previewBase+revision.id:null);
}
// Reads are cheap to repeat while a person browses: share in-flight calls and keep results for a few seconds.
const READ_TTL=30_000,reads=new Map<string,{until:number;value:Promise<unknown>}>();
const readOnly=new Set(['feed','work','profile','draft_list','moderation_list']);
const cacheable=(action:string,payload:{mode?:string})=>action==='feed'&&(payload.mode===undefined||payload.mode==='new'||payload.mode==='curated');
async function call<T>(action:string,payload:object):Promise<T>{
 if(!supabase)return request('/api/rpc',{action,payload});
 const {data,error}=await supabase.functions.invoke('gallery',{body:{action,payload},timeout:25000});
 if(error){let message=error.message;try{const body=await error.context?.json();if(typeof body?.error==='string')message=body.error}catch{}throw new Error(message)}
 if(data?.error)throw new Error(data.error);return data.data as T;
}
export async function rpc<T>(action:string,payload:object={}):Promise<T>{
 // The page only needs preview bytes through previewSrc(), so keep them out of JSON.
 const body=action==='feed'||action==='work'?{lite:true,...payload}:payload;
 if(!cacheable(action,body)){
  const result=await call<T>(action,body);
  if(!readOnly.has(action)&&!action.startsWith('dna_'))reads.clear();
  return result;
 }
 const key=JSON.stringify([action,body]),hit=reads.get(key);
 if(hit&&hit.until>Date.now())return hit.value as Promise<T>;
 const value=call<T>(action,body);
 reads.set(key,{until:Date.now()+READ_TTL,value});
 value.catch(()=>{if(reads.get(key)?.value===value)reads.delete(key)});
 return value;
}
export const dropReadCache=()=>reads.clear();
/** Several reads in one round trip. Each slot resolves to its result or an Error. */
export async function rpcBatch<T extends unknown[]>(requests:{[K in keyof T]:{action:string;payload?:object}}):Promise<{[K in keyof T]:T[K]|Error}>{
 const prepared=(requests as {action:string;payload?:object}[]).map(r=>({action:r.action,payload:r.action==='feed'||r.action==='work'?{lite:true,...r.payload}:{...r.payload}}));
 try{
  const {results}=await call<{results:({data:unknown}|{error:string})[]}>('batch',{requests:prepared});
  return results.map(r=>'error' in r?new Error(r.error):r.data) as {[K in keyof T]:T[K]|Error};
 }catch{
  // Older deployments have no batch action: fall back to parallel single calls.
  return Promise.all(prepared.map(r=>rpc(r.action,r.payload).catch(e=>e instanceof Error?e:new Error('Ошибка запроса')))) as Promise<{[K in keyof T]:T[K]|Error}>;
 }
}
export async function getSession():Promise<User|null>{if(!supabase)return request('/api/auth/session');const {data,error}=await supabase.auth.getUser();if(error)return null;return data.user?{id:data.user.id,email:data.user.email}:null;}
export async function signIn(email:string,password:string):Promise<User|null>{if(!supabase)return request('/api/auth/login',{email,password});const {data,error}=await supabase.auth.signInWithPassword({email,password});if(error)throw new Error(error.message);return data.user;}
export async function signUp(email:string,password:string,displayName:string):Promise<User|null>{if(!supabase)return request('/api/auth/signup',{email,password,display_name:displayName});const {data,error}=await supabase.auth.signUp({email,password,options:{data:{display_name:displayName},emailRedirectTo:location.origin+import.meta.env.BASE_URL}});if(error)throw new Error(error.message);return data.session?data.user:null;}
export async function signOut():Promise<void>{if(!supabase){await request('/api/auth/logout',{});return;}const {error}=await supabase.auth.signOut();if(error)throw new Error(error.message);}
export async function signInGoogle():Promise<void>{if(!supabase)throw new Error('Google OAuth доступен после настройки Supabase');const {error}=await supabase.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin+import.meta.env.BASE_URL}});if(error)throw new Error(error.message);}
