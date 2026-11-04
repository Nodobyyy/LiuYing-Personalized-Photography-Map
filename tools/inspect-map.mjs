import {execFileSync} from 'node:child_process';
import zlib from 'node:zlib';
function fields(b){let p=0;const result=[];function vint(){let n=0,m=1;while(true){const v=b[p++];if(v===undefined)throw Error('truncated protobuf');n+=(v&127)*m;if(v<128)return n;m*=128;}}while(p<b.length){const t=vint(),id=t>>3,wire=t&7;let value;if(wire===0)value=vint();else if(wire===2){const n=vint();value=b.subarray(p,p+n);p+=n;}else if(wire===1){value=b.readDoubleLE(p);p+=8;}else if(wire===5){value=b.readFloatLE(p);p+=4;}else throw Error('wire '+wire);result.push({id,wire,value});}return result;}
const [file='.tooling/china-z10.pmtiles',z='3',x='6',y='3']=process.argv.slice(2);
const tile=zlib.gunzipSync(execFileSync('.tooling/pmtiles/pmtiles.exe',['tile',file,z,x,y],{maxBuffer:8*1024*1024}));
for(const l of fields(tile).filter(f=>f.id===3)){
  const f=fields(l.value),name=f.find(e=>e.id===1).value.toString();const keys=f.filter(e=>e.id===3).map(e=>e.value.toString());
  const values=f.filter(e=>e.id===4).map(e=>{const v=fields(e.value)[0];return Buffer.isBuffer(v.value)?v.value.toString():v.value;});
  const features=f.filter(e=>e.id===2);const all=features.map(e=>{const packed=fields(e.value).find(v=>v.id===2)?.value;if(!packed)return {};let p=0;function v(){let n=0,m=1;while(true){const b=packed[p++];n+=(b&127)*m;if(b<128)return n;m*=128;}}const props={};while(p<packed.length){const k=v(),value=v();props[keys[k]]=values[value];}return props;});
  const samples=name==='places'?all.filter(p=>p.kind==='locality'||p.kind==='region').slice(0,4):all.slice(0,4);
  console.log(JSON.stringify({name,featureCount:features.length,keys,samples}));
}
