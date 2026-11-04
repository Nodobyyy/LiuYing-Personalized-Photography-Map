import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { safePath, sha256, validateBundle } from './map-bundle.mjs';
function fixture(fn) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'photomap-test-'));
  try {
    const tile = Buffer.alloc(127); tile.write('PMTiles'); tile[7]=3; tile[99]=1;
    const files = { 'china.pmtiles': tile, 'license.txt': Buffer.from('TEST ONLY'), 'review.txt': Buffer.from('TEST ONLY'),
      'style.json': Buffer.from(JSON.stringify({version:8, sources:{base:{type:'vector',url:'pmtiles://local://china.pmtiles'}},layers:[]})) };
    function write() {
      for (const [name,data] of Object.entries(files)) fs.writeFileSync(path.join(root,name),data);
      fs.writeFileSync(path.join(root,'bundle.json'),JSON.stringify({version:1,coordinateSystem:'WGS84',attribution:'TEST ONLY',licenseEvidence:'license.txt',boundaryReview:'review.txt',coverage:'synthetic header, no map',files:Object.fromEntries(Object.entries(files).map(([name,data])=>[name,sha256(data)]))}));
    }
    write(); fn(root, files, write);
  } finally { fs.rmSync(root,{recursive:true,force:true}); }
}
test('rejects traversal, Windows paths, encoded escapes and absolute paths',()=> {
  for (const p of ['../x','/x','C:/x','a/../x','a\\x','%2e%2e/x','a//x']) assert.throws(()=>safePath(p));
});
test('valid local references and checksum',()=>fixture(root=>assert.equal(validateBundle(root).fileCount,4)));
test('corrupted file rejected',()=>fixture(root=>{fs.appendFileSync(path.join(root,'china.pmtiles'),'bad');assert.throws(()=>validateBundle(root),/校验值/);}));
test('remote source rejected',()=>fixture((root,files,write)=>{files['style.json']=Buffer.from(JSON.stringify({version:8,sources:{base:{type:'vector',url:'https://example.com/map'}},layers:[]}));write();assert.throws(()=>validateBundle(root),/非本地/);}));
test('missing glyph resources rejected',()=>fixture((root,files,write)=>{let style=JSON.parse(files['style.json']);style.glyphs='local://fonts/{fontstack}/{range}.pbf';files['style.json']=Buffer.from(JSON.stringify(style));write();assert.throws(()=>validateBundle(root),/缺少本地/);}));
test('wrong coordinate system rejected',()=>fixture(root=>{const file=path.join(root,'bundle.json');const m=JSON.parse(fs.readFileSync(file));m.coordinateSystem='GCJ02';fs.writeFileSync(file,JSON.stringify(m));assert.throws(()=>validateBundle(root),/WGS84/);}));
