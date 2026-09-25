import {defineConfig} from 'vite';
export default defineConfig({root:'apps/web',base:process.env.GITHUB_PAGES==='true'?'/shader-gallery/':'/',build:{outDir:'../../build',emptyOutDir:true},server:{host:'127.0.0.1'},publicDir:'public'});
