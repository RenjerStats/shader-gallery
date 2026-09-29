import {randomUUID,createHash} from 'node:crypto';
import {createOpenRouterProvider,validateDnaResult,DnaError} from './dna-provider.mjs';
import {validateValues} from './shader-validation.mjs';

const one = async (db,sql,args=[]) => (await db.query(sql,args)).rows[0] || null;
const rows = async (db,sql,args=[]) => (await db.query(sql,args)).rows;
const json = v => typeof v === 'string' ? JSON.parse(v) : v;
const uuid = v => typeof v === 'string' && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(v);
const fail = message => {throw new Error(message);};
const text = (v,max,required=false) => {
  if (typeof v !== 'string' || v.length > max || (required && !v.trim())) fail('Проверьте описание и настройки генерации');
  return v.trim();
};
const active = ['queued','running'];

export function createDnaService(db,{requireUser,publish},options={}) {
  const models = options.models || ['google/gemini-2.5-flash-lite','qwen/qwen3-30b-a3b-instruct-2507'];
  const enabled = !!(options.provider || options.apiKey) && models.length === 2 && models.every(m => typeof m === 'string' && m.length > 0 && m.length <= 120) && models[0] !== models[1];
  const generate = options.provider || createOpenRouterProvider({apiKey:options.apiKey});
  const schedule = options.schedule || (task => { task.catch(() => {}); });
  const limit = 10, maxReferences = 3;
  const safeVariant = (v,includeResult=true) => ({id:v.id,slot:v.slot,model:v.model,status:v.status,attempts:v.attempts,
    ...(includeResult?{result:v.result ? json(v.result) : null}:{}),error_code:v.error_code,error_message:v.error_message,draft_id:v.draft_id,work_id:v.work_id||null});

  async function expire(ownerId=null) {
    await db.query(`update dna_variants v set status='failed',lease_token=null,lease_until=null,
      error_code='interrupted',error_message='Генерация прервалась. Повторите этот вариант.',updated_at=now()
      from dna_jobs j where j.id=v.job_id and ($1::uuid is null or j.owner_id=$1)
      and ((v.status='running' and v.lease_until<now()) or (v.status='queued' and v.updated_at<now()-interval '10 minutes'))`,[ownerId]);
  }
  async function variants(jobIds,ownerId,includeResult=true) {
    return rows(db,`select v.id,v.job_id,v.slot,v.model,v.status,v.attempts,v.error_code,v.error_message,v.draft_id,p.work_id${includeResult?',v.result':''}
      from dna_variants v left join publish_requests p on p.request_id=v.id and p.user_id=$2
      where v.job_id=any($1::uuid[]) order by v.slot`,[jobIds,ownerId]);
  }
  async function ownedJob(ownerId,id) {
    if (!uuid(id)) fail('Некорректная генерация');
    const job = await one(db,'select * from dna_jobs where id=$1 and owner_id=$2',[id,ownerId]);
    if (!job) fail('Генерация не найдена');
    return job;
  }
  async function ownedVariant(ownerId,id,tx=db,lock=false) {
    if (!uuid(id)) fail('Некорректный вариант');
    const variant = await one(tx,`select v.*,j.owner_id,j.reference_snapshots from dna_variants v join dna_jobs j on j.id=v.job_id
      where v.id=$1 and j.owner_id=$2 ${lock?'for update of v':''}`,[id,ownerId]);
    if (!variant) fail('Вариант не найден');
    return variant;
  }
  function origin(v) {
    return {job_id:v.job_id,variant_id:v.id,model:v.model,references:json(v.reference_snapshots).map(({code,parameters,...r}) => r)};
  }
  function body(v,values={}) {
    if (v.status !== 'ready') fail('Вариант ещё не готов');
    const result = json(v.result);
    validateValues(result.parameters,values);
    return {...result,parameters:result.parameters.map(p=>({...p,default:values[p.name]??p.default})),
      category:'Абстракция',tags:'shader-dna',license:'MIT',dnaVariantId:v.id};
  }
  async function snapshot(tx,ownerId,referenceIds) {
    const refs=[];
    for (const id of referenceIds) {
      const r=await one(tx,`select r.id as revision_id,r.work_id,r.code,r.parameters,r.license,r.dna_origin,w.title,u.display_name as author,u.id as author_id
        from revisions r join works w on w.id=r.work_id join users u on u.id=w.author_id
        where r.id=$1 and w.status='published' and not exists(select 1 from blocks b where b.user_id=$2 and b.author_id=w.author_id)`,[id,ownerId]);
      if (!r) fail('Один из референсов недоступен. Удалите его и попробуйте снова.');
      refs.push({...r,parameters:json(r.parameters)});
    }
    if (JSON.stringify(refs).length > 90000) fail('Референсы слишком большие. Оставьте меньше работ.');
    return refs;
  }
  async function runVariant(id) {
    if (!enabled) return;
    const token=randomUUID();
    const v=await one(db,`update dna_variants set status='running',attempts=attempts+1,lease_token=$2,
      lease_until=now()+interval '100 seconds',updated_at=now() where id=$1 and status='queued' and attempts<3 returning *`,[id,token]);
    if (!v) return;
    const job=await one(db,'select * from dna_jobs where id=$1',[v.job_id]);
    try {
      const result=validateDnaResult(await generate({model:v.model,prompt:job.prompt,controls:job.controls,references:json(job.reference_snapshots)}));
      await db.query(`update dna_variants set status='ready',result=($3::text)::jsonb,lease_token=null,lease_until=null,
        error_code=null,error_message=null,updated_at=now() where id=$1 and lease_token=$2 and status='running' and lease_until>now()`,[id,token,JSON.stringify(result)]);
    } catch(e) {
      const known=e instanceof DnaError;
      await db.query(`update dna_variants set status='failed',lease_token=null,lease_until=null,error_code=$3,error_message=$4,updated_at=now()
        where id=$1 and lease_token=$2 and status='running'`,[id,token,known?e.code:'provider_error',known?e.message:'Не удалось создать вариант. Попробуйте ещё раз.']);
    }
  }
  function kick(ids) {
    if (ids.length && enabled) schedule(Promise.allSettled(ids.map(runVariant)).then(results=>{
      // Do not log prompts, credentials or provider response bodies.
      if (results.some(r=>r.status==='rejected')) console.error('DNA worker persistence failed; lease recovery required');
    }));
  }
  async function recover() {
    await expire();
    const pending=await rows(db,"select id from dna_variants where status='queued' order by updated_at limit 20");
    kick(pending.map(v=>v.id));
  }
  async function get(ownerId,id) {
    await expire(ownerId);
    const job=await ownedJob(ownerId,id), vs=await variants([id],ownerId);
    kick(vs.filter(v=>v.status==='queued').map(v=>v.id));
    const running=vs.some(v=>active.includes(v.status)), ready=vs.filter(v=>v.status==='ready').length;
    return {id:job.id,prompt:job.prompt,controls:job.controls,created_at:job.created_at,
      references:json(job.reference_snapshots).map(({code,parameters,...r})=>r),
      status:running?'running':ready===2?'ready':ready?'partial':vs.every(v=>v.status==='cancelled')?'cancelled':'failed',variants:vs.map(v=>safeVariant(v))};
  }
  async function rpc(ownerId,action,p={}) {
    if (action==='dna_config') return {enabled,models,max_references:maxReferences,daily_limit:limit,max_attempts:3};
    await requireUser(ownerId);
    if (action==='dna_create') {
      if (!uuid(p.request_id)) fail('Некорректный идентификатор запроса');
      const prompt=text(p.prompt,2000,true),controls=text(p.controls??'',1000);
      const inputIds=p.reference_ids??[];
      if (!Array.isArray(inputIds)||inputIds.length>maxReferences||inputIds.some(id=>!uuid(id))) fail('Выберите до трёх разных референсов');
      const ids=inputIds.map(id=>id.toLowerCase());
      if(new Set(ids).size!==ids.length) fail('Выберите до трёх разных референсов');
      const fingerprint=createHash('sha256').update(JSON.stringify({prompt,controls,ids})).digest('hex');
      await expire(ownerId);
      const jobId=await db.transaction(async tx=>{
        // A user row serializes starts/retries/publications across processes and devices.
        await tx.query('select id from users where id=$1 for update',[ownerId]);
        const prior=await one(tx,'select * from dna_jobs where owner_id=$1 and request_id=$2',[ownerId,p.request_id]);
        if (prior) {if(prior.fingerprint!==fingerprint) fail('Идентификатор генерации уже использован');return prior.id;}
        if (!enabled) fail('Генерация пока не подключена. Попробуйте позже.');
        const count=await one(tx,"select count(*)::int as n from dna_jobs where owner_id=$1 and created_at>now()-interval '24 hours'",[ownerId]);
        if (count.n>=limit) fail('Достигнут лимит: 10 генераций за 24 часа. Попробуйте позже.');
        const busy=await one(tx,"select 1 from dna_variants v join dna_jobs j on j.id=v.job_id where j.owner_id=$1 and v.status in ('queued','running') limit 1",[ownerId]);
        if (busy) fail('Дождитесь завершения текущей генерации или остановите её.');
        const refs=await snapshot(tx,ownerId,ids),id=randomUUID();
        await tx.query('insert into dna_jobs(id,owner_id,request_id,fingerprint,prompt,controls,reference_snapshots) values($1,$2,$3,$4,$5,$6,($7::text)::jsonb)',[id,ownerId,p.request_id,fingerprint,prompt,controls,JSON.stringify(refs)]);
        for (let slot=0;slot<2;slot++) await tx.query('insert into dna_variants(id,job_id,slot,model) values($1,$2,$3,$4)',[randomUUID(),id,slot,models[slot]]);
        return id;
      });
      return get(ownerId,jobId);
    }
    if (action==='dna_get') return get(ownerId,p.id);
    if (action==='dna_list') {
      await expire(ownerId);
      const args=[ownerId];let cursor='';
      if (p.before) {const j=await ownedJob(ownerId,p.before);args.push(j.created_at,j.id);cursor='and (created_at,id)<($2::timestamptz,$3::uuid)';}
      const jobs=await rows(db,`select id,prompt,created_at from dna_jobs where owner_id=$1 ${cursor} order by created_at desc,id desc limit 21`,args);
      const page=jobs.slice(0,20),vs=page.length?await variants(page.map(j=>j.id),ownerId,false):[];
      kick(vs.filter(v=>v.status==='queued').map(v=>v.id));
      const items=page.map(j=>({...j,variants:vs.filter(v=>v.job_id===j.id).map(v=>safeVariant(v,false))}));
      return {items,next_cursor:jobs.length>20?jobs[19].id:null};
    }
    if (action==='dna_retry') {
      if (!enabled) fail('Генерация пока не подключена.');
      if (!Number.isInteger(p.expected_attempts)) fail('Обновите состояние варианта');
      await expire(ownerId);
      const jobId=await db.transaction(async tx=>{
        await tx.query('select id from users where id=$1 for update',[ownerId]);
        const v=await ownedVariant(ownerId,p.variant_id,tx,true);
        if (v.attempts!==p.expected_attempts||v.status!=='failed') return v.job_id;
        if (v.attempts>=3) fail('Лимит повторов исчерпан. Создайте новую генерацию.');
        const recent=await one(tx,"select 1 from dna_jobs where id=$1 and created_at>now()-interval '24 hours'",[v.job_id]);
        if (!recent) fail('Для этой идеи создайте новую генерацию: прошло больше суток.');
        const busy=await one(tx,"select 1 from dna_variants v join dna_jobs j on j.id=v.job_id where j.owner_id=$1 and j.id<>$2 and v.status in ('queued','running') limit 1",[ownerId,v.job_id]);
        if (busy) fail('Дождитесь завершения текущей генерации.');
        await snapshot(tx,ownerId,json(v.reference_snapshots).map(r=>r.revision_id));
        await tx.query("update dna_variants set status='queued',error_code=null,error_message=null,updated_at=now() where id=$1",[v.id]);
        return v.job_id;
      });
      return get(ownerId,jobId);
    }
    if (action==='dna_cancel') {
      await ownedJob(ownerId,p.id);
      await db.query("update dna_variants set status='cancelled',lease_token=null,lease_until=null,updated_at=now() where job_id=$1 and status in ('queued','running')",[p.id]);
      return get(ownerId,p.id);
    }
    if (action==='dna_save') {
      return db.transaction(async tx=>{
        const v=await ownedVariant(ownerId,p.variant_id,tx,true);
        if (v.draft_id) return {draft_id:v.draft_id};
        const draft=body(v,p.values??{}),id=randomUUID();
        await tx.query('insert into drafts(id,owner_id,version,body) values($1,$2,1,($3::text)::jsonb)',[id,ownerId,JSON.stringify(draft)]);
        await tx.query('update dna_variants set draft_id=$2 where id=$1',[v.id,id]);
        return {draft_id:id};
      });
    }
    if (action==='dna_publish') {
      const v=await ownedVariant(ownerId,p.variant_id);
      const prior=await one(db,'select work_id,revision_id from publish_requests where user_id=$1 and request_id=$2',[ownerId,v.id]);
      if(prior) return prior;
      const b=body(v,p.values??{});
      return publish(ownerId,{...b,dna_variant_id:v.id,tags:['shader-dna'],preview:p.preview,request_id:v.id});
    }
    fail('Неизвестное действие DNA');
  }
  return {rpc,recover,origin,ownedVariant};
}
