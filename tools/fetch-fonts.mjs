import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
const entries=JSON.parse(fs.readFileSync('.tooling/font-files.json'));
const root=path.resolve('.tooling/map-fonts/Noto Sans Regular');fs.mkdirSync(root,{recursive:true});
function valid(file,e){if(!fs.existsSync(file))return false;const bytes=fs.readFileSync(file);return bytes.length===e.size && crypto.createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex')===e.sha;}
let cursor=0,done=0;const failed=[];
await Promise.all(Array.from({length:12},async()=>{while(cursor<entries.length){const e=entries[cursor++];if(!e.name.endsWith('.pbf'))continue;const target=path.join(root,e.name);
if(!valid(target,e)){const code=await new Promise(resolve=>{const child=spawn('curl.exe',['-sS','-L','--fail','--connect-timeout','10','--max-time','40','--retry','2','-o',target,`https://protomaps.github.io/basemaps-assets/fonts/Noto%20Sans%20Regular/${e.name}`],{windowsHide:true,stdio:'ignore'});child.on('close',resolve);child.on('error',()=>resolve(1));});if(code!==0||!valid(target,e)){failed.push(e.name);continue;}}
done++;if(done%32===0)console.log(`${done}/256 verified glyph ranges`);
}}));
console.log(JSON.stringify({verified:done,failed}));if(failed.length)process.exit(1);
