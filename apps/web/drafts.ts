import {openDB} from 'idb';
import type {Draft} from './types';
export type LocalDraft=Draft;
const database=openDB('shader-gallery-drafts',1,{upgrade(db){db.createObjectStore('drafts',{keyPath:'key'});}});
export async function saveLocalDraft(ownerId:string,draft:LocalDraft){const db=await database;await db.put('drafts',{key:`${ownerId}:${draft.id}`,ownerId,draft:structuredClone(draft)});}
export async function listLocalDrafts(ownerId:string):Promise<LocalDraft[]>{const db=await database;const rows=await db.getAll('drafts');return rows.filter(r=>r.ownerId===ownerId).map(r=>r.draft).sort((a,b)=>b.updated_at.localeCompare(a.updated_at));}
export async function deleteLocalDraft(ownerId:string,id:string){const db=await database;await db.delete('drafts',`${ownerId}:${id}`);}
