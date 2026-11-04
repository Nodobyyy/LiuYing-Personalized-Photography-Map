// Recover an interrupted official go-pmtiles extract. Validate every gzip tile CRC
// before writing a final header; never turn a partial archive into an apparently valid one.
import fs from 'node:fs';
import zlib from 'node:zlib';
import {spawn} from 'node:child_process';
const url='https://build.protomaps.com/20261101.pmtiles';
const file=process.argv[2] || '.tooling/china-z10.pmtiles';
const maxZoom=Number(process.argv[3]||10),minZoom=Number(process.argv[4]||0);
const cache='.tooling/map-range-cache';fs.mkdirSync(cache,{recursive:true});
async function range(offset,length) {
  const output=`${cache}/${offset}-${length}.bin`;
  if(fs.existsSync(output)&&fs.statSync(output).size===length)return fs.readFileSync(output);
  const code=await new Promise(resolve=>{const child=spawn('curl.exe',['--http1.1','-sS','-L','--fail','--retry','4','--retry-all-errors','--connect-timeout','15','--max-time','150','--range',`${offset}-${offset+length-1}`,'-o',output,url],{windowsHide:true,stdio:'inherit'});child.on('close',resolve);child.on('error',()=>resolve(1));});
  if(code!==0 || !fs.existsSync(output) || fs.statSync(output).size!==length)throw Error(`Range download failed ${offset}:${length}`);
  return fs.readFileSync(output);
}
function directory(bytes) {
  const b=zlib.gunzipSync(bytes);let at=0;
  function vint(){let value=0,m=1;while(true){const byte=b[at++];if(byte===undefined)throw Error('bad varint');value+=(byte&127)*m;if(byte<128)return value;m*=128;if(m>2**56)throw Error('varint overflow');}}
  const n=vint();const entries=[];let id=0;
  for(let i=0;i<n;i++){id+=vint();entries.push({id});}
  for(const e of entries)e.run=vint();for(const e of entries)e.length=vint();
  for(let i=0;i<n;i++){const v=vint();entries[i].offset=v===0&&i>0?entries[i-1].offset+entries[i-1].length:v-1;}
  return entries;
}
function memberLength(bytes){ // gzip header from the official serializer has flags=0
  if(bytes[0]!==31||bytes[1]!==139||bytes[3]!==0)throw Error('Expected simple gzip member');
  const inflated=zlib.inflateRawSync(bytes.subarray(10),{info:true});return 10+inflated.engine.bytesWritten+8;
}
function lookup(entries,id){let lo=0,hi=entries.length-1,found=-1;while(lo<=hi){const m=(lo+hi)>>1;if(entries[m].id<=id){found=m;lo=m+1;}else hi=m-1;}if(found<0)throw Error('Missing tile ID');return entries[found];}
const sourceHeader=await range(0,127);if(sourceHeader.subarray(0,7).toString()!=='PMTiles')throw Error('Bad source');
const u64=off=>Number(sourceHeader.readBigUInt64LE(off));
const sourceRoot=directory(await range(u64(8),u64(16)));
const handle=fs.openSync(file,'r+');const rootBuffer=Buffer.alloc(16384);fs.readSync(handle,rootBuffer,0,rootBuffer.length,127);
const rootLength=memberLength(rootBuffer);const roots=directory(rootBuffer.subarray(0,rootLength));
const metadataOffset=127+rootLength;const metadataLength=u64(32);const leafOffset=metadataOffset+metadataLength;
const leafLength=Math.max(0,...roots.filter(e=>e.run===0).map(e=>e.offset+e.length));const tileOffset=leafOffset+leafLength;
const local=[];
for(const e of roots){if(e.run)local.push(e);else{const bytes=Buffer.alloc(e.length);fs.readSync(handle,bytes,0,e.length,leafOffset+e.offset);local.push(...directory(bytes));}}
console.log(`Local directory: ${local.length} entries, tile data begins ${tileOffset}`);
const leafIndexes=[...new Set(local.map(e=>lookup(sourceRoot,e.id)).filter(e=>e.run===0).map(e=>e.id))];
const sourceLeaves=new Map();for(const id of leafIndexes){const e=sourceRoot.find(e=>e.id===id);sourceLeaves.set(id,directory(await range(u64(40)+e.offset,e.length)));}
const unique=new Map();for(const e of local)unique.set(e.offset,e);
const missing=[];let checked=0;
for(const e of unique.values()){
  const bytes=Buffer.alloc(e.length);fs.readSync(handle,bytes,0,e.length,tileOffset+e.offset);
  try{zlib.gunzipSync(bytes);}catch{
    let s=lookup(sourceRoot,e.id);if(!s.run)s=lookup(sourceLeaves.get(s.id),e.id);
    if(e.id>=s.id+s.run||e.length!==s.length)throw Error('Source/local directory mismatch');
    missing.push({...e,source:u64(56)+s.offset});
  }
  checked++;
}
missing.sort((a,b)=>a.source-b.source);
const chunks=[];for(const e of missing){let c=chunks.at(-1);if(!c||e.source-c.end>16384||e.source+e.length-c.start>4*1024*1024){c={start:e.source,end:e.source+e.length,entries:[e]};chunks.push(c);}else{c.end=Math.max(c.end,e.source+e.length);c.entries.push(e);}}
console.log(`${checked-missing.length}/${checked} valid contents, repairing ${missing.length} using ${chunks.length} ranges (${Math.round(chunks.reduce((n,c)=>n+c.end-c.start,0)/1e6)} MB)`);
let cursor=0,complete=0;
await Promise.all(Array.from({length:8},async()=>{while(cursor<chunks.length){const c=chunks[cursor++];const bytes=await range(c.start,c.end-c.start);for(const e of c.entries){const tile=bytes.subarray(e.source-c.start,e.source-c.start+e.length);zlib.gunzipSync(tile);fs.writeSync(handle,tile,0,tile.length,tileOffset+e.offset);}complete++;console.log(`Repaired ranges ${complete}/${chunks.length}`);}}));
// Final pass catches holes, short writes, gzip CRC failures and truncation.
for(const e of unique.values()){const bytes=Buffer.alloc(e.length);fs.readSync(handle,bytes,0,e.length,tileOffset+e.offset);zlib.gunzipSync(bytes);}
const header=Buffer.from(sourceHeader);function put(offset,value){header.writeBigUInt64LE(BigInt(value),offset);}
put(8,127);put(16,rootLength);put(24,metadataOffset);put(32,metadataLength);put(40,leafOffset);put(48,leafLength);put(56,tileOffset);put(64,fs.fstatSync(handle).size-tileOffset);put(72,local.reduce((n,e)=>n+e.run,0));put(80,local.length);put(88,unique.size);header[100]=minZoom;header[101]=maxZoom;header[118]=Math.min(header[118],maxZoom);
for(const [i,value] of [73,3,136,54].entries())header.writeInt32LE(value*1e7,102+i*4);
header[118]=3;header.writeInt32LE(105*1e7,119);header.writeInt32LE(35*1e7,123);
fs.writeSync(handle,header,0,127,0);fs.fsyncSync(handle);fs.closeSync(handle);
console.log('All tile payloads passed gzip CRC validation; PMTiles header committed.');
