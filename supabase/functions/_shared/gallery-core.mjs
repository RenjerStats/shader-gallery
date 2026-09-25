import {randomUUID, createHash, randomBytes, scryptSync, timingSafeEqual} from 'node:crypto';

const fail = (message) => { throw new Error(message); };
const uuid = (value) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(value||''));
const iso = (v) => v instanceof Date ? v.toISOString() : String(v);
const hash = (v) => createHash('sha256').update(v).digest('hex');
const clean = (v, max) => String(v ?? '').trim().slice(0, max + 1);
const one = async (db, sql, args=[]) => (await db.query(sql,args)).rows[0] ?? null;
const rows = async (db, sql, args=[]) => (await db.query(sql,args)).rows;
const publicUser = (u) => u && ({id:u.id,email:u.email});
const jsonArray = value => {
  const parsed=typeof value==='string'?JSON.parse(value):value;
  if(!Array.isArray(parsed)) fail('Некорректные параметры работы');
  return parsed;
};
const jsonObject = value => {
  const parsed=typeof value==='string'?JSON.parse(value):value;
  if(!parsed||typeof parsed!=='object'||Array.isArray(parsed)) fail('Некорректные данные');
  return parsed;
};

function validateParameters(parameters) {
  if (!Array.isArray(parameters) || parameters.length > 12) fail('Допустимо не более 12 параметров');
  const names = new Set();
  for (const p of parameters) {
    if (!p || !/^[A-Za-z_][A-Za-z0-9_]{0,31}$/.test(p.name) || ['iTime','iResolution','iMouse','iTilt'].includes(p.name) || names.has(p.name)) fail('Некорректное имя параметра');
    names.add(p.name);
    if (!clean(p.label,60) || clean(p.label,60).length>60) fail('Укажите название параметра');
    if (p.type==='float') {
      if (![p.min,p.max,p.default].every(x=>typeof x==='number' && Number.isFinite(x)) || p.min>=p.max || p.default<p.min || p.default>p.max) fail('Некорректный диапазон параметра');
    } else if (p.type==='color') {
      if (!/^#[0-9a-f]{6}$/i.test(p.default)) fail('Цвет должен быть в формате #RRGGBB');
    } else fail('Неизвестный тип параметра');
  }
}
function validateValues(parameters, values) {
  if (!values || typeof values!=='object' || Array.isArray(values)) fail('Некорректный пресет');
  for (const [name,value] of Object.entries(values)) {
    const p=parameters.find(x=>x.name===name); if(!p) fail('Неизвестный параметр');
    if(p.type==='float' && (typeof value!=='number' || !Number.isFinite(value) || value<p.min || value>p.max)) fail('Значение вне диапазона');
    if(p.type==='color' && (typeof value!=='string' || !/^#[0-9a-f]{6}$/i.test(value))) fail('Некорректный цвет');
  }
}

export function createStore(db) {
  const requireUser = async id => {
    if(!uuid(id)) fail('Нужно войти в аккаунт');
    const u=await one(db,'select * from users where id=$1',[id]);
    if(!u) fail('Нужно войти в аккаунт');
    return u;
  };
  const profile = async (targetId, viewerId) => {
    const u=await one(db,'select id,username,display_name,bio from users where id=$1',[targetId]);
    if(!u) fail('Профиль не найден');
    return {...u,is_following:!!(viewerId && await one(db,'select 1 from follows where user_id=$1 and author_id=$2',[viewerId,targetId])),is_blocked:!!(viewerId && await one(db,'select 1 from blocks where user_id=$1 and author_id=$2',[viewerId,targetId])),is_moderator:!!(viewerId===targetId && await one(db,'select 1 from moderators where user_id=$1',[viewerId]))};
  };
  const canRead = async (w, viewerId) => {
    if(!w) return false;
    if(w.status!=='published' && w.author_id!==viewerId && !(viewerId && await one(db,'select 1 from moderators where user_id=$1',[viewerId]))) return false;
    if(viewerId && await one(db,'select 1 from blocks where user_id=$1 and author_id=$2',[viewerId,w.author_id])) return false;
    return true;
  };
  const work = async (id,viewerId,revisionId) => {
    const w=await one(db,'select * from works where id=$1',[id]);
    if(!await canRead(w,viewerId)) fail('Работа не найдена');
    const r=await one(db,'select * from revisions where id=$1 and work_id=$2',[revisionId||w.current_revision_id,id]);
    if(!r) fail('Версия не найдена');
    const [author,likes,saves,liked,saved]=await Promise.all([
      profile(w.author_id,viewerId),one(db,'select count(*)::int as n from likes where work_id=$1',[id]),one(db,'select count(*)::int as n from saves where work_id=$1',[id]),
      viewerId?one(db,'select 1 from likes where user_id=$1 and work_id=$2',[viewerId,id]):null,
      viewerId?one(db,'select 1 from saves where user_id=$1 and work_id=$2',[viewerId,id]):null
    ]);
    return {...w,created_at:iso(w.created_at),updated_at:iso(w.updated_at),author,likes_count:likes.n,saves_count:saves.n,liked:!!liked,saved:!!saved,revision:{...r,parameters:jsonArray(r.parameters),created_at:iso(r.created_at)}};
  };
  const addUser = async ({id=randomUUID(),email,display_name='Автор',password='test-password'}) => {
    email=clean(email,254).toLowerCase();
    if(!/^\S+@\S+\.\S+$/.test(email) || email.length>254) fail('Некорректный email');
    if(String(password).length<8) fail('Пароль должен содержать не менее 8 символов');
    const salt=randomBytes(16).toString('hex');
    const passwordHash=`${salt}:${scryptSync(password,salt,32).toString('hex')}`;
    let username=`user_${id.replaceAll('-','').slice(0,12)}`;
    await db.query('insert into users(id,email,password_hash,username,display_name) values($1,$2,$3,$4,$5)',[id,email,passwordHash,username,clean(display_name,80)||'Автор']);
    return {id,email};
  };
  const authenticate = async (email,password) => {
    const u=await one(db,'select * from users where email=$1',[clean(email,254).toLowerCase()]);
    if(!u) fail('Неверный email или пароль');
    const [salt,expected]=u.password_hash.split(':');
    const actual=scryptSync(String(password),salt,32);
    if(!timingSafeEqual(actual,Buffer.from(expected,'hex'))) fail('Неверный email или пароль');
    return publicUser(u);
  };
  const createSession = async id => {
    const token=randomBytes(32).toString('hex');
    await db.query('insert into sessions(token_hash,user_id,expires_at) values($1,$2,now()+interval \'30 days\')',[hash(token),id]);
    return token;
  };
  const sessionUser = async token => {
    if(!token || !/^[a-f0-9]{64}$/.test(token)) return null;
    const u=await one(db,'select u.id,u.email from sessions s join users u on u.id=s.user_id where s.token_hash=$1 and s.expires_at>now()',[hash(token)]);
    return publicUser(u);
  };
  const revokeSession = async token => {if(token) await db.query('delete from sessions where token_hash=$1',[hash(token)]);};

  async function rpc(viewerId,action,p={}) {
    const userId=viewerId||null;
    if(action==='feed') {
      const mode=['new','curated','following','saved'].includes(p.mode)?p.mode:'new';
      if((mode==='following'||mode==='saved')&&!userId) fail('Нужно войти в аккаунт');
      const limit=Math.min(30,Math.max(1,Number(p.limit)||12));
      const args=[userId]; let where="w.status='published' and not exists(select 1 from blocks b where b.user_id=$1 and b.author_id=w.author_id)";
      if(mode==='curated') where+=' and w.curated=true';
      if(mode==='following') where+=' and exists(select 1 from follows f where f.user_id=$1 and f.author_id=w.author_id)';
      if(mode==='saved') where+=' and exists(select 1 from saves s where s.user_id=$1 and s.work_id=w.id)';
      if(p.query){args.push(`%${clean(p.query,100)}%`);where+=` and (w.title ilike $${args.length} or w.description ilike $${args.length} or exists(select 1 from unnest(w.tags) t where t ilike $${args.length}))`;}
      if(p.category){args.push(clean(p.category,40));where+=` and w.category=$${args.length}`;}
      if(p.author_id){if(!uuid(p.author_id)) fail('Некорректный автор');args.push(p.author_id);where+=` and w.author_id=$${args.length}`;}
      if(p.cursor){if(!uuid(p.cursor.id)||!p.cursor.created_at) fail('Некорректная страница');args.push(p.cursor.created_at,p.cursor.id);where+=` and (w.created_at,w.id)<($${args.length-1}::timestamptz,$${args.length}::uuid)`;}
      args.push(limit+1);
      const selected=await rows(db,`select w.id,w.author_id,w.title,w.category,w.created_at,u.display_name,r.id as revision_id,r.preview
        from works w join users u on u.id=w.author_id join revisions r on r.id=w.current_revision_id
        where ${where} order by w.created_at desc,w.id desc limit $${args.length}`,args);
      const page=selected.slice(0,limit);
      const items=page.map(w=>({id:w.id,author_id:w.author_id,title:w.title,category:w.category,
        author:{display_name:w.display_name},revision:{id:w.revision_id,preview:w.preview}}));
      const last=page.at(-1);
      return {items,next_cursor:selected.length>limit&&last?{created_at:iso(last.created_at),id:last.id}:null};
    }
    if(action==='work') {
      if(!uuid(p.id)) fail('Некорректная работа');
      const item=await work(p.id,userId,p.revision_id);
      const commentRows=await rows(db,'select c.id,c.work_id,c.body,c.created_at,u.id as author_id,u.username,u.display_name,u.bio from comments c join users u on u.id=c.author_id where c.work_id=$1 order by c.created_at asc limit 200',[p.id]);
      const comments=commentRows.map(c=>({id:c.id,work_id:c.work_id,body:c.body,created_at:iso(c.created_at),author:{id:c.author_id,username:c.username,display_name:c.display_name,bio:c.bio}}));
      const revs=(await rows(db,'select id,created_at from revisions where work_id=$1 order by created_at desc',[p.id])).map(r=>({id:r.id,created_at:iso(r.created_at)}));
      let parent=null;
      if(item.revision.parent_revision_id){const pr=await one(db,'select r.work_id,r.id as revision_id,w.title,w.author_id from revisions r join works w on w.id=r.work_id where r.id=$1',[item.revision.parent_revision_id]);if(pr&&await canRead(await one(db,'select * from works where id=$1',[pr.work_id]),userId))parent={work_id:pr.work_id,revision_id:pr.revision_id,title:pr.title,author:(await profile(pr.author_id,userId)).display_name};}
      const remixes=await rows(db,"select distinct w.id,w.title from works w join revisions r on r.work_id=w.id where r.parent_revision_id=$1 and w.status='published' limit 30",[item.revision.id]);
      const preset=userId?await one(db,'select vals from presets where user_id=$1 and revision_id=$2',[userId,item.revision.id]):null;
      return {work:item,comments,revisions:revs,parent,remixes,preset:preset?jsonObject(preset.vals):null};
    }
    if(action==='profile') return profile(p.id,userId);
    if(action==='profile_update') {
      await requireUser(userId);
      const username=clean(p.username,30).toLowerCase(); if(!/^[a-z0-9_]{3,30}$/.test(username)) fail('Имя пользователя: 3–30 латинских букв, цифр или _');
      const display=clean(p.display_name,80),bio=clean(p.bio,500); if(!display||display.length>80||bio.length>500) fail('Некорректный профиль');
      await db.query('update users set username=$1,display_name=$2,bio=$3 where id=$4',[username,display,bio,userId]);return profile(userId,userId);
    }
    if(action==='publish') {
      await requireUser(userId);
      if(!uuid(p.request_id)) fail('Некорректный идентификатор запроса');
      const title=clean(p.title,120),description=clean(p.description,2000),category=clean(p.category,40);
      const tags=Array.isArray(p.tags)?p.tags.map(x=>clean(x,30).toLowerCase()):[];
      if(!title||title.length>120||description.length>2000||!category||category.length>40||tags.length>10||tags.some(t=>!t||t.length>30)) fail('Проверьте название, описание и теги');
      if(typeof p.code!=='string'||p.code.length<20||p.code.length>50000||!p.code.includes('mainImage')) fail('Некорректный исходник');
      if(!['MIT','CC0-1.0'].includes(p.license)) fail('Выберите лицензию');
      validateParameters(p.parameters);
      if(p.preview!=null){
        const format=typeof p.preview==='string'&&p.preview.match(/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/)?.[1];
        if(!format||p.preview.length>(format==='png'?400000:32000))fail('Некорректное превью: PNG до 400 КБ, JPEG или WebP до 32 КБ');
      }
      const fingerprint=hash(JSON.stringify(p));
      return db.transaction(async tx=>{
        const prior=await one(tx,'select * from publish_requests where user_id=$1 and request_id=$2',[userId,p.request_id]);
        if(prior){if(prior.fingerprint!==fingerprint)fail('Идентификатор публикации уже использован');return {work_id:prior.work_id,revision_id:prior.revision_id};}
        let workId=p.work_id, parentId=p.parent_revision_id||null;
        if(workId){
          if(!uuid(workId)||!uuid(p.base_revision_id))fail('Некорректная версия');
          const w=await one(tx,'select * from works where id=$1 for update',[workId]);
          if(!w||w.author_id!==userId)fail('Нет доступа к работе');
          if(w.current_revision_id!==p.base_revision_id)fail('Работа была изменена. Обновите версию.');
          const current=await one(tx,'select * from revisions where id=$1',[w.current_revision_id]);
          parentId=current.parent_revision_id;
          if(current.license!==p.license)fail('Лицензию работы менять нельзя');
        } else {
          workId=randomUUID();
          if(parentId){
            if(!uuid(parentId))fail('Некорректный оригинал');
            const parent=await one(tx,'select r.*,w.status,w.author_id from revisions r join works w on w.id=r.work_id where r.id=$1',[parentId]);
            if(!parent||parent.status!=='published'||parent.author_id===userId||parent.license!==p.license)fail('Ремикс оригинала недоступен');
          }
          await tx.query('insert into works(id,author_id,title,description,category,tags) values($1,$2,$3,$4,$5,$6)',[workId,userId,title,description,category,tags]);
        }
        const revisionId=randomUUID();
        await tx.query('insert into revisions(id,work_id,code,license,parameters,parent_revision_id,preview) values($1,$2,$3,$4,($5::text)::jsonb,$6,$7)',[revisionId,workId,p.code,p.license,JSON.stringify(p.parameters),parentId,p.preview||null]);
        await tx.query('update works set current_revision_id=$1,title=$2,description=$3,category=$4,tags=$5,updated_at=now() where id=$6',[revisionId,title,description,category,tags,workId]);
        await tx.query('insert into publish_requests(user_id,request_id,fingerprint,work_id,revision_id) values($1,$2,$3,$4,$5)',[userId,p.request_id,fingerprint,workId,revisionId]);
        return {work_id:workId,revision_id:revisionId};
      });
    }
    if(action==='draft_list'){await requireUser(userId);return (await rows(db,'select id,version,body,updated_at,conflict_of from drafts where owner_id=$1 order by updated_at desc',[userId])).map(d=>({...d,body:jsonObject(d.body),updated_at:iso(d.updated_at)}));}
    if(action==='draft_save'){
      await requireUser(userId);if(!uuid(p.id)||!Number.isInteger(p.expected_version)||!p.body||typeof p.body!=='object'||JSON.stringify(p.body).length>100000)fail('Некорректный черновик');
      return db.transaction(async tx=>{
        const current=await one(tx,'select * from drafts where id=$1 for update',[p.id]);
        if(!current){if(p.expected_version!==0)fail('Черновик не найден');await tx.query('insert into drafts(id,owner_id,version,body) values($1,$2,1,($3::text)::jsonb)',[p.id,userId,JSON.stringify(p.body)]);const d=await one(tx,'select * from drafts where id=$1',[p.id]);return {draft:{...d,body:jsonObject(d.body),updated_at:iso(d.updated_at)},conflict:false};}
        if(current.owner_id!==userId)fail('Нет доступа к черновику');
        if(current.version!==p.expected_version){const id=randomUUID();await tx.query('insert into drafts(id,owner_id,version,body,conflict_of) values($1,$2,1,($3::text)::jsonb,$4)',[id,userId,JSON.stringify(p.body),p.id]);const d=await one(tx,'select * from drafts where id=$1',[id]);return {draft:{...d,body:jsonObject(d.body),updated_at:iso(d.updated_at)},conflict:true};}
        await tx.query('update drafts set version=version+1,body=($1::text)::jsonb,updated_at=now() where id=$2',[JSON.stringify(p.body),p.id]);const d=await one(tx,'select * from drafts where id=$1',[p.id]);return {draft:{...d,body:jsonObject(d.body),updated_at:iso(d.updated_at)},conflict:false};
      });
    }
    if(action==='draft_delete'){await requireUser(userId);await db.query('delete from drafts where id=$1 and owner_id=$2',[p.id,userId]);return {ok:true};}
    if(['like','save'].includes(action)){
      await requireUser(userId);const w=await one(db,'select * from works where id=$1',[p.work_id]);if(!await canRead(w,userId)||w.status!=='published')fail('Работа недоступна');
      const table=action==='like'?'likes':'saves';if(p.active===true)await db.query(`insert into ${table}(user_id,work_id) values($1,$2) on conflict do nothing`,[userId,p.work_id]);else if(p.active===false)await db.query(`delete from ${table} where user_id=$1 and work_id=$2`,[userId,p.work_id]);else fail('Некорректное действие');return {ok:true};
    }
    if(['follow','block'].includes(action)){
      await requireUser(userId);if(!uuid(p.author_id)||p.author_id===userId||!await one(db,'select 1 from users where id=$1',[p.author_id]))fail('Автор недоступен');
      const table=action==='follow'?'follows':'blocks';if(p.active===true)await db.query(`insert into ${table}(user_id,author_id) values($1,$2) on conflict do nothing`,[userId,p.author_id]);else if(p.active===false)await db.query(`delete from ${table} where user_id=$1 and author_id=$2`,[userId,p.author_id]);else fail('Некорректное действие');return {ok:true};
    }
    if(action==='comment'){
      await requireUser(userId);if(!uuid(p.request_id))fail('Некорректный запрос');const body=clean(p.body,2000);if(!body||body.length>2000)fail('Комментарий: от 1 до 2000 символов');
      const existing=await one(db,'select * from comments where author_id=$1 and request_id=$2',[userId,p.request_id]);if(existing){if(existing.work_id!==p.work_id||existing.body!==body)fail('Идентификатор комментария уже использован');return {id:existing.id};}
      const w=await one(db,'select * from works where id=$1',[p.work_id]);if(!await canRead(w,userId)||w.status!=='published')fail('Работа недоступна');const id=randomUUID();await db.query('insert into comments(id,work_id,author_id,body,request_id) values($1,$2,$3,$4,$5)',[id,p.work_id,userId,body,p.request_id]);return {id};
    }
    if(action==='preset'){
      await requireUser(userId);const r=await one(db,'select r.*,w.status,w.author_id from revisions r join works w on w.id=r.work_id where r.id=$1',[p.revision_id]);if(!r||r.status!=='published'||!await canRead({status:r.status,author_id:r.author_id},userId))fail('Версия недоступна');validateValues(jsonArray(r.parameters),p.values);
      await db.query('insert into presets(user_id,revision_id,vals) values($1,$2,($3::text)::jsonb) on conflict(user_id,revision_id) do update set vals=excluded.vals',[userId,p.revision_id,JSON.stringify(p.values)]);return {ok:true};
    }
    if(action==='report'){
      await requireUser(userId);const reason=clean(p.reason,500);if(!reason||reason.length>500)fail('Укажите причину жалобы');const w=await one(db,'select * from works where id=$1',[p.work_id]);if(!await canRead(w,userId)||w.author_id===userId)fail('Работа недоступна');
      await db.query('insert into reports(id,work_id,reporter_id,reason) values($1,$2,$3,$4) on conflict(work_id,reporter_id) do update set reason=excluded.reason,status=\'open\'',[randomUUID(),p.work_id,userId,reason]);return {ok:true};
    }
    if(action==='moderation_list'){
      await requireUser(userId);if(!await one(db,'select 1 from moderators where user_id=$1',[userId]))fail('Нет доступа');
      const reports=await rows(db,'select r.id,r.work_id,r.reason,r.status,r.created_at,w.title as work_title from reports r join works w on w.id=r.work_id order by r.created_at desc limit 100');return {reports:reports.map(r=>({...r,created_at:iso(r.created_at)}))};
    }
    if(action==='moderate'){
      await requireUser(userId);if(!await one(db,'select 1 from moderators where user_id=$1',[userId]))fail('Нет доступа');if(!['hide','restore','dismiss'].includes(p.decision))fail('Некорректное решение');const reason=clean(p.reason,500);if(!reason||reason.length>500)fail('Укажите причину');
      const report=await one(db,'select * from reports where id=$1',[p.report_id]);if(!report)fail('Жалоба не найдена');
      await db.transaction(async tx=>{if(p.decision!=='dismiss')await tx.query('update works set status=$1 where id=$2',[p.decision==='hide'?'hidden':'published',report.work_id]);await tx.query("update reports set status='resolved' where id=$1",[p.report_id]);await tx.query('insert into moderation_actions(id,moderator_id,report_id,decision,reason) values($1,$2,$3,$4,$5)',[randomUUID(),userId,p.report_id,p.decision,reason]);});return {ok:true};
    }
    fail('Неизвестное действие');
  }
  return {db,rpc,addUser,authenticate,createSession,sessionUser,revokeSession,close:()=>db.close()};
}
