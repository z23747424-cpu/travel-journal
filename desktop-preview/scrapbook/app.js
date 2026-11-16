import {today,validDate,openDB,load,put,remove,writeStores,readPhoto,importData} from './data.js';
import {ProvinceMap,provincesFrom,shortName,drawProvince,node} from './map.js';
import {createPlanner} from './planner.js';
import {editMarker} from './markers.js';

const $=selector=>document.querySelector(selector);
const screen=$('#screen'),dialog=$('#dialog'),content=$('#dialog-content');
const state={atlas:null,provinces:[],records:[],designs:[],settings:[],plans:[],screen:'map',province:null,filter:'all',detail:null,draft:null};
let map,timer,draftTimer,revision,refreshPending=false,editorBusy=false,closingOverlay=false,pendingRoute=null,pendingEditor=null;
const iconPaths={map:'m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2ZM9 3v16M15 5v16',search:'M21 21l-5-5M17 10.5a6.5 6.5 0 1 0-13 0 6.5 6.5 0 0 0 13 0',book:'M12 5C9 2 5 3 3 4v16c3-2 6-2 9 0 3-2 6-2 9 0V4c-2-1-6-2-9 1ZM12 5v15',plus:'M12 5v14M5 12h14',minus:'M5 12h14',back:'m14 5-7 7 7 7',close:'m6 6 12 12M18 6 6 18',pen:'m15 5 4 4M5 19l4-1L20 7a2.8 2.8 0 0 0-4-4L5 14Z',flag:'M5 21V3c5-4 9 4 14 0v11c-5 4-9-4-14 0',pin:'M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 0-6 0 3 3 0 0 0 6 0',calendar:'M5 5h14v16H5ZM8 2v6M16 2v6M5 10h14',city:'M4 21V7h8v14M12 12h8v9M7 10h2M7 14h2M7 18h2M15 16h2M15 19h2',settings:'M4 7h16M4 17h16M8 4v6M16 14v6',compass:'M21 12a9 9 0 1 0-18 0 9 9 0 0 0 18 0ZM15 9l-2 4-4 2 2-4Z',plane:'m22 2-8 20-4-8-8-4ZM10 14 22 2',photo:'M3 3h18v18H3ZM3 17l5-5 4 4 4-6 5 7M9 8h.01',download:'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',check:'m5 12 4 4 10-10'};
function icon(name){const svg=node('svg',{class:'icon','aria-hidden':'true',viewBox:'0 0 24 24'});svg.append(node('path',{d:iconPaths[name]||iconPaths.book}));return svg;}
function h(tag,attrs={},...children){const e=document.createElement(tag);for(const [key,value]of Object.entries(attrs)){if(value==null||value===false)continue;if(key==='text')e.textContent=value;else if(key==='class')e.className=value;else if(key.startsWith('on'))e.addEventListener(key.slice(2).toLowerCase(),value);else if(key==='value')e.value=value;else if(value===true)e.setAttribute(key,'');else e.setAttribute(key,value);}children.flat().filter(x=>x!=null).forEach(x=>e.append(x instanceof Node?x:document.createTextNode(String(x))));return e;}
function button(text,fn,cls='text-button',name){const b=h('button',{class:cls,onClick:fn,type:'button'});if(name)b.append(icon(name));if(cls.split(' ').includes('chip'))b.setAttribute('aria-pressed',String(cls.split(' ').includes('selected')));if(text)b.append(h('span',{text}));else if(name)b.setAttribute('aria-label',({close:'关闭',book:'全部手账',plus:'添加',minus:'缩小',map:'全国地图'})[name]||name);return b;}
function toast(text){clearTimeout(timer);$('#toast').textContent=text;$('#toast').hidden=false;timer=setTimeout(()=>$('#toast').hidden=true,3500);}
function province(code=state.province){return state.provinces.find(p=>p.code===code);}
function notes(code=state.province){return state.records.filter(r=>r.provinceCode===code);}
function design(code=state.province){return state.designs.find(d=>d.id===code);}
function cover(code=state.province){const d=design(code);return d?.photos?.[d.coverIndex||0]||notes(code).find(r=>r.photos.length)?.photos[0]||null;}
function displayTitle(record){return record.title||record.body?.slice(0,20)||`${record.city||shortName(province(record.provinceCode)?.name||'旅途')}的一页`;}
function dateLabel(date){return `${date.slice(5,7)}月${date.slice(8,10)}日`;}
async function reload(){Object.assign(state,await load());state.draft=state.settings.find(s=>s.id==='draft')?.value||null;}
function topbar(title,back,save){return h('div',{class:'topbar'},back?button(back.text,back.fn,'text-button','back'):h('span',{class:'brand',text:'旅页'}),title?h('h1',{class:'editor-title',text:title}):h('span'),save||button('',()=>go('archive'),'icon-button','book'));}
function page({scroll=false}={}){map?.destroy();map=null;screen.replaceChildren();const el=h('section',{class:'page'+(scroll?' page-scroll':'')});screen.append(el);requestAnimationFrame(()=>{const heading=screen.querySelector('h1,h2');if(heading){heading.tabIndex=-1;heading.focus({preventScroll:true});}});return el;}
function nav(){const hidden=['album','editor','detail','plan'].includes(state.screen);$('#navigation').hidden=hidden;$('#navigation').replaceChildren();for(const [key,label,name]of [['map','地图','map'],['search','搜索','search'],['inspiration','灵感','compass'],['settings','设置','settings']]){const b=button(label,()=>go(key),state.screen===key?'active':'',name);b.setAttribute('aria-current',state.screen===key?'page':'false');$('#navigation').append(b);}}
function go(where,code=state.province,options={}){if(history.state?.overlay||closingOverlay){pendingRoute={where,code,options};closeDialog();return;}if(state.screen==='inspiration'&&where!=='inspiration')planner.cancel();state.screen=where;state.province=code;if(!options.fromHistory)history[options.replace?'replaceState':'pushState']({screen:where,province:code},'',`#${where}${code?'/'+code:''}`);render();}
function render(){nav();const routes={map:renderMap,album:renderAlbum,search:renderSearch,inspiration:renderInspiration,plan:()=>planner.renderEditor(),settings:renderSettings,archive:renderArchive,detail:renderDetail};(routes[state.screen]||renderMap)();}
function newEntry(code){return {id:crypto.randomUUID(),provinceCode:code||null,city:'',place:'',title:'',body:'',date:today(),visited:true,photos:[],tags:[],updatedAt:Date.now(),demo:false};}
function startEditor(code=state.province,record=null){
  if(record&&state.draft?.id===record.id){renderEditor(state.draft);toast('已恢复这篇手账尚未保存的编辑');return;}
  if(state.draft&&state.draft.id!==record?.id){openDialog('继续未完成的一页',box=>{box.append(h('p',{text:'你还有一篇草稿。先把这一页写完，照片和文字都已保留。',class:'quiet'}),button('继续草稿',()=>{closeDialog();renderEditor(state.draft);},'primary','pen'));});return;}
  renderEditor(record?{...record,photos:[...record.photos],tags:[...(record.tags||[])],demo:false}:newEntry(code));
}
function photoFrame(src,alt,open){const frame=h('div',{class:'photo-frame'});const img=h('img',{src,alt,loading:'lazy'});frame.append(open?button('',open,''):img);if(open)frame.firstChild.append(img);return frame;}
function renderMap(){
  const el=page();el.append(topbar());
  const heading=h('header',{class:'map-heading'},h('h1',{class:'page-title',text:'我的旅行地图'}),h('i',{class:'title-underline','aria-hidden':'true'}),h('p',{text:'把走过的地方，留成回忆'}),button('找省份',provinceSearch,'search-trigger','search'));const plane=icon('plane');plane.classList.add('paper-plane');heading.append(plane);el.append(heading);
  const area=h('div',{class:'map-area'});el.append(area);map=new ProvinceMap(area,state.atlas,state.records,state.designs,code=>go('album',code),code=>customizeMarker(province(code)),state.plans);
  area.append(h('div',{class:'map-buttons'},button('',()=>map?.zoom(1.35),'','plus'),button('',()=>map?.zoom(1/1.35),'','minus'),button('',()=>map?.home(),'','map')));[...area.querySelectorAll('.map-buttons button')].forEach((b,i)=>b.setAttribute('aria-label',['放大地图','缩小地图','返回全国'][i]));
  const compass=h('span',{class:'compass','aria-hidden':'true'},icon('compass'));area.append(compass,h('p',{class:'map-hint',text:'点一个省，翻开那里的回忆'}));
  const bottom=h('div',{class:'home-bottom'}),recent=state.records.find(r=>r.provinceCode&&r.photos.length)||state.records.find(r=>r.provinceCode);
  if(recent){const strip=button('',()=>go('album',recent.provinceCode),'memory-strip');if(recent.photos.length)strip.append(h('img',{src:recent.photos[0],alt:'最近的旅行照片'}));strip.append(h('div',{},h('strong',{text:shortName(province(recent.provinceCode)?.name||'旅行回忆')}),h('small',{text:(recent.demo?'示例回忆 · ':'')+displayTitle(recent)})));bottom.append(strip);}
  else bottom.append(h('div',{class:'memory-strip'},h('div',{style:'padding:8px 12px'},h('strong',{text:'第一程，会落在哪里？'}),h('small',{text:'选一个省，收好照片和当时的心情。'}))));
  if(state.draft)bottom.append(button('继续未完成的手账',()=>renderEditor(state.draft),'draft-banner'));
  bottom.append(button('记一笔',()=>startEditor(null),'primary','pen'));el.append(bottom);
}
function renderAlbum(){
  const p=province();if(!p){go('map');return;}const all=notes(),selected=all.filter(r=>state.filter==='all'||r.visited===(state.filter==='visited'));
  const el=page();el.append(topbar('',{text:'全国',fn:()=>go('map')}));
  const body=h('div',{class:'page-scroll',style:'flex:1;min-height:0'}),head=h('header',{class:'album-head'},h('span',{class:'decoration willow','aria-hidden':'true'}),h('h1',{class:'page-title',text:shortName(p.name)}),h('p',{text:'把这一程慢慢收藏'}));body.append(head);
  const inner=h('div',{class:'album-body'}),src=cover();if(src)inner.append(photoFrame(src,p.name+'旅行封面',()=>viewPhoto(src,p.name)));
  inner.append(h('div',{class:'album-caption'},h('span',{text:`${all.filter(r=>r.visited).length} 篇回忆 · ${all.filter(r=>!r.visited).length} 个期待`}),all.some(r=>r.demo)?h('span',{class:'sample-label',text:'含示例手账'}):null));
  const filters=h('div',{class:'filters'});for(const [key,label]of [['all','全部'],['visited','回忆'],['wish','想去']])filters.append(button(label,()=>{state.filter=key;renderAlbum();},'chip'+(state.filter===key?' selected':'')));inner.append(filters);
  if(!selected.length)inner.append(empty(state.filter==='wish'?'下一程，慢慢期待':'这一省，等你写下第一篇',state.filter==='wish'?'记录时选择「想去」，就在地图上留一面小旗。':'照片或一句话，都能成为这一页的开始。'));
  selected.forEach(r=>inner.append(entry(r)));
  body.append(inner);el.append(body,h('footer',{class:'sticky-actions'},button('记一笔',()=>startEditor(p.code),'primary','pen'),button('设计地图',()=>editDesign(p),'text-button','map')));
}
function entry(r){const open=button('',()=>{state.detail=r.id;go('detail',r.provinceCode);},'entry-open');open.append(h('h3',{text:displayTitle(r)}),r.body?h('p',{text:r.body.length>100?r.body.slice(0,100)+'…':r.body}):null,h('span',{class:'metadata'},icon(r.visited?'pin':'flag'),h('span',{text:[r.city,r.demo?'示例手账':r.visited?'去过':'想去'].filter(Boolean).join(' · ')})));return h('article',{class:'entry'},h('span',{class:'date',text:dateLabel(r.date)}),open,r.photos.length?photoFrame(r.photos[0],displayTitle(r),()=>{state.detail=r.id;go('detail',r.provinceCode);}):null);}
function empty(title,text){return h('div',{class:'empty-state'},h('div',{class:'decoration hills','aria-hidden':'true'}),h('h3',{text:title}),h('p',{text}));}
function renderEditor(initial){
  if(history.state?.overlay||closingOverlay){pendingEditor=initial;closeDialog();return;}
  if(history.state?.screen!=='editor'){state.editorOrigin={screen:state.screen,province:state.province};history.pushState({screen:'editor',province:initial.provinceCode},'',`#editor${initial.provinceCode?'/'+initial.provinceCode:''}`);}
  state.screen='editor';nav();const draft={...initial,photos:[...initial.photos]},returnProvince=initial.provinceCode;let busy=false,leaving=false;
  const el=page(),save=button('保存',saveEntry,'save-button');el.append(topbar('记一笔',{text:'取消',fn:leave},save));
  const body=h('div',{class:'editor-body page-scroll',style:'flex:1;min-height:0'}),photos=h('div'),writing=h('textarea',{class:'writing-area',placeholder:'今天的风，值得记下来。','aria-label':'旅行文字',maxlength:20000,value:draft.body});
  const provinceSelect=h('select',{'aria-label':'省份'},h('option',{value:'',text:'选择省份'}),state.provinces.map(p=>h('option',{value:p.code,text:shortName(p.name)})));provinceSelect.value=draft.provinceCode||'';
  const city=h('input',{'aria-label':'城市（选填）',placeholder:'城市（选填）',maxlength:120,value:draft.city||''});
  const date=h('input',{type:'date','aria-label':'旅行日期',value:draft.date});
  const form=h('div',{class:'metadata-form'},formRow('pin','省份',provinceSelect),formRow('city','城市（选填）',city),formRow('calendar','日期',date));
  const title=h('input',{'aria-label':'标题（选填）',placeholder:'给这一页起个名字（选填）',maxlength:200,value:draft.title}),tags=h('input',{'aria-label':'标签（选填）',placeholder:'散步，日落，咖啡',value:(draft.tags||[]).join('，'),maxlength:300});
  const status=h('div',{class:'filters'});for(const [visited,label]of [[true,'去过'],[false,'想去 · 插旗']])status.append(button(label,()=>{draft.visited=visited;updateStatus();queueDraft();},'chip'));
  const extras=h('details',{class:'extra-fields'},h('summary',{},icon('minus'),h('span',{text:'补充更多'})),field('标题（选填）',title),field('标签（选填）',tags),field('这一程',status));
  const error=h('p',{class:'form-error',role:'alert'});body.append(photos,writing,form,extras,error,h('div',{class:'editor-footer'},button('放弃这篇草稿',async()=>{if(!confirm('放弃当前草稿？已保存的手账不受影响。'))return;clearTimeout(draftTimer);await remove('settings','draft');state.draft=null;leaving=true;history.back();},'delete-draft danger'),h('span',{class:'decoration hills','aria-hidden':'true'})));el.append(body);
  for(const input of [writing,provinceSelect,city,date,title,tags])input.addEventListener('input',queueDraft);
  function snapshot(){return {...draft,body:writing.value,provinceCode:provinceSelect.value||null,city:city.value.trim(),date:date.value,title:title.value.trim(),tags:tags.value.split(/[,，]/).map(s=>s.trim()).filter(Boolean).slice(0,20),updatedAt:Date.now()};}
  function queueDraft(){clearTimeout(draftTimer);state.draft=snapshot();draftTimer=setTimeout(()=>put('settings',{id:'draft',value:state.draft}).catch(e=>toast('草稿暂存失败：'+e.message)),300);}
  async function leave({fromHistory=false}={}){if(busy)return;clearTimeout(draftTimer);const current=snapshot();if(current.body.trim()||current.photos.length||current.city||current.title){await put('settings',{id:'draft',value:current});state.draft=current;toast('未完成的这一页已保留为草稿');}leaving=true;if(fromHistory){renderHistoryRoute();}else history.back();}
  async function saveEntry(){
    if(busy)return;error.textContent='';const current=snapshot();
    if(!current.provinceCode){error.textContent='先选一个省份，把这一程安放好。';provinceSelect.focus();return;}
    if(!current.body.trim()&&!current.photos.length){error.textContent='添一张照片，或者写一句话再保存。';writing.focus();return;}
    if(!validDate(current.date)){error.textContent='请选择有效的旅行日期。';date.focus();return;}
    busy=true;editorBusy=true;save.disabled=true;clearTimeout(draftTimer);current.body=current.body.trim();current.demo=false;
    try{await writeStores(['records','designs','settings'],tx=>{tx.objectStore('records').put(current);if(!design(current.provinceCode))tx.objectStore('designs').put({id:current.provinceCode,color:'#B8CBB3',mode:'color',photos:current.photos.slice(0,1),coverIndex:0,demo:false});tx.objectStore('settings').delete('draft');});await reload();state.filter='all';leaving=true;if(state.editorOrigin?.screen==='album'&&state.editorOrigin.province===current.provinceCode){history.back();}else go('album',current.provinceCode,{replace:true});toast(current.visited?'这一页，已经好好收起来了':'小旗已插好，等一个出发的日子');}
    catch(e){error.textContent='保存失败，草稿还在：'+e.message;save.disabled=false;}finally{busy=false;editorBusy=false;}
  }
  async function addPhotos(){if(busy)return;const picker=$('#photo-picker');picker.value='';picker.onchange=async()=>{if(!picker.files.length)return;busy=true;save.disabled=true;try{const files=[...picker.files].slice(0,12-draft.photos.length);draft.photos.push(...await Promise.all(files.map(readPhoto)));renderPhotos();queueDraft();}catch(e){error.textContent=e.message;}finally{busy=false;save.disabled=false;}};picker.click();}
  function renderPhotos(){photos.replaceChildren();const top=h('div',{class:'photo-editor'});if(draft.photos.length)top.append(photoFrame(draft.photos[0],'已选择的旅行照片',()=>viewPhoto(draft.photos[0],'旅行照片')),button('',addPhotos,'add-photo','plus'));else top.append(button('选几张旅行照片',addPhotos,'no-photo','photo'));photos.append(top);if(draft.photos.length){const strip=h('div',{class:'photo-thumbnails'});draft.photos.forEach((photo,i)=>strip.append(h('div',{class:'thumbnail'},h('img',{src:photo,alt:'照片 '+(i+1)}),button('',()=>{draft.photos.splice(i,1);renderPhotos();queueDraft();},'icon-button','close'))));strip.querySelectorAll('button').forEach((b,i)=>b.setAttribute('aria-label','移除照片 '+(i+1)));photos.append(strip);}}
  function updateStatus(){[...status.children].forEach((b,i)=>{const selected=draft.visited===(i===0);b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));});}
  renderPhotos();updateStatus();state.editorLeave=options=>leaving?null:leave(options);
}
function formRow(name,label,input){const id='input-'+name;input.id=id;return h('div',{class:'form-row'},icon(name),h('label',{for:id,text:label}),input);}
function field(label,input){return h('label',{class:'field'},h('span',{text:label}),input);}
function renderDetail(){
  const r=state.records.find(r=>r.id===state.detail);if(!r){go('archive');return;}const el=page();el.append(topbar('',{text:r.provinceCode?shortName(province(r.provinceCode)?.name||'相册'):'全部手账',fn:()=>go(r.provinceCode?'album':'archive',r.provinceCode)},button('编辑',()=>startEditor(r.provinceCode,r),'text-button','pen')));
  const body=h('div',{class:'detail-content page-scroll'});body.append(h('p',{class:'metadata',text:[r.date,province(r.provinceCode)?.name,r.city,r.demo?'示例手账':''].filter(Boolean).join(' · ')}),h('h2',{text:displayTitle(r)}));
  r.photos.forEach((photo,i)=>body.append(photoFrame(photo,'旅行照片 '+(i+1),()=>viewPhoto(photo,displayTitle(r)))));body.append(h('p',{class:'journal-text',text:r.body||'这一页，风景已经说了很多。'}));
  if(!r.visited)body.append(button('开始这段旅行',async()=>{await put('records',{...r,visited:true,demo:false,updatedAt:Date.now()});await reload();renderDetail();toast('期待已变成回忆，原记录仍在这一页');},'primary','flag'));
  body.append(button('删除这一页',async()=>{if(!confirm('删除这篇手账？省份装饰与其他记录会保留。'))return;await remove('records',r.id);await reload();go(r.provinceCode?'album':'archive',r.provinceCode);},'text-button danger'));el.append(body);
}
function openDialog(title,fill){content.replaceChildren();dialog.setAttribute('aria-label',title);content.append(h('header',{class:'dialog-heading'},h('h2',{text:title}),button('',closeDialog,'icon-button','close')));const body=h('div',{class:'dialog-scroll'});content.append(body);fill(body);if(!dialog.open){history.pushState({...history.state,overlay:true},'',location.href);dialog.showModal();}return body;}
function closeDialog(){dialog.close();if(history.state?.overlay&&!closingOverlay){closingOverlay=true;history.back();}}
dialog.addEventListener('cancel',event=>{event.preventDefault();closeDialog();});
dialog.addEventListener('click',event=>{if(event.target===dialog&&!(event.clientX>=dialog.getBoundingClientRect().left&&event.clientX<=dialog.getBoundingClientRect().right&&event.clientY>=dialog.getBoundingClientRect().top&&event.clientY<=dialog.getBoundingClientRect().bottom))closeDialog();});
function viewPhoto(src,alt){openDialog('这一刻的完整画面',box=>box.append(h('img',{src,alt,class:'photo-viewer'})));}
function provinceSearch(){openDialog('找一个省份',box=>{const input=h('input',{class:'search-input',placeholder:'省份、直辖市…','aria-label':'查找省份'}),list=h('div',{class:'province-options'});box.append(input,list);const update=()=>{list.replaceChildren();const found=state.provinces.filter(p=>p.name.includes(input.value.trim()));found.forEach(p=>list.append(button('',()=>{closeDialog();go('album',p.code);},'')));[...list.children].forEach((b,i)=>b.append(h('span',{text:shortName(found[i].name)}),h('small',{text:notes(found[i].code).length?'有回忆':'选择'})));if(!found.length)list.append(h('p',{class:'quiet',text:'没有找到，试试输入省份简称。'}));};input.oninput=update;update();requestAnimationFrame(()=>input.focus());});}
function customizeMarker(p){if(p)editMarker(p,{h,button,field,openDialog,closeDialog,design,reload,render,toast});}
function editDesign(p){
  const current=design(p.code),draft=current?structuredClone(current):{id:p.code,color:'#B8CBB3',mode:'color',photos:[],coverIndex:0};draft.demo=false;
  openDialog('设计'+shortName(p.name)+'的这一页',box=>{
    const preview=node('svg',{class:'design-preview',viewBox:`${p.shape.bounds[0]-.3} ${p.shape.bounds[1]-.3} ${p.shape.bounds[2]-p.shape.bounds[0]+.6} ${p.shape.bounds[3]-p.shape.bounds[1]+.6}`});box.append(preview);
    const swatches=h('div',{class:'swatches'});for(const color of ['#B8CBB3','#D5C5A4','#C3CDD0','#CFAF9F','#D8D5AC']){const b=button('',()=>{draft.color=color;draft.mode='color';custom.value=color;draw();},'');b.style.background=color;b.setAttribute('aria-label','颜色 '+color);swatches.append(b);}box.append(swatches);
    const custom=h('input',{type:'color',value:draft.color,'aria-label':'自定义省份颜色'});custom.oninput=()=>{draft.color=custom.value;draw();};box.append(field('自定义颜色',custom));
    const modes=h('div',{class:'filters'},button('柔和填色',()=>{draft.mode='color';draw();},'chip'),button('照片拼贴',()=>{if(!draft.photos.length){toast('先从下方选择照片');return;}draft.mode='photos';draw();},'chip'));box.append(modes);
    const available=[...new Set([...draft.photos,...notes(p.code).flatMap(r=>r.photos)])],gallery=h('div',{class:'design-photos'});box.append(h('p',{class:'design-help',text:'选择最多四张作为拼贴；第一张选中的照片作为相册封面。写新手账不会替换这里的选择。'}),gallery);
    function renderGallery(){gallery.replaceChildren();available.forEach(photo=>{const index=draft.photos.indexOf(photo),b=button('',()=>{if(index>=0)draft.photos.splice(index,1);else if(draft.photos.length<4)draft.photos.push(photo);else{toast('拼贴可用四张照片，先取消其中一张再选');return;}draft.coverIndex=0;if(!draft.photos.length)draft.mode='color';renderGallery();draw();},'');b.classList.toggle('selected',index>=0);b.setAttribute('aria-pressed',String(index>=0));b.classList.toggle('cover',index===0);b.append(h('img',{src:photo,alt:index===0?'当前封面照片':'可选照片'}));gallery.append(b);});}
    box.append(button('添加自己的照片',()=>{const input=$('#photo-picker');input.value='';input.onchange=async()=>{try{const added=await Promise.all([...input.files].slice(0,4).map(readPhoto));available.push(...added);for(const photo of added)if(draft.photos.length<4)draft.photos.push(photo);renderGallery();draw();}catch(e){toast(e.message);}};input.click();},'text-button','photo'));
    box.append(button('保存设计并自定义标记',async()=>{try{await put('designs',draft);await reload();customizeMarker(p);}catch(e){toast('设计未能保存，请重试：'+e.message);}},'setting-row','flag'));
    box.append(button('保存设计',async()=>{try{await put('designs',draft);await reload();closeDialog();renderAlbum();toast('这一省的颜色与封面已经收好');}catch(e){toast('保存失败：'+e.message);}},'primary','check'));
    function draw(){preview.replaceChildren();drawProvince(preview,p,draft,true,'design-preview-clip');preview.querySelectorAll('[tabindex]').forEach(e=>e.removeAttribute('tabindex'));[...modes.children].forEach((b,i)=>{const selected=draft.mode===(i?'photos':'color');b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));});[...swatches.children].forEach(b=>{const selected=b.getAttribute('aria-label')?.toLowerCase()===('颜色 '+draft.color).toLowerCase();b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));});}
    renderGallery();draw();
  });
}
function renderSearch(){const el=page({scroll:true});el.append(topbar(),h('header',{class:'page-heading'},h('h1',{class:'page-title',text:'把回忆找回来'}),h('p',{text:'省份、城市、文字，记得一点就够了。'})));const body=h('div',{class:'section-body'}),input=h('input',{class:'search-input',placeholder:'比如：浙江、杭州、湖边的风…','aria-label':'搜索手账'}),list=h('div');body.append(input,list);el.append(body);function update(){const q=input.value.trim().toLowerCase();list.replaceChildren();if(!q){list.append(empty('想起哪一段旅程？','在上方写下还记得的地点或一句话。'));return;}const found=state.records.filter(r=>[r.title,r.body,r.city,r.place,province(r.provinceCode)?.name,...r.tags||[]].join(' ').toLowerCase().includes(q));found.forEach(r=>list.append(resultRow(r)));if(!found.length)list.append(empty('还没找到这一页','换个词试试，也可以搜索省份或城市。'));}input.oninput=update;update();}
function resultRow(r){return button('',()=>{state.detail=r.id;go('detail',r.provinceCode);},'result-row').appendChild(h('div',{},h('strong',{text:displayTitle(r)}),h('p',{text:r.body.slice(0,75)}),h('small',{text:[r.date,province(r.provinceCode)?.name||'待归类',r.city,r.demo?'示例':''].filter(Boolean).join(' · ')}))).parentElement;}
function renderArchive(){const el=page({scroll:true});el.append(topbar('',{text:'全国',fn:()=>go('map')}),h('header',{class:'page-heading'},h('h1',{class:'page-title',text:'写过的每一程'}),h('p',{text:'按旅行日期，慢慢翻回去。'})));const body=h('div',{class:'section-body'});if(!state.records.length)body.append(empty('这本日记，刚刚开始','记一笔，留下第一段旅途。'));state.records.forEach(r=>body.append(resultRow(r)));el.append(body);}
const planner=createPlanner({state,h,button,icon,field,page,topbar,empty,go,toast,openDialog,closeDialog,reload,resultRow,provinceSearch});
function renderInspiration(){planner.render();}
function renderSettings(){const el=page({scroll:true});el.append(topbar(),h('header',{class:'page-heading'},h('h1',{class:'page-title',text:'这本旅行日记'}),h('p',{text:'慢慢写，也好好保存。'})));const body=h('div',{class:'section-body'});body.append(h('p',{class:'settings-text',text:'手账与照片保存在当前浏览器。电脑原型与手机数据分开，清理浏览器前请先导出。可以导入旧电脑预览导出的 JSON，原预览的数据会保留。'}),button('接入账本精灵 / 旅行规划 AI',()=>planner.configure(),'setting-row','compass'),button('导出这本日记',exportBackup,'setting-row','download'),button('导入日记或旧预览记录',chooseBackup,'setting-row','book'),button('载入示例手账',loadDemo,'setting-row','photo'),button('移除未修改的示例',removeDemo,'setting-row'),button('查看全部记录',()=>go('archive'),'setting-row','book'),h('p',{class:'settings-text',text:'示例照片由 AI 生成，只用于展示排版。地图使用应用随包省级边界，省名和操作控件为真实文字。照片不会自动上传。只有主动生成旅行推荐或计划时，才使用你接入的搜索与 AI；计划和地图标记会随日记一起备份。'}));el.append(body);}
async function loadDemo(){
  if(['editor','plan'].includes(state.screen)){toast('先保存或收起当前这一页，再载入示例。');return;}
  if(state.records.some(r=>r.id==='sample-lake'||r.id==='sample-street')){toast('示例已在相册中');return;}
  const entries=[{id:'sample-lake',provinceCode:'330000',city:'杭州',title:'湖边慢慢走',body:'风吹过柳树，也吹慢了今天。\n走累了，就在湖边坐一会儿。',date:'2026-11-08',visited:true,photos:['/assets/lake.jpg.png'],tags:['散步'],demo:true,updatedAt:1},{id:'sample-street',provinceCode:'330000',city:'',title:'在老街，偷一点慢时光',body:'拐进一条小巷，把下午留给风和树影。',date:'2026-11-07',visited:true,photos:['/assets/street.jpg.png'],tags:['老街'],demo:true,updatedAt:1},{id:'sample-wish',provinceCode:'630000',city:'',title:'想去看看青海的风',body:'等天气正好的时候，去远一点的地方。',date:'2026-11-06',visited:false,photos:[],tags:[],demo:true,updatedAt:1}];
  try{await writeStores(['records','designs'],tx=>{for(const entry of entries)if(!state.records.some(r=>r.id===entry.id))tx.objectStore('records').put(entry);if(!design('330000'))tx.objectStore('designs').put({id:'330000',color:'#6D8C68',mode:'color',photos:['/assets/lake.jpg.png'],coverIndex:0,demo:true});});await reload();render();toast('已载入明确标注的示例手账');}catch(e){toast('示例未能载入：'+e.message);}
}
async function removeDemo(){await writeStores(['records','designs'],tx=>{state.records.filter(r=>r.demo).forEach(r=>tx.objectStore('records').delete(r.id));state.designs.filter(d=>d.demo&&!state.records.some(r=>!r.demo&&r.provinceCode===d.id)).forEach(d=>tx.objectStore('designs').delete(d.id));});await reload();renderSettings();toast('未修改的示例已移除，你保存过的内容会保留');}
async function exportBackup(){const payload={format:'lvye-province-scrapbook',version:2,records:state.records,designs:state.designs,plans:state.plans,legacy:state.settings.find(s=>s.id==='legacy')?.value||[]};const blob=new Blob([JSON.stringify(payload,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),link=h('a',{href:url,download:'旅页-省份日记-'+today()+'.json'});link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('日记已导出，文件中包含照片');}
function chooseBackup(){
  const input=$('#backup-picker');input.value='';
  input.onchange=async()=>{
    try{
      const file=input.files[0];if(!file)return;if(file.size>500*1024*1024)throw Error('导入文件超过 500MB');
      const incoming=importData(JSON.parse(await file.text()),state.atlas);
      const conflicts=incoming.records.some(r=>state.records.some(old=>old.id===r.id))||incoming.designs.some(d=>design(d.id))||incoming.plans.some(p=>state.plans.some(old=>old.id===p.id));
      if(conflicts&&!confirm('文件中有相同编号，是否使用文件中的版本更新？其他内容会保留。'))return;
      const legacy=[...new Map([...(state.settings.find(s=>s.id==='legacy')?.value||[]),...incoming.legacy].map(block=>[JSON.stringify(block),block])).values()];
      await writeStores(['records','designs','settings','plans'],tx=>{incoming.plans.forEach(p=>tx.objectStore('plans').put(p));incoming.records.forEach(r=>tx.objectStore('records').put(r));incoming.designs.forEach(d=>tx.objectStore('designs').put(d));tx.objectStore('settings').put({id:'legacy',value:legacy});});
      await reload();renderSettings();toast(`已收好 ${incoming.records.length} 篇手账`);
    }catch(e){toast('导入失败：'+e.message);}
  };
  input.click();
}
function renderHistoryRoute(){const target=location.hash.slice(1).split('/');state.screen=target[0]||'map';state.province=target[1]||null;if(state.screen==='editor')renderEditor(state.draft||newEntry(state.province));else render();}
window.addEventListener('popstate',()=>{const overlayWasOpen=dialog.open||closingOverlay;closingOverlay=false;if(dialog.open)dialog.close();if(pendingEditor){const draft=pendingEditor;pendingEditor=null;renderEditor(draft);return;}if(pendingRoute){const route=pendingRoute;pendingRoute=null;go(route.where,route.code,route.options);return;}if(overlayWasOpen)return;if(state.screen==='plan'){planner.persist().then(ok=>{if(ok)renderHistoryRoute();});return;}if(state.screen==='inspiration')planner.cancel();if(state.screen==='editor'){const savingDraft=state.editorLeave?.({fromHistory:true});if(!savingDraft)renderHistoryRoute();return;}renderHistoryRoute();});
window.addEventListener('beforeunload',event=>{if(editorBusy||state.plannerBusy){event.preventDefault();event.returnValue='';}});
$('#phone-size').onclick=()=>{$('.device').classList.remove('wide');$('#phone-size').classList.add('chosen');$('#tablet-size').classList.remove('chosen');};$('#tablet-size').onclick=()=>{$('.device').classList.add('wide');$('#phone-size').classList.remove('chosen');$('#tablet-size').classList.add('chosen');};$('#load-demo').onclick=loadDemo;
async function start(){try{const [atlas]=await Promise.all([fetch('/atlas.json').then(r=>{if(!r.ok)throw Error('地图素材没有加载成功');return r.json();}),openDB()]);state.atlas=atlas;state.provinces=provincesFrom(atlas);await reload();history.replaceState({screen:'map'},'','#map');if(new URLSearchParams(location.search).get('demo')==='1'&&!state.records.length)await loadDemo();render();}catch(e){screen.replaceChildren(h('div',{class:'empty-state'},h('h2',{text:'日记暂时没有翻开'}),h('p',{text:e.message}),button('重新打开',()=>location.reload(),'primary')));}}
setInterval(async()=>{try{const health=await(await fetch('/health')).json();if(revision&&health.revision!==revision){if(['editor','plan'].includes(state.screen)||state.plannerBusy||dialog.open){refreshPending=true;return;}location.reload();}revision=health.revision;if(refreshPending&&!['editor','plan'].includes(state.screen)&&!state.plannerBusy&&!dialog.open)location.reload();}catch{}},3500);
start();
