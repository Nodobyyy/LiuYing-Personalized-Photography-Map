import {readFileSync, writeFileSync} from 'node:fs';

const input = process.argv[2] || '.tooling/boundaries';
const output = process.argv[3] || 'app/src/online/assets/maps/world-boundaries.geojson';
const chinaView = process.argv[4] || 'app/src/online/assets/maps/china-view.json';

function simplify(points, tolerance) {
  if (points.length <= 2) return points;
  const [a, b] = [points[0], points.at(-1)];
  const dx = b[0] - a[0], dy = b[1] - a[1];
  const length = dx * dx + dy * dy;
  let furthest = 0, threshold = tolerance * tolerance;
  for (let i = 1; i < points.length - 1; i++) {
    const x = points[i][0] - a[0], y = points[i][1] - a[1];
    const t = length === 0 ? 0 : Math.max(0, Math.min(1, (x * dx + y * dy) / length));
    const distance = (x - dx * t) ** 2 + (y - dy * t) ** 2;
    if (distance > threshold) {
      threshold = distance;
      furthest = i;
    }
  }
  if (furthest === 0) return [a, b];
  return [...simplify(points.slice(0, furthest + 1), tolerance).slice(0, -1),
    ...simplify(points.slice(furthest), tolerance)];
}

const grouped = new Map();
for (const name of ['world', 'regions']) {
  const collection = JSON.parse(readFileSync(`${input}/${name}.geojson`, 'utf8'));
  for (const {geometry, properties} of collection.features) {
    if (!geometry) continue;
    if (name === 'regions' && ['CHN', 'TWN'].includes(properties.ADM0_A3)) continue;
    const kind = name === 'regions' ? 'region' :
      properties.FEATURECLA?.startsWith('International') ? 'country' : 'uncertain';
    const lines = geometry.type === 'LineString' ? [geometry.coordinates] :
      geometry.type === 'MultiLineString' ? geometry.coordinates : [];
    for (const points of lines) {
      const coordinates = simplify(points, name === 'world' ? 0.015 : 0.022)
        .map(point => point.slice(0, 2).map(value => Number(value.toFixed(4))));
      if (coordinates.length < 2 || coordinates.every(point => point[0] === coordinates[0][0] && point[1] === coordinates[0][1])) continue;
      if (!grouped.has(kind)) grouped.set(kind, []);
      grouped.get(kind).push(coordinates);
    }
  }
}

// A province polygon includes coastlines and the national perimeter. Keep
// only edges shared by two provinces and emit each shared line once.
const {provinceBoundaries} = JSON.parse(readFileSync(chinaView, 'utf8'));
const rings = [];
const owners = new Map();
const edgeKey = (a, b) => {
  const first = a.slice(0, 2).join(',');
  const second = b.slice(0, 2).join(',');
  return first < second ? first + '|' + second : second + '|' + first;
};
for (const {geometry, properties} of provinceBoundaries.features) {
  for (const polygon of geometry.coordinates) {
    for (const ring of polygon) {
      rings.push(ring);
      for (let i = 1; i < ring.length; i++) {
        const key = edgeKey(ring[i - 1], ring[i]);
        if (!owners.has(key)) owners.set(key, properties.gb);
        else if (owners.get(key) !== properties.gb) owners.set(key, null);
      }
    }
  }
}

const sharedLines = [];
for (const ring of rings) {
  let line = [];
  for (let i = 1; i < ring.length; i++) {
    const key = edgeKey(ring[i - 1], ring[i]);
    if (owners.get(key) === null) {
      if (!line.length) line.push(ring[i - 1]);
      line.push(ring[i]);
      owners.delete(key);
    } else if (line.length) {
      sharedLines.push(line);
      line = [];
    }
  }
  if (line.length) sharedLines.push(line);
}
if (!sharedLines.length) throw Error('Expected shared province boundaries');
grouped.set('china-region', sharedLines);

const features = [];
for (const [kind, lines] of grouped) {
  for (let i = 0; i < lines.length; i += 128) {
    features.push({type: 'Feature', properties: {kind}, geometry: {type: 'MultiLineString', coordinates: lines.slice(i, i + 128)}});
  }
}
writeFileSync(output, '{"type":"FeatureCollection","features":[\n' + features.map(feature => JSON.stringify(feature)).join(',\n') + '\n]}\n');
console.log(`Built ${features.length} groups of administrative boundary segments`);
