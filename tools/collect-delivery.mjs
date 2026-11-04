import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {hashFile} from './map-bundle.mjs';

const output = path.resolve('deliverables');
const national = 'LiuYing-China-Z15-offline.zip';
const nationalDigest = hashFile(path.join(output, national));
const digests = [];
const builds = [];

for (const flavor of ['offline', 'online']) {
  const directory = `app/build/outputs/apk/${flavor}/debug`;
  const [metadata] = JSON.parse(fs.readFileSync(path.join(directory, 'output-metadata.json'), 'utf8')).elements;
  const apk = path.join(directory, metadata.outputFile);
  const digest = hashFile(apk);
  const name = `LiuYing-${metadata.versionName}-${flavor}-debug.apk`;
  const suites = flavor === 'offline'
    ? [['overview', 1], ['import', 1], ['maps', 3]]
    : [['online', 2]];
  for (const api of [30, 37]) {
    for (const [suite, count] of suites) {
      const log = fs.readFileSync(`.tooling/qa-${api}-${suite}.log`, 'utf8');
      if (!log.includes(`APK SHA256: ${digest}`) ||
          !log.includes(`OK (${count} test${count === 1 ? '' : 's'})`) ||
          (suite === 'import' && !log.includes(`ZIP SHA256: ${nationalDigest}`))) {
        throw Error(`Missing passing evidence for current ${flavor} APK, API ${api}, suite ${suite}`);
      }
    }
  }
  builds.push({flavor, apk, name, digest});
}

for (const {flavor, apk, name, digest} of builds) {
  execFileSync(process.execPath, ['tools/check-apk-native.mjs', apk, path.join(output, `native-${flavor}.json`)], {stdio: 'inherit'});
  fs.copyFileSync(apk, path.join(output, name));
  digests.push(`${digest}  ${name}`);
}
digests.push(`${nationalDigest}  ${national}`);
fs.writeFileSync(path.join(output, 'SHA256SUMS.txt'), digests.join('\n') + '\n');
