import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

export const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
export function hashFile(file) {
  const hash=crypto.createHash('sha256'); const handle=fs.openSync(file,'r'); const bytes=Buffer.alloc(65536);
  try { while(true) {const n=fs.readSync(handle,bytes);if(!n)break;hash.update(bytes.subarray(0,n));} } finally {fs.closeSync(handle);}
  return hash.digest('hex');
}
export function safePath(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_./ -]+$/.test(value) || value.startsWith('/') || value.split('/').some(p => !p || p === '.' || p === '..')) throw Error(`不安全的相对路径: ${value}`);
  return value;
}
export function validateBundle(root,{styleName='style.json',verifyFiles=true}={}) {
  const resolve = name => {
    safePath(name);
    const resolved = fs.realpathSync(path.join(root, name));
    if (!resolved.startsWith(fs.realpathSync(root) + path.sep)) throw Error('拒绝符号链接越界');
    return resolved;
  };
  const read = name => {const file=resolve(name); if(fs.statSync(file).size>16*1024*1024)throw Error('元数据文件过大');return fs.readFileSync(file);};
  const manifest = JSON.parse(read('bundle.json'));
  if (manifest.version !== 1 || manifest.coordinateSystem !== 'WGS84') throw Error('仅支持 v1 / WGS84 地图包');
  for (const key of ['attribution', 'licenseEvidence', 'boundaryReview', 'coverage']) if (typeof manifest[key] !== 'string' || !manifest[key].trim()) throw Error(`缺少人工核验说明: ${key}`);
  if (!manifest.files || !manifest.files['style.json'] || !manifest.files[manifest.licenseEvidence] || !manifest.files[manifest.boundaryReview]) throw Error('必须列出样式、许可和边界核验文件');
  let bytes = 0;
  for (const [name, hash] of Object.entries(manifest.files)) {
    if (!/^[a-f0-9]{64}$/.test(hash)) throw Error(`无效 SHA-256: ${name}`);
    const file=resolve(name);bytes+=fs.statSync(file).size;
    if(manifest.fileSizes && manifest.fileSizes[name]!==fs.statSync(file).size)throw Error(`文件大小不匹配: ${name}`);
    if (verifyFiles && hashFile(file) !== hash) throw Error(`校验值不匹配: ${name}`);
  }
  if(!manifest.files[styleName])throw Error('样式未在清单声明');
  const style = JSON.parse(read(styleName));
  if (style.version !== 8 || !Array.isArray(style.layers) || !style.sources) throw Error('无效 MapLibre 样式');
  const references = [];
  function checkRef(ref) {
    if (typeof ref !== 'string') throw Error('资源引用必须为字符串');
    const relative = ref.replace(/^pmtiles:\/\//, '').replace(/^local:\/\//, '');
    if (!/^(pmtiles:\/\/)?local:\/\//.test(ref)) throw Error(`拒绝非本地资源: ${ref}`);
    const pattern = relative.replaceAll('{fontstack}', '*').replaceAll('{range}', '*');
    safePath(pattern.replaceAll('*', 'template'));
    const regex = new RegExp('^' + pattern.split('*').map(p => p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('.+') + '$');
    const matches = Object.keys(manifest.files).filter(name => regex.test(name));
    if (!matches.length) throw Error(`缺少本地资源: ${relative}`);
    references.push(ref);
  }
  function walk(value, key = '') {
    if (typeof value === 'string') {
      if (value.includes('://') || ['url', 'glyphs', 'sprite'].includes(key)) checkRef(value);
    } else if (Array.isArray(value)) value.forEach(v => walk(v, key));
    else if (value && typeof value === 'object') Object.entries(value).forEach(([k,v]) => walk(v,k));
  }
  walk(style);
  if (!Object.values(style.sources).some(s => ['vector','raster'].includes(s.type) && typeof s.url === 'string' && s.url.startsWith('pmtiles://local://'))) throw Error('必须包含本地 PMTiles 地图源');
  if (style.sprite) throw Error('阶段 0 暂不接纳 sprite；请使用无 sprite 样式（后续扩展）');
  for (const source of Object.values(style.sources)) if (source.tiles) throw Error('阶段 0 仅支持本地 PMTiles，不接纳独立 tiles 数组');
  for (const ref of references.filter(r => r.startsWith('pmtiles://'))) {
    const file=resolve(ref.slice('pmtiles://local://'.length));const data=Buffer.alloc(127);const handle=fs.openSync(file,'r');let length;
    try {length=fs.readSync(handle,data);} finally {fs.closeSync(handle);}
    const source=Object.values(style.sources).find(s=>s.url===ref);
    if (length < 127 || data.subarray(0,7).toString() !== 'PMTiles' || data[7] !== 3 || (source?.type==='raster' ? ![2,3,4].includes(data[99]) : data[99]!==1)) throw Error('需要匹配源类型的 PMTiles v3 文件');
  }
  return { manifest, bytes, fileCount: Object.keys(manifest.files).length, references };
}
