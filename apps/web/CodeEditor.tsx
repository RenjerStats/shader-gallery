import {useEffect,useRef} from 'react';
import {Compartment} from '@codemirror/state';
import {EditorView,basicSetup} from 'codemirror';
import {cpp} from '@codemirror/lang-cpp';
import {syntaxHighlighting,HighlightStyle} from '@codemirror/language';
import {tags} from '@lezer/highlight';

const darkHighlight=HighlightStyle.define([
  {tag:tags.comment,color:'#8793a6'},
  {tag:[tags.keyword,tags.controlKeyword],color:'#c7a4ff'},
  {tag:[tags.number,tags.bool,tags.atom],color:'#f7bd86'},
  {tag:tags.string,color:'#a8dca2'},
  {tag:[tags.function(tags.variableName),tags.definition(tags.variableName)],color:'#89c8ff'},
  {tag:tags.typeName,color:'#8dd9d4'},
]);
const editorTheme=(dark:boolean)=>[
  EditorView.theme({
    '&':{backgroundColor:dark?'#13151e':'#faf9f5',color:dark?'#edf0f8':'#30352f'},
    '.cm-content':{caretColor:dark?'#c4ceff':'#315ee8'},
    '.cm-gutters':{backgroundColor:dark?'#1b1e29':'#f2f1eb',color:dark?'#8691a7':'#858980',border:'none'},
    '.cm-activeLine,.cm-activeLineGutter':{backgroundColor:dark?'#242938':'#f2f3eb'},
    '.cm-selectionBackground':{backgroundColor:dark?'#405077':'#c4d2ff'},
    '.cm-cursor':{borderLeftColor:dark?'#c4ceff':'#315ee8'},
  },{dark}),
  ...(dark?[syntaxHighlighting(darkHighlight)]:[]),
];

export default function CodeEditor({value,onChange}:{value:string;onChange:(value:string)=>void}){
  const root=useRef<HTMLDivElement>(null),view=useRef<EditorView|null>(null),callback=useRef(onChange);
  const theme=useRef(new Compartment());
  callback.current=onChange;
  useEffect(()=>{
    if(!root.current)return;
    const dark=document.documentElement.dataset.theme==='dark';
    const editor=new EditorView({doc:value,extensions:[basicSetup,cpp(),theme.current.of(editorTheme(dark)),EditorView.updateListener.of(update=>{if(update.docChanged)callback.current(update.state.doc.toString())})],parent:root.current});
    view.current=editor;
    const observer=new MutationObserver(()=>editor.dispatch({effects:theme.current.reconfigure(editorTheme(document.documentElement.dataset.theme==='dark'))}));
    observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    return()=>{observer.disconnect();editor.destroy();view.current=null};
  },[]);
  useEffect(()=>{const editor=view.current;if(editor&&editor.state.doc.toString()!==value)editor.dispatch({changes:{from:0,to:editor.state.doc.length,insert:value}})},[value]);
  return <div className="code-editor" ref={root} aria-label="Исходный код"/>;
}
