export function mapStyle({maxZoom=10,bounds=[73,3,136,54],tile='china.pmtiles'}={}) {
 const layers=[{id:'background',type:'background',paint:{'background-color':'#cbdde3'}}];
 const text=['coalesce',['get','name:zh-Hans'],['get','name'],['get','name:en']];
 const font=['Noto Sans Regular'];
 const named=['any',['has','name'],['has','name:zh-Hans'],['has','name:en']];
 function layer(id,type,sourceLayer,paint,extra={}) {layers.push({id,type,source:'base','source-layer':sourceLayer,paint,...extra});}
 const textPaint={'text-color':'#566156','text-halo-color':'#f4f2ec','text-halo-width':1.3};
 const label=(size,padding=6)=>({'text-field':text,'text-font':font,'text-size':size,'text-padding':padding,'symbol-sort-key':['coalesce',['get','min_zoom'],0]});
 layer('land','fill','earth',{'fill-color':'#efeee7'});
 layer('landcover','fill','landcover',{'fill-color':'#dce4d5','fill-opacity':0.6});
 layer('landuse','fill','landuse',{'fill-color':'#e4e7db','fill-opacity':0.6},{minzoom:7});
 // The source mixes polygons, river/canal lines and label points. Filling open
 // river lines closes them into huge imaginary lakes (e.g. south Beijing).
 layer('water','fill','water',{'fill-color':'#a9d4e9'},{filter:['==',['geometry-type'],'Polygon']});
 layer('waterways','line','water',{'line-color':'#83bfdc','line-width':['interpolate',['linear'],['zoom'],8,0.4,13,1,17,2.5]},{minzoom:8,filter:['==',['geometry-type'],'LineString']});
 layer('buildings','fill','buildings',{'fill-color':'#d2d4cb','fill-outline-color':'#c1c8bc'},{minzoom:13});
 layer('roads','line','roads',{'line-color':'#ffffff','line-width':['interpolate',['linear'],['zoom'],5,0.5,10,1.3,15,3.2,17,5]},{minzoom:5,filter:['!=','kind','rail']});
 layer('major-roads','line','roads',{'line-color':['match',['get','kind_detail'],['motorway','motorway_link','trunk','trunk_link'],'#dba866',['primary','primary_link'],'#e9c78e','#f0dfbc'],'line-width':['interpolate',['linear'],['zoom'],6,0.6,12,1.8,17,5]},{minzoom:6,filter:['in','kind','highway','major_road']});
 layer('railways','line','roads',{'line-color':'#a7b1aa','line-width':1,'line-dasharray':[2,2]},{minzoom:11,filter:['==','kind','rail']});
 layer('boundaries','line','boundaries',{'line-color':'#a6aca0','line-width':0.8,'line-dasharray':[3,2]},{minzoom:7,filter:['in','kind','county','locality']});
 layer('country-labels','symbol','places',textPaint,{minzoom:1,maxzoom:3.25,filter:['==','kind','country'],layout:label(16,12)});
 // Province labels come from the complete local 34-name source at runtime.
 layer('place-labels','symbol','places',textPaint,{minzoom:3.25,filter:['all',['==','kind','locality'],['in','kind_detail','city','town']],layout:label(['interpolate',['linear'],['zoom'],3.25,11,10,14],7)});
 layer('county-labels','symbol','places',textPaint,{minzoom:6,filter:['any',['==','kind','county'],['in','kind_detail','county','district','borough']],layout:label(12,6)});
 layer('locality-labels','symbol','places',textPaint,{minzoom:10,filter:['all',['==','kind','locality'],['!in','kind_detail','city','town','county','district','borough']],layout:label(['interpolate',['linear'],['zoom'],10,10,16,12],7)});
 layer('poi-labels','symbol','pois',{...textPaint,'text-color':'#355b45'},{minzoom:11,filter:['all',named,['!=',['get','kind'],'administrative']],layout:{...label(['interpolate',['linear'],['zoom'],11,10,16,12],8),'text-max-width':7,'text-anchor':'bottom','text-offset':[0,-0.4]}});
 layer('road-labels','symbol','roads',{...textPaint,'text-color':'#65706a','text-halo-width':1.5},{minzoom:11,filter:['all',named,['!',['in',['get','kind'],['literal',['rail','aeroway','ferry']]]]],layout:{...label(['interpolate',['linear'],['zoom'],11,10,16,12],4),'symbol-placement':'line','symbol-spacing':220,'text-max-angle':35,'text-rotation-alignment':'map','text-keep-upright':true}});
 layer('water-labels','symbol','water',{...textPaint,'text-color':'#5e8493'},{minzoom:10,filter:named,layout:label(11,8)});
 return {version:8,name:'PhotoMap Offline Streets',glyphs:'local://fonts/{fontstack}/{range}.pbf',sources:{base:{type:'vector',url:`pmtiles://local://${tile}`,minzoom:0,maxzoom:maxZoom,bounds}},layers};
}
