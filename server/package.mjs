import {createHash} from 'node:crypto';

export function makePackage(detail,origin){
  const work=detail.work,revision=work.revision;
  return {
    schemaVersion:1,
    runtimeKind:'shader',
    renderProfile:'webgl2-gles3-single-pass',
    entryPoint:'mainImage',
    workId:work.id,
    revisionId:revision.id,
    contentHash:createHash('sha256').update(revision.code,'utf8').digest('hex'),
    code:revision.code,
    licenseId:revision.license,
    parentRevisionId:revision.parent_revision_id,
    author:{id:work.author_id,name:work.author.display_name},
    title:work.title,
    parameters:revision.parameters,
    inputs:['iTime','iResolution','iMouse','iTilt'],
    sourceUrl:`${origin}/works/${work.id}?revision=${revision.id}`,
    preview:revision.preview,
    publishedAt:revision.created_at
  };
}
