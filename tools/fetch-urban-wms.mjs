import fs from 'node:fs';
const groups=JSON.parse(fs.readFileSync('.tooling/urban-satellite-groups.json')).filter(g=>g.z===14),root='.tooling/urban-wms';fs.mkdirSync(root,{recursive:true});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function image(url,timeout){const r=await fetch(url,{signal:AbortSignal.timeout(timeout)});if(r.status===429){await pause(Math.max(30000,Number(r.headers.get('retry-after')||30)*1000));throw Object.assign(Error('Service rate limited; saved progress, retry later'),{rateLimited:true});}if(!r.ok)throw Error(`HTTP ${r.status}`);const b=Buffer.from(await r.arrayBuffer()),jpeg=b[0]===255&&b[1]===216,png=b.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));if(b.length<100||(!jpeg&&!png))throw Error('Not an image: '+b.toString().slice(0,120));return b;}
let cursor=0,done=0;
await Promise.all(Array.from({length:6},async()=>{while(cursor<groups.length){const g=groups[cursor++],key=`${g.z}-${g.x}-${g.y}`,file=`${root}/${key}.jpg`,marker=`${root}/${key}.cropped`;
 if(!fs.existsSync(file)&&!fs.existsSync(marker)){
  const xs=g.tiles.map(t=>t.x),ys=g.tiles.map(t=>t.y),l=Math.min(...xs),r=Math.max(...xs)+1,t=Math.min(...ys),b=Math.max(...ys)+1,n=2**g.z,w=40075016.68557849,o=w/2,bbox=[l/n*w-o,o-b/n*w,r/n*w-o,o-t/n*w];
  const p=new URLSearchParams({SERVICE:'WMS',VERSION:'1.1.1',REQUEST:'GetMap',LAYERS:'s2cloudless_3857',STYLES:'',SRS:'EPSG:3857',BBOX:bbox.join(','),WIDTH:String((r-l)*256),HEIGHT:String((b-t)*256),FORMAT:'image/jpeg'});
  try{const bytes=await image('https://tiles.maps.eox.at/wms?'+p,15000);fs.writeFileSync(file+'.part',bytes);fs.renameSync(file+'.part',file);}
  catch(error){if(error.rateLimited)throw error;console.log(`WMTS fallback ${key}: ${error.message}`);
   for(const tile of g.tiles){const dir=`.tooling/urban-satellite-tiles/${tile.z}/${tile.x}`,out=`${dir}/${tile.y}.jpg`;if(fs.existsSync(out))continue;fs.mkdirSync(dir,{recursive:true});const old=`.tooling/satellite-cache/${tile.z}-${tile.x}-${tile.y}.jpg`;
    if(fs.existsSync(old)){fs.copyFileSync(old,out);continue;}
    for(let retry=0;retry<4;retry++)try{const bytes=await image(`https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/${tile.z}/${tile.y}/${tile.x}.jpg`,25000);if(bytes[0]!==255||bytes[1]!==216)throw Error('WMTS must be JPEG');fs.writeFileSync(out+'.part',bytes);fs.renameSync(out+'.part',out);break;}catch(e){if(e.rateLimited||retry===3)throw e;await pause((retry+1)*1500);}
    await pause(100);
   }fs.writeFileSync(marker,'Unmodified EOX 2016 CC BY 4.0 JPEG WMTS tiles.');
  }await pause(150);
 }if(++done%25===0)console.log(`${done}/${groups.length} finest-level windows completed`);
}}));
console.log('All finest urban imagery completed.');
