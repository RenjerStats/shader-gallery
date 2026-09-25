import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash,randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {makePackage} from '../server/package.mjs';

test('Android package identifies an immutable revision and verifies its source',async()=>{
  const workId=randomUUID(),revisionId=randomUUID();
  const code='void mainImage(out vec4 c, in vec2 xy) { c = vec4(0.2,0.4,0.6,1.0); }';
  const pkg=makePackage({work:{id:workId,author_id:randomUUID(),title:'Тест',author:{display_name:'Автор'},revision:{id:revisionId,code,license:'MIT',parent_revision_id:null,parameters:[],preview:null,created_at:new Date().toISOString()}}},'https://example.test');
  const schema=JSON.parse(await readFile(new URL('../packages/shader-contract/v1.schema.json',import.meta.url),'utf8'));
  for(const field of schema.required)assert.ok(Object.hasOwn(pkg,field),field);
  assert.equal(pkg.contentHash,createHash('sha256').update(code,'utf8').digest('hex'));
  assert.equal(pkg.sourceUrl,`https://example.test/works/${workId}?revision=${revisionId}`);
  assert.equal(pkg.renderProfile,'webgl2-gles3-single-pass');
});
