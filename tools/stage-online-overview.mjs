import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {hashFile} from './map-bundle.mjs';
import {mapStyle} from './map-style.mjs';

const source = path.resolve(process.argv[2] || 'app/src/offline/assets/maps');
const pmtiles = path.resolve(process.argv[3] || '.tooling/pmtiles/pmtiles.exe');
const target = path.resolve('app/src/online/assets/maps/overview');
const worldSatellite = path.resolve(process.argv[4] || '.tooling/world-satellite.pmtiles');
const worldStreets = path.resolve(process.argv[5] || '.tooling/world-z8-compact.pmtiles');
const worldRoads = path.resolve(process.argv[6] || '.tooling/world-roads-z9.pmtiles');
const archives = ['china.pmtiles', 'context.pmtiles', 'satellite-overview.pmtiles', 'world-satellite.pmtiles', 'world-roads.pmtiles'];
const files = ['style.json', 'style-context.json', 'style-satellite.json', 'LICENSE.txt', 'SATELLITE-LICENSE.txt'];
const base = JSON.parse(fs.readFileSync(path.join(source, 'bundle.json'), 'utf8'));

for (const name of [...archives.slice(0, 3), ...files]) {
  if (hashFile(path.join(source, name)) !== base.files[name]) throw Error(`Invalid source asset: ${name}`);
}
fs.mkdirSync(target, {recursive: true});
for (const name of archives) {
  const input = name === 'world-satellite.pmtiles' ? worldSatellite :
    name === 'context.pmtiles' ? worldStreets : name === 'world-roads.pmtiles' ? worldRoads : path.join(source, name);
  const output = path.join(target, name);
  if (fs.existsSync(output) && hashFile(output) === hashFile(input)) continue;
  const temporary = output + '.tmp';
  try { fs.linkSync(input, temporary); } catch { fs.copyFileSync(input, temporary); }
  fs.renameSync(temporary, output);
}
for (const name of files) fs.copyFileSync(path.join(source, name), path.join(target, name));
const header = Buffer.alloc(127);
const fd = fs.openSync(worldStreets, 'r');
try {
  if (fs.readSync(fd, header) !== 127 || header.toString('ascii', 0, 7) !== 'PMTiles' ||
      header[99] !== 1 || header[101] < 8) throw Error('Expected a global Z8+ vector archive');
} finally {
  fs.closeSync(fd);
}
const context = mapStyle({maxZoom: header[101], bounds: [-180, -85.0511287, 180, 85.0511287], tile: 'context.pmtiles'});
const included = new Set(['background', 'land', 'water', 'waterways', 'roads', 'major-roads',
  'country-labels', 'place-labels', 'county-labels', 'road-labels']);
context.sources.context = context.sources.base;
delete context.sources.base;
context.layers = context.layers.filter(layer => included.has(layer.id)).map(layer => {
  if (layer.source) layer.source = 'context';
  if (layer.id === 'road-labels') {
    layer.minzoom = 8.5;
    layer.maxzoom = 16;
  }
  layer.id = 'context-' + layer.id;
  return layer;
});
const roadsStyle = mapStyle({maxZoom: 9, bounds: [-180, -85.0511287, 180, 85.0511287], tile: 'world-roads.pmtiles'});
context.sources['context-detail'] = {...roadsStyle.sources.base, minzoom: 9};
const roadIds = new Set(['roads', 'major-roads', 'road-labels']);
for (const layer of context.layers) {
  if (roadIds.has(layer.id.replace('context-', ''))) layer.maxzoom = 9.5;
}
for (const layer of roadsStyle.layers.filter(layer => roadIds.has(layer.id))) {
  if (layer.id === 'road-labels') {
    layer.filter = ['all', ['any', ['has', 'name'], ['has', 'name:zh-Hans'], ['has', 'name:en'], ['has', 'ref']],
      ['!in', 'kind', 'rail', 'aeroway', 'ferry']];
    layer.layout['text-field'].push(['get', 'ref']);
  }
  layer.source = 'context-detail';
  layer.id = 'context-detail-' + layer.id;
  layer.minzoom = 9.5;
  context.layers.push(layer);
}
fs.writeFileSync(path.join(target, 'style-context.json'), JSON.stringify(context, null, 2) + '\n');
const licenseFile = path.join(target, 'LICENSE.txt');
const license = fs.readFileSync(licenseFile, 'utf8');
fs.writeFileSync(licenseFile, license.replace('Global context Z0-6 from same source.',
  `Global Z0-${header[101]} compact context and a global Z9 selected-road overlay from the same source; retained geometry is unchanged.`));
const manifest = Object.fromEntries(archives.map(name => {
  const archive = path.join(target, name);
  execFileSync(pmtiles, ['verify', archive], {stdio: 'inherit'});
  return [name, {size: fs.statSync(archive).size, sha256: hashFile(archive)}];
}));
fs.writeFileSync(path.join(target, 'archives.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(`Online overview: ${Object.values(manifest).reduce((sum, entry) => sum + entry.size, 0)} bytes`);
