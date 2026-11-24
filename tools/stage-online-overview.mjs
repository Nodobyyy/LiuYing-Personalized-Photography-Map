import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {hashFile} from './map-bundle.mjs';
import {mapStyle} from './map-style.mjs';

const source = path.resolve(process.argv[2] || 'app/src/offline/assets/maps');
const pmtiles = path.resolve(process.argv[3] || '.tooling/pmtiles/pmtiles.exe');
const target = path.resolve('app/src/online/assets/maps/overview');
const worldSatellite = path.resolve(process.argv[4] || '.tooling/world-satellite.pmtiles');
const worldStreets = path.resolve(process.argv[5] || '.tooling/world-z6-compact.pmtiles');
const chinaZoom = 8;
const worldZoom = 6;
const base = JSON.parse(fs.readFileSync(path.join(source, 'bundle.json'), 'utf8'));
const sourceAssets = ['china.pmtiles', 'satellite-overview.pmtiles', 'style-satellite.json', 'LICENSE.txt', 'SATELLITE-LICENSE.txt'];

for (const name of sourceAssets) {
  if (hashFile(path.join(source, name)) !== base.files[name]) throw Error(`Invalid source asset: ${name}`);
}
fs.mkdirSync(target, {recursive: true});
const archives = new Map([
  ['china.pmtiles', [path.join(source, 'china.pmtiles'), chinaZoom]],
  ['context.pmtiles', [worldStreets, worldZoom]],
  ['satellite-overview.pmtiles', [path.join(source, 'satellite-overview.pmtiles')]],
  ['world-satellite.pmtiles', [worldSatellite]],
]);

const manifest = {};
for (const [name, [input, zoom]] of archives) {
  const output = path.join(target, name);
  const temporary = output + '.tmp';
  fs.rmSync(temporary, {force: true});
  try {
    if (zoom !== undefined) {
      execFileSync(pmtiles, ['extract', input, temporary, `--maxzoom=${zoom}`], {stdio: 'inherit'});
    } else {
      fs.copyFileSync(input, temporary);
    }
    execFileSync(pmtiles, ['verify', temporary], {stdio: 'inherit'});
    manifest[name] = {size: fs.statSync(temporary).size, sha256: hashFile(temporary)};
    fs.renameSync(temporary, output);
  } finally {
    fs.rmSync(temporary, {force: true});
  }
}
fs.rmSync(path.join(target, 'world-roads.pmtiles'), {force: true});
for (const name of ['style-satellite.json', 'SATELLITE-LICENSE.txt']) fs.copyFileSync(path.join(source, name), path.join(target, name));
const china = mapStyle({maxZoom: chinaZoom});
const context = mapStyle({maxZoom: worldZoom, bounds: [-180, -85.0511287, 180, 85.0511287], tile: 'context.pmtiles'});
context.sources.context = context.sources.base;
delete context.sources.base;
const included = new Set(['background', 'land', 'water', 'roads', 'major-roads', 'country-labels', 'place-labels', 'county-labels']);
context.layers = context.layers.filter(layer => included.has(layer.id)).map(layer => ({
  ...layer, id: 'context-' + layer.id, ...(layer.source ? {source: 'context'} : {})
}));
const license = fs.readFileSync(path.join(source, 'LICENSE.txt'), 'utf8')
  .replace('Z0-10 compact overview', 'Z0-8 compact overview')
  .replace('Global context Z0-6 from same source.', 'Global context Z0-6 from the same source.');
for (const [name, value] of [['style.json', china], ['style-context.json', context], ['archives.json', manifest]]) {
  fs.writeFileSync(path.join(target, name), JSON.stringify(value, null, 2) + '\n');
}
fs.writeFileSync(path.join(target, 'LICENSE.txt'), license);
console.log(`Online overview: ${Object.values(manifest).reduce((sum, entry) => sum + entry.size, 0)} bytes`);
