import {node,shortName} from './map.js';
import {put,readPhoto} from './data.js';
export const markerDefaults=()=>({kind:'flag',color:'#AC7850',size:24});
const paths={flag:'M5 22V3c5-4 9 4 14 0v11c-5 4-9-4-14 0',pin:'M20 9c0 6-8 13-8 13S4 15 4 9a8 8 0 1 1 16 0ZM15 9a3 3 0 1 0-6 0 3 3 0 0 0 6 0',star:'m12 2 3 6.5 7 1-5 5 1.2 7-6.2-3.3L5.8 21.5l1.2-7-5-5 7-1Z',heart:'M12 21S2 15 2 8a5 5 0 0 1 10-1A5 5 0 0 1 22 8c0 7-10 13-10 13Z'};
export function markerGlyph(marker,attrs={}){const m={...markerDefaults(),...marker},svg=node('svg',{viewBox:'0 0 24 24',width:m.size,height:m.size,'aria-hidden':'true',...attrs});if(m.kind==='image'&&m.image)svg.append(node('image',{href:m.image,width:24,height:24,preserveAspectRatio:'xMidYMid meet'}));else svg.append(node('path',{d:paths[m.kind]||paths.flag,fill:m.color,stroke:'#FFF9EF','stroke-width':1.2,'stroke-linejoin':'round'}));return svg;}
export function editMarker(p,{h,button,field,openDialog,closeDialog,design,reload,render,toast}){
  const draft=structuredClone(design(p.code)||{id:p.code,color:'#B8CBB3',mode:'color',photos:[],coverIndex:0});draft.demo=false;const marker={...markerDefaults(),...draft.marker};
  openDialog(shortName(p.name)+'的想去标记',box=>{
    const preview=h('div',{class:'marker-sample'}),options=h('div',{class:'marker-options'}),error=h('p',{class:'form-error',role:'alert'});box.append(h('p',{class:'quiet',text:'为这个省选一个小记号。只改变标记，不会修改手账或点亮状态。'}),preview,options);
    for(const [kind,label]of [['flag','小旗'],['pin','定位'],['star','星星'],['heart','爱心']]){const b=button(label,()=>{marker.kind=kind;draw();},'chip');b.dataset.kind=kind;b.prepend(markerGlyph({...marker,kind,size:22}));options.append(b);}
    const color=h('input',{type:'color',value:marker.color,'aria-label':'标记颜色'});color.oninput=()=>{marker.color=color.value;draw();};
    const size=h('input',{type:'range',min:16,max:40,step:1,value:marker.size,'aria-label':'标记大小'}),sizeText=h('output',{text:marker.size+' px'});size.oninput=()=>{marker.size=Number(size.value);sizeText.textContent=marker.size+' px';draw();};
    const upload=h('input',{type:'file',accept:'image/png,image/jpeg,image/webp',hidden:true});upload.onchange=async()=>{if(!upload.files[0])return;try{marker.image=await readPhoto(upload.files[0]);marker.kind='image';error.textContent='';draw();}catch(e){error.textContent=e.message;}};
    box.append(field('颜色',color),field('大小',h('div',{class:'range-field'},size,sizeText)),upload,button('上传自己的图标',()=>{upload.value='';upload.click();},'setting-row','photo'));
    if(marker.image)box.append(button('使用已上传的图片',()=>{marker.kind='image';draw();},'text-button'));
    box.append(h('p',{class:'settings-text',text:'支持 PNG、JPG、WebP，最大 25MB。透明 PNG 适合手绘贴纸；原图会保存在本地并随备份导出。'}),button('恢复默认小旗',()=>{Object.assign(marker,markerDefaults());color.value=marker.color;size.value=marker.size;sizeText.textContent=marker.size+' px';draw();},'text-button'),error,button('保存标记',async()=>{try{draft.marker={...marker};await put('designs',draft);await reload();closeDialog();render();toast('这个小记号，已经收好');}catch(e){error.textContent='保存失败：'+e.message;}},'primary','check'));
    function draw(){preview.replaceChildren(markerGlyph(marker));for(const b of options.children){b.classList.toggle('selected',b.dataset.kind===marker.kind);b.setAttribute('aria-pressed',String(b.dataset.kind===marker.kind));}}
    draw();
  });
}
