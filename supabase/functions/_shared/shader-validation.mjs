const fail = message => {throw new Error(message);};
const clean = (v,max) => String(v ?? '').trim().slice(0,max+1);

export function validateParameters(parameters) {
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
export function validateValues(parameters, values) {
  if (!values || typeof values!=='object' || Array.isArray(values)) fail('Некорректный пресет');
  for (const [name,value] of Object.entries(values)) {
    const p=parameters.find(x=>x.name===name); if(!p) fail('Неизвестный параметр');
    if(p.type==='float' && (typeof value!=='number' || !Number.isFinite(value) || value<p.min || value>p.max)) fail('Значение вне диапазона');
    if(p.type==='color' && (typeof value!=='string' || !/^#[0-9a-f]{6}$/i.test(value))) fail('Некорректный цвет');
  }
}

