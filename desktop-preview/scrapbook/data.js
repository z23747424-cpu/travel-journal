import {validPlan} from './plans-data.js';
export const DB_NAME='lvye-province-scrapbook';
let db;
export const today=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
export function validDate(value){if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;const date=new Date(value+'T12:00:00Z');return !isNaN(date)&&date.toISOString().slice(0,10)===value;}
export async function openDB(){return new Promise((resolve,reject)=>{const request=indexedDB.open(DB_NAME,2);request.onupgradeneeded=()=>{for(const name of ['records','designs','settings','plans'])if(!request.result.objectStoreNames.contains(name))request.result.createObjectStore(name,{keyPath:'id'});};request.onsuccess=()=>{db=request.result;db.onversionchange=()=>db.close();resolve(db);};request.onerror=()=>reject(request.error);request.onblocked=()=>reject(Error('请关闭另一页旅页预览，再重新打开。'));});}
export async function readStore(name){return new Promise((resolve,reject)=>{const request=db.transaction(name).objectStore(name).getAll();request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});}
export async function writeStores(names,write){return new Promise((resolve,reject)=>{const tx=db.transaction(names,'readwrite');try{write(tx);}catch(error){tx.abort();reject(error);return;}tx.oncomplete=resolve;tx.onabort=()=>reject(tx.error||Error('保存被中断'));tx.onerror=()=>reject(tx.error);});}
export const put=(store,value)=>writeStores([store],tx=>tx.objectStore(store).put(value));
export const remove=(store,id)=>writeStores([store],tx=>tx.objectStore(store).delete(id));
export async function load(){const [records,designs,settings,plans]=await Promise.all(['records','designs','settings','plans'].map(readStore));return {records:records.sort((a,b)=>b.date.localeCompare(a.date)||b.updatedAt-a.updatedAt),designs,settings,plans:plans.sort((a,b)=>b.updatedAt-a.updatedAt)};}
export function safePhoto(value){if(typeof value!=='string'||value.length>36_000_000)return false;if(value==='/assets/lake.jpg.png'||value==='/assets/street.jpg.png'||/^data:image\/(?:jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(value))return true;if(/^data:image\/svg\+xml;base64,[A-Za-z0-9+/=]+$/.test(value)){try{const text=atob(value.split(',')[1]);return /<svg[\s>]/i.test(text)&&!/<(?:script|foreignObject|iframe|object|embed)\b|\bon\w+\s*=|(?:href|url)\s*[=(]\s*["']?(?:https?:|\/\/|javascript:|data:)/i.test(text);}catch{return false;}}return false;}
export async function readPhoto(file){
  if(file.size>25*1024*1024)throw Error('这张图片超过 25MB，请选择较小的图片。');
  if(!['image/png','image/jpeg','image/webp'].includes(file.type))throw Error('请选择 JPG、PNG 或 WebP 图片。');
  const url=URL.createObjectURL(file);
  try{const image=new Image();image.src=url;await image.decode();return await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(Error('照片无法读取'));reader.readAsDataURL(file);});}
  finally{URL.revokeObjectURL(url);}
}
export function importData(data,atlas){
  const bad=()=>{throw Error('文件内容不完整或格式无效，原记录未改动。');};
  if(!data||!['lvye-province-scrapbook','lvye-desktop-preview'].includes(data.format)||![1,2].includes(data.version)||!Array.isArray(data.records)||data.records.length>5000)bad();
  const provinces=new Set(atlas.provinces.map(p=>String(p.properties.adcode)));
  const cityMap=new Map(atlas.cities.map(c=>[c.properties.code,c.properties]));
  const ids=new Set();
  const records=data.records.map(raw=>{
    if(!raw||typeof raw.id!=='string'||!raw.id||raw.id.length>160||ids.has(raw.id)||!validDate(raw.date)||typeof raw.visited!=='boolean'||!Array.isArray(raw.photos)||raw.photos.length>12||!raw.photos.every(safePhoto))bad();ids.add(raw.id);
    for(const key of ['body','title','city','place'])if(raw[key]!=null&&(typeof raw[key]!=='string'||raw[key].length>20000))bad();
    const city=cityMap.get(raw.cityCode);let provinceCode=raw.provinceCode||city?.provinceCode||null;
    if(provinceCode!=null&&!provinces.has(String(provinceCode)))provinceCode=null;
    if(raw.tags!=null&&(!Array.isArray(raw.tags)||raw.tags.length>20||raw.tags.some(t=>typeof t!=='string'||t.length>100)))bad();
    return {...raw,provinceCode:provinceCode?String(provinceCode):null,city:raw.city??city?.name??'',body:raw.body||'',title:raw.title||'',tags:raw.tags||[],updatedAt:Number.isFinite(raw.updatedAt)?raw.updatedAt:Date.now()};
  });
  const designs=data.format==='lvye-province-scrapbook'?(data.designs||[]):[];
  if(!Array.isArray(designs)||designs.length>34)bad();
  const designIds=new Set();
  for(const style of designs){if(!style||!provinces.has(style.id)||designIds.has(style.id)||!/^#[0-9A-Fa-f]{6}$/.test(style.color)||!Array.isArray(style.photos)||style.photos.length>4||!style.photos.every(safePhoto)||!['color','photos'].includes(style.mode))bad();designIds.add(style.id);}
  const legacy=data.format==='lvye-desktop-preview'?[{format:data.format,version:data.version,trips:data.trips||[],cityStyles:data.cityStyles||[]}]:data.legacy||[];
  if(!Array.isArray(legacy)||legacy.length>1000)bad();
  for(const d of designs)if(d.marker!=null){const m=d.marker;if(!['flag','pin','star','heart','image'].includes(m.kind)||!/^#[0-9A-Fa-f]{6}$/.test(m.color)||!Number.isInteger(m.size)||m.size<16||m.size>40||m.kind==='image'&&!safePhoto(m.image))bad();}
  const plans=data.plans||[];
  if(!Array.isArray(plans)||plans.length>1000)bad();
  const planIds=new Set();for(const p of plans){if(!validPlan(p,provinces)||planIds.has(p.id))bad();planIds.add(p.id);}
  return {records,designs,legacy,plans};
}
