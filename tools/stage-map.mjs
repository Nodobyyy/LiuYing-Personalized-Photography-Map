import fs from 'node:fs';
import path from 'node:path';
import { validateBundle } from './map-bundle.mjs';
const input = process.argv[2];
if (!input) { console.error('用法: node tools/stage-map.mjs <已核验地图包目录> [--check]'); process.exit(2); }
try {
  const report = validateBundle(path.resolve(input));
  if (!process.argv.includes('--check')) {
    const output = path.resolve('app/src/offline/assets/maps');
    if (fs.existsSync(output)) throw Error('目标已存在。请先保留现有地图包，再显式移出该目录');
    // Validation succeeds before any project assets are written.
    fs.mkdirSync(output, { recursive: true });
    for (const name of ['bundle.json', ...Object.keys(report.manifest.files)]) {
      const target = path.join(output, name); fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.copyFileSync(path.join(input, name), target);
    }
  }
  console.log(JSON.stringify({ status: '结构与哈希检查通过；不代表法律、边界和实际覆盖验收通过', ...report }, null, 2));
} catch (error) { console.error(error.message); process.exit(1); }
