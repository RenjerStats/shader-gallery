-- Reproducible public demo content. Reserved .invalid addresses and seed-only
-- password markers make these visible profiles, not sign-in accounts.
begin;

insert into public.users (id,email,password_hash,username,display_name,bio,created_at) values
('d0000000-0000-4000-8000-000000000001','lena@shader-gallery.invalid','seed-only-no-login','lena_north','Лена Север','Свет, туман и холодные берега.',now()-interval '45 days'),
('d0000000-0000-4000-8000-000000000002','mika@shader-gallery.invalid','seed-only-no-login','mika_forms','Мика Формы','Геометрия, которая не стоит на месте.',now()-interval '40 days'),
('d0000000-0000-4000-8000-000000000003','artem@shader-gallery.invalid','seed-only-no-login','artem_glow','Артём Луч','Цветные поля и мягкие источники света.',now()-interval '35 days'),
('d0000000-0000-4000-8000-000000000004','sora@shader-gallery.invalid','seed-only-no-login','sora_garden','Сора Сад','Органические формы и воображаемая ботаника.',now()-interval '30 days'),
('d0000000-0000-4000-8000-000000000005','niko@shader-gallery.invalid','seed-only-no-login','niko_waves','Нико Волна','Море, стекло и отражения.',now()-interval '25 days'),
('d0000000-0000-4000-8000-000000000006','ira@shader-gallery.invalid','seed-only-no-login','ira_atelier','Ира Ателье','Цифровые материалы и ручные фактуры.',now()-interval '20 days'),
('d0000000-0000-4000-8000-000000000007','lev@shader-gallery.invalid','seed-only-no-login','lev_signal','Лев Сигнал','Ритм, импульсы и синтетический цвет.',now()-interval '15 days'),
('d0000000-0000-4000-8000-000000000008','maya@shader-gallery.invalid','seed-only-no-login','maya_dreams','Майя Дым','Тихие места, которых нет на карте.',now()-interval '10 days')
on conflict (id) do update set email=excluded.email,password_hash=excluded.password_hash,username=excluded.username,display_name=excluded.display_name,bio=excluded.bio;

insert into public.works (id,author_id,title,description,category,tags,status,curated,created_at,updated_at) values
('e0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000001','Северное сияние','Медленные полосы света дрейфуют над ночным горизонтом. Настройте оттенок и скорость свечения.','Природа',array['сияние','ночь','градиент'],'published',true,now()-interval '9 days',now()-interval '9 days'),
('e0000000-0000-4000-8000-000000000002','d0000000-0000-4000-8000-000000000002','Сад орбит','Планеты и тонкие орбиты собираются в небольшой движущийся атлас.','Геометрия',array['орбиты','кольца','космос'],'published',true,now()-interval '8 days',now()-interval '8 days'),
('e0000000-0000-4000-8000-000000000003','d0000000-0000-4000-8000-000000000003','Жидкий янтарь','Тёплые световые капли медленно перетекают друг в друга.','Свет',array['янтарь','свечение','жидкость'],'published',true,now()-interval '7 days',now()-interval '7 days'),
('e0000000-0000-4000-8000-000000000004','d0000000-0000-4000-8000-000000000004','Ночная оранжерея','Абстрактные листья раскрываются навстречу цветным световым пятнам.','Природа',array['листья','ботаника','ночь'],'published',false,now()-interval '6 days',now()-interval '6 days'),
('e0000000-0000-4000-8000-000000000005','d0000000-0000-4000-8000-000000000005','Дождь на стекле','Капли скользят по невидимому стеклу над синим вечерним городом.','Абстракция',array['дождь','стекло','синий'],'published',true,now()-interval '5 days',now()-interval '5 days'),
('e0000000-0000-4000-8000-000000000006','d0000000-0000-4000-8000-000000000006','Керамика и золото','Слои цвета и тонкие золотые прожилки напоминают глазурованную керамику.','Абстракция',array['керамика','золото','фактура'],'published',false,now()-interval '4 days',now()-interval '4 days'),
('e0000000-0000-4000-8000-000000000007','d0000000-0000-4000-8000-000000000007','Хроматический пульс','Радиальные импульсы расходятся по спектру, словно сигнал в темноте.','Свет',array['пульс','спектр','ритм'],'published',false,now()-interval '3 days',now()-interval '3 days'),
('e0000000-0000-4000-8000-000000000008','d0000000-0000-4000-8000-000000000008','Тихий берег','Слои тумана скользят над воображаемым морем перед рассветом.','Природа',array['море','туман','рассвет'],'published',false,now()-interval '2 days',now()-interval '2 days'),
('e0000000-0000-4000-8000-000000000009','d0000000-0000-4000-8000-000000000002','Бумажные фонари','Светящиеся геометрические фонари качаются на тонких невидимых нитях.','Геометрия',array['фонари','геометрия','свет'],'published',false,now()-interval '1 day',now()-interval '1 day'),
('e0000000-0000-4000-8000-000000000010','d0000000-0000-4000-8000-000000000005','Перламутровый прилив','Мягкие волны смешивают розовый, бирюзовый и цвет морской глубины.','Другое',array['перламутр','волны','цвет'],'published',false,now()-interval '12 hours',now()-interval '12 hours')
on conflict (id) do update set author_id=excluded.author_id,title=excluded.title,description=excluded.description,category=excluded.category,tags=excluded.tags,status=excluded.status,curated=excluded.curated,updated_at=excluded.updated_at;

