/* Local code only. Journal strings always go through textContent/attributes. */
(function () {
  'use strict';
  var map = L.map('map', {zoomControl:false, attributionControl:true, worldCopyJump:true, minZoom:2, maxZoom:19, zoomSnap:0.25});
  map.createPane('countryBase');map.getPane('countryBase').style.zIndex='150';
  map.createPane('countryNames');map.getPane('countryNames').style.zIndex='170';map.getPane('countryNames').style.pointerEvents='none';
  var regions=[],visitedCodes=Object.create(null);
  var country=L.geoJSON(window.TRAVEL_CHINA,{
    pane:'countryBase',interactive:false,
    attribution:'地图边界 © 高德 · <a href="https://datav.aliyun.com/portal?tool=atlas">DataV.GeoAtlas</a>（学习交流）',
    style:function(feature){var named=!!feature.properties.name;return {className:named?'china-region region-'+feature.properties.adcode:'china-dashes',color:'#a4b8aa',weight:named?1:0.7,opacity:0.95,fill:true,fillColor:'#566f62',fillOpacity:1};},
    onEachFeature:function(feature,layer){if(feature.properties.name)regions.push({feature:feature,layer:layer,bounds:layer.getBounds()});}
  }).addTo(map);
  var labels=L.layerGroup().addTo(map);
  function renderNames(){
    labels.clearLayers();
    window.TRAVEL_CHINA.features.forEach(function(feature){
      var p=feature.properties,c=p.centroid||p.center;
      if(!p.name||!Array.isArray(c))return;
      if(map.getZoom()<4.5&&['北京市','天津市','上海市','香港特别行政区','澳门特别行政区'].indexOf(p.name)>=0)return;
      var span=document.createElement('span');span.textContent=p.name.replace(/(维吾尔自治区|壮族自治区|回族自治区|特别行政区|自治区|省|市)$/,'');
      if(visitedCodes[String(p.adcode)])span.className='visited-region-name';
      L.marker([c[1],c[0]],{pane:'countryNames',interactive:false,keyboard:false,icon:L.divIcon({html:span,className:'province-name',iconSize:[76,22],iconAnchor:[38,11]})}).addTo(labels);
    });
  }
  var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom:19, noWrap:true, keepBuffer:1, updateWhenIdle:true,
    attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  });
  map.attributionControl.setPrefix('<a href="https://leafletjs.com">Leaflet</a>');
  L.control.zoom({position:'bottomright',zoomInTitle:'放大地图',zoomOutTitle:'缩小地图'}).addTo(map);
  var layer = L.layerGroup().addTo(map), selected = null, notes = [], sawTile = false, tileErrors = 0, timer = null, streets=false;
  var status = document.getElementById('tile-status');
  function notify(method) {
    var bridge = window.AndroidMap;
    if (!bridge) return;
    if(method==='ready')bridge.ready();
    else if(method==='selectPoint')bridge.selectPoint(arguments[1],arguments[2]);
    else if(method==='openNotes')bridge.openNotes(arguments[1]);
    else if(method==='cameraChanged')bridge.cameraChanged(arguments[1],arguments[2],arguments[3]);
    else if(method==='moveNote'&&bridge.moveNote)bridge.moveNote(arguments[1],arguments[2],arguments[3]);
  }
  function valid(lat,lon) { return typeof lat==='number'&&typeof lon==='number'&&isFinite(lat)&&isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180; }
  function isVisited(note){return note.visited!==false;}
  function inRing(p,ring){
    var inside=false;
    for(var i=0,j=ring.length-1;i<ring.length;j=i++){
      var a=ring[i],b=ring[j],cross=(p[0]-a[0])*(b[1]-a[1])-(p[1]-a[1])*(b[0]-a[0]);
      if(Math.abs(cross)<1e-10&&p[0]>=Math.min(a[0],b[0])&&p[0]<=Math.max(a[0],b[0])&&p[1]>=Math.min(a[1],b[1])&&p[1]<=Math.max(a[1],b[1]))return true;
      if((a[1]>p[1])!==(b[1]>p[1])&&p[0]<(b[0]-a[0])*(p[1]-a[1])/(b[1]-a[1])+a[0])inside=!inside;
    }
    return inside;
  }
  function inPolygon(p,polygon){return polygon.length>0&&inRing(p,polygon[0])&&!polygon.slice(1).some(function(hole){return inRing(p,hole);});}
  function inGeometry(p,geometry){return geometry.type==='Polygon'?inPolygon(p,geometry.coordinates):geometry.type==='MultiPolygon'&&geometry.coordinates.some(function(poly){return inPolygon(p,poly);});}
  function updateVisited(){
    visitedCodes=Object.create(null);
    var visited=notes.filter(isVisited);
    regions.forEach(function(region){
      var lit=visited.some(function(n){return region.bounds.contains([n.latitude,n.longitude])&&inGeometry([n.longitude,n.latitude],region.feature.geometry);});
      if(lit)visitedCodes[String(region.feature.properties.adcode)]=true;
      region.layer.setStyle({fillColor:lit?'#d8e9bd':'#566f62',color:lit?'#8fa980':'#a4b8aa'});
      var element=region.layer.getElement();if(element)element.setAttribute('data-visited',String(lit));
    });
    renderNames();
  }
  function normalized(point) { return {lat:Math.max(-85.05112878,Math.min(85.05112878,point.lat)),lng:((point.lng+180)%360+360)%360-180}; }
  function showChina() { map.fitBounds(country.getBounds(),{paddingTopLeft:[16,112],paddingBottomRight:[16,32],animate:false}); }
  function choose(point) {
    var p=normalized(point);
    if(selected) layer.removeLayer(selected);
    selected=L.circleMarker([p.lat,p.lng],{radius:9,color:'#a05b3c',weight:3,fillColor:'#fff',fillOpacity:1}).addTo(layer);
    notify('selectPoint',p.lat,p.lng);
  }
  function render() {
    layer.clearLayers(); selected=null;
    var groups=[],artGroups=[];
    function samePoint(a,b){return Math.abs(a.latitude-b.latitude)<0.00001&&Math.abs(a.longitude-b.longitude)<0.00001;}
    function hasArt(n){return typeof n.illustration==='string'&&/^\/photos\/[A-Za-z0-9_-]{1,100}\.(jpg|jpeg|png|webp|heic|heif)$/.test(n.illustration);}
    notes.filter(hasArt).forEach(function(n){if(artGroups.some(function(g){return samePoint(g.art,n);}))return;artGroups.push({art:n,items:notes.filter(function(other){return samePoint(n,other);})});});
    artGroups.forEach(function(group){
      var art=group.art,size=Math.max(24,Math.min(96,Number(art.illustrationSize)||32));
      if(map.getZoom()<5)size=Math.min(32,size);
      var hit=Math.max(48,size+8),el=document.createElement('div');el.className='map-artwork '+(map.getZoom()<5?'compact ':'')+(isVisited(art)?'visited-marker':'unvisited-marker');el.style.width=hit+'px';el.style.height=hit+'px';
      var image=document.createElement('img');image.src=art.illustration;image.alt=art.place||art.title;image.draggable=false;image.style.width=size+'px';image.style.height=size+'px';el.appendChild(image);
      var label=document.createElement('span');label.className='artwork-label';label.textContent=art.place||art.title;el.appendChild(label);
      if(group.items.length>1){var badge=document.createElement('b');badge.className='artwork-count';badge.textContent=String(group.items.length);el.appendChild(badge);}
      var marker=L.marker([art.latitude,art.longitude],{draggable:true,keyboard:true,title:(art.place||art.title)+' · '+(isVisited(art)?'去过':'未去过')+' · 拖动插画调整位置',icon:L.divIcon({html:el,className:'travel-marker artwork-marker',iconSize:[hit,hit],iconAnchor:[hit/2,hit/2]})}).addTo(layer);
      marker.on('click',function(){notify('openNotes',JSON.stringify(group.items.map(function(n){return n.id;})));});
      marker.on('dragend',function(){var p=normalized(marker.getLatLng());notify('moveNote',art.id,p.lat,p.lng);});
    });
    notes.filter(function(n){return !artGroups.some(function(g){return samePoint(g.art,n);});}).forEach(function(n){
      var p=map.project([n.latitude,n.longitude],map.getZoom()), group=null;
      for(var i=0;i<groups.length;i++){if(groups[i].pixel.distanceTo(p)<44){group=groups[i];break;}}
      if(group)group.items.push(n); else groups.push({pixel:p,items:[n]});
    });
    groups.forEach(function(group){
      var items=group.items, first=items[0], same=items.every(function(n){return Math.abs(n.latitude-first.latitude)<0.00001&&Math.abs(n.longitude-first.longitude)<0.00001;});
      var center=same?L.latLng(first.latitude,first.longitude):L.latLngBounds(items.map(function(n){return [n.latitude,n.longitude];})).getCenter();
      var el=document.createElement('div');
      if(items.length>1&&!same){el.className='travel-cluster';el.textContent=String(items.length);}
      else {
        el.className='travel-pin';
        el.innerHTML='<svg viewBox="0 0 44 48" aria-hidden="true"><path d="M22 45C18 39 5 29 5 19a17 17 0 1 1 34 0c0 10-13 20-17 26Z" fill="#a05b3c" stroke="#f7f5ef" stroke-width="2"/></svg>';
        var count=document.createElement('span');count.textContent=items.length>1?String(items.length):'•';el.appendChild(count);
      }
      el.classList.add(items.some(isVisited)?'visited-marker':'unvisited-marker');
      var marker=L.marker(center,{icon:L.divIcon({html:el,className:'travel-marker',iconSize:[48,48],iconAnchor:[24,40]}),title:(first.place||first.title)+' · '+items.length+'篇笔记',keyboard:true}).addTo(layer);
      marker.on('click',function(){
        if(!same&&map.getZoom()<18){map.fitBounds(L.latLngBounds(items.map(function(n){return [n.latitude,n.longitude];})),{padding:[65,65],maxZoom:Math.min(18,map.getZoom()+4)});return;}
        notify('openNotes',JSON.stringify(items.map(function(n){return n.id;})));
      });
    });
  }
  map.on('click',function(e){choose(e.latlng);});
  map.on('contextmenu',function(e){choose(e.latlng);});
  map.on('moveend zoomend',function(){render();renderNames();var c=normalized(map.getCenter());notify('cameraChanged',c.lat,c.lng,map.getZoom());});
  tiles.on('tileload',function(){sawTile=true;status.hidden=true;});
  tiles.on('tileerror',function(){tileErrors++;if(streets&&!sawTile&&tileErrors>=3)status.hidden=false;});
  document.getElementById('china').onclick=showChina;
  document.getElementById('footprints').onclick=function(){if(notes.length)map.fitBounds(L.latLngBounds(notes.map(function(n){return [n.latitude,n.longitude];})),{padding:[55,55],maxZoom:14});};
  document.getElementById('retry').onclick=function(){sawTile=false;tileErrors=0;status.hidden=true;tiles.redraw();};
  document.getElementById('street').onclick=function(){
    streets=!streets;this.setAttribute('aria-pressed',String(streets));this.textContent=streets?'关闭街道':'街道底图';
    window.clearTimeout(timer);status.hidden=true;
    if(streets){sawTile=false;tileErrors=0;tiles.addTo(map);timer=window.setTimeout(function(){if(!sawTile)status.hidden=false;},12000);}
    else map.removeLayer(tiles);
  };
  window.TravelMap={
    configure:function(config){
      if(config&&valid(config.latitude,config.longitude))map.setView([config.latitude,config.longitude],Math.max(2,Math.min(19,config.zoom||13)),{animate:false});else showChina();
      window.setTimeout(function(){map.invalidateSize();},40);
    },
    setNotes:function(data){notes=(Array.isArray(data)?data:[]).filter(function(n){return n&&typeof n.id==='string'&&valid(n.latitude,n.longitude);});updateVisited();render();},
    chooseCenter:function(){choose(map.getCenter());},
    clearSelection:function(){if(selected){layer.removeLayer(selected);selected=null;}},
    getCenter:function(){var c=normalized(map.getCenter());return {latitude:c.lat,longitude:c.lng,zoom:map.getZoom()};}
  };
  showChina();
  window.addEventListener('pagehide',function(){window.clearTimeout(timer);});
  notify('ready');
})();
