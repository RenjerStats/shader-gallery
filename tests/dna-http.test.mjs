import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {createServer} from 'node:net';
import {randomUUID} from 'node:crypto';

test('HTTP DNA workflow: auth, two results, private drafts, public packages, partial failures and CSRF',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'shader-dna-http-'));
  const port=await new Promise((resolve,reject)=>{const server=createServer();server.once('error',reject);server.listen(0,'127.0.0.1',()=>{const port=server.address().port;server.close(()=>resolve(port))})});
  const url=`http://127.0.0.1:${port}`;
  const child=spawn(process.execPath,['--import','./tests/fixtures/dna-provider.mjs','server/index.mjs','--production'],{
    cwd:resolve(import.meta.dirname,'..'),env:{...process.env,PORT:String(port),DATA_DIR:join(dir,'db'),PUBLIC_ORIGIN:url,OPENROUTER_API_KEY:'local-test-key',DNA_MODELS:'google/gemini-2.5-flash-lite,qwen/qwen3-30b-a3b-instruct-2507'},stdio:'ignore'});
  const call=async(action,payload={},cookie='',origin=url)=>{
    const response=await fetch(`${url}/api/rpc`,{method:'POST',headers:{'content-type':'application/json',cookie,origin},body:JSON.stringify({action,payload})});
    return {status:response.status,...await response.json()};
  };
  const signup=async(email)=>{
    const response=await fetch(`${url}/api/auth/signup`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email,password:'test-password',display_name:'Тест'})});
    assert.equal(response.status,200);return response.headers.get('set-cookie').split(';')[0];
  };
  async function complete(id,cookie){for(let i=0;i<50;i++){const r=await call('dna_get',{id},cookie);assert.equal(r.status,200);if(r.data.status!=='running')return r.data;await new Promise(r=>setTimeout(r,20))}throw new Error('Generation did not complete');}
  try {
    let ready=false;
    for(let i=0;i<100;i++){if(child.exitCode!==null)throw new Error('HTTP server exited');try{if((await fetch(`${url}/api/config`)).ok){ready=true;break}}catch{}await new Promise(r=>setTimeout(r,100))}
    assert.ok(ready,'server started');
    const a=await signup('dna-a@example.test'),b=await signup('dna-b@example.test');
    const config=await call('dna_config');assert.equal(config.data.enabled,true);assert.ok(!JSON.stringify(config).includes('local-test-key'));
    const input={request_id:randomUUID(),prompt:'Свет',controls:'',reference_ids:[]};
    assert.equal((await call('dna_create',input)).status,400);
    assert.equal((await call('dna_create',input,a,'https://foreign.example')).status,403);
    const [first,second]=await Promise.all([call('dna_create',input,a),call('dna_create',input,a)]);
    assert.equal(first.status,200);assert.equal(second.data.id,first.data.id);
    const job=await complete(first.data.id,a);assert.equal(job.status,'ready');assert.equal(job.variants.length,2);assert.ok(job.variants.every(v=>v.result.code.includes('mainImage')&&v.attempts===1));
    assert.equal((await call('dna_get',{id:job.id},b)).status,400);
    assert.equal((await call('dna_list',{},b)).data.items.length,0);
    const variant_id=job.variants[0].id;
    const saved=await call('dna_save',{variant_id},a);assert.equal(saved.status,200);assert.equal((await call('dna_save',{variant_id},a)).data.draft_id,saved.data.draft_id);
    const preview='data:image/jpeg;base64,/9j/2Q==';
    const publication=await call('dna_publish',{variant_id,preview},a);assert.equal(publication.status,200);
    assert.deepEqual((await call('dna_publish',{variant_id,preview},a)).data,publication.data);
    const pkg=await fetch(`${url}/api/packages/${publication.data.work_id}/${publication.data.revision_id}`);assert.equal(pkg.status,200);
    const packageData=(await pkg.json()).data;assert.equal(packageData.dnaOrigin.variant_id,variant_id);assert.equal(packageData.code,job.variants[0].result.code);
    const partial=await call('dna_create',{...input,request_id:randomUUID(),prompt:'partial-failure'},a);
    const failed=await complete(partial.data.id,a);assert.equal(failed.status,'partial');assert.equal(failed.variants[0].status,'ready');assert.equal(failed.variants[1].status,'failed');assert.ok(!JSON.stringify(failed).includes('private upstream'));
  } finally {
    const exit=new Promise(resolve=>child.once('exit',resolve));if(child.exitCode===null){child.kill();await exit;}
    await rm(dir,{recursive:true,force:true});
  }
});
