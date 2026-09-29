import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,mkdtemp,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {createDatabase} from '../server/db.mjs';
import {createOpenRouterProvider,validateDnaResult,DnaError} from '../supabase/functions/_shared/dna-provider.mjs';

const result={title:'Тихий свет',description:'Волны света',code:'void mainImage(out vec4 c, in vec2 p) { c = vec4(p / iResolution.xy, speed * 0.2, 1.0); }',parameters:[{name:'speed',label:'Скорость',type:'float',min:0,max:2,default:1}]};
const request=()=>({request_id:randomUUID(),prompt:'Мягкие волны',controls:'Скорость',reference_ids:[]});
async function setup(provider=async()=>result){
  const tasks=[];
  const s=await createDatabase({memory:true,dna:{provider,schedule:p=>tasks.push(p)}});
  const a=await s.addUser({email:'dna-a@test.test'}),b=await s.addUser({email:'dna-b@test.test'});
  const drain=async()=>{while(tasks.length)await Promise.all(tasks.splice(0));};
  return {s,a,b,drain,close:async()=>{await drain();await s.close();}};
}
test('DNA creates exactly two independent variants; duplicate requests, saves and publications are idempotent',async()=>{
  let calls=0;const f=await setup(async()=>{calls++;return result});const {s,a,b,drain}=f;
  try {
    const p=request();
    const [j1,j2]=await Promise.all([s.rpc(a.id,'dna_create',p),s.rpc(a.id,'dna_create',p)]);
    assert.equal(j1.id,j2.id);await drain();assert.equal(calls,2);
    const job=await s.rpc(a.id,'dna_get',{id:j1.id});assert.equal(job.status,'ready');assert.equal(job.variants.length,2);
    assert.ok(job.variants.every(v=>v.result?.code===result.code));
    assert.deepEqual(job.variants.map(v=>v.model),['~deepseek/deepseek-flash-latest','~openai/gpt-luna-latest']);
    await assert.rejects(s.rpc(a.id,'dna_create',{...p,prompt:'Другая идея'}),/уже использован/);
    await assert.rejects(s.rpc(b.id,'dna_get',{id:job.id}),/не найдена/);
    await assert.rejects(s.rpc(b.id,'dna_save',{variant_id:job.variants[0].id}),/не найден/);
    await assert.rejects(s.rpc(null,'dna_list'),/войти/);
    const variant_id=job.variants[0].id;
    const saves=await Promise.all([s.rpc(a.id,'dna_save',{variant_id,values:{speed:1.5}}),s.rpc(a.id,'dna_save',{variant_id})]);
    assert.equal(saves[0].draft_id,saves[1].draft_id);
    const drafts=await s.rpc(a.id,'draft_list');assert.equal(drafts.length,1);assert.equal(drafts[0].body.parameters[0].default,1.5);
    const [published,replayed]=await Promise.all([s.rpc(a.id,'dna_publish',{variant_id,values:{speed:0.5}}),s.rpc(a.id,'dna_publish',{variant_id,values:{speed:1.5}})]);
    assert.deepEqual(replayed,published);
    assert.deepEqual(await s.rpc(a.id,'dna_publish',{variant_id,values:{speed:1}}),published);
    const work=(await s.rpc(null,'work',{id:published.work_id})).work;
    assert.equal(work.revision.dna_origin.variant_id,variant_id);assert.equal(work.revision.parameters[0].default,0.5);
    assert.equal((await s.rpc(a.id,'dna_get',{id:job.id})).variants[0].work_id,published.work_id);
    await assert.rejects(s.rpc(b.id,'publish',{...result,category:'Свет',tags:[],license:'MIT',request_id:randomUUID(),dna_variant_id:variant_id}),/не найден/);
  } finally {await f.close();}
});
test('DNA snapshots public immutable references and retains attribution after editor publication',async()=>{
  const inputs=[];const f=await setup(async p=>{inputs.push(p);return result});const {s,a,b,drain}=f;
  try {
    const source=await s.rpc(a.id,'publish',{...result,category:'Свет',tags:[],license:'MIT',request_id:randomUUID()});
    const j=await s.rpc(b.id,'dna_create',{...request(),reference_ids:[source.revision_id]});await drain();
    assert.deepEqual(inputs[0].references,inputs[1].references);assert.equal(inputs[0].references[0].code,result.code);
    const ready=await s.rpc(b.id,'dna_get',{id:j.id});assert.equal(ready.references[0].author_id,a.id);assert.equal(ready.references[0].code,undefined);
    const saved=await s.rpc(b.id,'dna_save',{variant_id:ready.variants[0].id});
    const d=(await s.rpc(b.id,'draft_list')).find(d=>d.id===saved.draft_id);
    const pub=await s.rpc(b.id,'publish',{...d.body,tags:[],dna_variant_id:d.body.dnaVariantId,request_id:randomUUID()});
    const detail=await s.rpc(null,'work',{id:pub.work_id});assert.equal(detail.work.revision.dna_origin.references[0].revision_id,source.revision_id);
    await s.db.query("update works set status='hidden' where id=$1",[source.work_id]);
    await assert.rejects(s.rpc(b.id,'dna_create',{...request(),reference_ids:[source.revision_id]}),/недоступен/);
    await assert.rejects(s.rpc(b.id,'dna_create',{...request(),reference_ids:[source.revision_id,source.revision_id]}),/трёх/);
  } finally {await f.close();}
});
test('one provider failing does not erase success; retry is bounded and does not rerun successful variant',async()=>{
  let calls=0;const f=await setup(async ({model})=>{calls++;if(model.startsWith('~openai/'))throw new DnaError('timeout','Тайм-аут');return result});const {s,a,drain}=f;
  try {
    const j=await s.rpc(a.id,'dna_create',request());await drain();
    let job=await s.rpc(a.id,'dna_get',{id:j.id});assert.equal(job.status,'partial');assert.equal(calls,2);
    const failed=job.variants[1];
    await Promise.all([s.rpc(a.id,'dna_retry',{variant_id:failed.id,expected_attempts:1}),s.rpc(a.id,'dna_retry',{variant_id:failed.id,expected_attempts:1})]);await drain();assert.equal(calls,3);
    await s.rpc(a.id,'dna_retry',{variant_id:failed.id,expected_attempts:1});await drain();assert.equal(calls,3);
    await s.rpc(a.id,'dna_retry',{variant_id:failed.id,expected_attempts:2});await drain();assert.equal(calls,4);
    await assert.rejects(s.rpc(a.id,'dna_retry',{variant_id:failed.id,expected_attempts:3}),/исчерпан/);
    job=await s.rpc(a.id,'dna_get',{id:j.id});assert.equal(job.variants[0].attempts,1);
  } finally {await f.close();}
});
test('cancellation and expired leases fence late results; recovery never silently spends again',async()=>{
  let release;const gate=new Promise(resolve=>release=resolve);let calls=0;
  const f=await setup(async()=>{calls++;await gate;return result});const {s,a,drain}=f;
  try {
    const j=await s.rpc(a.id,'dna_create',request());
    // Let the claim queries complete before simulating a worker disappearing.
    while(calls<2)await new Promise(r=>setTimeout(r,5));
    await assert.rejects(s.rpc(a.id,'dna_create',request()),/Дождитесь/);
    await s.db.query("update dna_variants set lease_until=now()-interval '1 second' where job_id=$1",[j.id]);
    let job=await s.rpc(a.id,'dna_get',{id:j.id});assert.equal(job.status,'failed');
    release();await drain();await s.dna.recover();await drain();assert.equal(calls,2);
    job=await s.rpc(a.id,'dna_get',{id:j.id});assert.equal(job.variants[0].error_code,'interrupted');
    const second=await s.rpc(a.id,'dna_create',request());await s.rpc(a.id,'dna_cancel',{id:second.id});await drain();
    const cancelled=await s.rpc(a.id,'dna_get',{id:second.id});assert.ok(cancelled.variants.every(v=>!['queued','running'].includes(v.status)));
  } finally {release();await f.close();}
});
test('limits, disabled service, invalid values and private history are enforced',async()=>{
  const f=await setup();const {s,a,b,drain}=f;
  try {
    await assert.rejects(s.rpc(a.id,'dna_create',{...request(),prompt:' '.repeat(10)}),/Проверьте/);
    for(let i=0;i<10;i++){await s.rpc(a.id,'dna_create',request());await drain();}
    await assert.rejects(s.rpc(a.id,'dna_create',request()),/лимит/);
    const list=await s.rpc(a.id,'dna_list');assert.equal(list.items.length,10);assert.equal(list.items[0].variants[0].result,undefined);
    assert.equal((await s.rpc(b.id,'dna_list')).items.length,0);
    await assert.rejects(s.rpc(a.id,'dna_save',{variant_id:list.items[0].variants[0].id,values:{speed:999}}),/диапазона/);
  } finally {await f.close();}
  const disabled=await createDatabase({memory:true});
  try {const a=await disabled.addUser({email:'disabled@test.test'});assert.equal((await disabled.rpc(null,'dna_config')).enabled,false);await assert.rejects(disabled.rpc(a.id,'dna_create',request()),/не подключена/);}finally{await disabled.close();}
});
test('OpenRouter adapter constrains requests, validates output and sanitizes failures',async()=>{
  let payload;
  const args={model:'model/a',prompt:'Волны',controls:'',references:[]};
  const provider=createOpenRouterProvider({apiKey:'test-secret',fetchImpl:async(url,init)=>{assert.equal(url,'https://openrouter.ai/api/v1/chat/completions');payload=JSON.parse(init.body);return Response.json({choices:[{finish_reason:'stop',message:{content:JSON.stringify(result)}}]});}});
  assert.deepEqual(await provider(args),result);assert.equal(payload.max_tokens,16000);assert.deepEqual(payload.reasoning,{effort:'high'});assert.equal(payload.messages.length,2);
  assert.match(payload.messages[0].content,/description \(Russian, <=300 chars\)/);
  const tooLong='Стеклянная капля переливается всеми цветами радуги. Как это сделано. '+('Технический разбор шейдера. '.repeat(30));
  assert.equal(validateDnaResult({...result,description:tooLong}).description,'Стеклянная капля переливается всеми цветами радуги.');
  assert.ok(validateDnaResult({...result,description:'Сияние '.repeat(100)}).description.length<=300);
  await assert.rejects(createOpenRouterProvider({apiKey:'x',fetchImpl:async()=>new Response('SECRET PROVIDER ERROR',{status:429})})(args),e=>e.code==='rate_limit'&&!e.message.includes('SECRET'));
  await assert.rejects(createOpenRouterProvider({apiKey:'x',timeoutMs:5,fetchImpl:(_,init)=>new Promise((_,reject)=>init.signal.addEventListener('abort',()=>reject(new Error('abort'))))})(args),e=>e.code==='timeout');
  for(const content of ['not json',JSON.stringify({...result,code:'void mainImage(out vec4 c,in vec2 p){while(true){} c=vec4(1.);}'}),JSON.stringify({...result,parameters:[{...result.parameters[0],default:99}]})]){
    await assert.rejects(createOpenRouterProvider({apiKey:'x',fetchImpl:async()=>Response.json({choices:[{finish_reason:'stop',message:{content}}]})})(args),e=>e.code==='invalid_output');
  }
  assert.throws(()=>validateDnaResult({...result,code:'#version 300 es\n'+result.code}));
});

