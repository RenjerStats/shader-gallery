import {PGlite} from '@electric-sql/pglite';
import {mkdir,writeFile,readFile,stat} from 'node:fs/promises';
import {dirname,resolve} from 'node:path';
import {createHash} from 'node:crypto';

const [mode,source,target]=process.argv.slice(2);
if(!['backup','restore'].includes(mode)||!source||!target){console.error('Usage: node server/backup.mjs backup DATA_DIR ARCHIVE.tar.gz | restore ARCHIVE.tar.gz NEW_DATA_DIR');process.exit(2)}
if(mode==='backup'){
  const db=new PGlite(resolve(source));await db.waitReady;
  try{
    const blob=await db.dumpDataDir('gzip');const bytes=Buffer.from(await blob.arrayBuffer());
    await mkdir(dirname(resolve(target)),{recursive:true});await writeFile(target,bytes,{flag:'wx'});
    await writeFile(`${target}.sha256`,createHash('sha256').update(bytes).digest('hex'),{flag:'wx'});
    console.log(`Backup saved: ${target} (${bytes.length} bytes)`);
  }finally{await db.close()}
}else{
  try{await stat(resolve(target));throw new Error('Target data directory already exists; choose a new path')}catch(e){if(e.code!=='ENOENT')throw e}
  const bytes=await readFile(source);
  const expected=(await readFile(`${source}.sha256`,'utf8')).trim();
  if(createHash('sha256').update(bytes).digest('hex')!==expected)throw new Error('Backup checksum mismatch');
  await mkdir(dirname(resolve(target)),{recursive:true});
  const db=new PGlite(resolve(target),{loadDataDir:new Blob([bytes])});
  await db.waitReady;await db.close();console.log(`Restored into: ${target}`);
}
