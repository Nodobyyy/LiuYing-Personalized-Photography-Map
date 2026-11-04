import fs from 'node:fs';
import path from 'node:path';
import {DatabaseSync} from 'node:sqlite';
import {execFileSync} from 'node:child_process';

const archive = path.resolve(process.argv[2] || '.tooling/world-satellite.pmtiles');
const pmtiles = path.resolve(process.argv[3] || '.tooling/pmtiles/pmtiles.exe');
const cache = path.join(path.dirname(archive), 'world-satellite-cache');
fs.mkdirSync(cache, {recursive: true});
const tiles = [];
const maxZoom = 2;
for (let z = 0; z <= maxZoom; z++) {
  for (let x = 0; x < 2 ** z; x++) {
    for (let y = 0; y < 2 ** z; y++) tiles.push({z, x, y, file: path.join(cache, `${z}-${x}-${y}.jpg`)});
  }
}
let cursor = 0;
async function worker() {
  while (cursor < tiles.length) {
    const {z, x, y, file} = tiles[cursor++];
    if (fs.existsSync(file)) continue;
    const url = `https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/${z}/${y}/${x}.jpg`;
    for (let attempt = 1; ; attempt++) {
      try {
        const response = await fetch(url, {signal: AbortSignal.timeout(30000)});
        if (!response.ok) throw Error(`HTTP ${response.status}: ${url}`);
        const tile = Buffer.from(await response.arrayBuffer());
        if (tile.length < 100 || tile.readUInt16BE(0) !== 0xffd8 || tile.readUInt16BE(tile.length - 2) !== 0xffd9) {
          throw Error(`Invalid JPEG: ${url}`);
        }
        fs.writeFileSync(file, tile);
        break;
      } catch (error) {
        if (attempt >= 4) throw error;
        await new Promise(resolve => setTimeout(resolve, attempt * 1500));
      }
    }
  }
}
await Promise.all(Array.from({length: 6}, worker));
const database = archive + '.mbtiles';
if (fs.existsSync(database)) fs.unlinkSync(database);
const db = new DatabaseSync(database);
try {
  db.exec('CREATE TABLE metadata(name TEXT,value TEXT); CREATE TABLE tiles(zoom_level INTEGER,tile_column INTEGER,tile_row INTEGER,tile_data BLOB); CREATE UNIQUE INDEX tile_index ON tiles(zoom_level,tile_column,tile_row); BEGIN');
  const meta = db.prepare('INSERT INTO metadata VALUES(?,?)');
  for (const [key, value] of Object.entries({
    name: 'EOX Sentinel-2 cloudless 2016 global overview', format: 'jpg', type: 'baselayer',
    bounds: '-180,-85,180,85', minzoom: '0', maxzoom: String(maxZoom),
    attribution: 'EOX / Contains modified Copernicus Sentinel data 2016 / CC BY 4.0'
  })) meta.run(key, value);
  const insert = db.prepare('INSERT INTO tiles VALUES(?,?,?,?)');
  for (const {z, x, y, file} of tiles) insert.run(z, x, 2 ** z - 1 - y, fs.readFileSync(file));
  db.exec('COMMIT');
} finally {
  db.close();
}
const temp = archive + '.part';
execFileSync(pmtiles, ['convert', database, temp], {stdio: 'inherit'});
execFileSync(pmtiles, ['verify', temp], {stdio: 'inherit'});
fs.renameSync(temp, archive);
console.log(`${tiles.length} tiles, ${fs.statSync(archive).size} bytes`);
