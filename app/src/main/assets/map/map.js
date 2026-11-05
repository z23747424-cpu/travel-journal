/* Local code only. Journal strings always go through textContent/attributes. */
(function () {
  'use strict';
  var map = L.map('map', {zoomControl:false, attributionControl:true, worldCopyJump:true, minZoom:2, maxZoom:19, zoomSnap:0.25});
  var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom:19, noWrap:true, keepBuffer:1, updateWhenIdle:true,
    attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  });
  tiles.addTo(map);
  map.attributionControl.setPrefix('<a href="https://leafletjs.com">Leaflet</a>');
  L.control.zoom({position:'bottomright',zoomInTitle:'放大地图',zoomOutTitle:'缩小地图'}).addTo(map);
  var layer = L.layerGroup().addTo(map), selected = null, notes = [], sawTile = false, tileErrors = 0, timer = null;
  var status = document.getElementById('tile-status');
  function notify(method) {
    var bridge = window.AndroidMap;
    if (!bridge) return;
    if(method==='ready')bridge.ready();
    else if(method==='selectPoint')bridge.selectPoint(arguments[1],arguments[2]);
    else if(method==='openNotes')bridge.openNotes(arguments[1]);
    else if(method==='cameraChanged')bridge.cameraChanged(arguments[1],arguments[2],arguments[3]);
  }
  function valid(lat,lon) { return typeof lat==='number'&&typeof lon==='number'&&isFinite(lat)&&isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180; }
  function normalized(point) { return {lat:Math.max(-85.05112878,Math.min(85.05112878,point.lat)),lng:((point.lng+180)%360+360)%360-180}; }
  function showChina() { map.fitBounds([[16,72],[55,136]],{padding:[14,28],animate:false}); }
  function choose(point) {
    var p=normalized(point);
    if(selected) layer.removeLayer(selected);
    selected=L.circleMarker([p.lat,p.lng],{radius:9,color:'#a05b3c',weight:3,fillColor:'#fff',fillOpacity:1}).addTo(layer);
    notify('selectPoint',p.lat,p.lng);
  }
  function render() {
    layer.clearLayers(); selected=null;
    var groups=[];
    notes.forEach(function(n){
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
      var marker=L.marker(center,{icon:L.divIcon({html:el,className:'travel-marker',iconSize:[46,48],iconAnchor:[23,45]}),title:(first.place||first.title)+' · '+items.length+'篇笔记',keyboard:true}).addTo(layer);
      marker.on('click',function(){
        if(!same&&map.getZoom()<18){map.fitBounds(L.latLngBounds(items.map(function(n){return [n.latitude,n.longitude];})),{padding:[65,65],maxZoom:Math.min(18,map.getZoom()+4)});return;}
        notify('openNotes',JSON.stringify(items.map(function(n){return n.id;})));
      });
    });
  }
  map.on('click',function(e){choose(e.latlng);});
  map.on('contextmenu',function(e){choose(e.latlng);});
  map.on('moveend zoomend',function(){render();var c=normalized(map.getCenter());notify('cameraChanged',c.lat,c.lng,map.getZoom());});
  tiles.on('tileload',function(){sawTile=true;status.hidden=true;});
  tiles.on('tileerror',function(){tileErrors++;if(!sawTile&&tileErrors>=3)status.hidden=false;});
  document.getElementById('china').onclick=showChina;
  document.getElementById('footprints').onclick=function(){if(notes.length)map.fitBounds(L.latLngBounds(notes.map(function(n){return [n.latitude,n.longitude];})),{padding:[55,55],maxZoom:14});};
  document.getElementById('retry').onclick=function(){sawTile=false;tileErrors=0;status.hidden=true;tiles.redraw();};
  window.TravelMap={
    configure:function(config){
      if(config&&valid(config.latitude,config.longitude))map.setView([config.latitude,config.longitude],Math.max(2,Math.min(19,config.zoom||13)),{animate:false});else showChina();
      window.setTimeout(function(){map.invalidateSize();},40);
    },
    setNotes:function(data){notes=(Array.isArray(data)?data:[]).filter(function(n){return n&&typeof n.id==='string'&&valid(n.latitude,n.longitude);});render();},
    chooseCenter:function(){choose(map.getCenter());},
    clearSelection:function(){if(selected){layer.removeLayer(selected);selected=null;}},
    getCenter:function(){var c=normalized(map.getCenter());return {latitude:c.lat,longitude:c.lng,zoom:map.getZoom()};}
  };
  showChina();
  timer=window.setTimeout(function(){if(!sawTile)status.hidden=false;},12000);
  window.addEventListener('pagehide',function(){window.clearTimeout(timer);});
  notify('ready');
})();
