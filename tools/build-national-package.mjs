import fs from 'node:fs';import path from 'node:path';
import {hashFile,validateBundle} from './map-bundle.mjs';import {mapStyle} from './map-style.mjs';
const [vector='.tooling/china-full-z15.pmtiles',raster='.tooling/satellite-urban.pmtiles',output='.tooling/national-package']=process.argv.slice(2);
const root=path.resolve(output),workspace=path.resolve('.');if(!root.startsWith(workspace+path.sep))throw Error('Stage must stay within project');fs.mkdirSync(root,{recursive:true});
for(const [source,name]of[[vector,'china-fine.pmtiles'],[raster,'satellite-urban.pmtiles']]){
 const target=path.join(root,name);if(fs.existsSync(target)){if(fs.statSync(target).size!==fs.statSync(source).size)fs.unlinkSync(target);}
 if(!fs.existsSync(target)){try{fs.linkSync(path.resolve(source),target);}catch{fs.copyFileSync(source,target);}}
}
const assets='app/src/offline/assets/maps';fs.cpSync(path.join(assets,'fonts'),path.join(root,'fonts'),{recursive:true});
for(const name of ['FONT-LICENSE.txt','BOUNDARY_REVIEW.txt','SATELLITE-LICENSE.txt','URBAN-LICENSE.txt','urban-coverage.json'])fs.copyFileSync(path.join(assets,name),path.join(root,name));
fs.writeFileSync(path.join(root,'LICENSE.txt'),'Protomaps 2026-11-01 v4.15.2 / OpenStreetMap contributors; ODbL Produced Work.\nhttps://build.protomaps.com/20261101.pmtiles\nhttps://docs.protomaps.com/basemaps/downloads\nhttps://www.openstreetmap.org/copyright\nComplete China/nearby bbox 73,3,136,54 Z0-15, unmodified MVT payloads. Street/building/POI detail is uniform nationwide, subject to OSM completeness.\nSatellite EOX / Copernicus Sentinel 2016 CC BY 4.0; see SATELLITE-LICENSE.txt. Urban imagery coverage selected using GHSL 2025 urban-centre index, then resampled into a standard tile pyramid; original observed colours retained, no invented imagery.\nFonts SIL OFL. No formal China publication/map approval certification.\n');
fs.writeFileSync(path.join(root,'style.json'),JSON.stringify(mapStyle({maxZoom:15,tile:'china-fine.pmtiles'}),null,2));
fs.writeFileSync(path.join(root,'style-satellite.json'),JSON.stringify({version:8,sources:{'satellite-urban':{type:'raster',url:'pmtiles://local://satellite-urban.pmtiles',tileSize:256,minzoom:8,maxzoom:14,bounds:[73,3,136,54]}},layers:[{id:'satellite-urban',type:'raster',source:'satellite-urban',minzoom:8,paint:{'raster-fade-duration':0}}]},null,2));
const names=[];function walk(dir,prefix=''){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const n=prefix+e.name;if(e.isDirectory())walk(path.join(dir,e.name),n+'/');else if(n!=='bundle.json')names.push(n);}}walk(root);
const files=Object.fromEntries(names.map(n=>[n,hashFile(path.join(root,n))])),fileSizes=Object.fromEntries(names.map(n=>[n,fs.statSync(path.join(root,n)).size]));
const m={version:1,name:'全国精细离线大包 Z15＋城区卫星影像',coordinateSystem:'WGS84',bounds:[73,3,136,54],minZoom:7,maxZoom:15,attribution:'Protomaps · © OpenStreetMap contributors',coverage:'Complete national bbox Z0-15 streets/buildings/POIs, plus 1995 GHSL-indexed urban centres with EOX 2016 nominal 10m imagery Z8-14. Outside fine imagery cells the app shows its local overview, not fabricated detail.',licenseEvidence:'LICENSE.txt',boundaryReview:'BOUNDARY_REVIEW.txt',boundaryReviewPassed:false,files,fileSizes};
fs.writeFileSync(path.join(root,'bundle.json'),JSON.stringify(m,null,2));
const result=validateBundle(root);validateBundle(root,{styleName:'style-satellite.json',verifyFiles:false});
console.log(JSON.stringify({files:result.fileCount,bytes:result.bytes,vectorSHA:files['china-fine.pmtiles'],rasterSHA:files['satellite-urban.pmtiles']},null,2));