test('concurrent independent starts cannot bypass the active job limit',async()=>{
  let release;const gate=new Promise(r=>release=r);const f=await setup(async()=>{await gate;return result});
  try {
    const requests=await Promise.allSettled(Array.from({length:12},()=>f.s.rpc(f.a.id,'dna_create',request())));
    assert.equal(requests.filter(r=>r.status==='fulfilled').length,1);
    assert.equal((await f.s.db.query('select count(*)::int as n from dna_jobs')).rows[0].n,1);
    assert.ok(requests.filter(r=>r.status==='rejected').every(r=>/Дождитесь/.test(r.reason.message)));
  } finally {release();await f.close();}
});

test('cancelling a running job discards both late provider responses',async()=>{
  let release;const gate=new Promise(r=>release=r);const f=await setup(async()=>{await gate;return result});
  try {
    const job=await f.s.rpc(f.a.id,'dna_create',request());
    await f.s.rpc(f.a.id,'dna_cancel',{id:job.id});
    release();await f.drain();
    const final=await f.s.rpc(f.a.id,'dna_get',{id:job.id});
    assert.equal(final.status,'cancelled');assert.ok(final.variants.every(v=>v.result===null));
    await assert.rejects(f.s.rpc(f.a.id,'dna_save',{variant_id:final.variants[0].id}),/не готов/);
    await assert.rejects(f.s.rpc(f.b.id,'dna_cancel',{id:job.id}),/не найдена/);
  } finally {release();await f.close();}
});