insert into public.revisions (id,work_id,code,license,parameters,parent_revision_id,preview,created_at) values
('f0000000-0000-4000-8000-000000000001','e0000000-0000-4000-8000-000000000001',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float t = iTime * speed * 0.25;
  vec3 col = vec3(0.012, 0.019, 0.055);
  for (int i = 0; i < 8; i++) {
    float f = float(i);
    float wave = sin(uv.x * 7.0 + t + f * 0.32) * 0.11;
    float y = uv.y - 0.46 - wave - f * 0.023;
    float glow = exp(-abs(y) * 22.0) * (0.5 + 0.5 * sin(uv.x * 35.0 + f + t));
    col += tint * glow * 0.09;
  }
  fragColor = vec4(col, 1.0);
}
$shader$,'MIT','[{"name":"tint","label":"Цвет сияния","type":"color","default":"#6ce9c0"},{"name":"speed","label":"Скорость","type":"float","min":0,"max":3,"default":1}]'::jsonb,null,null,now()-interval '9 days'),
('f0000000-0000-4000-8000-000000000002','e0000000-0000-4000-8000-000000000002',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
  float r = length(p);
  float a = atan(p.y, p.x);
  float orbit = pow(0.5 + 0.5 * cos(r * density * 6.0 - iTime), 24.0) * exp(-r * 1.1);
  float spokes = pow(0.5 + 0.5 * cos(a * 5.0 + iTime * 0.15), 14.0);
  vec3 col = vec3(0.018, 0.022, 0.055) + accent * orbit * (0.45 + spokes);
  fragColor = vec4(col, 1.0);
}
$shader$,'CC0-1.0','[{"name":"accent","label":"Цвет орбит","type":"color","default":"#a893ff"},{"name":"density","label":"Частота","type":"float","min":2,"max":14,"default":7}]'::jsonb,null,null,now()-interval '8 days'),
('f0000000-0000-4000-8000-000000000003','e0000000-0000-4000-8000-000000000003',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float t = iTime * 0.35;
  float a = sin(uv.x * 8.0 + sin(uv.y * 5.0 - t) * 2.0 + t);
  float b = cos(uv.y * 9.0 - sin(uv.x * 6.0 + t) * 1.5);
  float field = a * b;
  float glow = smoothstep(0.18, 0.95, field) * (0.55 + 0.45 * sin(t + uv.x * 3.0));
  vec3 col = vec3(0.045, 0.018, 0.012) + gold * glow * 0.9;
  col += vec3(0.22, 0.055, 0.018) * smoothstep(-0.2, 0.85, field) * 0.22;
  fragColor = vec4(col, 1.0);
}
$shader$,'MIT','[{"name":"gold","label":"Оттенок янтаря","type":"color","default":"#ffc36b"}]'::jsonb,null,null,now()-interval '7 days'),
('f0000000-0000-4000-8000-000000000004','e0000000-0000-4000-8000-000000000004',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
  float a = atan(p.y, p.x);
  float r = length(p);
  float petal = cos(a * 6.0 + sin(r * 5.0 - iTime * 0.3) * 0.5);
  float shape = (1.0 - smoothstep(0.44, 0.48, abs(r - (0.38 + petal * 0.13))));
  float vein = (1.0 - smoothstep(0.0, 0.025, abs(sin(a * 6.0) * r)));
  vec3 col = vec3(0.018, 0.035, 0.035) + leaf * shape * (0.55 + vein * 0.45);
  col += vec3(0.26, 0.12, 0.34) * exp(-length(p - vec2(0.42, 0.3)) * 5.0) * 0.4;
  fragColor = vec4(col, 1.0);
}
$shader$,'MIT','[{"name":"leaf","label":"Цвет листьев","type":"color","default":"#71dda5"}]'::jsonb,null,null,now()-interval '6 days'),
('f0000000-0000-4000-8000-000000000005','e0000000-0000-4000-8000-000000000005',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float t = iTime * 0.8;
  vec2 q = uv * vec2(18.0, 9.0);
  vec2 cell = fract(q + vec2(0.0, t + floor(q.x) * 0.17));
  float drop = (1.0 - smoothstep(0.0, 0.09, abs(cell.x - 0.5))) * (1.0 - smoothstep(0.15, 0.8, cell.y));
  float lines = (1.0 - smoothstep(0.0, 0.03, abs(fract(uv.x * 34.0 + sin(uv.y * 4.0) * 0.04) - 0.5)));
  vec3 city = vec3(0.025, 0.06, 0.13) + vec3(0.06, 0.12, 0.24) * (1.0 - uv.y);
  vec3 col = city + rainColor * (drop * 0.65 + lines * 0.08);
  fragColor = vec4(col, 1.0);
}
$shader$,'CC0-1.0','[{"name":"rainColor","label":"Цвет бликов","type":"color","default":"#8de8ff"}]'::jsonb,null,null,now()-interval '5 days'),
('f0000000-0000-4000-8000-000000000006','e0000000-0000-4000-8000-000000000006',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p = fragCoord / iResolution.xy;
  float t = iTime * 0.18;
  float bands = sin(p.x * 5.0 + sin(p.y * 7.0 + t) * 1.7);
  float crack = abs(sin(p.x * 31.0 + cos(p.y * 19.0 + bands) * 2.0));
  float goldLine = (1.0 - smoothstep(0.0, 0.045, crack)) * smoothstep(-0.2, 0.6, bands);
  vec3 clay = mix(vec3(0.08, 0.045, 0.09), glaze, 0.5 + 0.5 * bands);
  clay += vec3(1.0, 0.61, 0.19) * goldLine * 0.8;
  fragColor = vec4(clay, 1.0);
}
$shader$,'MIT','[{"name":"glaze","label":"Цвет глазури","type":"color","default":"#9168c5"}]'::jsonb,null,null,now()-interval '4 days'),
('f0000000-0000-4000-8000-000000000007','e0000000-0000-4000-8000-000000000007',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
  float r = length(p);
  float wave = 0.5 + 0.5 * cos(r * 18.0 - iTime * tempo * 2.0);
  float ring = pow(wave, 18.0) * exp(-r * 1.35);
  float halo = exp(-abs(r - 0.48 - 0.08 * sin(iTime * tempo)) * 12.0);
  vec3 col = vec3(0.012, 0.016, 0.04) + pulseColor * (ring * 0.7 + halo * 0.3);
  col += vec3(0.3, 0.05, 0.38) * halo * (0.4 + 0.4 * sin(r * 9.0 - iTime));
  fragColor = vec4(col, 1.0);
}
$shader$,'MIT','[{"name":"pulseColor","label":"Цвет импульса","type":"color","default":"#67e8ff"},{"name":"tempo","label":"Темп","type":"float","min":0.2,"max":2,"default":1}]'::jsonb,null,null,now()-interval '3 days'),
('f0000000-0000-4000-8000-000000000008','e0000000-0000-4000-8000-000000000008',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float horizon = 0.44 + 0.03 * sin(uv.x * 5.0 + iTime * 0.12);
  float mist = exp(-abs(uv.y - horizon) * 18.0);
  float water = smoothstep(horizon - 0.02, horizon + 0.02, uv.y);
  float ripple = sin(uv.x * 24.0 + uv.y * 12.0 + iTime * 0.4) * 0.5 + 0.5;
  vec3 sky = mix(vec3(0.055, 0.065, 0.13), vec3(0.48, 0.22, 0.29), 1.0 - uv.y);
  vec3 ocean = sea * (0.45 + 0.18 * ripple);
  vec3 col = mix(sky + vec3(0.43, 0.2, 0.24) * mist, ocean, water);
  fragColor = vec4(col, 1.0);
}
$shader$,'CC0-1.0','[{"name":"sea","label":"Цвет воды","type":"color","default":"#477eac"}]'::jsonb,null,null,now()-interval '2 days'),
('f0000000-0000-4000-8000-000000000009','e0000000-0000-4000-8000-000000000009',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 p = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
  p.x += 0.08 * sin(iTime * 0.7 + p.y * 3.0);
  float box = max(abs(p.x) * 0.7, abs(p.y + 0.08) * 0.9);
  float body = (1.0 - smoothstep(0.39, 0.42, box)) * smoothstep(0.25, 0.28, box);
  float wire = smoothstep(0.016, 0.0, abs(abs(p.x) - 0.28)) + smoothstep(0.015, 0.0, abs(p.y - 0.22));
  float glow = exp(-length(p) * 2.7) * (0.7 + 0.3 * sin(iTime * 2.0));
  vec3 col = vec3(0.018, 0.022, 0.06) + lantern * (body * 0.65 + glow * 0.32) + vec3(0.45, 0.3, 0.13) * wire;
  fragColor = vec4(col, 1.0);
}
$shader$,'MIT','[{"name":"lantern","label":"Цвет фонаря","type":"color","default":"#ffb76a"}]'::jsonb,null,null,now()-interval '1 day'),
('f0000000-0000-4000-8000-000000000010','e0000000-0000-4000-8000-000000000010',$shader$
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = fragCoord / iResolution.xy;
  float t = iTime * speed * 0.25;
  float a = sin(uv.x * 8.0 + uv.y * 5.0 + t);
  float b = cos(uv.y * 11.0 - uv.x * 4.0 - t * 0.7);
  float pearl = 0.5 + 0.5 * sin((a + b) * 3.0 + uv.x * 2.0);
  vec3 col = mix(vec3(0.025, 0.08, 0.14), tide, smoothstep(0.12, 0.92, pearl));
  col += vec3(0.9, 0.47, 0.64) * pow(pearl, 8.0) * 0.42;
  fragColor = vec4(col, 1.0);
}
$shader$,'MIT','[{"name":"tide","label":"Цвет прилива","type":"color","default":"#45c8bf"},{"name":"speed","label":"Скорость","type":"float","min":0,"max":2,"default":0.7}]'::jsonb,null,null,now()-interval '12 hours')
on conflict (id) do update set work_id=excluded.work_id,code=excluded.code,license=excluded.license,parameters=excluded.parameters,parent_revision_id=excluded.parent_revision_id,preview=coalesce(excluded.preview,public.revisions.preview);

update public.works w
set current_revision_id = r.id
from public.revisions r
where r.work_id = w.id
  and w.id::text like 'e0000000-0000-4000-8000-0000000000%'
  and r.id::text = replace(w.id::text, 'e', 'f');

commit;
