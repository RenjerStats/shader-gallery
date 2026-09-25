# Milestones 3–5: implementation contract

Status: implementation decisions 2026-09-22. Existing dist prototype is preserved; new Vite sources in apps/web, output build/. No Android implementation in this scope. Supabase is production backend. Local Node + PGlite is a loopback development backend using the SAME SQL functions/RLS for two-user acceptance without cloud credentials; it is not production auth.

## Shared API

Single PostgreSQL RPC `public.gallery(action text, payload jsonb default '{}'::jsonb) returns jsonb`. Local HTTP POST /api/rpc accepts {action,payload}, returns {data} or {error:string}. Authenticated identity comes ONLY from auth.uid(), never payload. Supabase calls rpc('gallery',{action,payload}). Client wraps errors by throwing Error. All times ISO strings, UUID identifiers.

Types:

```ts
type Parameter = {name:string;label:string;type:'float'|'color';min?:number;max?:number;default:number|string};
type Profile = {id:string;username:string;display_name:string;bio:string;is_following:boolean;is_blocked:boolean;is_moderator?:boolean};
type Work = {id:string;author_id:string;title:string;description:string;category:string;tags:string[];status:string;curated:boolean;created_at:string;updated_at:string;current_revision_id:string;author:Profile;likes_count:number;saves_count:number;liked:boolean;saved:boolean;revision:Revision};
type Revision = {id:string;work_id:string;code:string;license:string;parameters:Parameter[];parent_revision_id:string|null;preview:string|null;created_at:string};
type Draft = {id:string;version:number;body:Record<string,unknown>;updated_at:string;conflict_of:string|null};
type Comment = {id:string;work_id:string;author:Profile;body:string;created_at:string};
```

`preview` is an optional bounded PNG data URL <= 400000 chars, attached immutably to revision. No arbitrary HTML/SVG/remote preview URLs. Server OG image route serves its decoded PNG, never fetches an untrusted URL. Default null uses generated non-user-executable image. Runtime supports single-pass GLSL ES 3.00, iTime/iResolution/iMouse, custom float/color uniforms. Licenses initially MIT, CC0-1.0 only; remix inherits license and attribution through immutable parent revision. No external code/content import.

Actions and payload/return:

- feed {mode:'new'|'following'|'curated'|'saved',query?:string,category?:string,author_id?:uuid,cursor?:{created_at:string,id:uuid},limit?:number} => {items:FeedWork[],next_cursor:object|null}, where FeedWork contains only id, author_id, title, category, author.display_name, revision.id and revision.preview. Defaults new, limit 12 max 30; stable keyset (created_at,id) desc. Filter blocked authors and unpublished works. Following/saved require auth. Full shader source is loaded only by work/package requests.
- work {id:uuid,revision_id?:uuid} => {work:Work,comments:Comment[],revisions:{id,created_at}[],parent:{work_id,revision_id,title,author:string}|null,remixes:{id,title}[],preset:Record<string,number|string>|null}. Optional revision must belong to work. Hidden works inaccessible except author/moderator; comments publicly visible only if work is public. Parent metadata must not leak hidden/private originals.
- profile {id:uuid} => Profile. profile_update {username,display_name,bio} => Profile (own only).
- publish {request_id:uuid,work_id?:uuid,base_revision_id?:uuid,title,description,category,tags,code,license,parameters,preview?:string,parent_revision_id?:uuid} => {work_id,revision_id}. Atomic and idempotent per user + request_id with payload fingerprint. Retries same body return original, different body reject. Existing work locks row; require base_revision_id equal current; preserve status/author/curated and immutable lineage. Hidden work cannot self-unhide. Reject nonexistent/inaccessible parent, license changes on remix, invalid parameters/code lengths.
- draft_list {} => Draft[]. draft_save {id:uuid,expected_version:number,body:object} => {draft:Draft,conflict:boolean}; create version 1 with expected_version 0, optimistic concurrent updates. Stale save creates private conflict copy retaining BOTH cloud and incoming bodies, not overwriting cloud. draft_delete {id} => {ok:true}.
- like/save {work_id,active:boolean} => {ok:true}; idempotent set/unset (not toggle), require readable published work.
- follow {author_id,active:boolean} => {ok:true}; prohibit self, missing author.
- comment {work_id,request_id:uuid,body:string} => {id}; idempotent per user/request_id; reject changed retries and empty/>2000 body.
- preset {revision_id,values:object} => {ok:true}; private per-user revision, validate names/type/range/color against revision.parameters; empty values valid.
- report {work_id,reason:string} => {ok:true}; own report with limits.
- block {author_id,active:boolean} => {ok:true}; own block, prohibits self; blocked profiles should also not be interactable.
- moderation_list {} => {reports:{id,work_id,reason,created_at,work_title,status}[]}; only moderator, status open/resolved.
- moderate {report_id,decision:'hide'|'restore'|'dismiss',reason:string} => {ok:true}; role from private moderator table, audit immutable. Hide removes public reads to revisions/comments/assets, restore deliberate.

