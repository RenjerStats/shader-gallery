import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createDatabase} from '../server/db.mjs';

test('backup restores profiles into a new database directory',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'gallery-backup-'));
  const source=join(dir,'source'),archive=join(dir,'backup.tar.gz'),restored=join(dir,'restored');
  try{
    const db=await createDatabase({path:source});
    const user=await db.addUser({email:'restore@example.test'});
    await db.close();
    const root=resolve(import.meta.dirname,'..');
    const backup=spawnSync(process.execPath,['server/backup.mjs','backup',source,archive],{cwd:root,encoding:'utf8'});
    assert.equal(backup.status,0,backup.stderr);
    const restore=spawnSync(process.execPath,['server/backup.mjs','restore',archive,restored],{cwd:root,encoding:'utf8'});
    assert.equal(restore.status,0,restore.stderr);
    const copy=await createDatabase({path:restored});
    assert.equal((await copy.rpc(null,'profile',{id:user.id})).id,user.id);
    await copy.close();
  }finally{await rm(dir,{recursive:true,force:true})}
});
