import fs from 'node:fs';import path from 'node:path';import {mapStyle} from './map-style.mjs';import {hashFile} from './map-bundle.mjs';
const root='app/src/online/assets/maps',offline='app/src/offline/assets/maps';fs.mkdirSync(root,{recursive:true});
for(const name of ['fonts','china-view.json','urban-coverage.json','FONT-LICENSE.txt','VIEW-SOURCES.txt','BOUNDARY-LICENSE.txt','SATELLITE-LICENSE.txt','URBAN-LICENSE.txt'])fs.cpSync(path.join(offline,name),path.join(root,name),{recursive:true});
const style=mapStyle({maxZoom:14});style.name='PhotoMap online OpenFreeMap';style.sources={base:{type:'vector',url:'https://tiles.openfreemap.org/planet',maxzoom:14}};
const sourceLayer={landcover:'landcover',landuse:'landuse',water:'water',buildings:'building',roads:'transportation',boundaries:'boundary',places:'place',pois:'poi'};
style.layers=style.layers.filter(l=>!['land','boundaries'].includes(l.id));
const name=['coalesce',['get','name:zh-Hans'],['get','name:zh'],['get','name'],['get','name:en']];
for(const layer of style.layers){if(layer['source-layer'])layer['source-layer']=sourceLayer[layer['source-layer']];
 if(layer.id==='background')layer.paint['background-color']='#efeee7';
 if(layer.layout?.['text-field']){layer.layout['text-field']=name;layer.layout['symbol-sort-key']=['coalesce',['get','rank'],0];}
 switch(layer.id){
 case 'waterways':layer['source-layer']='waterway';break;
 case 'roads':layer.filter=['all',['==',['geometry-type'],'LineString'],['!',['in',['get','class'],['literal',['rail','ferry','aerialway']]]]];break;
 case 'major-roads':layer.filter=['in',['get','class'],['literal',['motorway','trunk','primary','secondary']]];layer.paint['line-color']=['match',['get','class'],['motorway','trunk'],'#dba866','primary','#e9c78e','#f0dfbc'];break;
 case 'railways':layer.filter=['==',['get','class'],'rail'];break;
 case 'country-labels':layer.filter=['==',['get','class'],'country'];break;
 case 'place-labels':layer.filter=['in',['get','class'],['literal',['city','town']]];break;
 case 'county-labels':layer.filter=['in',['get','class'],['literal',['suburb','quarter','neighbourhood']]];break;
 case 'locality-labels':layer.filter=['in',['get','class'],['literal',['village','hamlet','isolated_dwelling']]];break;
 case 'poi-labels':layer.filter=['any',['has','name'],['has','name:zh']];break;
 case 'road-labels':layer['source-layer']='transportation_name';layer.filter=['all',['any',['has','name'],['has','name:zh']],['!=',['get','class'],'rail']];break;
 case 'water-labels':layer['source-layer']='water_name';layer.filter=['all',['==',['geometry-type'],'Point'],['any',['has','name'],['has','name:zh']]];break;
 }
}
const country=JSON.parse(JSON.stringify(style.layers.find(l=>l.id==='country-labels')));country.id='context-country-labels';country.maxzoom=7;country.filter=['all',country.filter,['!',['in',['get','iso_a2'],['literal',['CN','TW','HK','MO']]]]];style.layers.push(country);
fs.writeFileSync(path.join(root,'style-online.json'),JSON.stringify(style,null,2));
fs.writeFileSync(path.join(root,'satellite-online.json'),JSON.stringify({version:8,sources:{'satellite-online':{type:'raster',tiles:['https://satellite.photomap.local/{z}/{x}/{y}.jpg'],tileSize:256,minzoom:0,maxzoom:18}},layers:[{id:'satellite-online',type:'raster',source:'satellite-online',paint:{'raster-fade-duration':200}}]},null,2));
fs.writeFileSync(path.join(root,'ONLINE-LICENSE.txt'),'Online street maps: OpenFreeMap / OpenMapTiles / OpenStreetMap contributors.\nhttps://openfreemap.org/quick_start/\nhttps://openfreemap.org/\nhttps://www.openstreetmap.org/copyright\nSource endpoint https://tiles.openfreemap.org/planet (weekly data, no key or account). Local custom style; no remote style/sprite execution. Chinese boundary references remain separate.\nSatellite (mainland China): AutoNavi / Gaode satellite imagery (webst01-04.is.autonavi.com), converted on-device from GCJ-02 to WGS-84 so it aligns with photo GPS coordinates.\nSatellite (outside mainland China / high-zoom fallback): Microsoft Bing Maps Aerial via domestic Tencent EdgeOne CDN (t0-t3.dynamic.tiles.ditu.live.com, standard WGS-84 QuadKey tiles), with Esri, Maxar, Earthstar Geographics, and the GIS User Community (ArcGIS World Imagery) as final fallback.\nhttps://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer\nBundled overview imagery: EOX Sentinel-2 cloudless 2016 (Contains modified Copernicus Sentinel data 2016, CC BY 4.0). https://maps.eox.at/\nOnly HTTPS GET/HEAD map resource requests to the listed tile hosts are permitted. No photograph, EXIF or photo-library data is sent. Fonts are local SIL OFL.\n');
const names=[];function walk(dir,p=''){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const n=p+e.name;if(e.isDirectory())walk(path.join(dir,e.name),n+'/');else if(n!=='online-files.json')names.push(n);}}walk(root);
fs.writeFileSync(path.join(root,'online-files.json'),JSON.stringify({files:Object.fromEntries(names.map(n=>[n,hashFile(path.join(root,n))]))},null,2));
console.log(JSON.stringify({bytes:names.reduce((n,p)=>n+fs.statSync(path.join(root,p)).size,0),files:names.length}));