## Database ownership

SQL agent owns supabase/migrations/202609220001_gallery.sql, supabase/migrations/202609220002_storage.sql, tests/database.test.mjs only. tables profiles,works,work_revisions,drafts,likes,saves,follows,comments,presets,assets,reports,user_blocks,moderators,moderation_actions plus request tracking as needed. public tables RLS, REVOKE direct writes from anon/authenticated (RPC only). SELECT grants + policies for readable content/own private data; private moderator/request tables no client grants. Private helpers explicit search_path and no public execute (except RLS visibility helper if needed). Avoid recursive RLS. Trigger profile from auth.users using UUID-based username, no leaking email. auth.users exists in real Supabase; server bootstrap makes minimal mock auth schema/users/uid for local tests. Do NOT create auth schema in production migration. pgcrypto extension unavailable in PGlite: built-in md5 for idempotency fingerprint (not security) and gen_random_uuid available; no extension requirement.

Storage migration bucket gallery-assets private: uploads owner path prefix auth.uid, size/type limits PNG/JPEG/WebP; metadata `assets(id uuid,owner_id uuid,revision_id uuid nullable,storage_path text unique,mime_type text,size_bytes int)`. Direct public read only for asset linked to readable published revision; uploads/deletes own staged assets only. Separate migration can be tested with local storage schema scaffolding, do not require running object storage for primary SQL tests.

Tests support (root agent will create server/db.mjs): `await createDatabase({memory:true,seed:false})` returns `{db, rpc(userIdOrNull,action,payload), addUser({id?,email,display_name?}), close()}`. addUser creates auth user triggering profile; returns {id,email}. `db` PGlite available for SQL grants/RLS/adversarial assertions; rpc wraps transaction SET LOCAL ROLE anon/authenticated, set_config('request.jwt.claim.sub',id,true), then gallery. Tests can use db.query to insert moderators as DB owner. Node test/assert built-ins.

## Frontend ownership and runtime interface

Frontend agent owns apps/web/App.tsx, apps/web/main.tsx, apps/web/style.css, apps/web/components/* only. Root agent creates api.ts, runtime.ts, drafts.ts, types.ts, templates.ts and config. Do NOT change dist/. UI Russian, retain visual theme from prototype with improvements; semantic forms, stable labels.

api.ts exports `rpc<T>(action:string,payload?:object):Promise<T>`, `getSession():Promise<User|null>`, `signIn(email,password):Promise<User|null>`, `signUp(email,password,displayName):Promise<User|null>`, `signOut():Promise<void>`, `signInGoogle():Promise<void>`, `authMode:'local'|'supabase'`. User={id,email?}. Profile via rpc('profile',{id}). Email sign up may return null pending verification. Google only if supabase. All auth persistence handled by api.

runtime.ts exports class ShaderRenderer: constructor(canvas:HTMLCanvasElement); compile(code:string,parameters:Parameter[]):{ok:boolean,error?:string,line?:number}; setValues(values:Record<string,number|string>):void; setPaused(paused:boolean):void; snapshot():string; destroy():void. Successful compile replaces program; failure preserves last-good. Initial paused based reduced motion. One instance in work view/editor only, NO live canvas per feed card. preview <img> or CSS placeholder. No browser compilation on feed.

drafts.ts exports `saveLocalDraft(ownerId:string,draft:LocalDraft):Promise<void>`, `listLocalDrafts(ownerId:string):Promise<LocalDraft[]>`, `deleteLocalDraft(ownerId:string,id:string):Promise<void>`. LocalDraft={id,version,body,updated_at,conflict_of:null|string}. ownerId user.id or guest; do not merge guests into accounts implicitly. Autosave local after edit; explicit cloud save calls draft_save with expected_version. Show conflicts with both copies and choice. Never lose local copy on network/save failure. Root tests cover storage helper separately if needed.

templates.ts exports `templates: {id,title,code,parameters:Parameter[]}[]`. types.ts exports Parameter/Profile/Work/Revision/Draft/Comment/User as above.

Frontend routes via history/popstate: / (feed), /saved, /profile/:id, /editor (new, query remix=<work-id> or edit=<work-id>), /works/:id (?revision=<id> optional), /drafts, /moderation. Work page visible Remix, save/like, follow/block, report, comments, presets, version list, parent link, QR + share via qrcode package. UI guards auth but backend also validates. Editor CodeMirror cpp() language approximates GLSL; lint diagnostics on compile. Compiler success code+parameters fingerprint must match current draft before publish. Request UUID stable across retry; change on payload change. Choose MIT/CC0. Form-based parameter rows float/color. Publication shows attribution and license before confirmation. Existing edit must send base_revision_id. Reset local/cloud draft after successful publish only. Draft conflict message and local files list. Profile editable own only. Feed paging/search/category + explicit empty/error/loading. Moderator page only shown for role; role checked backend too. No hardcoded current user, no fake likes.
