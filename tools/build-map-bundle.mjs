import fs from 'node:fs';
import path from 'node:path';
import {hashFile,validateBundle} from './map-bundle.mjs';
import {mapStyle} from './map-style.mjs';
const input=process.argv[2]||'.tooling/china-base-z10-compact.pmtiles';
const root=path.resolve('app/src/offline/assets/maps');
fs.mkdirSync(root,{recursive:true});
// Remove only superseded generated map assets, never source/user files.
for(const name of ['beijing.pmtiles','style-beijing.json','satellite-beijing.pmtiles','satellite-urban.pmtiles','urban.pmtiles']){
 const file=path.resolve(root,name);if(!file.startsWith(root+path.sep))throw Error('Invalid asset path');if(fs.existsSync(file))fs.unlinkSync(file);
}
fs.copyFileSync(input,path.join(root,'china.pmtiles'));
fs.copyFileSync('.tooling/satellite-overview.pmtiles',path.join(root,'satellite-overview.pmtiles'));
fs.copyFileSync('.tooling/world-context-z6.pmtiles',path.join(root,'context.pmtiles'));
const fontDirectory='.tooling/map-fonts/Noto Sans Regular';
fs.mkdirSync(path.join(root,'fonts','Noto Sans Regular'),{recursive:true});
if(fs.readdirSync(fontDirectory).filter(n=>n.endsWith('.pbf')).length!==256)throw Error('All font ranges required');
for(const name of fs.readdirSync(fontDirectory))fs.copyFileSync(path.join(fontDirectory,name),path.join(root,'fonts','Noto Sans Regular',name));
fs.copyFileSync('.tooling/downloads/fonts-license.txt',path.join(root,'FONT-LICENSE.txt'));
fs.writeFileSync(path.join(root,'style.json'),JSON.stringify(mapStyle({maxZoom:10}),null,2));
const context=mapStyle({maxZoom:6,bounds:[-180,-85,180,85],tile:'context.pmtiles'});context.sources.context=context.sources.base;delete context.sources.base;
context.layers=context.layers.filter(l=>['background','land','water','waterways','major-roads','country-labels'].includes(l.id)).map(l=>{if(l.source)l.source='context';l.id='context-'+l.id;return l;});
const country=context.layers.find(l=>l.id==='context-country-labels');country.maxzoom=7;country.filter=['all',country.filter,['!',['in',['get','name'],['literal',['中国','China','中华人民共和国']]]]];
fs.writeFileSync(path.join(root,'style-context.json'),JSON.stringify(context,null,2));
fs.writeFileSync(path.join(root,'style-satellite.json'),JSON.stringify({version:8,sources:{'satellite-overview':{type:'raster',url:'pmtiles://local://satellite-overview.pmtiles',tileSize:256,minzoom:0,maxzoom:7,bounds:[73,3,136,54]}},layers:[{id:'satellite-overview',type:'raster',source:'satellite-overview',paint:{'raster-fade-duration':0}}]},null,2));
const urban=JSON.parse(fs.readFileSync('.tooling/china-urban-centres.json'));if(urban.properties?.exceededTransferLimit)throw Error('Truncated urban index');
for(const feature of urban.features)feature.properties={id:feature.properties.ID_UC_G0,name:feature.properties.GC_UCN_MAI_2025};
const groups=JSON.parse(fs.readFileSync('.tooling/urban-satellite-groups.json'));
const tiles=groups.filter(g=>g.z===14).flatMap(g=>g.tiles.map(t=>t.y*16384+t.x)).sort((a,b)=>a-b);
fs.writeFileSync(path.join(root,'urban-coverage.json'),JSON.stringify({version:1,zoom:14,urbanCentres:urban.features.length,tiles,centres:urban.features.map(f=>f.properties)}));
fs.writeFileSync(path.join(root,'URBAN-LICENSE.txt'),`GHSL Urban Centre Database R2024A, reference epoch 2025, European Commission / Joint Research Centre.\nSource: https://human-settlement.emergency.copernicus.eu/ghs_ucdb_2024.php\nOfficial feature service: https://services2.arcgis.com/jUpNdisbWqRpMo35/ArcGIS/rest/services/Urban_Centers_Database_2024_Socioeconomic/FeatureServer\nIndex contains ${urban.features.length} urban-centre polygons for China including Taiwan, used only to select/describe satellite detail coverage. Country categories removed from distributed metadata. Not an administrative boundary source and not every village/new development is an Urban Centre. Street data coverage is the complete China extraction, independent of this index.\nEuropean Commission reuse policy: https://commission.europa.eu/legal-notice_en\n`);
fs.writeFileSync(path.join(root,'SATELLITE-LICENSE.txt'),`Sentinel-2 cloudless 2016 by EOX IT Services GmbH. Contains modified Copernicus Sentinel data 2016.\nCreative Commons Attribution 4.0 International: https://creativecommons.org/licenses/by/4.0/\nAttribution/source: https://maps.eox.at/ and https://cloudless.eox.at/\nPrimary license evidence: https://tiles.maps.eox.at/wmts/1.0.0/WMTSCapabilities.xml (s2cloudless_3857 abstract).\nAPK includes China overview bbox 73,3,136,54 Z0-7 only. Imported national fine package adds urban imagery Z8-14. Imagery year 2016, not live/current. Sensor nominal 10m; overview is coarser.\nFine imagery is retrieved through standard WMS EPSG:3857 pixel-aligned windows, cropped to the exact XYZ tile grid and JPEG encoded. See fetch-urban-wms.mjs / crop-urban-wms.ps1. No fabricated imagery or coordinate shift.\nNo EOX/Copernicus endorsement or formal China map approval is claimed.\n`);
fs.writeFileSync(path.join(root,'LICENSE.txt'),`Protomaps basemap 2026-11-01 v4.15.2, derived from OpenStreetMap and Natural Earth.\nODbL Produced Work. Attribution: Protomaps / OpenStreetMap contributors.\nSource: https://build.protomaps.com/20261101.pmtiles\nhttps://docs.protomaps.com/basemaps/downloads\nhttps://www.openstreetmap.org/copyright\nhttps://opendatacommons.org/licenses/odbl/1-0/\nAPK China bbox 73,3,136,54 Z0-10 compact overview. Retained feature geometry unchanged; unused layers/attributes removed. Global context Z0-6 from same source. National fine package Z0-15 is imported separately. Fonts SIL OFL, see FONT-LICENSE.txt. China display references see VIEW-SOURCES.txt. Satellite/index see SATELLITE-LICENSE.txt / URBAN-LICENSE.txt.\n`);
fs.writeFileSync(path.join(root,'BOUNDARY_REVIEW.txt'),'China boundary, names and publication review have NOT been certified. Development build only; reference image approval number does not apply to this app.\n');
const names=[];function walk(dir,prefix=''){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const name=prefix+e.name;if(e.isDirectory())walk(path.join(dir,e.name),name+'/');else if(name!=='bundle.json')names.push(name);}}walk(root);
const files=Object.fromEntries(names.map(n=>[n,hashFile(path.join(root,n))])),fileSizes=Object.fromEntries(names.map(n=>[n,fs.statSync(path.join(root,n)).size]));
fs.writeFileSync(path.join(root,'bundle.json'),JSON.stringify({version:1,name:'中国离线基础概览',coordinateSystem:'WGS84',bounds:[73,3,136,54],minZoom:0,maxZoom:10,attribution:'Protomaps · © OpenStreetMap contributors',coverage:'China compact Z0-10, global context Z0-6, satellite overview Z0-7; national Z15 streets and urban imagery imported separately',licenseEvidence:'LICENSE.txt',boundaryReview:'BOUNDARY_REVIEW.txt',boundaryReviewPassed:false,fileSizes,files},null,2));
const result=validateBundle(root);console.log(JSON.stringify({bytes:result.bytes,files:result.fileCount,urbanCentres:urban.features.length,baseSHA:files['china.pmtiles']},null,2));
