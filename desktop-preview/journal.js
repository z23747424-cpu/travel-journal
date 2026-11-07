'use strict';

// Reading, writing and decorating are separate surfaces. The map remains the home.
function today(){return new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'});}
function cityOf(record){return state.cities.find(c=>c.code===record.cityCode);}
function tripOf(record){return state.trips.find(t=>t.id===record.tripId);}
function field(name,node){return html('label',{class:'journal-field'},html('span',{text:name}),node);}
function noteDate(record){return record.date||today();}
function chronological(notes){return [...notes].sort((a,b)=>noteDate(a).localeCompare(noteDate(b))||a.updatedAt-b.updatedAt);}
function styleFor(city){return state.cityStyles.find(s=>s.id===city.code);}
function localPhotoAspect(src){return new Promise(resolve=>{const img=new Image();img.onload=()=>resolve(img.naturalWidth/img.naturalHeight||1);img.onerror=()=>resolve(1);img.src=src;});}
async function initialCityStyle(record){return {id:record.cityCode,color:record.color||PALETTE[0],fillMode:record.fillMode||'color',photos:[...record.photos],aspects:await Promise.all(record.photos.map(localPhotoAspect)),crops:record.photos.map(()=>({x:50,y:50,zoom:1})),coverIndex:0,demo:!!record.demo};}
function coverFor(city,notes=recordsFor(city.code)){
  const style=styleFor(city);return style?style.photos[style.coverIndex||0]||null:notes.find(n=>n.photos.length)?.photos[0];
}
function memoryCard(record,onOpen){
  const button=html('button',{class:'memory-card','data-note':record.id,onClick:onOpen});
  if(record.photos.length)button.append(html('img',{src:record.photos[0],alt:'',loading:'lazy'}));
  const text=html('div',{},html('small',{text:`${noteDate(record)} · ${record.visited?'足迹':'想去'}${record.demo?' · 示例':''}`}),html('h4',{text:record.title}),html('p',{text:(record.body||'留一页，给下一次出发。').slice(0,100)}));
  if(tripOf(record))text.append(html('span',{class:'book-caption',text:tripOf(record).title}));
  button.append(text);return button;
}
function cityCard(city){
  focusCity(city);const notes=recordsFor(city.code),visited=notes.filter(r=>r.visited),trips=new Set(notes.map(r=>r.tripId).filter(Boolean));
  const sheet=openSheet(city.name,provinceFor(city)?.name||'城市档案');sheet.classList.add('archive-sheet');
  const cover=coverFor(city,notes);
  if(cover)sheet.append(html('img',{class:'archive-cover',src:cover,alt:city.name+'的城市封面'}));
  else sheet.append(html('div',{class:'archive-blank'},icon('map'),html('span',{text:visited.length?'这座城，已有你的故事':'留一片空白，等一场相遇'})));
  sheet.append(html('p',{class:'archive-counts',text:`${visited.length} 篇回忆 · ${notes.reduce((n,r)=>n+r.photos.length,0)} 张照片 · ${trips.size} 本旅行册`}));
  const actions=html('div',{class:'archive-actions'},action(notes.length?'继续记录':'点亮这座城市',()=>editRecord(city),'primary-button'),action('装饰这座城',()=>decorateCity(city),'secondary-button'));sheet.append(actions);
  const plan=notes.find(r=>!r.visited);
  if(plan)sheet.append(html('div',{class:'wish-callout'},icon('flag'),html('div',{},html('p',{text:plan.body||plan.title}),action('开始这段旅行',()=>startWishedRecord(plan),'text-button'))));
  else if(!notes.length)sheet.append(action('先插一面想去的旗',()=>editRecord(city,null,false),'text-button'));
  if(notes.length){sheet.append(html('h4',{class:'section-title',text:'在这座城的日子'}));for(const note of chronological(notes).reverse())sheet.append(memoryCard(note,()=>recordDetail(note,()=>cityCard(city))));}
  else sheet.append(html('p',{class:'empty-hint',text:'照片和故事会留在这里。城市图块的颜色与拼贴可以单独慢慢设计。'}));
}
function startWishedRecord(record){
  const draft={...record,visited:true,date:today(),wish:record.wish||{title:record.title,body:record.body,date:record.date}};
  if(record.title==='想去'+record.place)draft.title=record.place+'的一页';
  editRecord(cityOf(record),draft);
}
function recordDetail(record,back=null){
  const sheet=openSheet(record.title,`${noteDate(record)} · ${record.place}${record.demo?' · 示例记录':''}`);sheet.classList.add('reading-sheet');
  if(back)sheet.append(action('返回上一页',back,'text-button'));
  if(record.photos.length){const gallery=html('div',{class:'journal-gallery layout-'+(record.layout||'story')});record.photos.forEach((src,i)=>gallery.append(html('button',{'aria-label':`查看第 ${i+1} 张完整照片`,onClick:()=>photoViewer(record,i,()=>recordDetail(record,back))},html('img',{src,alt:`${record.place} · 照片 ${i+1}`}))));sheet.append(gallery);}
  if(record.wish)sheet.append(html('blockquote',{class:'original-wish'},html('small',{text:`出发前的心愿 · ${record.wish.date}`}),html('p',{text:record.wish.body||record.wish.title})));
  sheet.append(html('p',{class:'journal-body',text:record.body||'这一页，风景已经说了很多。'}));
  if(record.tags?.length)sheet.append(html('p',{class:'note-tags',text:record.tags.map(t=>'#'+t).join('  ')}));
  const trip=tripOf(record);if(trip)sheet.append(action('收录于 · '+trip.title,()=>tripDetail(trip),'book-link'));
  if(!record.visited)sheet.append(action('开始这段旅行',()=>startWishedRecord(record),'primary-button'));
  sheet.append(html('div',{class:'record-actions'},action('编辑这一页',()=>editRecord(cityOf(record),record),'text-button'),action('删除这一页',async()=>{if(!confirm('删除这一页？城市装饰和同册的其他记录会保留。'))return;try{await removeRecord(record.id);if(back)back();else booksHome();}catch(e){toast('未能删除：'+e.message);}},'danger')));
}
function photoViewer(record,index,back){
  const sheet=openSheet(`${index+1} / ${record.photos.length}`,record.place);sheet.classList.add('reading-sheet');
  sheet.append(action('返回手账',back,'text-button'),html('img',{class:'full-photo',src:record.photos[index],alt:`${record.place}完整照片 ${index+1}`}));
  const row=html('div',{class:'archive-actions'});if(index>0)row.append(action('上一张',()=>photoViewer(record,index-1,back)));if(index<record.photos.length-1)row.append(action('下一张',()=>photoViewer(record,index+1,back)));sheet.append(row);
}
function editRecord(city,existing=null,visited=true,location=null,initialPhotos=[]){
  let selectedCity=city;
  const draft=existing?{...existing,id:existing.demo?crypto.randomUUID():existing.id,demo:false,photos:[...existing.photos],tags:[...(existing.tags||[])]}:{id:crypto.randomUUID(),cityCode:city?.code||null,place:city?.name||'',title:'',body:'',date:today(),visited,color:PALETTE[0],fillMode:'color',photos:initialPhotos,longitude:location?.[0]??city?.center[0]??state.camera.x,latitude:location?.[1]??city?.center[1]??unproject(state.camera.x,state.camera.y)[1],updatedAt:Date.now(),demo:false,layout:'story',tripId:null,tags:[]};
  const sheet=openSheet(existing?'再添一笔回忆':visited?'留下一程':'给下一程，留一面旗','先收好照片和一句话，排版可以慢慢来');state.sheet='editor';
  const strip=html('div',{class:'photo-strip editable-photos'});
  const addPhotos=action('选择自己的照片 · 最多 4 张',()=>{
    const input=$('#photo-input');input.value='';input.onchange=async()=>{if(!input.files.length)return;addPhotos.disabled=true;try{const photos=await Promise.all([...input.files].slice(0,4).map(readPhoto));draft.photos=photos;renderPhotos();}catch(e){toast(e.message);}finally{addPhotos.disabled=false;}};input.click();
  },'photo-add');addPhotos.prepend(icon('photo'));sheet.append(addPhotos,strip);
  const body=html('textarea',{class:'text-field quick-story',placeholder:'今天的风景，值得记住的一句话…',maxlength:10000,value:draft.body,'aria-label':'旅途故事'});sheet.append(field('这一刻，想记住什么',body));
  const place=html('input',{class:'text-field',list:'journal-city-options',placeholder:'输入并选择城市',value:draft.place,maxlength:120,'aria-label':'记录城市',autocomplete:'off'}),options=html('datalist',{id:'journal-city-options'});
  state.cities.forEach(c=>options.append(html('option',{value:c.name,label:c.provinceName})));
  const date=html('input',{class:'text-field',type:'date',value:draft.date,'aria-label':'旅行日期'});
  sheet.append(html('div',{class:'field-pair'},field('地点',place),field('日期',date)),options);
  place.oninput=()=>{selectedCity=state.cities.find(c=>c.name===place.value.trim()||shortName(c.name)===place.value.trim())||null;};
  const status=html('div',{class:'choice-row'}),been=action('去过 · 点亮城市',()=>{draft.visited=true;updateStatus();},''),want=action('想去 · 插一面旗',()=>{draft.visited=false;updateStatus();},'');status.append(been,want);sheet.append(status);
  const details=html('details',{class:'writing-details'}),summary=html('summary',{text:'标题、旅行册与照片排版'});details.append(summary);
  const title=html('input',{class:'text-field',placeholder:'给这一程起个名字（可选）',maxlength:200,value:draft.title,'aria-label':'手账标题'});details.append(field('这一页的名字',title));
  const trip=html('select',{class:'text-field','aria-label':'所属旅行册'},html('option',{value:'',text:'暂不归册，随手记一页'}),state.trips.map(t=>html('option',{value:t.id,text:t.title})),html('option',{value:'__new',text:'＋ 新建一本旅行册'}));trip.value=draft.tripId||'';
  const newTrip=html('input',{class:'text-field',placeholder:'例如：浙江秋游',maxlength:80,'aria-label':'新旅行册名称'});newTrip.hidden=true;trip.onchange=()=>{newTrip.hidden=trip.value!=='__new';};details.append(field('旅行册',trip),newTrip);
  const layouts=html('div',{class:'choice-row layout-options'});for(const [key,text]of [['story','大图叙事'],['spread','双图并置'],['collage','当日拼贴']])layouts.append(action(text,()=>{draft.layout=key;updateLayout();},''));details.append(field('照片版式',layouts));
  const tags=html('input',{class:'text-field',value:draft.tags.join('，'),placeholder:'日落，散步，咖啡',maxlength:220,'aria-label':'手账标签'});details.append(field('标签（逗号分隔）',tags));sheet.append(details);
  if(existing)details.open=true;
  const error=html('p',{class:'form-error',role:'alert'});sheet.append(error);
  const save=action('保存到我的地图',async()=>{
    error.textContent='';const typed=place.value.trim();
    if(!validDate(date.value)){error.textContent='请选择有效的旅行日期。';date.focus();return;}
    if(!selectedCity&&!(location&&typed||existing&&!existing.cityCode&&typed)){error.textContent='请从城市列表中选择一个地点，也可以在地图上长按精确选点。';place.focus();return;}
    if(trip.value==='__new'&&!newTrip.value.trim()){error.textContent='给新旅行册起一个名字，或选择暂不归册。';newTrip.focus();return;}
    if(selectedCity){draft.cityCode=selectedCity.code;draft.place=selectedCity.name;if(selectedCity.code!==existing?.cityCode&&!(location&&selectedCity.code===city?.code)){[draft.longitude,draft.latitude]=selectedCity.center;}}
    else draft.place=typed;
    draft.title=title.value.trim()||(draft.visited?`${draft.place}的一页`:`想去${draft.place}`);draft.body=body.value.trim();draft.date=date.value;draft.tags=tags.value.split(/[,，]/).map(t=>t.trim()).filter(Boolean).slice(0,12).map(t=>t.slice(0,30));draft.updatedAt=Date.now();
    let createdTrip=null;if(trip.value==='__new'){createdTrip={id:crypto.randomUUID(),title:newTrip.value.trim(),description:'',updatedAt:Date.now(),demo:false};draft.tripId=createdTrip.id;}else draft.tripId=trip.value||null;
    save.disabled=true;try{await saveRecord(draft,existing?.demo?existing.id:null,createdTrip);closeSheet();toast(draft.visited?'这一页已收好，城市装饰可以单独设计':'小旗已经插好，等下一次出发');}catch(e){error.textContent='未能保存：'+e.message;save.disabled=false;}
  },'primary-button');sheet.append(save);
  function updateStatus(){been.classList.toggle('selected',draft.visited);want.classList.toggle('selected',!draft.visited);}
  function updateLayout(){[...layouts.children].forEach((b,i)=>b.classList.toggle('selected',['story','spread','collage'][i]===(draft.layout||'story')));strip.dataset.layout=draft.layout||'story';}
  function renderPhotos(){strip.replaceChildren(...draft.photos.map((src,i)=>html('div',{},html('img',{src,alt:'选中的旅行照片'}),html('button',{'aria-label':`移除第 ${i+1} 张照片`,onClick:()=>{draft.photos.splice(i,1);renderPhotos();}},icon('close')))));}
  renderPhotos();updateStatus();updateLayout();
}
function booksHome(){
  const sheet=openSheet('我的旅行册','把同一段旅程里的城市和故事，装订在一起');sheet.classList.add('reading-sheet');
  sheet.append(action('新建旅行册',()=>editTrip(),'primary-button'));
  for(const trip of [...state.trips].sort((a,b)=>b.updatedAt-a.updatedAt)){
    const notes=chronological(state.records.filter(r=>r.tripId===trip.id)),cover=notes.find(r=>r.photos.length)?.photos[0];
    const button=html('button',{class:'trip-cover','data-trip':trip.id,onClick:()=>tripDetail(trip)});
    if(cover)button.append(html('img',{src:cover,alt:''}));else button.append(html('div',{class:'trip-cover-blank'},icon('book')));
    button.append(html('div',{},html('h4',{text:trip.title}),html('p',{text:`${new Set(notes.map(n=>n.cityCode||n.place)).size} 座城市 · ${notes.length} 页故事${trip.demo?' · 示例':''}`}),html('small',{text:notes.length?`${noteDate(notes[0])} — ${noteDate(notes.at(-1))}`:'还没有故事，下一程从这里开始'})));sheet.append(button);
  }
  if(!state.trips.length)sheet.append(html('p',{class:'empty-hint',text:'可以先写一页，再归入旅行册。不必在出发前安排好所有事情。'}));
  const loose=chronological(state.records.filter(r=>!r.tripId)).reverse();sheet.append(html('h4',{class:'section-title',text:`随手记下的日子 · ${loose.length}`}));
  for(const note of loose)sheet.append(memoryCard(note,()=>recordDetail(note,booksHome)));
}
function editTrip(existing=null){
  const sheet=openSheet(existing?'整理这本旅行册':'新建旅行册','一场旅行可以经过很多座城市');state.sheet='editor';
  const title=html('input',{class:'text-field',maxlength:80,value:existing?.title||'',placeholder:'例如：浙江秋游','aria-label':'旅行册名称'}),description=html('textarea',{class:'text-field',maxlength:1000,value:existing?.description||'',placeholder:'写一小段序言…','aria-label':'旅行册序言'}),error=html('p',{class:'form-error',role:'alert'});
  sheet.append(field('旅行册名称',title),field('序言（可选）',description),error);
  const save=action('收好这本旅行册',async()=>{if(!title.value.trim()){error.textContent='请给旅行册起一个名字。';return;}save.disabled=true;try{const trip={id:existing?.id||crypto.randomUUID(),title:title.value.trim(),description:description.value.trim(),updatedAt:Date.now(),demo:false};await transaction('readwrite',s=>s.put(trip),'trips');await loadRecords();tripDetail(trip);}catch(e){error.textContent=e.message;save.disabled=false;}},'primary-button');sheet.append(save);
  if(existing)sheet.append(action('移除旅行册，保留所有手账',async()=>{if(!confirm('移除这本旅行册？所有手账会回到随手记录，地图足迹和照片都保留。'))return;try{await atomicWrite(['records','trips'],tx=>{tx.objectStore('trips').delete(existing.id);state.records.filter(r=>r.tripId===existing.id).forEach(r=>tx.objectStore('records').put({...r,tripId:null}));});await loadRecords();renderScene();booksHome();}catch(e){toast(e.message);}},'text-button danger'));
}
function tripDetail(trip){
  const notes=chronological(state.records.filter(r=>r.tripId===trip.id)),sheet=openSheet(trip.title,`${new Set(notes.map(r=>r.cityCode||r.place)).size} 座城市 · ${notes.length} 页故事`);sheet.classList.add('reading-sheet');
  sheet.append(html('div',{class:'record-actions'},action('全部旅行册',booksHome,'text-button'),action('整理旅行册',()=>editTrip(trip),'text-button')));
  if(trip.description)sheet.append(html('p',{class:'trip-preface',text:trip.description}));
  if(!notes.length)sheet.append(html('p',{class:'empty-hint',text:'在地图上选一座城市留下一页，保存时选择这本旅行册。'}));
  let lastDay=null;const firstDay=notes[0]?.date;
  for(const note of notes){if(note.date!==lastDay){const day=1+Math.round((Date.parse(noteDate(note))-Date.parse(firstDay))/86400000);sheet.append(html('h4',{class:'timeline-day',text:`第 ${day} 天 · ${note.date}`}));lastDay=note.date;}const row=html('div',{class:'timeline-entry'},html('span',{class:'timeline-city',text:note.place}),memoryCard(note,()=>recordDetail(note,()=>tripDetail(trip))));sheet.append(row);}
  sheet.append(action('再记一页',()=>{editRecord(null);const select=$('[aria-label="所属旅行册"]');select.value=trip.id;$('.writing-details').open=true;},'secondary-button'));
}
function decorateCity(city){
  const notes=recordsFor(city.code),saved=styleFor(city);
  const draft=saved?structuredClone(saved):{id:city.code,color:PALETTE[0],fillMode:'color',photos:[],aspects:[],crops:[],coverIndex:0};draft.demo=false;
  const sheet=openSheet('装饰'+shortName(city.name),'颜色、封面和照片裁切会独立保存');state.sheet='editor';
  const preview=svg('svg',{class:'city-decoration-preview',role:'img','aria-label':'城市图块裁切预览'}),b=city.shape.bounds;preview.setAttribute('viewBox',`${b.left} ${b.top} ${b.right-b.left} ${b.bottom-b.top}`);sheet.append(preview);
  const mode=html('div',{class:'choice-row'}),colorMode=action('纯色图块',()=>{draft.fillMode='color';draw();},''),photoMode=action('照片拼贴',()=>{if(!draft.photos.length){toast('先从下方选择照片');return;}draft.fillMode='photos';draw();},'');mode.append(colorMode,photoMode);sheet.append(mode);
  const palette=html('div',{class:'palette'});for(const color of PALETTE)palette.append(html('button',{style:`--swatch:${color}`,'aria-label':`城市颜色 ${color}`,onClick:()=>{draft.color=color;draft.fillMode='color';colorInput.value=color;draw();}}));
  const colorInput=html('input',{type:'color',value:draft.color,'aria-label':'自定义城市颜色',onInput:e=>{draft.color=e.target.value;draw();}});sheet.append(palette,field('自选颜色',colorInput));
  const selection=html('div',{class:'decoration-selection'}),available=html('div',{class:'photo-choices'});let selected=0;
  sheet.append(html('h4',{class:'section-title',text:'拼进地图里的照片 · 最多四张'}),selection,available);
  const allPhotos=[...new Set(notes.flatMap(n=>n.photos))];
  for(const src of allPhotos)available.append(html('button',{'aria-label':'将手账照片加入城市拼贴',onClick:async()=>{if(draft.photos.includes(src)){toast('这张照片已在拼贴中');return;}await add([src]);}},html('img',{src,alt:'可选的手账照片'}),icon('plus')));
  const upload=action('从电脑添加装饰照片',()=>{const input=$('#photo-input');input.value='';input.onchange=async()=>{if(!input.files.length)return;upload.disabled=true;try{await add(await Promise.all([...input.files].slice(0,4).map(readPhoto)));}catch(e){toast(e.message);}finally{upload.disabled=false;}};input.click();});sheet.append(upload);
  const controls=html('div',{class:'crop-controls'}),caption=html('p',{class:'empty-hint'});controls.append(caption);
  const sliders={};for(const[key,label,min,max,step]of [['x','水平位置',0,100,1],['y','垂直位置',0,100,1],['zoom','照片放大',1,3,.05]]){const input=html('input',{type:'range',min,max,step,'aria-label':label,onInput:e=>{draft.crops[selected][key]=Number(e.target.value);draw();}});sliders[key]=input;controls.append(field(label,input));}sheet.append(controls);
  const cover=html('select',{class:'text-field','aria-label':'城市封面照片',onChange:e=>{draft.coverIndex=Number(e.target.value);}});sheet.append(field('城市档案封面',cover));
  const save=action('保存城市装饰',async()=>{save.disabled=true;try{await transaction('readwrite',s=>s.put(draft),'cityStyles');await loadRecords();renderScene();cityCard(city);toast(notes.some(n=>n.visited)?'城市装饰已收好，新增手账不会替换它':'装饰已收好，第一次记录去过后会点亮地图');}catch(e){toast('未能保存：'+e.message);save.disabled=false;}},'primary-button');sheet.append(save);
  async function add(photos){const room=4-draft.photos.length;if(!room){toast('最多四张，先移除一张再添加');return;}const additions=photos.slice(0,room);draft.photos.push(...additions);draft.aspects.push(...await Promise.all(additions.map(localPhotoAspect)));draft.crops.push(...additions.map(()=>({x:50,y:50,zoom:1})));draft.fillMode='photos';selected=draft.photos.length-1;draw();}
  function draw(){
    preview.replaceChildren();const defs=svg('defs'),g=svg('g');preview.append(defs,svg('path',{d:city.shape.d,fill:draft.color,'fill-rule':'evenodd',stroke:'#6f8871','stroke-width':1,'vector-effect':'non-scaling-stroke'}),g);if(draft.fillMode==='photos')drawCityPhotos(city,draft,defs,g,'decoration');
    colorMode.classList.toggle('selected',draft.fillMode==='color');photoMode.classList.toggle('selected',draft.fillMode==='photos');controls.hidden=!draft.photos.length;cover.disabled=!draft.photos.length;
    selection.replaceChildren(...draft.photos.map((src,i)=>html('div',{class:i===selected?'selected':''},html('button',{'aria-label':`调整第 ${i+1} 张拼贴照片`,onClick:()=>{selected=i;draw();}},html('img',{src,alt:`拼贴照片 ${i+1}`})),html('button',{class:'remove-photo','aria-label':`移除第 ${i+1} 张拼贴照片`,onClick:()=>{draft.photos.splice(i,1);draft.aspects.splice(i,1);draft.crops.splice(i,1);selected=Math.max(0,Math.min(selected,draft.photos.length-1));draft.coverIndex=Math.min(draft.coverIndex,Math.max(0,draft.photos.length-1));if(!draft.photos.length)draft.fillMode='color';draw();}},icon('close')))));
    if(draft.photos.length){draft.crops[selected]??={x:50,y:50,zoom:1};caption.textContent=`调整第 ${selected+1} 张照片 · 仅改变地图中的取景`;for(const k of ['x','y','zoom'])sliders[k].value=draft.crops[selected][k];}
    cover.replaceChildren(...draft.photos.map((_,i)=>html('option',{value:i,text:`第 ${i+1} 张拼贴照片`})));cover.value=String(draft.coverIndex||0);
  }
  draw();
}
