import fs from 'node:fs';
import crypto from 'node:crypto';
import clipping from './vendor/polygon-clipping.cjs';
const root='app/src/offline/assets/maps';fs.mkdirSync(root,{recursive:true});
const input='.tooling/prc-regions-wgs84.json';
const source=JSON.parse(fs.readFileSync(input));
const regions=source.features.filter(f=>f.properties.gb && f.geometry.type==='MultiPolygon');
const lines=source.features.filter(f=>!f.properties.gb && f.geometry.type==='MultiLineString');
if(regions.length!==34 || lines.length!==8)throw Error('Expected 34 provinces and 8 reference boundary features');
const polygons=clipping.union(...regions.map(f=>f.geometry.coordinates));
const outline={type:'MultiPolygon',coordinates:polygons};
const outside={type:'Polygon',coordinates:[[[ -179.999,-85],[179.999,-85],[179.999,85],[-179.999,85],[-179.999,-85]],...polygons.map(p=>p[0])]};
// Text visibility uses a smaller geometry; displayed boundaries retain the original vertices.
function simplifyRing(ring,tolerance=0.008) {
 const keep=new Set([0,ring.length-1]),stack=[[0,ring.length-1]];
 while(stack.length) {const [a,b]=stack.pop();let selected=-1,best=tolerance*tolerance;const dx=ring[b][0]-ring[a][0],dy=ring[b][1]-ring[a][1];
  for(let i=a+1;i<b;i++){const x=ring[i][0]-ring[a][0],y=ring[i][1]-ring[a][1],t=Math.max(0,Math.min(1,(x*dx+y*dy)/(dx*dx+dy*dy||1))),distance=(x-t*dx)**2+(y-t*dy)**2;if(distance>best){best=distance;selected=i;}}
  if(selected>=0){keep.add(selected);stack.push([a,selected],[selected,b]);}
 }
 const result=[...keep].sort((a,b)=>a-b).map(i=>ring[i]);return result.length>=4?result:ring;
}
const labelOutline={type:'MultiPolygon',coordinates:polygons.map(p=>p.map(r=>simplifyRing(r)))};
function inside(x,y,ring) {let yes=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[i],b=ring[j];if((a[1]>y)!==(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])yes=!yes;}return yes;}
function area(ring){let a=0;for(let i=0,j=ring.length-1;i<ring.length;j=i++)a+=ring[j][0]*ring[i][1]-ring[i][0]*ring[j][1];return Math.abs(a)/2;}
function labelPoint(geometry) {
 const ring=geometry.coordinates.map(p=>p[0]).sort((a,b)=>area(b)-area(a))[0];
 const xs=ring.map(p=>p[0]),ys=ring.map(p=>p[1]);
 let west=Math.min(...xs),east=Math.max(...xs),south=Math.min(...ys),north=Math.max(...ys),best=ring[0],distance=-1;
 function clearance(x,y){let nearest=Infinity;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[j],b=ring[i];const dx=b[0]-a[0],dy=b[1]-a[1];const t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy||1)));nearest=Math.min(nearest,(x-a[0]-t*dx)**2+(y-a[1]-t*dy)**2);}return nearest;}
 for(let round=0;round<3;round++) {
  const size=round===0?20:10,dx=(east-west)/size,dy=(north-south)/size;
  for(let i=0;i<size;i++)for(let j=0;j<size;j++){const x=west+(i+0.5)*dx,y=south+(j+0.5)*dy;if(inside(x,y,ring)){const d=clearance(x,y);if(d>distance){distance=d;best=[x,y];}}}
  west=best[0]-dx;east=best[0]+dx;south=best[1]-dy;north=best[1]+dy;
 }
 return best.map(n=>Number(n.toFixed(6)));
}
const provinces=regions.map(f=>({type:'Feature',properties:{name:f.properties.full_name,code:f.properties.gb,kind:'region'},geometry:{type:'Point',coordinates:labelPoint(f.geometry)}}));
if(!provinces.some(f=>f.properties.name==='台湾省'))throw Error('Taiwan province is required');
const boundaryLines={type:'FeatureCollection',features:lines};
const provinceBoundaries={type:'FeatureCollection',features:regions};
fs.writeFileSync(`${root}/china-view.json`,JSON.stringify({outline,labelOutline,outside,provinces:{type:'FeatureCollection',features:provinces},provinceBoundaries,boundaryLines}));
fs.copyFileSync('.tooling/prc-geodata-license.txt',`${root}/BOUNDARY-LICENSE.txt`);
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
fs.writeFileSync(`${root}/VIEW-SOURCES.txt`,[
 'China display references: chinese-global-compliant-geodata, MIT license; see BOUNDARY-LICENSE.txt.',
 'Pinned commit: 3cf3485789876c37be6193bf2848cf9aaa40f758',
 'https://github.com/JayMuShui/chinese-global-compliant-geodata/blob/3cf3485789876c37be6193bf2848cf9aaa40f758/src/geojson/countries/as/chn/global/chn-level-1.json',
 'Upstream documentation identifies Tianditu / National Platform for Common Geospatial Information Services as its source and WGS84 as its coordinate system.',
 `Source SHA256: ${sha(input)}`,
 'All 34 provinces and all 8 boundary-line features are retained, including South China Sea reference lines and island polygons. Province polygons are unioned for display masking; label positions are computed inside their main polygon.',
 'Natural Earth is NOT used for the China display boundary. No GCJ-02 shapes are mixed into the WGS84 street map.',
 'User-provided reference: 版图.jpg, printed approval GS(2019)1818. That approval belongs to the supplied reference image, not this modified interactive map.',
 'This third-party dataset and the rendered interactive map have NOT received a fresh official boundary/publication certification. No approval number is claimed for the application.'
].join('\n')+'\n');
console.log(JSON.stringify({provinceCount:provinces.length,boundaryFeatures:lines.length,polygons:polygons.length,bytes:fs.statSync(`${root}/china-view.json`).size}));
