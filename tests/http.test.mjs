import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {createServer} from 'node:net';
import {randomUUID} from 'node:crypto';

const root=resolve(import.meta.dirname,'..');
async function freePort(){return new Promise((resolve,reject)=>{const s=createServer();s.once('error',reject);s.listen(0,'127.0.0.1',()=>{const port=s.address().port;s.close(()=>resolve(port))})})}
async function ready(url,child){for(let i=0;i<100;i++){if(child.exitCode!==null)throw new Error('Сервер завершился до запуска');try{const r=await fetch(`${url}/api/config`);if(r.ok)return}catch{}await new Promise(r=>setTimeout(r,100))}throw new Error('Сервер не запустился')}

test('HTTP auth, public package and OG metadata work together',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'shader-gallery-test-'));
  const port=await freePort();const url=`http://127.0.0.1:${port}`;
  const child=spawn(process.execPath,['server/index.mjs','--production'],{cwd:root,env:{...process.env,PORT:String(port),DATA_DIR:join(dir,'db'),PUBLIC_ORIGIN:url},stdio:'ignore'});
  try{
    await ready(url,child);
    const signup=await fetch(`${url}/api/auth/signup`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:'author@example.test',password:'long-password',display_name:'Автор'})});
    assert.equal(signup.status,200);
    const cookie=signup.headers.get('set-cookie')?.split(';')[0];assert.ok(cookie);
    const author=(await signup.json()).data;assert.ok(author.id);
    const preview='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
    const payload={request_id:randomUUID(),title:'Проверка пакета',description:'Ссылка для двух устройств',category:'Свет',tags:['тест'],code:'void mainImage(out vec4 c, in vec2 xy) { c = vec4(xy.xy / iResolution.xy, 0.5, 1.0); }',license:'MIT',parameters:[],preview};
    const published=await fetch(`${url}/api/rpc`,{method:'POST',headers:{'content-type':'application/json',cookie},body:JSON.stringify({action:'publish',payload})});
    assert.equal(published.status,200);
    const ids=(await published.json()).data;
    const pkg=await fetch(`${url}/api/packages/${ids.work_id}/${ids.revision_id}`);
    assert.equal(pkg.status,200);
    const manifest=(await pkg.json()).data;
    assert.equal(manifest.revisionId,ids.revision_id);
    assert.equal(manifest.code,payload.code);
    assert.equal(manifest.sourceUrl,`${url}/works/${ids.work_id}?revision=${ids.revision_id}`);
    const page=await fetch(`${url}/works/${ids.work_id}`);
    assert.equal(page.status,200,await page.clone().text());
    assert.match(await page.text(),/og:title/);
    const feed=await fetch(`${url}/api/rpc`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action:'feed',payload:{}})});
    const card=(await feed.json()).data.items[0];
    assert.equal(card.revision.preview,preview);
    assert.equal(card.revision.code,undefined);
    const updated=await fetch(`${url}/api/rpc`,{method:'POST',headers:{'content-type':'application/json',cookie},body:JSON.stringify({action:'publish',payload:{...payload,request_id:randomUUID(),work_id:ids.work_id,base_revision_id:ids.revision_id,code:payload.code+'\n// new',preview:undefined}})});
    assert.equal(updated.status,200);
    const oldPage=await fetch(`${url}/works/${ids.work_id}?revision=${ids.revision_id}`);
    assert.match(await oldPage.text(),new RegExp(`/og/${ids.work_id}\\.png\\?revision=${ids.revision_id}`));
    const oldImage=await fetch(`${url}/og/${ids.work_id}.png?revision=${ids.revision_id}`);
    assert.equal(oldImage.status,200);
    assert.deepEqual(Buffer.from(await oldImage.arrayBuffer()),Buffer.from(preview.split(',')[1],'base64'));
    const fallback=await fetch(`${url}/og/${ids.work_id}.png`);
    assert.equal(fallback.status,200);
    assert.match(fallback.headers.get('content-type'),/image\/png/);
    const anon=await fetch(`${url}/api/rpc`,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action:'draft_list',payload:{}})});
    assert.equal(anon.status,400);
  }finally{child.kill();await rm(dir,{recursive:true,force:true})}
});
