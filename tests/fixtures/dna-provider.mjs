// Isolated HTTP integration test preload. Production never imports this file.
const actualFetch=globalThis.fetch;
globalThis.fetch=async (url,options)=>{
  if(String(url)!=='https://openrouter.ai/api/v1/chat/completions')return actualFetch(url,options);
  const body=JSON.parse(options.body);
  if(body.model.startsWith('~openai/') && body.messages[1].content.includes('partial-failure'))return new Response('private upstream error',{status:503});
  return Response.json({choices:[{finish_reason:'stop',message:{content:JSON.stringify({title:'Свет DNA',description:'Интеграционный тест',code:'void mainImage(out vec4 c, in vec2 p) { c=vec4(p/iResolution.xy,0.5,1.0); }',parameters:[]})}}]});
};