test('queued jobs survive a process restart; expired running jobs require explicit retry',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'shader-dna-recovery-'));
  let s;
  try {
    s=await createDatabase({path:dir});const a=await s.addUser({email:'recovery@test.test'});
    const jobId=randomUUID(),queued=randomUUID(),expired=randomUUID();
    await s.db.query('insert into dna_jobs(id,owner_id,request_id,fingerprint,prompt) values($1,$2,$3,$4,$5)',[jobId,a.id,randomUUID(),'fixture','Волны']);
    await s.db.query("insert into dna_variants(id,job_id,slot,model) values($1,$2,0,'test/a')",[queued,jobId]);
    await s.db.query("insert into dna_variants(id,job_id,slot,model,status,attempts,lease_token,lease_until) values($1,$2,1,'test/b','running',1,$3,now()-interval '1 minute')",[expired,jobId,randomUUID()]);
    await s.close();s=null;
    let calls=0;const tasks=[];
    s=await createDatabase({path:dir,dna:{provider:async()=>{calls++;return result},schedule:p=>tasks.push(p)}});
    await s.dna.recover();await Promise.all(tasks);
    const job=await s.rpc(a.id,'dna_get',{id:jobId});
    assert.equal(calls,1);assert.equal(job.status,'partial');assert.equal(job.variants[0].status,'ready');assert.equal(job.variants[1].error_code,'interrupted');
    const saved=await s.rpc(a.id,'dna_save',{variant_id:queued});
    await s.rpc(a.id,'draft_save',{id:saved.draft_id,expected_version:1,body:{title:'Ручная правка'}});
    assert.equal((await s.rpc(a.id,'dna_save',{variant_id:queued})).draft_id,saved.draft_id);
    assert.equal((await s.rpc(a.id,'draft_list'))[0].body.title,'Ручная правка');
    await s.rpc(a.id,'draft_delete',{id:saved.draft_id});
    const recreated=await s.rpc(a.id,'dna_save',{variant_id:queued});assert.notEqual(recreated.draft_id,saved.draft_id);
  } finally {if(s)await s.close();await rm(dir,{recursive:true,force:true});}
});

