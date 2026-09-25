import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createDatabase} from '../server/db.mjs';

const code=`void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  fragColor = vec4(fragCoord.xy / iResolution.xy, 0.4, 1.0);
}`;
const base={title:'Первый свет',description:'Тестовая работа',category:'Свет',tags:['цвет'],code,license:'MIT',parameters:[{name:'speed',label:'Скорость',type:'float',min:0,max:2,default:1}]};

test('two users can publish, discover, discuss, save and remix a version',async()=>{
  const s=await createDatabase({memory:true});
  try{
    const a=await s.addUser({email:'a@example.test',display_name:'Автор А'});
    const b=await s.addUser({email:'b@example.test',display_name:'Автор Б'});
    const publish=await s.rpc(a.id,'publish',{...base,request_id:randomUUID()});
    const feed=await s.rpc(null,'feed',{query:'Первый'});
    assert.equal(feed.items.length,1);
    assert.equal(feed.items[0].author_id,a.id);
    const detail=await s.rpc(b.id,'work',{id:publish.work_id});
    assert.equal(detail.work.revision.id,publish.revision_id);
    await s.rpc(b.id,'follow',{author_id:a.id,active:true});
    assert.equal((await s.rpc(b.id,'feed',{mode:'following'})).items.length,1);
    await s.rpc(b.id,'like',{work_id:publish.work_id,active:true});
    await s.rpc(b.id,'like',{work_id:publish.work_id,active:true});
    await s.rpc(b.id,'save',{work_id:publish.work_id,active:true});
    assert.equal((await s.rpc(b.id,'feed',{mode:'saved'})).items.length,1);
    assert.equal((await s.rpc(null,'work',{id:publish.work_id})).work.likes_count,1);
    const commentRequest=randomUUID();
    const c1=await s.rpc(b.id,'comment',{work_id:publish.work_id,request_id:commentRequest,body:'Красиво!'});
    const c2=await s.rpc(b.id,'comment',{work_id:publish.work_id,request_id:commentRequest,body:'Красиво!'});
    assert.equal(c1.id,c2.id);
    assert.equal((await s.rpc(null,'work',{id:publish.work_id})).comments.length,1);
    const remix=await s.rpc(b.id,'publish',{...base,title:'Мой свет',request_id:randomUUID(),parent_revision_id:publish.revision_id});
    assert.equal((await s.rpc(null,'work',{id:remix.work_id})).parent.revision_id,publish.revision_id);
    await assert.rejects(s.rpc(b.id,'publish',{...base,request_id:randomUUID(),work_id:publish.work_id,base_revision_id:publish.revision_id}),/Нет доступа/);
    await assert.rejects(s.rpc(null,'draft_list',{}),/войти/);
  }finally{await s.close()}
});

test('publication is idempotent and drafts preserve conflicting edits',async()=>{
  const s=await createDatabase({memory:true});
  try{
    const a=await s.addUser({email:'a@example.test'});
    const request_id=randomUUID();
    const first=await s.rpc(a.id,'publish',{...base,request_id});
    assert.deepEqual(await s.rpc(a.id,'publish',{...base,request_id}),first);
    await assert.rejects(s.rpc(a.id,'publish',{...base,title:'Изменено',request_id}),/уже использован/);
    const draftId=randomUUID();
    const firstDraft=await s.rpc(a.id,'draft_save',{id:draftId,expected_version:0,body:{title:'Первая'}});
    assert.equal(firstDraft.draft.version,1);
    const current=await s.rpc(a.id,'draft_save',{id:draftId,expected_version:1,body:{title:'Облако'}});
    assert.equal(current.draft.version,2);
    const conflict=await s.rpc(a.id,'draft_save',{id:draftId,expected_version:1,body:{title:'Устройство'}});
    assert.equal(conflict.conflict,true);
    assert.notEqual(conflict.draft.id,draftId);
    const drafts=await s.rpc(a.id,'draft_list');
    assert.equal(drafts.length,2);
    assert.deepEqual(new Set(drafts.map(d=>d.body.title)),new Set(['Облако','Устройство']));
  }finally{await s.close()}
});

test('moderation hides public content and validates presets',async()=>{
  const s=await createDatabase({memory:true});
  try{
    const a=await s.addUser({email:'a@example.test'});
    const b=await s.addUser({email:'b@example.test'});
    const mod=await s.addUser({email:'mod@example.test'});
    const published=await s.rpc(a.id,'publish',{...base,request_id:randomUUID()});
    await assert.rejects(s.rpc(b.id,'preset',{revision_id:published.revision_id,values:{speed:10}}),/диапазона/);
    await s.rpc(b.id,'preset',{revision_id:published.revision_id,values:{speed:1.5}});
    assert.deepEqual((await s.rpc(b.id,'work',{id:published.work_id})).preset,{speed:1.5});
    await s.rpc(b.id,'report',{work_id:published.work_id,reason:'Проверка модерации'});
    await assert.rejects(s.rpc(b.id,'moderation_list'),/Нет доступа/);
    await s.db.query('insert into moderators(user_id) values($1)',[mod.id]);
    const report=(await s.rpc(mod.id,'moderation_list')).reports[0];
    await s.rpc(mod.id,'moderate',{report_id:report.id,decision:'hide',reason:'Проверено'});
    assert.equal((await s.rpc(null,'feed',{})).items.length,0);
    await assert.rejects(s.rpc(null,'work',{id:published.work_id}),/не найдена/);
    assert.equal((await s.rpc(a.id,'work',{id:published.work_id})).work.status,'hidden');
  }finally{await s.close()}
});

test('a hundred works paginate without duplicates or shipping shader source',async()=>{
  const s=await createDatabase({memory:true});
  try{
    const author=await s.addUser({email:'many@example.test'});
    await s.db.query(`with entries as materialized (
      select gen_random_uuid() as work_id,gen_random_uuid() as revision_id,n
      from generate_series(1,105) as n
    ), inserted as (
      insert into works(id,author_id,title,category,current_revision_id,created_at)
      select work_id,$1,'Серия '||n,case when n%2=0 then 'Свет' else 'Природа' end,revision_id,now()-n*interval '1 second'
      from entries returning id
    )
    insert into revisions(id,work_id,code,license)
    select e.revision_id,e.work_id,$2,'MIT' from entries e join inserted w on w.id=e.work_id`,[author.id,code]);
    const seen=new Set();let cursor=null;let pages=0;
    do{
      const result=await s.rpc(null,'feed',{limit:12,cursor});
      assert.ok(result.items.length<=12);
      for(const item of result.items){assert.ok(!seen.has(item.id));seen.add(item.id);assert.equal(item.revision.code,undefined)}
      cursor=result.next_cursor;pages++;
    }while(cursor);
    assert.equal(seen.size,105);
    assert.equal(pages,9);
    const filtered=await s.rpc(null,'feed',{category:'Свет',query:'Серия 2'});
    assert.ok(filtered.items.length>0);
    assert.ok(filtered.items.every(item=>item.category==='Свет' && item.title.includes('2')));
  }finally{await s.close()}
});
