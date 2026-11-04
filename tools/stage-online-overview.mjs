import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {hashFile} from './map-bundle.mjs';

const source = path.resolve(process.argv[2] || 'app/src/offline/assets/maps');
const pmtiles = path.resolve(process.argv[3] || '.tooling/pmtiles/pmtiles.exe');
const target = path.resolve('app/src/online/assets/maps/overview');
const worldSatellite = path.resolve(process.argv[4] || '.tooling/world-satellite.pmtiles');
const archives = ['china.pmtiles', 'context.pmtiles', 'satellite-overview.pmtiles', 'world-satellite.pmtiles'];
const files = ['style.json', 'style-context.json', 'style-satellite.json', 'LICENSE.txt', 'SATELLITE-LICENSE.txt'];
const base = JSON.parse(fs.readFileSync(path.join(source, 'bundle.json'), 'utf8'));

for (const name of [...archives.slice(0, 3), ...files]) {
  if (hashFile(path.join(source, name)) !== base.files[name]) throw Error(`Invalid source asset: ${name}`);
}
fs.mkdirSync(target, {recursive: true});
for (const name of archives) {
  const input = name === 'world-satellite.pmtiles' ? worldSatellite : path.join(source, name);
  const output = path.join(target, name);
  if (fs.existsSync(output) && hashFile(output) === (base.files[name] || hashFile(input))) continue;
  const temporary = output + '.tmp';
  try { fs.linkSync(input, temporary); } catch { fs.copyFileSync(input, temporary); }
  fs.renameSync(temporary, output);
}
for (const name of files) fs.copyFileSync(path.join(source, name), path.join(target, name));
const manifest = Object.fromEntries(archives.map(name => {
  const archive = path.join(target, name);
  execFileSync(pmtiles, ['verify', archive], {stdio: 'inherit'});
  return [name, {size: fs.statSync(archive).size, sha256: hashFile(archive)}];
}));
fs.writeFileSync(path.join(target, 'archives.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(`Online overview: ${Object.values(manifest).reduce((sum, entry) => sum + entry.size, 0)} bytes`);
