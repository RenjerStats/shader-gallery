import {PGlite} from '@electric-sql/pglite';
import {readFile} from 'node:fs/promises';
import {createStore} from '../supabase/functions/_shared/gallery-core.mjs';

const schema = await readFile(new URL('./schema.sql', import.meta.url), 'utf8');
const dnaSchema = await readFile(new URL('./dna-schema.sql', import.meta.url), 'utf8');

export async function createDatabase({memory=false,path='data/pglite',dna}={}) {
  const db = new PGlite(memory ? undefined : path);
  await db.waitReady;
  await db.exec(schema);
  await db.exec(dnaSchema);
  return createStore(db,{dna});
}
