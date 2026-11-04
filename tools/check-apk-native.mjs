import fs from 'node:fs';
import zlib from 'node:zlib';
import assert from 'node:assert/strict';
const apk=process.argv[2];if(!apk)throw Error('Usage: node tools/check-apk-native.mjs app.apk [report.json]');
const fd=fs.openSync(apk,'r');
function read(offset,size) {const buffer=Buffer.alloc(size);assert.equal(fs.readSync(fd,buffer,0,size,offset),size);return buffer;}
try {
  const size=fs.fstatSync(fd).size,tail=read(Math.max(0,size-65557),Math.min(size,65557));
  let end=tail.length-22;while(end>=0 && tail.readUInt32LE(end)!==0x06054b50)end--;
  assert(end>=0,'Missing ZIP central directory');
  const central=read(tail.readUInt32LE(end+16),tail.readUInt32LE(end+12)),libraries=[];
  let cursor=0;
  while(cursor<central.length) {
    assert.equal(central.readUInt32LE(cursor),0x02014b50);
    const method=central.readUInt16LE(cursor+10),packed=central.readUInt32LE(cursor+20),nameLength=central.readUInt16LE(cursor+28),extra=central.readUInt16LE(cursor+30),comment=central.readUInt16LE(cursor+32),localOffset=central.readUInt32LE(cursor+42);
    const name=central.toString('utf8',cursor+46,cursor+46+nameLength);
    cursor+=46+nameLength+extra+comment;
    if(!/^lib\/[^/]+\/[^/]+\.so$/.test(name))continue;
    const header=read(localOffset,30),offset=localOffset+30+header.readUInt16LE(26)+header.readUInt16LE(28);
    const compressed=read(offset,packed),elf=method===0?compressed:zlib.inflateRawSync(compressed);
    assert.equal(elf.toString('hex',0,4),'7f454c46',name);assert.equal(elf[5],1,'Little endian ELF required');
    const bits=elf[4]===2?64:32;
    const phoff=bits===64?Number(elf.readBigUInt64LE(32)):elf.readUInt32LE(28);
    const stride=elf.readUInt16LE(bits===64?54:42),count=elf.readUInt16LE(bits===64?56:44),segments=[];
    for(let i=0;i<count;i++) {
      const p=phoff+i*stride;if(elf.readUInt32LE(p)!==1)continue;
      const fileOffset=bits===64?Number(elf.readBigUInt64LE(p+8)):elf.readUInt32LE(p+4);
      const virtual=bits===64?Number(elf.readBigUInt64LE(p+16)):elf.readUInt32LE(p+8);
      const alignment=bits===64?Number(elf.readBigUInt64LE(p+48)):elf.readUInt32LE(p+28);
      if(bits===64) assert(alignment>=16384 && fileOffset%16384===virtual%16384,`${name}: PT_LOAD is incompatible with 16 KB pages`);
      segments.push({fileOffset,virtual,alignment});
    }
    assert(segments.length>0,name);if(method===0)assert.equal(offset%16384,0,`${name}: uncompressed .so ZIP offset is not 16 KB aligned`);
    libraries.push({name,bits,requires16KB:bits===64,compression:method,apkDataOffset:offset,segments});
  }
  assert(libraries.some(l=>l.name.includes('/arm64-v8a/')),'Missing ARM64 library for Snapdragon');
  assert(libraries.some(l=>l.name.includes('/x86_64/')),'Missing emulator library');
  const report={apk,compatible16KB:true,libraries};
  if(process.argv[3])fs.writeFileSync(process.argv[3],JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report,null,2));
} finally {fs.closeSync(fd);}
