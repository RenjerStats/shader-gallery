import {useEffect,useRef} from 'react';
import {EditorView,basicSetup} from 'codemirror';
import {cpp} from '@codemirror/lang-cpp';
export default function CodeEditor({value,onChange}:{value:string;onChange:(value:string)=>void}){
  const root=useRef<HTMLDivElement>(null),view=useRef<EditorView|null>(null),callback=useRef(onChange);callback.current=onChange;
  useEffect(()=>{if(!root.current)return;const editor=new EditorView({doc:value,extensions:[basicSetup,cpp(),EditorView.theme({'&':{backgroundColor:'#faf9f5',color:'#30352f'},'.cm-gutters':{backgroundColor:'#f2f1eb',color:'#858980',border:'none'},'.cm-activeLine':{backgroundColor:'#f2f3eb'},'.cm-cursor':{borderLeftColor:'#315ee8'}},{dark:false}),EditorView.updateListener.of(update=>{if(update.docChanged)callback.current(update.state.doc.toString())})],parent:root.current});view.current=editor;return()=>{editor.destroy();view.current=null}},[]);
  useEffect(()=>{const editor=view.current;if(editor&&editor.state.doc.toString()!==value)editor.dispatch({changes:{from:0,to:editor.state.doc.length,insert:value}})},[value]);
  return <div className="code-editor" ref={root} aria-label="Исходный код"/>;
}
