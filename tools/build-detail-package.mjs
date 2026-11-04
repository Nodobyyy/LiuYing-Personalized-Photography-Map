import fs from 'node:fs';
import path from 'node:path';
import {mapStyle} from './map-style.mjs';
import {hashFile,validateBundle} from './map-bundle.mjs';
const [input,output]=process.argv.slice(2);if(!input||!output)throw Error('Usage: node tools/build-detail-package.mjs verified-china-z13.pmtiles output-directory');
const root=path.resolve(output);fs.mkdirSync(root,{recursive:true});
const tile=path.join(root,'china-streets.pmtiles');
if(!fs.existsSync(tile)) {try{fs.linkSync(path.resolve(input),tile);}catch{fs.copyFileSync(input,tile);}}
if(hashFile(input)!==hashFile(tile))throw Error('Staged tile file does not match input');
const assets='app/src/offline/assets/maps';
fs.cpSync(path.join(assets,'fonts'),path.join(root,'fonts'),{recursive:true});
for(const name of ['FONT-LICENSE.txt','BOUNDARY_REVIEW.txt'])fs.copyFileSync(path.join(assets,name),path.join(root,name));
fs.writeFileSync(path.join(root,'LICENSE.txt'),'Protomaps 2026-11-01 v4.15.2 / OpenStreetMap contributors. ODbL Produced Work.\nSource: https://build.protomaps.com/20261101.pmtiles\nLicense: https://docs.protomaps.com/basemaps/downloads\nhttps://www.openstreetmap.org/copyright\nChina/nearby extraction bbox=73,3,136,54; Z0-13; unmodified MVT tile payloads.\nFonts: SIL OFL; see FONT-LICENSE.txt. Official China map approval is not claimed.\n');
fs.writeFileSync(path.join(root,'style.json'),JSON.stringify(mapStyle({maxZoom:13,tile:'china-streets.pmtiles'}),null,2));
const names=[];function walk(dir,prefix=''){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const name=prefix+e.name;if(e.isDirectory())walk(path.join(dir,e.name),name+'/');else if(name!=='bundle.json')names.push(name);}}walk(root);
const manifest={version:1,name:'全国街道与景点增强图 Z13',coordinateSystem:'WGS84',bounds:[73,3,136,54],minZoom:8,maxZoom:13,coverage:'China bbox 73,3,136,54, Z0-13; street/POI completeness follows OSM; no invented detail above Z13',attribution:'Protomaps · © OpenStreetMap contributors · 全国增强图',licenseEvidence:'LICENSE.txt',boundaryReview:'BOUNDARY_REVIEW.txt',boundaryReviewPassed:false,fileSizes:Object.fromEntries(names.map(n=>[n,fs.statSync(path.join(root,n)).size])),files:Object.fromEntries(names.map(n=>[n,hashFile(path.join(root,n))]))};
fs.writeFileSync(path.join(root,'bundle.json'),JSON.stringify(manifest,null,2));const result=validateBundle(root);
console.log(JSON.stringify({directory:root,files:result.fileCount,bytes:result.bytes,sha256:manifest.files['china-streets.pmtiles']}));
