// Offline generalisation of an already verified Protomaps archive. Road and
// water geometry is preserved; no coordinates are shifted or features invented.
import fs from 'node:fs';import zlib from 'node:zlib';import {execFileSync} from 'node:child_process';
const input=process.argv[2]||'.tooling/china-z13.pmtiles',output=process.argv[3]||'.tooling/china-streets-z12-compact.pmtiles';
const maxZoom=Number(process.argv[4]||12);if(!Number.isInteger(maxZoom)||maxZoom<0||maxZoom>15)throw Error('Bad max zoom');
const majorTop = process.argv.includes('--major-top');
const roadsOnly = process.argv.includes('--roads-only');
const source=fs.openSync(input,'r');const read=(at,n)=>{const b=Buffer.alloc(n);if(fs.readSync(source,b,0,n,at)!==n)throw Error('Short read');return b;};const h=read(0,127),u64=off=>Number(h.readBigUInt64LE(off));
if(h.subarray(0,7).toString()!=='PMTiles'||h[97]!==2||h[98]!==2||h[99]!==1)throw Error('Expected gzip PMTiles v3 MVT');
function vint(b,state){let n=0,m=1;while(true){const v=b[state.p++];if(v===undefined)throw Error('Bad protobuf');n+=(v&127)*m;if(v<128)return n;m*=128;}}
function enc(n){const b=[];do{const v=n%128;n=Math.floor(n/128);b.push(v+(n?128:0));}while(n);return Buffer.from(b);}
function fields(b){const s={p:0},r=[];while(s.p<b.length){const start=s.p,t=vint(b,s),wire=t&7,id=Math.floor(t/8);let value;if(wire===2){const n=vint(b,s);value=b.subarray(s.p,s.p+n);s.p+=n;}else if(wire===0)vint(b,s);else if(wire===1)s.p+=8;else if(wire===5)s.p+=4;else throw Error('Wire type');r.push({id,value,raw:b.subarray(start,s.p)});}return r;}
const field=(id,b)=>Buffer.concat([enc(id*8+2),enc(b.length),b]);
function dir(b){const data=zlib.gunzipSync(b),s={p:0},n=vint(data,s),a=[];let id=0;for(let i=0;i<n;i++){id+=vint(data,s);a.push({id});}for(const e of a)e.run=vint(data,s);for(const e of a)e.length=vint(data,s);for(let i=0;i<n;i++){const v=vint(data,s);a[i].offset=v===0&&i>0?a[i-1].offset+a[i-1].length:v-1;}return a;}
const entries=[];for(const e of dir(read(u64(8),u64(16)))) {if(e.run)entries.push(e);else entries.push(...dir(read(u64(40)+e.offset,e.length)));}
const maxId=(4**(maxZoom+1)-1)/3;
const minId=roadsOnly?(4**maxZoom-1)/3:0;
const tiles=entries.filter(e=>e.id<maxId&&e.id+e.run>minId).map(e=>{
 const id=Math.max(minId,e.id);
 return {...e,id,run:Math.min(e.id+e.run,maxId)-id};
});
const keepLayers=new Set(['earth','water','roads','places','pois']);
const keepKey=k=>['name','name:zh-Hans','name:zh-Hant','name:en','kind','kind_detail','min_zoom','sort_rank','sort_key','population','population_rank','capital','ref','is_bridge','is_tunnel','layer'].includes(k);
function compact(bytes, trimTop){const layers=[];for(const layer of fields(zlib.gunzipSync(bytes)).filter(f=>f.id===3)){
 const all=fields(layer.value),name=all.find(f=>f.id===1).value.toString();if(!keepLayers.has(name))continue;
 if(roadsOnly && name!=='roads')continue;
 if(trimTop && name==='pois')continue;
 const keys=all.filter(f=>f.id===3).map(f=>f.value.toString()),values=all.filter(f=>f.id===4),keyMap=new Map(),valueMap=new Map(),newKeys=[],newVals=[],features=[];
 for(const feature of all.filter(f=>f.id===2)) {const parts=fields(feature.value),tag=parts.find(f=>f.id===2),tags=[];
  if(trimTop && name==='roads'){
   const state={p:0};let kind='', named=false;
   while(tag && state.p<tag.value.length){
    const k=vint(tag.value,state),v=vint(tag.value,state);
    if(keys[k]==='kind'){
     kind=fields(values[v].value).find(f=>f.id===1)?.value?.toString();
    }
    if(keys[k]==='name')named=true;
   }
   if(kind!=='highway'&&kind!=='major_road'&&!(roadsOnly&&named&&kind==='minor_road'))continue;
  }
  if(tag){const state={p:0};while(state.p<tag.value.length){const k=vint(tag.value,state),v=vint(tag.value,state);if(!keepKey(keys[k]))continue;if(!keyMap.has(k)){keyMap.set(k,newKeys.length);newKeys.push(field(3,Buffer.from(keys[k])));}if(!valueMap.has(v)){valueMap.set(v,newVals.length);newVals.push(values[v].raw);}tags.push(enc(keyMap.get(k)),enc(valueMap.get(v)));}}
  const rest=parts.filter(f=>f.id!==2).map(f=>f.raw);if(tags.length)rest.push(field(2,Buffer.concat(tags)));features.push(field(2,Buffer.concat(rest)));
 }
 layers.push(field(3,Buffer.concat([...all.filter(f=>![2,3,4].includes(f.id)).map(f=>f.raw),...features,...newKeys,...newVals])));
}return zlib.gzipSync(Buffer.concat(layers),{level:6});}
const payload=output+'.payload';const out=fs.openSync(payload,'w'),cache=new Map(),result=[];let offset=0,count=0;
const topId=(4**maxZoom-1)/3;
for(const e of tiles){const trimTop=majorTop&&e.id>=topId,key=trimTop?-e.offset-1:e.offset;let converted=cache.get(key);if(!converted){const bytes=compact(read(u64(56)+e.offset,e.length),trimTop);converted={offset,length:bytes.length};fs.writeSync(out,bytes);offset+=bytes.length;cache.set(key,converted);}result.push({...e,...converted});if(++count%50000===0)console.log(`${count}/${tiles.length}: ${Math.round(offset/1e6)} MB`);}
fs.closeSync(out);
function encodeDir(list){let previous=0;const parts=[enc(list.length)];for(const e of list){parts.push(enc(e.id-previous));previous=e.id;}for(const e of list)parts.push(enc(e.run));for(const e of list)parts.push(enc(e.length));for(let i=0;i<list.length;i++)parts.push(enc(i>0&&list[i].offset===list[i-1].offset+list[i-1].length?0:list[i].offset+1));return zlib.gzipSync(Buffer.concat(parts));}
const leaves=[],root=[];let leafSize=0;for(let i=0;i<result.length;i+=4096){const chunk=result.slice(i,i+4096),b=encodeDir(chunk);root.push({id:chunk[0].id,run:0,length:b.length,offset:leafSize});leaves.push(b);leafSize+=b.length;}
const rootBytes=encodeDir(root);if(rootBytes.length>16257)throw Error('Root too large');
const metadata=JSON.parse(zlib.gunzipSync(read(u64(24),u64(32))));metadata.name=`PhotoMap compact context Z${maxZoom}`;metadata.description+=roadsOnly?`; PhotoMap Z${maxZoom} road overlay, retained geometry unchanged.`:`; PhotoMap subset: Z0-${maxZoom}, retained roads/water/places/POIs/earth; buildings, landcover, landuse, upstream boundaries and unused attributes omitted; retained geometry unchanged.`;metadata.vector_layers=metadata.vector_layers?.filter(l=>roadsOnly?l.id==='roads':keepLayers.has(l.id));
const meta=zlib.gzipSync(Buffer.from(JSON.stringify(metadata))),header=Buffer.from(h),put=(at,n)=>header.writeBigUInt64LE(BigInt(n),at);
put(8,127);put(16,rootBytes.length);put(24,127+rootBytes.length);put(32,meta.length);put(40,127+rootBytes.length+meta.length);put(48,leafSize);put(56,127+rootBytes.length+meta.length+leafSize);put(64,offset);put(72,result.reduce((n,e)=>n+e.run,0));put(80,result.length);put(88,cache.size);header[101]=maxZoom;header[118]=Math.min(3,maxZoom);
if(roadsOnly){header[100]=maxZoom;header[118]=maxZoom;}
const final=fs.openSync(output,'w');for(const b of [header,rootBytes,meta,...leaves])fs.writeSync(final,b);const data=fs.openSync(payload,'r'),buf=Buffer.alloc(1024*1024);let n;while((n=fs.readSync(data,buf))>0)fs.writeSync(final,buf,0,n);fs.closeSync(data);fs.closeSync(final);fs.closeSync(source);fs.unlinkSync(payload);
execFileSync('.tooling/pmtiles/pmtiles.exe',['verify',output],{stdio:'inherit'});console.log(`${output}: ${fs.statSync(output).size} bytes; ${result.length} entries; ${cache.size} contents`);
