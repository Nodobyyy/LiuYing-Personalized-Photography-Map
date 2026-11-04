import fs from 'node:fs';
const tasks=JSON.parse(fs.readFileSync('.tooling/urban-satellite-groups.json')).filter(g=>g.z===14).flatMap(g=>g.tiles);
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let cursor=0,done=0,downloaded=0,concurrency=12,pausedUntil=0;
async function worker(id){while(true){while(id>=concurrency||Date.now()<pausedUntil)await sleep(500);if(cursor>=tasks.length)return;const t=tasks[cursor++],dir=`.tooling/urban-satellite-tiles/${t.z}/${t.x}`,file=`${dir}/${t.y}.jpg`;fs.mkdirSync(dir,{recursive:true});
 const old=`.tooling/satellite-cache/${t.z}-${t.x}-${t.y}.jpg`;
 if(!fs.existsSync(file)&&fs.existsSync(old))fs.copyFileSync(old,file);
 if(!fs.existsSync(file))for(let retry=0;retry<8;retry++){
  while(Date.now()<pausedUntil)await sleep(500);
  try{const r=await fetch(`https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/${t.z}/${t.y}/${t.x}.jpg`,{signal:AbortSignal.timeout(25000)});
   if(r.status===429){concurrency=Math.max(2,Math.floor(concurrency/2));pausedUntil=Math.max(pausedUntil,Date.now()+Math.max(30000,Number(r.headers.get('retry-after')||30)*1000));console.log(`Respecting Retry-After; concurrency ${concurrency}`);retry--;continue;}
   if(!r.ok)throw Error(`HTTP ${r.status}`);const b=Buffer.from(await r.arrayBuffer());if(b.length<100||b[0]!==255||b[1]!==216||b.at(-2)!==255||b.at(-1)!==217)throw Error('Invalid/truncated JPEG');fs.writeFileSync(file+'.part',b);fs.renameSync(file+'.part',file);downloaded++;break;
  }catch(error){if(retry===7)throw Error(`${t.z}/${t.x}/${t.y}: ${error.message}`);console.log(`Retry ${t.x}/${t.y}: ${error.message}`);await sleep((retry+1)*1500);}
 }
 if(++done%250===0)console.log(`${done}/${tasks.length} finest tiles; new downloads ${downloaded}`);
 await sleep(80);
}}
await Promise.all(Array.from({length:12},(_,id)=>worker(id)));
console.log(`All ${tasks.length} genuine finest-level tiles complete.`);
