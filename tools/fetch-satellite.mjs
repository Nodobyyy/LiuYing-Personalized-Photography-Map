// Bounded, resumable offline extract. Only the 2016 EOX layer is CC BY 4.0;
// later NC/commercial products are deliberately not substituted.
import fs from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {execFileSync} from 'node:child_process';
const root='.tooling/satellite-cache';fs.mkdirSync(root,{recursive:true});
const jobs=new Map();
function add(bounds,min,max){for(let z=min;z<=max;z++){
 const n=2**z,x=lon=>Math.floor((lon+180)/360*n),y=lat=>Math.floor((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*n);
 for(let col=x(bounds[0]);col<=Math.min(n-1,x(bounds[2]));col++)for(let row=y(bounds[3]);row<=Math.min(n-1,y(bounds[1]));row++)jobs.set(`${z}-${col}-${row}`,{z,x:col,y:row});
}}
add([73,3,136,54],0,7);
add([116.1,39.5,116.7,40.2],8,14);
const tasks=[...jobs.entries()];let cursor=0,done=0;
async function worker(){while(cursor<tasks.length){const [key,tile]=tasks[cursor++],file=`${root}/${key}.jpg`;
 if(!fs.existsSync(file)){let success=false;
  for(let retry=0;retry<5;retry++){try{
   const r=await fetch(`https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/${tile.z}/${tile.y}/${tile.x}.jpg`,{signal:AbortSignal.timeout(45000)});
   if(!r.ok)throw Error(`HTTP ${r.status}`);const b=Buffer.from(await r.arrayBuffer());if(b.length<100||b[0]!==255||b[1]!==216)throw Error('Not JPEG');fs.writeFileSync(file,b);success=true;break;
  }catch(e){await new Promise(r=>setTimeout(r,1500*(retry+1)));if(retry===4)throw e;}}
  if(!success)throw Error(key);await new Promise(r=>setTimeout(r,150));
 }
 if(++done%100===0)console.log(`${done}/${tasks.length}`);
}}
console.log(`Fetching ${tasks.length} cached JPEG tiles, 3 workers`);
await Promise.all([worker(),worker(),worker()]);
for(const [part,bounds,maxZoom] of [['overview',[73,3,136,54],7],['beijing',[116.1,39.5,116.7,40.2],14]]) {
const mb=`.tooling/satellite-${part}.mbtiles`;if(fs.existsSync(mb))fs.unlinkSync(mb);
const db=new DatabaseSync(mb);db.exec('CREATE TABLE metadata(name TEXT,value TEXT); CREATE TABLE tiles(zoom_level INTEGER,tile_column INTEGER,tile_row INTEGER,tile_data BLOB); CREATE UNIQUE INDEX tile_index ON tiles(zoom_level,tile_column,tile_row); BEGIN');
const meta=db.prepare('INSERT INTO metadata VALUES(?,?)');for(const [k,v]of Object.entries({name:`EOX Sentinel-2 cloudless 2016 ${part}`,format:'jpg',type:'baselayer',version:'1',bounds:bounds.join(','),minzoom:'0',maxzoom:String(maxZoom),attribution:'EOX / Contains modified Copernicus Sentinel data 2016 / CC BY 4.0'}))meta.run(k,v);
const subset=tasks.filter(([,t])=>{if(t.z>maxZoom)return false;const n=2**t.z,x=lon=>Math.floor((lon+180)/360*n),y=lat=>Math.floor((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*n);return t.x>=x(bounds[0])&&t.x<=x(bounds[2])&&t.y>=y(bounds[3])&&t.y<=y(bounds[1]);});
const insert=db.prepare('INSERT INTO tiles VALUES(?,?,?,?)');for(const [key,t]of subset)insert.run(t.z,t.x,2**t.z-1-t.y,fs.readFileSync(`${root}/${key}.jpg`));db.exec('COMMIT');db.close();
const out=`.tooling/satellite-${part}.pmtiles`;if(fs.existsSync(out))fs.unlinkSync(out);execFileSync('.tooling/pmtiles/pmtiles.exe',['convert',mb,out],{stdio:'inherit'});execFileSync('.tooling/pmtiles/pmtiles.exe',['verify',out],{stdio:'inherit'});
console.log(`${out}: ${fs.statSync(out).size} bytes, ${subset.length} genuine imagery tiles`);
}
