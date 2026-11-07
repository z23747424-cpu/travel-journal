'use strict';
const $ = (s, root = document) => root.querySelector(s);
const NS = 'http://www.w3.org/2000/svg';
const PALETTE = ['#A9BFAE','#C4A58D','#DACAA4','#A8BDC7','#CAB4BF'];
const state = {atlas:null, provinces:[], cities:[], records:[], trips:[], cityStyles:[], province:null, tab:'map', demo:false, sheet:null, camera:{x:105,y:-37,k:5}, width:390,height:550};
const elements = {stage:$('#map-stage'),world:$('#world'),context:$('#context-layer'),active:$('#active-layer'),labels:$('#label-layer'),markers:$('#marker-layer'),defs:$('#map-defs')};
let db, cameraFrame=0, pressTimer, pointers=new Map(), drag=null, pinch=null, suppressClickUntil=0, revision=null;
let labelGroups=[], markerGroups=[], toastTimer, sheetReturnFocus=null;

function html(tag, attrs={}, ...children) {
  const e=document.createElement(tag);
  for(const [k,v] of Object.entries(attrs)) {
    if(k==='class')e.className=v; else if(k==='text')e.textContent=v;
    else if(k.startsWith('on'))e.addEventListener(k.slice(2).toLowerCase(),v);
    else if(k==='value')e.value=v;
    else e.setAttribute(k,String(v));
  }
  children.flat().filter(c=>c!==null&&c!==undefined).forEach(c=>e.append(c instanceof Node?c:document.createTextNode(c)));
  return e;
}
function svg(tag, attrs={}) {const e=document.createElementNS(NS,tag);for(const[k,v]of Object.entries(attrs))e.setAttribute(k,String(v));return e;}
function icon(name) {const e=svg('svg',{class:'icon','aria-hidden':'true'});e.append(svg('use',{href:'#i-'+name}));return e;}
function action(text, fn, cls='secondary-button') {return html('button',{class:cls,text,onClick:fn});}
function toast(text) {clearTimeout(toastTimer);$('#toast').textContent=text;$('#toast').hidden=false;toastTimer=setTimeout(()=>$('#toast').hidden=true,3200);}
function project(p){return [p[0],-180/Math.PI*Math.log(Math.tan(Math.PI/4+Math.max(-85,Math.min(85,p[1]))*Math.PI/360))];}
function unproject(x,y){return [Math.max(-180,Math.min(180,x)),Math.max(-85,Math.min(85,(2*Math.atan(Math.exp(-y*Math.PI/180))-Math.PI/2)*180/Math.PI))];}
function makeShape(feature) {
  const raw=feature.geometry.type==='Polygon'?[feature.geometry.coordinates]:feature.geometry.coordinates;
  const polygons=raw.map(poly=>poly.map(ring=>ring.map(project)));
  let left=Infinity,top=Infinity,right=-Infinity,bottom=-Infinity;
  let biggest=null,biggestArea=0;
  const d=polygons.map(poly=>poly.map(ring=>{
    let area=0;for(let i=0;i<ring.length;i++){const p=ring[i],q=ring[(i+1)%ring.length];area+=p[0]*q[1]-q[0]*p[1];left=Math.min(left,p[0]);top=Math.min(top,p[1]);right=Math.max(right,p[0]);bottom=Math.max(bottom,p[1]);}
    if(Math.abs(area)>biggestArea){biggestArea=Math.abs(area);biggest=ring;}
    return ring.map((p,i)=>(i?'L':'M')+p[0].toFixed(5)+','+p[1].toFixed(5)).join('')+'Z';
  }).join('')).join('');
  const primary={left:Math.min(...biggest.map(p=>p[0])),top:Math.min(...biggest.map(p=>p[1])),right:Math.max(...biggest.map(p=>p[0])),bottom:Math.max(...biggest.map(p=>p[1]))};
  return {d,polygons,bounds:{left,top,right,bottom},primary,area:biggestArea};
}
function contains(shape,p) {
  const b=shape.bounds;if(p[0]<b.left||p[0]>b.right||p[1]<b.top||p[1]>b.bottom)return false;
  function ringContains(ring){let hit=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[i],b=ring[j];const cross=(p[0]-a[0])*(b[1]-a[1])-(p[1]-a[1])*(b[0]-a[0]);if(Math.abs(cross)<1e-9&&p[0]>=Math.min(a[0],b[0])-1e-9&&p[0]<=Math.max(a[0],b[0])+1e-9&&p[1]>=Math.min(a[1],b[1])-1e-9&&p[1]<=Math.max(a[1],b[1])+1e-9)return 2;if((a[1]>p[1])!==(b[1]>p[1])&&p[0]<(b[0]-a[0])*(p[1]-a[1])/(b[1]-a[1])+a[0])hit=!hit;}return hit?1:0;}
  return shape.polygons.some(poly=>{let inside=false;for(const ring of poly){const result=ringContains(ring);if(result===2)return true;if(result===1)inside=!inside;}return inside;});
}
function cityAt(p){return state.cities.filter(c=>contains(c.shape,p)).sort((a,b)=>a.shape.area-b.shape.area)[0]||null;}
function shortName(name){return name.replace(/(维吾尔自治区|壮族自治区|回族自治区|特别行政区|自治区|省|市)$/,'');}
function provinceFor(city){return state.provinces.find(p=>p.code===city.provinceCode);}
function recordsFor(code){return state.records.filter(r=>r.cityCode===code).sort((a,b)=>b.updatedAt-a.updatedAt);}
function fit(bounds,padding=28){const w=Math.max(.002,bounds.right-bounds.left),h=Math.max(.002,bounds.bottom-bounds.top);return {x:(bounds.left+bounds.right)/2,y:(bounds.top+bounds.bottom)/2,k:Math.min((state.width-padding*2)/w,(state.height-padding*2)/h,10000)};}
function homeCamera(){return fit({left:73,top:-65,right:137,bottom:-3},18);}
function setCamera(target, animate=true){
  cancelAnimationFrame(cameraFrame);const from={...state.camera};
  if(!animate||matchMedia('(prefers-reduced-motion: reduce)').matches){state.camera=target;renderCamera();return;}
  const start=performance.now();function frame(now){const t=Math.min(1,(now-start)/420),ease=1-Math.pow(1-t,4);state.camera={x:from.x+(target.x-from.x)*ease,y:from.y+(target.y-from.y)*ease,k:from.k+(target.k-from.k)*ease};renderCamera();if(t<1)cameraFrame=requestAnimationFrame(frame);}
  cameraFrame=requestAnimationFrame(frame);
}
function renderCamera(){
  const {x,y,k}=state.camera;
  elements.world.setAttribute('transform',`translate(${state.width/2} ${state.height/2}) scale(${k}) translate(${-x} ${-y})`);
  for(const label of labelGroups)label.e.setAttribute('transform',`translate(${label.x} ${label.y}) scale(${1/k})`);
  for(const marker of markerGroups)marker.e.setAttribute('transform',`translate(${marker.x} ${marker.y}) scale(${1/k})`);
}
function makeLabel(name,p,type,attrs={}){
  const e=svg('g',{...attrs,class:'label-group'});
  const width=Math.max(28,name.length*(type==='province-name'?12:10)+8);
  const rect=svg('rect',{x:-width/2,y:-13,width,height:26,fill:'transparent'});
  const text=svg('text',{class:type,x:0,y:0,'font-size':type==='province-name'?12:10});text.textContent=name;e.append(rect,text);elements.labels.append(e);labelGroups.push({e,x:p[0],y:p[1],type,width});
}
function provinceLabelPositions(){
  const home=homeCamera(),k=home.k;
  const offsets={'110000':[-1.6,-1.2],'120000':[3.1,.8],'310000':[3,.3],'810000':[3.8,0],'820000':[-.4,2.3],'710000':[2.6,.5]};
  const positions=state.provinces.map(p=>{const anchor=project(p.center),offset=offsets[p.code]||[0,0];const x=state.width/2+(anchor[0]+offset[0]-home.x)*k,y=state.height/2+(anchor[1]+offset[1]-home.y)*k;return {p,anchor,x,y,desiredX:x,desiredY:y,width:Math.max(28,shortName(p.name).length*12+8)};});
  for(let iteration=0;iteration<80;iteration++){
    for(let i=0;i<positions.length;i++)for(let j=i+1;j<positions.length;j++){
      const a=positions[i],b=positions[j],dx=b.x-a.x,dy=b.y-a.y,overlapX=(a.width+b.width)/2+3-Math.abs(dx),overlapY=24-Math.abs(dy);
      if(overlapX>0&&overlapY>0){if(overlapX<overlapY){const push=(overlapX+.4)/2*(dx>=0?1:-1);a.x-=push;b.x+=push;}else{const push=(overlapY+.4)/2*(dy>=0?1:-1);a.y-=push;b.y+=push;}}
    }
    for(const p of positions){p.x+=(p.desiredX-p.x)*.012;p.y+=(p.desiredY-p.y)*.012;p.x=Math.max(p.width/2+7,Math.min(state.width-p.width/2-7,p.x));p.y=Math.max(45,Math.min(state.height-70,p.y));}
  }
  return positions.map(p=>({...p,point:[home.x+(p.x-state.width/2)/k,home.y+(p.y-state.height/2)/k]}));
}
function drawCityPhotos(city,style,defs,target,prefix='map'){
  const id=prefix+'-photo-'+city.code.replace(/[^\w-]/g,'');const b=city.shape.bounds;
  const clip=svg('clipPath',{id});clip.append(svg('path',{d:city.shape.d,'fill-rule':'evenodd','clip-rule':'evenodd'}));defs.append(clip);
  const g=svg('g',{'clip-path':`url(#${id})`,'pointer-events':'none'}),count=Math.min(style.photos.length,4),cols=count===1?1:2,rows=count<=2?1:2;
  style.photos.slice(0,4).forEach((src,i)=>{
    let x=b.left+(i%cols)*(b.right-b.left)/cols,y=b.top+Math.floor(i/cols)*(b.bottom-b.top)/rows,w=(b.right-b.left)/cols,h=(b.bottom-b.top)/rows;if(count===3&&i===2){x=b.left;w=b.right-b.left;}
    const tileId=id+'-'+i,tileClip=svg('clipPath',{id:tileId});tileClip.append(svg('rect',{x,y,width:w,height:h}));defs.append(tileClip);
    const crop=style.crops?.[i]||{x:50,y:50,zoom:1},aspect=style.aspects?.[i]||1,drawW=Math.max(w,h*aspect)*crop.zoom,drawH=drawW/aspect;
    const tile=svg('g',{'clip-path':`url(#${tileId})`});tile.append(svg('image',{href:src,x:x+(w-drawW)*crop.x/100,y:y+(h-drawH)*crop.y/100,width:drawW,height:drawH,preserveAspectRatio:'none'}));g.append(tile);
  });target.append(g);
}
function makeMarker(record,city){
  const p=project([record.longitude,record.latitude]);const e=svg('g',{class:'marker','data-record':record.id,tabindex:0,role:'button','aria-label':`${record.visited?'去过':'想去'}${city?.name||record.place}`});
  e.append(svg('circle',{cx:0,cy:0,r:22,fill:'transparent'}));
  if(record.visited){e.append(svg('circle',{cx:0,cy:0,r:4,fill:'#a9755b',stroke:'#fffaf0','stroke-width':1.5}));}
  else {e.append(svg('path',{d:'M0 5V-17 M0-17Q7-21 14-16V-6Q7-10 0-6',fill:'#bd9466',stroke:'#8d7753','stroke-width':1.3,'stroke-linejoin':'round'}));e.append(svg('ellipse',{cx:0,cy:7,rx:5,ry:1.5,fill:'#81906e',opacity:.18}));}
  elements.markers.append(e);markerGroups.push({e,x:p[0],y:p[1]});
}
function renderScene(){
  if(!state.atlas)return;
  elements.context.replaceChildren();elements.active.replaceChildren();elements.labels.replaceChildren();elements.markers.replaceChildren();
  for(const n of [...elements.defs.querySelectorAll('clipPath')])n.remove();labelGroups=[];markerGroups=[];
  const focused=state.provinces.find(p=>p.code===state.province);elements.stage.classList.toggle('focused',!!focused);
  for(const p of state.provinces){if(p.code===focused?.code)continue;const path=svg('path',{d:p.shape.d,'fill-rule':'evenodd',class:focused?'context-province':'province-shape','data-province':p.code,tabindex:focused?-1:0,role:'button','aria-label':'进入'+p.name});elements.context.append(path);}
  const sea=state.sea.map(s=>svg('path',{d:s.d,class:'context-province','fill-rule':'evenodd'}));elements.context.append(...sea);
  if(focused)elements.active.append(svg('path',{d:focused.shape.d,class:'active-province','fill-rule':'evenodd'}));
  for(const city of state.cities){
    if(focused&&city.provinceCode!==focused.code)continue;
    const notes=recordsFor(city.code).filter(r=>r.visited),latest=notes[0],decoration=styleFor(city);
    if(!focused&&!latest)continue;
    const attrs={d:city.shape.d,'fill-rule':'evenodd',class:'city-shape'+(latest?' visited':''),'data-city':city.code};
    if(focused){attrs.tabindex=0;attrs.role='button';attrs['aria-label']=city.name+(latest?'，已去过':'，尚未点亮');}else attrs['pointer-events']='none';
    const path=svg('path',attrs);if(latest)path.setAttribute('fill',decoration?.color||latest.color);
    if(latest)path.style.fill=decoration?.color||latest.color;
    elements.active.append(path);
    if(latest&&decoration?.fillMode==='photos'&&decoration.photos.length)drawCityPhotos(city,decoration,elements.defs,elements.active);
    if(focused)makeLabel(shortName(city.name),project(city.center),'city-name',{'data-city':city.code,role:'button','aria-label':city.name});
  }
  if(!focused){
    for(const p of provinceLabelPositions()){if(Math.hypot(p.point[0]-p.anchor[0],p.point[1]-p.anchor[1])*homeCamera().k>7)elements.labels.append(svg('path',{d:`M${p.anchor[0]},${p.anchor[1]}L${p.point[0]},${p.point[1]}`,class:'label-leader'}));makeLabel(shortName(p.p.name),p.point,'province-name',{'data-province':p.p.code,tabindex:0,role:'button','aria-label':'进入'+p.p.name});}
    const plans=state.records.filter(r=>!r.visited||!r.cityCode);
    for(const record of plans){const city=state.cities.find(c=>c.code===record.cityCode);makeMarker(record,city);}
  } else {
    const grouped=new Map();for(const record of state.records){const city=state.cities.find(c=>c.code===record.cityCode);if(city?.provinceCode!==focused.code)continue;const key=record.cityCode+':'+record.visited;if(!grouped.has(key))grouped.set(key,record);}
    for(const record of grouped.values())makeMarker(record,state.cities.find(c=>c.code===record.cityCode));
  }
  $('#back-button').hidden=!focused;$('#province-focus-label').hidden=!focused;
  if(state.tab==='map'){$('#screen-title').textContent=focused?shortName(focused.name)+' · 旅行手账':'我的旅行地图';$('#screen-subtitle').textContent=focused?`${state.cities.filter(c=>c.provinceCode===focused.code).length} 座城市，等你慢慢收藏`:'点击省份，翻开下一页回忆';}
  const visited=new Set(state.records.filter(r=>r.visited).map(r=>r.cityCode)),plans=state.records.filter(r=>!r.visited);
  $('#travel-stats').textContent=state.records.length?`已点亮 ${visited.size} 座城市 · ${plans.length} 面想去的旗`:'还没有足迹，先选一片喜欢的风景';
  $('#demo-indicator').hidden=!state.demo;$('#demo-button').textContent=state.demo?'清除示例，开始自己的地图':'载入示例，看看拼贴效果';
  renderCamera();
}
function chooseProvince(code,animate=true){
  closeSheet();const p=state.provinces.find(p=>p.code===code);if(!p)return;
  state.province=code;sessionStorage.setItem('lvye-preview-province',code);setTab('map',false);renderScene();setCamera(fit(p.shape.primary,40),animate);
}
function overview(animate=true){closeSheet();state.province=null;sessionStorage.removeItem('lvye-preview-province');renderScene();setCamera(homeCamera(),animate);}
function focusCity(city,animate=true){if(state.province!==city.provinceCode){state.province=city.provinceCode;sessionStorage.setItem('lvye-preview-province',city.provinceCode);renderScene();}setCamera(fit(city.shape.primary,75),animate);}
function zoom(factor,clientX=state.width/2,clientY=state.height/2){
  cancelAnimationFrame(cameraFrame);const c=state.camera,worldX=c.x+(clientX-state.width/2)/c.k,worldY=c.y+(clientY-state.height/2)/c.k;
  const k=Math.max(homeCamera().k,Math.min(10000,c.k*factor));state.camera={k,x:worldX-(clientX-state.width/2)/k,y:worldY-(clientY-state.height/2)/k};
  if((state.province&&k<homeCamera().k*1.35)||(factor<1&&k<=homeCamera().k*1.001)){overview();return;}renderCamera();
}
function mapPoint(event){const r=elements.stage.getBoundingClientRect();return [state.camera.x+(event.clientX-r.left-state.width/2)/state.camera.k,state.camera.y+(event.clientY-r.top-state.height/2)/state.camera.k];}
function closeSheet(){state.sheet=null;$('#bottom-sheet').hidden=true;$('#bottom-sheet').replaceChildren();$('#sheet-backdrop').hidden=true;if(sheetReturnFocus?.isConnected)sheetReturnFocus.focus({preventScroll:true});sheetReturnFocus=null;}
function openSheet(title,subtitle=''){
  if(!state.sheet)sheetReturnFocus=document.activeElement;
  const sheet=$('#bottom-sheet');sheet.replaceChildren();sheet.classList.remove('archive-sheet','reading-sheet');sheet.hidden=false;$('#sheet-backdrop').hidden=false;state.sheet='view';sheet.setAttribute('aria-label',title);
  const top=html('div',{class:'sheet-top'},html('div',{},html('h3',{text:title}),subtitle?html('p',{text:subtitle}):null));
  const close=html('button',{class:'icon-button','aria-label':'关闭卡片',onClick:closeSheet},icon('close'));top.append(close);sheet.append(top);sheet.scrollTop=0;queueMicrotask(()=>{if(!sheet.hidden&&!sheet.contains(document.activeElement))close.focus({preventScroll:true});});return sheet;
}
function readPhoto(file){return new Promise((resolve,reject)=>{
  if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>15*1024*1024)return reject(Error('请选择 15MB 以内的 JPG、PNG 或 WebP 图片'));
  const image=new Image(),url=URL.createObjectURL(file);image.onload=()=>{try{const ratio=Math.min(1,1200/Math.max(image.width,image.height)),canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(image.width*ratio));canvas.height=Math.max(1,Math.round(image.height*ratio));canvas.getContext('2d').drawImage(image,0,0,canvas.width,canvas.height);resolve(canvas.toDataURL(file.type==='image/png'?'image/png':'image/jpeg',.86));}catch(e){reject(e);}finally{URL.revokeObjectURL(url);}};image.onerror=()=>{URL.revokeObjectURL(url);reject(Error('这张图片暂时无法读取，请换一张 JPEG 或 PNG'));};image.src=url;
});}
function citySearch(){
  const sheet=openSheet('找一座城','搜索城市或省份，去看看它的轮廓');const input=html('input',{class:'text-field city-search-input',placeholder:'例如：杭州、四川、台北…','aria-label':'城市或省份'}),list=html('div',{class:'city-list'});sheet.append(input,list);
  const render=()=>{const q=input.value.trim();const cities=state.cities.filter(c=>!q||c.name.includes(q)||c.provinceName.includes(q)||(q.includes('台')&&c.name.includes(q.replaceAll('台','臺'))));list.replaceChildren(...cities.map(city=>html('button',{class:'city-row','data-search-city':city.code,onClick:()=>{closeSheet();chooseProvince(city.provinceCode,false);cityCard(city);}},html('span',{text:city.name}),html('small',{text:provinceFor(city)?.name||city.provinceName}))));};input.oninput=render;render();input.focus();
}
async function openDatabase(){return new Promise((resolve,reject)=>{
  const request=indexedDB.open('lvye-desktop-preview',2);
  request.onupgradeneeded=()=>{for(const name of ['records','trips','cityStyles'])if(!request.result.objectStoreNames.contains(name))request.result.createObjectStore(name,{keyPath:'id'});};
  request.onsuccess=()=>{request.result.onversionchange=()=>request.result.close();resolve(request.result);};request.onerror=()=>reject(request.error);request.onblocked=()=>toast('请关闭其他旅页预览标签页，再刷新以升级记录存储');
});}
async function atomicWrite(names,run){return new Promise((resolve,reject)=>{
  const tx=db.transaction(names,'readwrite');tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error('保存已取消'));
  try{run(tx);}catch(error){tx.abort();reject(error);}
});}
async function transaction(mode,run,name='records'){if(mode==='readwrite')return atomicWrite([name],tx=>run(tx.objectStore(name)));return new Promise((resolve,reject)=>{const tx=db.transaction(name,mode);run(tx.objectStore(name));tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});}
async function loadRecords(){
  const read=name=>new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
  [state.records,state.trips,state.cityStyles]=await Promise.all(['records','trips','cityStyles'].map(read));
  state.demo=state.records.some(r=>r.demo)||state.trips.some(t=>t.demo);
  // Seed legacy map designs exactly once. Future notes never replace them.
  const missing=[];for(const record of [...state.records].sort((a,b)=>b.updatedAt-a.updatedAt))if(record.visited&&record.cityCode&&!state.cityStyles.some(s=>s.id===record.cityCode)&&!missing.some(s=>s.id===record.cityCode))missing.push(await initialCityStyle({...record,photos:recordsFor(record.cityCode).filter(r=>r.visited).flatMap(r=>r.photos).slice(0,4)}));
  if(missing.length){await transaction('readwrite',s=>missing.forEach(style=>s.put(style)),'cityStyles');state.cityStyles.push(...missing);}
}
async function saveRecord(record,replacingId=null,createdTrip=null){
  const existingStyle=state.cityStyles.find(s=>s.id===record.cityCode),style=record.visited&&record.cityCode&&!existingStyle?await initialCityStyle(record):existingStyle?.demo&&!record.demo?{...existingStyle,demo:false}:null;
  // Editing a sample in its sample trip makes that book personal as well.
  const sampleTrip=state.trips.find(t=>t.id===record.tripId&&t.demo),personalTrip=sampleTrip&&!record.demo?{...sampleTrip,demo:false}:null;
  await atomicWrite(['records','trips','cityStyles'],tx=>{const notes=tx.objectStore('records');if(replacingId)notes.delete(replacingId);notes.put(record);if(style)tx.objectStore('cityStyles').put(style);if(createdTrip||personalTrip)tx.objectStore('trips').put(createdTrip||personalTrip);});
  await loadRecords();renderScene();if(state.tab==='search')renderSearch();
}
async function removeRecord(id){await transaction('readwrite',s=>s.delete(id));await loadRecords();renderScene();}
function illustration(){const image='<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="#e9e5d7"/><path d="M0 180Q100 70 200 170T400 140V400H0" fill="#a9bca4"/><path d="M0 240Q130 160 230 250T400 220V400H0" fill="#6f9680"/><path d="M0 290Q140 220 260 300T400 290V400H0" fill="#c1d2c0"/><circle cx="305" cy="85" r="31" fill="#dbb993"/><path d="M0 340Q90 310 190 350T400 330" fill="none" stroke="#e6eadd" stroke-width="3"/></svg>';return 'data:image/svg+xml;base64,'+btoa(image);}
async function toggleDemo(){
  if(state.sheet==='editor'){toast('先保存或关闭正在编辑的这一页，再切换示例');return;}
  if(state.demo){await atomicWrite(['records','trips','cityStyles'],tx=>{state.records.filter(r=>r.demo).forEach(r=>tx.objectStore('records').delete(r.id));state.trips.filter(t=>t.demo).forEach(t=>tx.objectStore('trips').delete(t.id));state.cityStyles.filter(s=>s.demo).forEach(s=>tx.objectStore('cityStyles').delete(s.id));});await loadRecords();renderScene();closeSheet();toast('示例已清除，你的手账和装饰都已保留');return;}
  if(state.records.length&&!confirm('保留你的记录，并添加明确标注的示例？'))return;
  const samples=[['330100','杭州的慢时光',true,'photos',PALETTE[0]],['510100','成都，一杯茶的下午',true,'color',PALETTE[1]],['530100','留一页给昆明',true,'color',PALETTE[2]],['320500','苏州的小巷',true,'color',PALETTE[4]],['330200','下一站，宁波',false,'color',PALETTE[0]],['540100','想去拉萨看看',false,'color',PALETTE[0]]];
  const notes=samples.map(([code,title,visited,fillMode,color])=>{const city=state.cities.find(c=>c.code===code);return{id:'demo-'+code,cityCode:code,place:city.name,title,visited,fillMode,color,photos:fillMode==='photos'?[illustration()]:[],body:'这是一篇电脑预览示例，用来展示城市拼贴和笔记卡片。你可以换成自己的照片和故事。',date:'2026-11-07',longitude:city.center[0],latitude:city.center[1],updatedAt:Date.now(),demo:true};});
  for(const note of notes){note.layout='story';note.tags=[];note.tripId=['330100','330200'].includes(note.cityCode)?'demo-zhejiang':null;if(note.cityCode==='330200')note.date='2026-11-09';}
  await atomicWrite(['records','trips'],tx=>{notes.forEach(n=>tx.objectStore('records').put(n));if(!state.trips.some(t=>t.id==='demo-zhejiang'))tx.objectStore('trips').put({id:'demo-zhejiang',title:'浙江慢游 · 示例',description:'从杭州的一页开始，把下一站宁波也收进这本旅行册。',demo:true,updatedAt:Date.now()});});await loadRecords();renderScene();toast('已载入示例，点击浙江看看城市拼贴');
}
function renderSearch(){const q=$('#record-search').value.trim().toLocaleLowerCase(),box=$('#search-results');const notes=state.records.filter(r=>!q||[r.place,r.title,r.body,...(r.tags||[]),tripOf(r)?.title||''].some(s=>s.toLocaleLowerCase().includes(q)));box.replaceChildren();if(!notes.length){box.append(html('p',{class:'empty-hint',text:'还没有找到这一页。先去地图留下一段回忆吧。'}));return;}for(const note of chronological(notes).reverse())box.append(memoryCard(note,()=>recordDetail(note)));}
function setTab(tab,close=true){if(close)closeSheet();state.tab=tab;for(const name of ['map','search','inspiration','settings'])$('#'+name+'-page').hidden=name!==tab;for(const b of document.querySelectorAll('[data-tab]'))b.classList.toggle('active',b.dataset.tab===tab);$('#back-button').hidden=tab!=='map'||!state.province;const titles={search:['找回一页回忆','把走过的地方，再看一遍'],inspiration:['下一站灵感','把期待留给下一次出发'],settings:['我的旅行手账','在电脑里，慢慢试一试']};if(tab==='map'){renderScene();requestAnimationFrame(resizeMap);}else{$('#screen-title').textContent=titles[tab][0];$('#screen-subtitle').textContent=titles[tab][1];}if(tab==='search')renderSearch();}
function resizeMap(){const r=elements.stage.getBoundingClientRect();if(!r.width||!r.height)return;const initial=state.width===390&&state.height===550;const oldWidth=state.width;state.width=r.width;state.height=r.height;$('#atlas-map').setAttribute('viewBox',`0 0 ${r.width} ${r.height}`);if(initial||Math.abs(oldWidth-r.width)>30){const p=state.provinces.find(p=>p.code===state.province);setCamera(p?fit(p.shape.primary,40):homeCamera(),false);}else renderCamera();}
function exportRecords(){const blob=new Blob([JSON.stringify({format:'lvye-desktop-preview',version:2,records:state.records,trips:state.trips,cityStyles:state.cityStyles},null,2)],{type:'application/json'}),a=html('a',{href:URL.createObjectURL(blob),download:'旅页-电脑预览记录.json'});a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);}
function validDate(value){return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)&&!Number.isNaN(Date.parse(value))&&new Date(value+'T00:00:00Z').toISOString().slice(0,10)===value;}
function validateImport(data){
  const bad=()=>{throw Error('预览文件包含无效字段，原有记录未改动');},text=(s,max)=>typeof s==='string'&&s.length<=max,id=s=>text(s,128)&&s.length>0,photo=p=>text(p,7_000_000)&&/^data:image\/(png|jpeg|webp|svg\+xml);base64,[a-z\d+/=]+$/i.test(p),color=c=>typeof c==='string'&&/^#[\da-f]{6}$/i.test(c),photos=a=>Array.isArray(a)&&a.length<=4&&a.every(photo);
  if(!data||data.format!=='lvye-desktop-preview'||![1,2].includes(data.version)||!Array.isArray(data.records)||data.records.length>2000)bad();
  const trips=data.version===2?data.trips:[],styles=data.version===2?data.cityStyles:[];
  if(!Array.isArray(trips)||trips.length>2000||!Array.isArray(styles)||styles.length>state.cities.length)bad();
  const unique=items=>{if(new Set(items.map(i=>i.id)).size!==items.length)bad();};
  const cleanTrips=trips.map(t=>{if(!t||!id(t.id)||!text(t.title,80)||!t.title.trim()||!text(t.description,1000))bad();return{id:t.id,title:t.title,description:t.description,updatedAt:Number.isFinite(t.updatedAt)?t.updatedAt:Date.now(),demo:t.demo===true};});unique(cleanTrips);
  const records=data.records.map(r=>{
    if(!r||!id(r.id)||!text(r.title,200)||!text(r.body,10000)||!text(r.place,120)||!validDate(r.date)||!color(r.color)||!['color','photos'].includes(r.fillMode)||typeof r.visited!=='boolean'||!photos(r.photos)||!Number.isFinite(r.latitude)||Math.abs(r.latitude)>90||!Number.isFinite(r.longitude)||Math.abs(r.longitude)>180)bad();
    if(r.layout!==undefined&&!['story','spread','collage'].includes(r.layout)||r.tags!==undefined&&(!Array.isArray(r.tags)||r.tags.length>12||r.tags.some(t=>!text(t,30)))||r.tripId!==undefined&&r.tripId!==null&&!id(r.tripId))bad();
    if(r.wish&&(!text(r.wish.title,200)||!text(r.wish.body,10000)||!validDate(r.wish.date)))bad();
    const city=cityAt(project([r.longitude,r.latitude]));return{id:r.id,title:r.title,body:r.body,place:r.place,date:r.date,color:r.color,fillMode:r.fillMode,visited:r.visited,photos:r.photos,longitude:r.longitude,latitude:r.latitude,cityCode:city?.code||null,updatedAt:Number.isFinite(r.updatedAt)?r.updatedAt:Date.now(),demo:r.demo===true,layout:r.layout||'story',tags:r.tags||[],tripId:r.tripId&&[...cleanTrips,...state.trips].some(t=>t.id===r.tripId)?r.tripId:null,...(r.wish?{wish:{title:r.wish.title,body:r.wish.body,date:r.wish.date}}:{})};
  });unique(records);
  const cityStyles=styles.map(s=>{
    if(!s||!state.cities.some(c=>c.code===s.id)||!color(s.color)||!['color','photos'].includes(s.fillMode)||!photos(s.photos)||!Array.isArray(s.aspects)||s.aspects.length!==s.photos.length||s.aspects.some(a=>!Number.isFinite(a)||a<=0||a>10000)||!Array.isArray(s.crops)||s.crops.length!==s.photos.length||s.crops.some(c=>!c||!Number.isFinite(c.x)||c.x<0||c.x>100||!Number.isFinite(c.y)||c.y<0||c.y>100||!Number.isFinite(c.zoom)||c.zoom<1||c.zoom>3)||!Number.isInteger(s.coverIndex)||s.coverIndex<0||s.coverIndex>=Math.max(1,s.photos.length))bad();
    return{id:s.id,color:s.color,fillMode:s.fillMode,photos:s.photos,aspects:s.aspects,crops:s.crops,coverIndex:s.coverIndex,demo:s.demo===true};
  });unique(cityStyles);return {records,trips:cleanTrips,cityStyles};
}

