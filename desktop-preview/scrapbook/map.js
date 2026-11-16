import {markerGlyph,markerDefaults} from './markers.js';
const NS='http://www.w3.org/2000/svg';
export const project=([lon,lat])=>[lon,-180/Math.PI*Math.log(Math.tan(Math.PI/4+Math.max(-85,Math.min(85,lat))*Math.PI/360))];
export const shortName=name=>name.replace(/(维吾尔自治区|壮族自治区|回族自治区|特别行政区|自治区|省|市)$/,'');
export function node(tag,attrs={}){const e=document.createElementNS(NS,tag);Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v));return e;}
export function shape(feature){const polygons=feature.geometry.type==='Polygon'?[feature.geometry.coordinates]:feature.geometry.coordinates;const rings=polygons.flat().map(r=>r.map(project));const points=rings.flat();return {d:rings.map(r=>r.map((p,i)=>`${i?'L':'M'}${p[0]},${p[1]}`).join('')+'Z').join(''),bounds:[Math.min(...points.map(p=>p[0])),Math.min(...points.map(p=>p[1])),Math.max(...points.map(p=>p[0])),Math.max(...points.map(p=>p[1]))]};}
export function provincesFrom(atlas){return atlas.provinces.filter(p=>p.properties.name).map(p=>({code:String(p.properties.adcode),name:p.properties.name,center:project(p.properties.centroid||p.properties.center),shape:shape(p)}));}
export function drawProvince(target,province,design,visited,clipID){
  const fill=visited?(design?.color||'#B8CBB3'):'transparent';
  const path=node('path',{d:province.shape.d,'fill-rule':'evenodd',fill,class:'province-outline','data-province':province.code,role:'button',tabindex:0,'aria-label':province.name});target.append(path);
  if(visited&&design?.mode==='photos'&&design.photos.length){
    const clip=node('clipPath',{id:clipID});clip.append(node('path',{d:province.shape.d,'fill-rule':'evenodd'}));target.append(clip);
    const group=node('g',{'clip-path':`url(#${clipID})`,'pointer-events':'none'}),[left,top,right,bottom]=province.shape.bounds;
    const count=design.photos.length,cols=count===1?1:2,rows=count<=2?1:2;
    design.photos.forEach((photo,i)=>{let x=left+(i%cols)*(right-left)/cols,y=top+Math.floor(i/cols)*(bottom-top)/rows,w=(right-left)/cols,h=(bottom-top)/rows;if(count===3&&i===2){x=left;w=right-left;}group.append(node('image',{href:photo,x,y,width:w,height:h,preserveAspectRatio:'xMidYMid slice'}));});target.append(group);
  }
}
export class ProvinceMap {
  constructor(container,atlas,records,designs,onSelect,onMarker=onSelect,plans=[]){
    this.container=container;this.atlas=atlas;this.provinces=provincesFrom(atlas);this.records=records;this.designs=designs;this.onSelect=onSelect;this.camera=null;this.labels=[];this.pointers=new Map();this.dragged=false;
    this.svg=node('svg',{class:'province-map',role:'group','aria-label':'中国省级旅行地图，可缩放与平移'});container.append(this.svg);
    this.observer=new ResizeObserver(()=>this.render());this.observer.observe(container);
    this.plans=plans;
    this.svg.addEventListener('click',e=>{if(this.dragged)return;const marker=e.target.closest('[data-marker]')?.dataset.marker||this.pointerMarker;if(marker){onMarker(marker);return;}const code=e.target.closest('[data-province]')?.dataset.province||this.pointerSelection;if(code)this.onSelect(code);});
    this.svg.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){const marker=e.target.closest('[data-marker]')?.dataset.marker;if(marker){e.preventDefault();onMarker(marker);return;}const code=e.target.closest('[data-province]')?.dataset.province;if(code){e.preventDefault();this.onSelect(code);}}});
    this.svg.addEventListener('wheel',e=>{e.preventDefault();this.zoom(Math.exp(-e.deltaY*.0015),e.offsetX,e.offsetY);},{passive:false});
    this.svg.addEventListener('pointerdown',e=>{this.pointerMarker=e.target.closest('[data-marker]')?.dataset.marker;this.pointerSelection=e.target.closest('[data-province]')?.dataset.province;this.startPoint={x:e.clientX,y:e.clientY};this.svg.setPointerCapture(e.pointerId);this.pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});this.dragged=false;this.distance=this.pinchDistance();});
    this.svg.addEventListener('pointermove',e=>{if(!this.pointers.has(e.pointerId))return;const previous=this.pointers.get(e.pointerId);this.pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});if(Math.hypot(e.clientX-this.startPoint.x,e.clientY-this.startPoint.y)>5)this.dragged=true;if(this.pointers.size===2){this.dragged=true;const distance=this.pinchDistance();if(this.distance)this.zoom(distance/this.distance);this.distance=distance;}else{this.camera.x-=(e.clientX-previous.x)/this.camera.k;this.camera.y-=(e.clientY-previous.y)/this.camera.k;this.transform();}});
    const end=e=>{this.pointers.delete(e.pointerId);this.distance=this.pinchDistance();};this.svg.addEventListener('pointerup',end);this.svg.addEventListener('pointercancel',end);
  }
  pinchDistance(){const points=[...this.pointers.values()];return points.length===2?Math.hypot(points[0].x-points[1].x,points[0].y-points[1].y):0;}
  home(){this.camera=null;this.render();}
  zoom(factor,x=this.width/2,y=this.height/2){if(!this.camera)return;const next=Math.max(this.base*.8,Math.min(this.base*8,this.camera.k*factor));this.camera.x+=(x-this.width/2)*(1/this.camera.k-1/next);this.camera.y+=(y-this.height/2)*(1/this.camera.k-1/next);this.camera.k=next;this.transform();}
  transform(){const c=this.camera;this.world.setAttribute('transform',`translate(${this.width/2},${this.height/2}) scale(${c.k}) translate(${-c.x},${-c.y})`);for(const label of this.labels){label.element.setAttribute('transform',`translate(${label.x},${label.y}) scale(${1/c.k})`);label.element.style.display=label.primary||c.k/this.base>1.45?'':'none';}}
  render(){
    this.width=this.container.clientWidth;this.height=this.container.clientHeight;if(!this.width||!this.height)return;
    const oldBase=this.base;this.base=Math.min((this.width-14)/64,(this.height-26)/49);if(this.camera&&oldBase)this.camera.k*=this.base/oldBase;this.camera??={x:104.5,y:-40.5,k:this.base};this.svg.setAttribute('viewBox',`0 0 ${this.width} ${this.height}`);this.svg.replaceChildren();this.labels=[];this.world=node('g');this.svg.append(this.world);
    for(const p of this.provinces){const notes=this.records.filter(r=>r.provinceCode===p.code),visited=notes.some(r=>r.visited),design=this.designs.find(d=>d.id===p.code);drawProvince(this.world,p,design,visited,'map-'+p.code);}
    const placed=[];
    const offsets={'110000':[-10,-16],'120000':[18,0],'310000':[15,1],'810000':[20,1],'820000':[-10,17]};
    for(const p of this.provinces){
      const text=shortName(p.name),offset=offsets[p.code]||[0,0];let x=(p.center[0]-104.5)*this.base+this.width/2+offset[0],y=(p.center[1]+40.5)*this.base+this.height/2+offset[1];const width=text.length*11+8;
      for(let step=0;step<14;step++){if(!placed.some(b=>Math.abs(b.x-x)<(b.w+width)/2+1&&Math.abs(b.y-y)<17))break;y+=(step%2?1:-1)*(4+step);}
      x=Math.max(width/2+4,Math.min(this.width-width/2-4,x));y=Math.max(10,Math.min(this.height-14,y));placed.push({x,y,w:width});
      const group=node('g',{'data-province':p.code,class:'province-label',role:'button',tabindex:0,'aria-label':'查看'+p.name});group.append(node('rect',{x:-width/2,y:-15,width,height:28,fill:'transparent'}));const label=node('text',{'text-anchor':'middle',y:3});label.textContent=text;group.append(label);this.world.append(group);const primary=['650000','540000','630000','510000','530000','150000','230000','330000','440000','710000','460000'].includes(p.code)||this.records.some(r=>r.provinceCode===p.code);this.labels.push({element:group,x:104.5+(x-this.width/2)/this.base,y:-40.5+(y-this.height/2)/this.base,primary});
      if(this.records.some(r=>r.provinceCode===p.code&&!r.visited)||this.plans.some(r=>r.provinceCode===p.code&&r.status==='planned')){const m={...markerDefaults(),...this.designs.find(d=>d.id===p.code)?.marker};const marker=node('g',{'data-marker':p.code,class:'wish-marker',role:'button',tabindex:0,'aria-label':'修改'+shortName(p.name)+'的想去标记'});marker.append(node('rect',{x:-14,y:-42,width:44,height:44,fill:'transparent'}),markerGlyph(m,{x:-m.size/2+6,y:-m.size-12}));this.world.append(marker);const position=this.labels[this.labels.length-1];this.labels.push({element:marker,x:position.x,y:position.y,primary:true});}
    }
    const inset=node('svg',{x:this.width-63,y:this.height-87,width:56,height:78,viewBox:'105 -29 22 28',class:'sea-inset','aria-label':'南海诸岛'});
    for(const feature of this.atlas.provinces){const s=shape(feature);inset.append(node('path',{d:s.d,fill:'none',stroke:'#a9b59c','stroke-width':'.15'}));}this.svg.append(inset);this.transform();
  }
  destroy(){this.observer.disconnect();this.container.replaceChildren();}
}
