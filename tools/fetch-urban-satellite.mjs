// Public CC BY 4.0 EOX 2016 imagery, bounded national urban-centre subset.
// Resumable SQLite cache, polite concurrency and Retry-After handling.
import fs from 'node:fs';import {DatabaseSync} from 'node:sqlite';import {execFileSync} from 'node:child_process';
const features=JSON.parse(fs.readFileSync('.tooling/china-urban-centres.json')).features;
const polygons=features.flatMap(f=>f.geometry.type==='Polygon'?[f.geometry.coordinates]:f.geometry.coordinates);
function within(x,y,ring){let yes=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[i],b=ring[j];if((a[1]>y)!==(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])yes=!yes;}return yes;}
const jobs=new Map();
for(let z=8;z<=14;z++){const n=2**z,px=x=>(x+180)/360*n,py=y=>(1-Math.asinh(Math.tan(y*Math.PI/180))/Math.PI)/2*n;
 for(const poly of polygons){const ring=poly[0].map(([x,y])=>[px(x),py(y)]),xs=ring.map(p=>p[0]),ys=ring.map(p=>p[1]);
  for(let x=Math.floor(Math.min(...xs));x<=Math.floor(Math.max(...xs));x++)for(let y=Math.floor(Math.min(...ys));y<=Math.floor(Math.max(...ys));y++){
   // Include vertices/edge cells, plus tiles with a corner/centre inside. The
   // boundary grid walk prevents thin urban corridors disappearing between tests.
   if([[x,y],[x+1,y],[x,y+1],[x+1,y+1],[x+0.5,y+0.5]].some(p=>within(...p,ring)))jobs.set(`${z}-${x}-${y}`,{z,x,y});
  }
  for(let i=1;i<ring.length;i++){const a=ring[i-1],b=ring[i],steps=Math.ceil(Math.max(Math.abs(a[0]-b[0]),Math.abs(a[1]-b[1]))*4);for(let s=0;s<=steps;s++){const x=Math.floor(a[0]+(b[0]-a[0])*s/(steps||1)),y=Math.floor(a[1]+(b[1]-a[1])*s/(steps||1));jobs.set(`${z}-${x}-${y}`,{z,x,y});}}
 }
}
const tasks=[...jobs.entries()].sort((a,b)=>a[1].z-b[1].z||a[1].x-b[1].x||a[1].y-b[1].y);
const groups=new Map();for(const [key,t]of tasks){const g=`${t.z}-${Math.floor(t.x/8)}-${Math.floor(t.y/8)}`;if(!groups.has(g))groups.set(g,{z:t.z,x:Math.floor(t.x/8),y:Math.floor(t.y/8),tiles:[]});groups.get(g).tiles.push(t);}
fs.writeFileSync('.tooling/urban-satellite-groups.json',JSON.stringify([...groups.values()]));
fs.writeFileSync('.tooling/urban-satellite-plan.json',JSON.stringify({urbanCentres:features.length,tiles:tasks.length,byZoom:Object.fromEntries(Array.from({length:7},(_,i)=>[i+8,tasks.filter(t=>t[1].z===i+8).length])),source:'EOX Sentinel-2 cloudless 2016 CC BY 4.0',bounds:[73,3,136,54]}));
if(process.argv.includes('--plan')){console.log(fs.readFileSync('.tooling/urban-satellite-plan.json','utf8'));console.log(`WMS 2048px groups: ${groups.size}`);process.exit(0);}
const db=new DatabaseSync('.tooling/urban-satellite.mbtiles');db.exec('PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; CREATE TABLE IF NOT EXISTS metadata(name TEXT PRIMARY KEY,value TEXT); CREATE TABLE IF NOT EXISTS tiles(zoom_level INTEGER,tile_column INTEGER,tile_row INTEGER,tile_data BLOB,PRIMARY KEY(zoom_level,tile_column,tile_row));');
const meta=db.prepare('INSERT OR REPLACE INTO metadata VALUES(?,?)');for(const [k,v]of Object.entries({name:'China urban centres EOX Sentinel-2 cloudless 2016',format:'jpg',type:'baselayer',version:'1',bounds:'73,3,136,54',minzoom:'8',maxzoom:'14',attribution:'EOX / Contains modified Copernicus Sentinel data 2016 / CC BY 4.0'}))meta.run(k,v);
const old=new Set(db.prepare('SELECT zoom_level z,tile_column x,tile_row y FROM tiles').all().map(t=>`${t.z}-${t.x}-${2**t.z-1-t.y}`));
const put=db.prepare('INSERT OR REPLACE INTO tiles VALUES(?,?,?,?)');let cursor=0,done=0,inserted=0;db.exec('BEGIN');
async function worker(){while(cursor<tasks.length){const [key,t]=tasks[cursor++];if(!old.has(key)){
 let b;const converted=`.tooling/urban-satellite-tiles/${t.z}/${t.x}/${t.y}.jpg`;const cached=fs.existsSync(converted)?converted:`.tooling/satellite-cache/${key}.jpg`;
 if(fs.existsSync(cached))b=fs.readFileSync(cached);else if(process.argv.includes('--cache-only'))throw Error('Missing imagery tile '+key);else for(let retry=0;retry<8;retry++) {try {
  const r=await fetch(`https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/${t.z}/${t.y}/${t.x}.jpg`,{signal:AbortSignal.timeout(60000)});
  if(r.status===429||r.status===503){await new Promise(r2=>setTimeout(r2,Math.max(30000,Number(r.headers.get('retry-after')||30)*1000)));throw Error(`HTTP ${r.status}`);}
  if(!r.ok)throw Error(`HTTP ${r.status}`);b=Buffer.from(await r.arrayBuffer());if(b.length<100||b[0]!==255||b[1]!==216)throw Error('Invalid JPEG');break;
 }catch(error){if(retry===7)throw error;await new Promise(r=>setTimeout(r,2000*(retry+1)));}}
 if(b[0]!==255||b[1]!==216||b.at(-2)!==255||b.at(-1)!==217)throw Error('Invalid/truncated cached JPEG '+key);
 put.run(t.z,t.x,2**t.z-1-t.y,b);if(++inserted%100===0)db.exec('COMMIT; BEGIN');if(!process.argv.includes('--cache-only'))await new Promise(r=>setTimeout(r,150));
 }if(++done%250===0)console.log(`${done}/${tasks.length}; cached at start ${old.size}`);
}}
console.log(`Urban centres ${features.length}; tiles ${tasks.length}; already cached ${old.size}; 4 workers`);
try{await Promise.all(Array.from({length:4},worker));}finally{db.exec('COMMIT; PRAGMA wal_checkpoint(TRUNCATE)');db.close();}
const out='.tooling/satellite-urban.pmtiles';if(fs.existsSync(out))fs.unlinkSync(out);execFileSync('.tooling/pmtiles/pmtiles.exe',['convert','.tooling/urban-satellite.mbtiles',out],{stdio:'inherit'});execFileSync('.tooling/pmtiles/pmtiles.exe',['verify',out],{stdio:'inherit'});console.log(`${out}: ${fs.statSync(out).size} bytes`);