test('migration is compatible with existing data, repeatable, and blocks direct anonymous/authenticated access',async()=>{
  const f=await setup();
  try {
    await f.s.rpc(f.a.id,'dna_create',request());await f.drain();
    await f.s.db.exec('create role anon; create role authenticated;');
    const migration=await readFile(new URL('../supabase/migrations/20260927080553_shader_dna_studio.sql',import.meta.url),'utf8');
    const local=await readFile(new URL('../server/dna-schema.sql',import.meta.url),'utf8');
    assert.ok(migration.replace(/^\uFEFF/,'').startsWith(local.trim()));
    await f.s.db.exec(migration);await f.s.db.exec(migration);
    assert.equal((await f.s.rpc(f.a.id,'dna_list')).items.length,1);
    const secured=(await f.s.db.query("select relname,relrowsecurity from pg_class where relname in ('dna_jobs','dna_variants')")).rows;
    assert.equal(secured.length,2);assert.ok(secured.every(r=>r.relrowsecurity));
    for(const role of ['anon','authenticated']){
      await f.s.db.exec(`set role ${role}`);
      try {
        await assert.rejects(f.s.db.query('select * from dna_jobs'),/permission denied/);
        await assert.rejects(f.s.db.query('select * from dna_variants'),/permission denied/);
      } finally {await f.s.db.exec('reset role');}
    }
  } finally {await f.close();}
});

test('provider rejects oversized, truncated and structurally unsafe results without exposing upstream text',async()=>{
  const args={model:'test/model',prompt:'x',controls:'',references:[]};
  for(const response of [
    ()=>new Response('x'.repeat(180001)),
    ()=>Response.json({choices:[{finish_reason:'length',message:{content:JSON.stringify(result)}}]}),
    ()=>Response.json({error:{message:'sensitive upstream body'}}),
    ()=>Response.json({choices:[{finish_reason:'stop',message:{content:JSON.stringify({...result,parameters:[{...result.parameters[0],max:1e40}]})}}]})
  ])await assert.rejects(createOpenRouterProvider({apiKey:'x',fetchImpl:async()=>response()})(args),e=>e.code==='invalid_output'&&!e.message.includes('sensitive'));
  for(const code of [
    'void mainImage(out vec4 c,in vec2 p){for(int i=0;i<1000000;i++){} c=vec4(1.);}',
    'uniform float speed; '+result.code,
    'void mainImage(out vec4 c,in vec2 p){for(int i=0;i<10;i++){i=0;} c=vec4(1.);}',
    'void mainImage(out vec4 c,in vec2 p){for(int i=0;i<10;i++){for(int j=0;j<10;j++){} } c=vec4(1.);}',
    'void mainImage(out vec4 c,in vec2 p){ c=texture(iChannel0,p); }'
  ])assert.throws(()=>validateDnaResult({...result,code}),e=>e.code==='invalid_output');
  assert.doesNotThrow(()=>validateDnaResult({...result,code:'void mainImage(out vec4 c,in vec2 p){float x=0.;for(int i=0;i<10;i++){x+=sin(float(i));} c=vec4(x);}'}));
});
