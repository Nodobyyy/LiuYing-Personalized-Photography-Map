// Inspect real local MVT properties; no network or changes to the archive.
import {spawnSync} from 'node:child_process';
import zlib from 'node:zlib';
function message(b) {
  let p=0;const result=[];
  function vint(){let v=0,s=1;for(;;){const n=b[p++];if(n===undefined)throw Error('Bad protobuf');v+=(n&127)*s;if(n<128)return v;s*=128;}}
  while(p<b.length) {
    const tag=vint(),field=tag>>3,wire=tag&7;let value;
    if(wire===0)value=vint();else if(wire===2){const size=vint();value=b.subarray(p,p+size);p+=size;}
    else if(wire===1){value=b.subarray(p,p+8);p+=8;}else if(wire===5){value=b.subarray(p,p+4);p+=4;}else throw Error('Unsupported wire');
    result.push({field,wire,value});
  }return result;
}
function varints(b){let p=0;const values=[];while(p<b.length){let v=0,s=1;for(;;){const n=b[p++];v+=(n&127)*s;if(n<128)break;s*=128;}values.push(v);}return values;}
export function tileFeatures(tile,layerName='places') {
  const out=[];
  if(tile[0]===31 && tile[1]===139)tile=zlib.gunzipSync(tile);
  for(const entry of message(tile).filter(f=>f.field===3)) {
    const layer=message(entry.value);
    if(layer.find(f=>f.field===1)?.value.toString()!==layerName)continue;
    const keys=layer.filter(f=>f.field===3).map(f=>f.value.toString());
    const values=layer.filter(f=>f.field===4).map(f=>{const x=message(f.value)[0];return x.field===1?x.value.toString():x.field===2?x.value.readFloatLE(0):x.field===3?x.value.readDoubleLE(0):x.field===6?(x.value%2===1?-(x.value+1)/2:x.value/2):x.value;});
    for(const feature of layer.filter(f=>f.field===2)) {
      const tags=message(feature.value).find(f=>f.field===2);if(!tags)continue;
      const pairs=varints(tags.value),props={};for(let i=0;i<pairs.length;i+=2)props[keys[pairs[i]]]=values[pairs[i+1]];
      out.push(props);
    }
  }return out;
}
if(process.argv[1]?.endsWith('inspect-map-places.mjs')) for(const z of [3,4,5]) {
  const n=2**z,x0=Math.floor((73+180)/360*n),x1=Math.floor((136+180)/360*n);
  const ty=lat=>Math.floor((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*n);
  const found=new Map();
  for(let x=x0;x<=x1;x++)for(let y=ty(54);y<=ty(3);y++) {
    const result=spawnSync('.tooling/pmtiles/pmtiles.exe',['tile','.tooling/china-z10.pmtiles',String(z),String(x),String(y)],{maxBuffer:16*1024*1024,windowsHide:true});
    if(result.status!==0)continue;
    for(const p of tileFeatures(result.stdout).filter(p=>p.kind==='region'))found.set(p.name,{'name':p.name,'zh':p['name:zh-Hans'],'min_zoom':p.min_zoom});
  }
  console.log(JSON.stringify({zoom:z,regions:[...found.values()]}));
}
