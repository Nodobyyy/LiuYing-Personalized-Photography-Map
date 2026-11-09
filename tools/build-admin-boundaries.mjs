import {readFileSync, writeFileSync} from 'node:fs';

const input = process.argv[2] || '.tooling/boundaries';
const output = process.argv[3] || 'app/src/online/assets/maps/world-boundaries.geojson';

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

const features = [];
for (const [kind, lines] of grouped) {
  for (let i = 0; i < lines.length; i += 128) {
    features.push({type: 'Feature', properties: {kind}, geometry: {type: 'MultiLineString', coordinates: lines.slice(i, i + 128)}});
  }
}
writeFileSync(output, '{"type":"FeatureCollection","features":[\n' + features.map(feature => JSON.stringify(feature)).join(',\n') + '\n]}\n');
console.log(`Built ${features.length} groups of administrative boundary segments`);
