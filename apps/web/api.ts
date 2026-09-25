import {createClient} from '@supabase/supabase-js';
import type {User} from './types';
const url=import.meta.env.VITE_SUPABASE_URL as string|undefined;
const key=import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string|undefined;
const config=url&&key?{mode:'supabase' as const,url,key}:await fetch('/api/config').then(r=>{if(!r.ok)throw new Error('Сервер недоступен');return r.json()}) as {mode:'local'|'supabase';url?:string;key?:string};
export const authMode=config.mode;
const supabase=config.mode==='supabase'?createClient(config.url!,config.key!):null;
async function request<T>(url:string,body?:unknown):Promise<T>{
 const res=await fetch(url,{method:body===undefined?'GET':'POST',credentials:'same-origin',headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 const result=await res.json();if(!res.ok||result.error)throw new Error(result.error||'Ошибка запроса');return result.data as T;
}
export async function rpc<T>(action:string,payload:object={}):Promise<T>{
 if(!supabase)return request('/api/rpc',{action,payload});
 const {data,error}=await supabase.functions.invoke('gallery',{body:{action,payload}});if(error)throw new Error(error.message);if(data?.error)throw new Error(data.error);return data.data as T;
}
export async function getSession():Promise<User|null>{if(!supabase)return request('/api/auth/session');const {data,error}=await supabase.auth.getUser();if(error)return null;return data.user?{id:data.user.id,email:data.user.email}:null;}
export async function signIn(email:string,password:string):Promise<User|null>{if(!supabase)return request('/api/auth/login',{email,password});const {data,error}=await supabase.auth.signInWithPassword({email,password});if(error)throw new Error(error.message);return data.user;}
export async function signUp(email:string,password:string,displayName:string):Promise<User|null>{if(!supabase)return request('/api/auth/signup',{email,password,display_name:displayName});const {data,error}=await supabase.auth.signUp({email,password,options:{data:{display_name:displayName},emailRedirectTo:location.origin+import.meta.env.BASE_URL}});if(error)throw new Error(error.message);return data.session?data.user:null;}
export async function signOut():Promise<void>{if(!supabase){await request('/api/auth/logout',{});return;}const {error}=await supabase.auth.signOut();if(error)throw new Error(error.message);}
export async function signInGoogle():Promise<void>{if(!supabase)throw new Error('Google OAuth доступен после настройки Supabase');const {error}=await supabase.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin+import.meta.env.BASE_URL}});if(error)throw new Error(error.message);}