elements.stage.addEventListener('wheel',e=>{e.preventDefault();const r=elements.stage.getBoundingClientRect();zoom(Math.exp(-e.deltaY*.0015),e.clientX-r.left,e.clientY-r.top);},{passive:false});
elements.stage.addEventListener('pointerdown',e=>{
  if(e.target.closest('button'))return;cancelAnimationFrame(cameraFrame);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});
  if(pointers.size===2){clearTimeout(pressTimer);const[a,b]=[...pointers.values()];pinch={distance:Math.hypot(a.x-b.x,a.y-b.y)};drag=null;return;}
  drag={x:e.clientX,y:e.clientY,camera:{...state.camera},moved:false};clearTimeout(pressTimer);const p=mapPoint(e);
  pressTimer=setTimeout(()=>{if(drag&&!drag.moved&&pointers.size===1){elements.stage.setPointerCapture(e.pointerId);const city=cityAt(p);if(city&&state.province!==city.provinceCode)chooseProvince(city.provinceCode);editRecord(city,null,true,unproject(...p));suppressClickUntil=performance.now()+600;drag=null;}},600);
});
elements.stage.addEventListener('pointermove',e=>{
  if(!pointers.has(e.pointerId))return;pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});
  if(pointers.size===2){const[a,b]=[...pointers.values()],distance=Math.hypot(a.x-b.x,a.y-b.y),r=elements.stage.getBoundingClientRect();if(pinch?.distance)zoom(distance/pinch.distance,(a.x+b.x)/2-r.left,(a.y+b.y)/2-r.top);pinch={distance};suppressClickUntil=performance.now()+400;return;}
  if(!drag)return;const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.hypot(dx,dy)>6){if(!elements.stage.hasPointerCapture(e.pointerId))elements.stage.setPointerCapture(e.pointerId);drag.moved=true;clearTimeout(pressTimer);state.camera={...drag.camera,x:drag.camera.x-dx/drag.camera.k,y:drag.camera.y-dy/drag.camera.k};renderCamera();}
});
function pointerEnd(e){clearTimeout(pressTimer);pointers.delete(e.pointerId);if(drag?.moved)suppressClickUntil=performance.now()+400;if(!pointers.size){drag=null;pinch=null;}else{drag=null;}}
elements.stage.addEventListener('pointerup',pointerEnd);elements.stage.addEventListener('pointercancel',pointerEnd);
elements.stage.addEventListener('click',e=>{
  if(performance.now()<suppressClickUntil||e.target.closest('button'))return;
  const recordTarget=e.target.closest('[data-record]');
  if(e.target.closest('.label-group')||recordTarget){
    const rect=elements.stage.getBoundingClientRect(),x=e.clientX-rect.left,y=e.clientY-rect.top;
    const hits=labelGroups.map(l=>{const dx=(l.x-state.camera.x)*state.camera.k+state.width/2-x,dy=(l.y-state.camera.y)*state.camera.k+state.height/2-y;return {l,d:Math.hypot(dx,dy),inside:Math.abs(dx)<=l.width/2&&Math.abs(dy)<=13};}).sort((a,b)=>a.d-b.d),hit=recordTarget?hits.filter(h=>h.inside)[0]:hits[0];
    const marker=recordTarget&&markerGroups.find(m=>m.e===recordTarget),markerDistance=marker?Math.hypot((marker.x-state.camera.x)*state.camera.k+state.width/2-x,(marker.y-state.camera.y)*state.camera.k+state.height/2-y):Infinity;
    const nearest=hit&&(!recordTarget||hit.d<markerDistance-.5)?hit.l:null;
    if(nearest?.type==='province-name'){chooseProvince(nearest.e.dataset.province);return;}
    if(nearest?.type==='city-name'){const city=state.cities.find(c=>c.code===nearest.e.dataset.city);if(city)cityCard(city);return;}
  }
  if(recordTarget){const record=state.records.find(r=>r.id===recordTarget.dataset.record),city=state.cities.find(c=>c.code===record?.cityCode);if(city){if(state.province!==city.provinceCode)chooseProvince(city.provinceCode,false);cityCard(city);}else if(record)recordDetail(record);return;}
  const cityTarget=e.target.closest('[data-city]');if(cityTarget&&state.province){const city=state.cities.find(c=>c.code===cityTarget.dataset.city);if(city)cityCard(city);return;}
  const provinceTarget=e.target.closest('[data-province]');if(provinceTarget)chooseProvince(provinceTarget.dataset.province);
});
elements.stage.addEventListener('keydown',e=>{const target=e.target.closest('[data-province],[data-city],[data-record]');if(e.key==='Enter'&&target){e.preventDefault();const b=target.getBoundingClientRect();target.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:b.x+b.width/2,clientY:b.y+b.height/2}));}else if(e.key==='+'||e.key==='='){e.preventDefault();zoom(1.35);}else if(e.key==='-'){e.preventDefault();zoom(1/1.35);}else if(e.key==='Home'){e.preventDefault();overview();}});
$('#zoom-in').onclick=()=>zoom(1.45);$('#zoom-out').onclick=()=>zoom(1/1.45);$('#reset-map').onclick=()=>overview();$('#back-button').onclick=()=>overview();$('#find-city').onclick=citySearch;$('#sheet-backdrop').onclick=closeSheet;$('#demo-button').onclick=()=>toggleDemo().catch(e=>toast(e.message));
$('#archive-button').setAttribute('aria-label','查看旅行册');$('#archive-button').onclick=booksHome;$('#quick-record').onclick=()=>editRecord(null);$('#record-search').oninput=renderSearch;$('#back-to-map').onclick=()=>setTab('map');
for(const b of document.querySelectorAll('[data-tab]'))b.onclick=()=>setTab(b.dataset.tab);
for(const b of document.querySelectorAll('[data-size]'))b.onclick=()=>{for(const n of document.querySelectorAll('[data-size]'))n.classList.toggle('active',n===b);$('#device-frame').classList.toggle('tablet',b.dataset.size==='tablet');setTimeout(resizeMap,300);};
$('#export-records').onclick=exportRecords;$('#import-records').onclick=()=>{$('#import-input').value='';$('#import-input').click();};
$('#import-input').onchange=async e=>{try{const file=e.target.files[0];if(!file)return;if(file.size>80*1024*1024)throw Error('预览文件过大');const incoming=validateImport(JSON.parse(await file.text()));const conflicts=incoming.records.some(r=>state.records.some(x=>x.id===r.id))||incoming.trips.some(r=>state.trips.some(x=>x.id===r.id))||incoming.cityStyles.some(r=>state.cityStyles.some(x=>x.id===r.id));if(conflicts&&!confirm('文件中有相同编号的手账、旅行册或城市装饰，是否用文件中的版本更新？其他内容会保留。'))return;await atomicWrite(['records','trips','cityStyles'],tx=>{for(const name of ['records','trips','cityStyles'])incoming[name].forEach(r=>tx.objectStore(name).put(r));});await loadRecords();renderScene();if(state.tab==='search')renderSearch();toast('手账、旅行册和城市装饰已导入');}catch(error){toast(error.message);}};
$('#clear-records').onclick=async()=>{if(!confirm('清空当前浏览器的手账、旅行册和城市装饰？手机数据不受影响。'))return;try{await atomicWrite(['records','trips','cityStyles'],tx=>{for(const name of ['records','trips','cityStyles'])tx.objectStore(name).clear();});await loadRecords();renderScene();toast('预览记录已清空');}catch(e){toast(e.message);}};
$('#map-info').onclick=()=>{const sheet=openSheet('地图里的每一片风景');sheet.append(html('p',{class:'credits',text:'地图使用本地真实行政边界，不连接在线地图服务。DataV.GeoAtlas（高德来源）素材用于课程学习；台湾地区县市来自 NLSC/MOI 开放数据，采用 Open Government Data License 1.0。边界经过简化，仅用于旅行手账可视化。'}),html('p',{class:'credits',text:'示例中的风景为程序绘制的插画，示例笔记不代表真实旅行。你可以导入自己的照片体验拼贴。'}));};
window.addEventListener('keydown',e=>{if(e.key==='Escape'){if(state.sheet)closeSheet();else if(state.province)overview();}if(e.key==='Tab'&&state.sheet){const sheet=$('#bottom-sheet'),items=[...sheet.querySelectorAll('button,input,select,textarea,summary,a[href]')].filter(n=>!n.disabled&&n.getClientRects().length);const first=items[0],last=items.at(-1);if(e.shiftKey&&(document.activeElement===first||!sheet.contains(document.activeElement))){e.preventDefault();last?.focus();}else if(!e.shiftKey&&(document.activeElement===last||!sheet.contains(document.activeElement))){e.preventDefault();first?.focus();}}});
new ResizeObserver(resizeMap).observe(elements.stage);
function tickClock(){$('#clock').textContent=new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false,timeZone:'Asia/Shanghai'});}tickClock();setInterval(tickClock,30_000);
async function watchRevision(){try{const data=await(await fetch('/health',{cache:'no-store'})).json();if(revision&&data.revision!==revision){if(state.sheet==='editor')$('#connection-status').textContent='预览已更新 · 关闭编辑后刷新';else location.reload();}else $('#connection-status').textContent='本地预览 · 修改后自动刷新';revision??=data.revision;}catch{$('#connection-status').textContent='预览服务已断开 · 双击启动文件重开';}}
async function start(){try{
  const [atlas,database]=await Promise.all([fetch('/atlas.json').then(r=>{if(!r.ok)throw Error('地图素材未找到');return r.json();}),openDatabase()]);state.atlas=atlas;db=database;
  state.sea=atlas.provinces.filter(f=>!f.properties.name).map(makeShape);
  state.provinces=atlas.provinces.filter(f=>f.properties.name).map(f=>({code:String(f.properties.adcode),name:f.properties.name,center:f.properties.centroid||f.properties.center,shape:makeShape(f)}));
  state.cities=atlas.cities.map(f=>({...f.properties,shape:makeShape(f)}));await loadRecords();if(new URLSearchParams(location.search).get('demo')==='1'&&!state.records.length)await toggleDemo();const savedProvince=sessionStorage.getItem('lvye-preview-province');state.province=state.provinces.some(p=>p.code===savedProvince)?savedProvince:null;resizeMap();const selected=state.provinces.find(p=>p.code===state.province);setCamera(selected?fit(selected.shape.primary,40):homeCamera(),false);renderScene();$('#map-loading').hidden=true;await watchRevision();setInterval(watchRevision,1800);
}catch(error){$('#map-loading').textContent='预览没有打开：'+error.message;console.error(error);}}
window.TravelPreview={getState:()=>({province:state.province,tab:state.tab,cities:state.cities.length,provinces:state.provinces.length,records:state.records.length,camera:{...state.camera},demo:state.demo,trips:state.trips.length,cityStyles:state.cityStyles.length})};
start();
