import {put,remove,writeStores,validDate} from './data.js';
import {blankPlan,blankDay,blankActivity,validPlan} from './plans-data.js';
import {shortName} from './map.js';

export function createPlanner(ui){
  const {state,h,button,field,page,topbar,empty,go,toast,openDialog,closeDialog,reload}=ui;
  let tab='discover',results=null,controller=null,activeRequest=0,saveTimer,editing=null;
  const preferences={needs:'',origin:'',duration:3,budget:''};
  const clone=value=>structuredClone(value);
  function getConfig(){try{return JSON.parse(sessionStorage.getItem('lvye-travel-ai')||'{}');}catch{return {};}}
  function ready(){const c=getConfig();return c.endpoint&&c.model&&c.apiKey&&c.searchKey&&c.consent;}
  function configure(){
    const c=getConfig();openDialog('接入账本精灵',box=>{
      const endpoint=h('input',{type:'url',placeholder:'https://你的服务商/v1',value:c.endpoint||'',required:true,'aria-label':'AI 接口地址'});
      const model=h('input',{placeholder:'服务商提供的模型名称',value:c.model||'',required:true,'aria-label':'AI 模型名称'});
      const key=h('input',{type:'password',autocomplete:'off',value:c.apiKey||'','aria-label':'AI API Key'});
      const search=h('input',{type:'password',autocomplete:'off',value:c.searchKey||'','aria-label':'Tavily API Key'});
      const consent=h('input',{type:'checkbox','aria-label':'同意发送旅行需求'});consent.checked=!!c.consent;
      const error=h('p',{class:'form-error',role:'alert'});
      box.append(h('p',{class:'quiet',text:'使用你自己的兼容 Chat Completions 的 AI 和 Tavily 搜索服务。精灵先查网页，再整理目的地和行程。'}),field('AI 接口地址',endpoint),field('模型名称',model),field('AI API Key',key),field('Tavily API Key',search),h('label',{class:'consent-row'},consent,h('span',{text:'同意把我填写的旅行需求与查询发给这些服务，可能产生服务商费用。不会发送手账、照片或其他计划。'})),h('p',{class:'settings-text',text:'配置仅在当前标签页会话保留；关闭标签页后需要重新填写。密钥不会写入日记备份或服务器日志。'}),error,button('保存接入设置',()=>{
        if(!endpoint.value.trim().startsWith('https://')||![model,key,search].every(i=>i.value.trim())||!consent.checked){error.textContent='请填写 HTTPS 接口、模型和两项密钥，并确认发送需求。';return;}
        try{sessionStorage.setItem('lvye-travel-ai',JSON.stringify({endpoint:endpoint.value.trim(),model:model.value.trim(),apiKey:key.value.trim(),searchKey:search.value.trim(),consent:true}));closeDialog();toast('接入设置已保留，回到灵感即可开始');}catch{error.textContent='当前浏览器无法保存会话配置，请检查存储权限。';}
      },'primary','check'),button('清除接入设置',()=>{sessionStorage.removeItem('lvye-travel-ai');closeDialog();toast('当前会话的密钥已清除');},'text-button danger'));
    });
  }
  function cancel(){activeRequest++;controller?.abort();controller=null;state.plannerBusy=false;}
  async function request(action,extra={}){
    if(!ready()){configure();return null;}
    if(controller)return null;
    const id=++activeRequest;controller=new AbortController();state.plannerBusy=true;
    const timeout=setTimeout(()=>controller?.abort(),95000);
    try{
      const response=await fetch('/api/travel',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,...preferences,...extra,config:getConfig()}),signal:controller.signal});
      const result=await response.json();if(id!==activeRequest)return null;
      if(!response.ok)throw Error(result.error||'暂时无法生成，请稍后重试。');return result;
    }catch(e){if(id===activeRequest)toast(e.name==='AbortError'?'本次等待已结束，可以重试或手写计划':e.message);return null;}
    finally{clearTimeout(timeout);if(id===activeRequest){controller=null;state.plannerBusy=false;}}
  }
  function sourceLinks(sources,ids=null){const list=h('ul',{class:'source-links'});for(const s of sources.filter(s=>!ids||ids.includes(s.id))){let u;try{u=new URL(s.url);}catch{continue;}if(!['http:','https:'].includes(u.protocol)||u.username||u.password)continue;list.append(h('li',{},h('a',{href:u.href,target:'_blank',rel:'noopener noreferrer',text:s.title||u.hostname})));}return list;}
  function render(){
    const el=page({scroll:true});el.append(topbar(),h('header',{class:'page-heading inspiration-heading'},h('span',{class:'decoration hills','aria-hidden':'true'}),h('h1',{class:'page-title',text:'下一程，慢慢计划'}),h('p',{text:'把一点向往，写成出发的日子。'})));
    const body=h('div',{class:'section-body planner-body'}),tabs=h('div',{class:'planner-tabs',role:'group','aria-label':'灵感内容'});
    for(const [value,label]of [['discover','找下一站'],['plans','我的计划'],['wishes','想去清单']])tabs.append(button(label,()=>{cancel();tab=value;render();},'chip'+(tab===value?' selected':'')));
    body.append(tabs);el.append(body);
    const draft=state.settings.find(s=>s.id==='plan-draft')?.value;
    if(draft)body.append(button('继续未完成的旅行计划',()=>open(draft),'draft-banner','pen'));
    if(tab==='plans'){
      body.append(button('自己写计划',()=>open(),'primary','plus'));
      if(!state.plans.length)body.append(empty('给下一程留一页','可以先请精灵找目的地，也可以直接写下自己的安排。'));
      for(const plan of state.plans){const row=button('',()=>open(plan),'plan-list-item');row.append(h('div',{},h('h2',{text:plan.title||plan.destination}),h('p',{text:[plan.destination,plan.startDate||'日期待定',plan.days.length+' 天'].join(' · ')}),h('small',{text:plan.summary?.slice(0,90)||'继续完善这份出游安排'})),ui.icon('back'));body.append(row);}return;
    }
    if(tab==='wishes'){
      const wishes=state.records.filter(r=>!r.visited);for(const r of wishes)body.append(ui.resultRow(r));
      if(!wishes.length)body.append(empty('先把向往收好','地图上的小旗，和每一次出发前的期待，都在这里。'));
      body.append(button('找一个想去的省份',ui.provinceSearch,'primary','flag'));return;
    }
    const needs=h('textarea',{class:'travel-needs',rows:3,maxlength:4000,placeholder:'想看海、少走路，预算 2000 元。哪里适合放空几天？','aria-label':'出游期待',value:preferences.needs});needs.oninput=()=>preferences.needs=needs.value;
    const origin=h('input',{maxlength:120,placeholder:'比如：杭州',value:preferences.origin,'aria-label':'出发地'});origin.oninput=()=>preferences.origin=origin.value;
    const duration=h('input',{type:'number',min:1,max:30,value:preferences.duration,'aria-label':'旅行天数'});duration.oninput=()=>preferences.duration=Number(duration.value);
    const budget=h('input',{maxlength:160,placeholder:'比如：每人 2000 元',value:preferences.budget,'aria-label':'旅行预算'});budget.oninput=()=>preferences.budget=budget.value;
    const options=h('details',{class:'extra-fields'},h('summary',{text:'出发地、天数与预算'}),h('div',{class:'plan-field-grid'},field('从哪里出发',origin),field('旅行天数',duration)),field('预算（选填）',budget));
    const output=h('div',{class:'destination-results'}),status=h('p',{class:'ai-status',role:'status'});
    const search=button('让精灵找下一站',recommend,'primary','compass'),stop=button('停止等待',()=>{cancel();updateBusy();status.textContent='已停止等待，原有计划保留。';},'text-button');stop.hidden=true;
    body.append(field('这次想怎样旅行？',needs),options,h('div',{class:'ai-actions'},search,stop),status,output,button('已经决定了，自己写计划',()=>open(),'setting-row','pen'),button('接入 / 修改 AI',configure,'text-button','settings'),h('p',{class:'settings-text',text:'推荐会先联网查找，再由你接入的 AI 整理。行程是可编辑草案，出发前请核对营业、交通与预订情况。'}));
    function updateBusy(){search.disabled=state.plannerBusy;stop.hidden=!state.plannerBusy;search.querySelector('span').textContent=state.plannerBusy?'精灵正在查阅网页…':'让精灵找下一站';}
    async function recommend(){
      if(!needs.value.trim()){needs.focus();toast('先写一点对这次旅行的期待');return;}
      if(!Number.isInteger(preferences.duration)||preferences.duration<1||preferences.duration>30){toast('旅行天数请填写 1 至 30 的整数');duration.focus();return;}
      const pending=request('recommend');updateBusy();status.textContent=state.plannerBusy?'正在联网寻找有依据的目的地…':'';
      const result=await pending;if(state.screen!=='inspiration'||tab!=='discover')return;updateBusy();status.textContent='';if(result){results=result;drawResults();}
    }
    function drawResults(){output.replaceChildren();if(!results)return;output.append(h('h2',{text:'把心动落在这里'}));for(const candidate of results.destinations){const item=h('article',{class:'destination-item'},h('h3',{text:candidate.name}),h('p',{class:'metadata',text:candidate.region}),h('p',{text:candidate.reason}),candidate.caution?h('p',{class:'quiet',text:candidate.caution}):null,sourceLinks(results.sources,candidate.sourceIds));
      const create=button('就去这里，生成行程',async()=>{if(state.plannerBusy)return;const pending=request('plan',{destination:candidate.name});updateBusy();create.disabled=true;status.textContent=state.plannerBusy?'正在核对资料，编排逐日草案…':'';const result=await pending;if(state.screen!=='inspiration'||tab!=='discover')return;create.disabled=false;updateBusy();status.textContent='';if(result){const plan=blankPlan();Object.assign(plan,result.plan,{sources:result.sources,aiGenerated:true,origin:preferences.origin,budget:preferences.budget});plan.days=plan.days.map(d=>({...d,id:crypto.randomUUID(),activities:d.activities.map(a=>({...a,id:crypto.randomUUID()}))}));open(plan);}},'secondary','calendar');item.append(create);output.append(item);}}
    drawResults();updateBusy();
  }
  function open(initial=null){
    const existingDraft=state.settings.find(s=>s.id==='plan-draft')?.value;
    if(existingDraft&&existingDraft.id!==initial?.id){openDialog('还有一份计划草稿',box=>box.append(h('p',{class:'quiet',text:'先继续或放弃这份草稿，之前的修改都还在。'}),button('继续计划草稿',()=>{state.planDraft=clone(existingDraft);closeDialog();go('plan',null);},'primary','pen')));return;}
    state.planDraft=clone(existingDraft?.id===initial?.id?existingDraft:initial||blankPlan());go('plan',null);
  }
  function renderEditor(){
    editing=clone(state.planDraft||state.settings.find(s=>s.id==='plan-draft')?.value||blankPlan());
    const plan=editing;let busy=false;
    const el=page(),save=button('保存计划',savePlan,'save-button');el.append(topbar('旅行计划',{text:'收起',fn:async()=>{if(await persist())go('inspiration',null,{replace:true});}},save));
    const body=h('div',{class:'plan-editor page-scroll'}),error=h('p',{class:'form-error',role:'alert'});
    const input=(label,key,attrs={})=>{const control=h(attrs.area?'textarea':'input',{'aria-label':label,value:plan[key]||'',maxlength:attrs.area?20000:200,...attrs});control.addEventListener('input',()=>{plan[key]=control.value;queue();});return field(label,control);};
    body.append(h('p',{class:'plan-draft-note',text:plan.aiGenerated?'精灵整理的初版 · 每一天都可以按你的节奏改写':'自己的旅行计划 · 从一个想去的地方开始'}),input('给这次旅行起个名字','title',{placeholder:'比如：去海边偷个闲'}),input('目的地','destination',{maxlength:160,placeholder:'城市、地区或国家都可以'}),h('div',{class:'plan-field-grid'},input('出发地（选填）','origin',{maxlength:120}),input('出发日期（选填）','startDate',{type:'date'})),input('预算（选填）','budget',{maxlength:160}),input('这趟旅行的想法','summary',{area:true,rows:2,placeholder:'慢一点，留一些空白给沿途的风。'}));
    const select=h('select',{'aria-label':'在地图上插旗（选填）'},h('option',{value:'',text:'不关联省份 / 海外目的地'}),state.provinces.map(p=>h('option',{value:p.code,text:shortName(p.name)})));select.value=plan.provinceCode||'';select.onchange=()=>{plan.provinceCode=select.value||null;queue();};
    const status=h('select',{'aria-label':'计划状态'},h('option',{value:'planned',text:'准备出发 · 在关联省份插旗'}),h('option',{value:'draft',text:'先收藏想法 · 不插旗'}));status.value=plan.status;status.onchange=()=>{plan.status=status.value;queue();};
    body.append(h('details',{class:'extra-fields'},h('summary',{text:'把计划标在地图上（选填）'}),field('省份',select),field('计划状态',status),h('p',{class:'quiet',text:'只有你选择的省份会插旗；计划不会自动把地图标成「去过」。'})));
    const days=h('div',{class:'itinerary'});body.append(days,button('再添一天',()=>{if(plan.days.length>=30){toast('一份计划最多 30 天');return;}plan.days.push(blankDay());drawDays();queue();},'secondary add-day','plus'),input('出发前备忘','notes',{area:true,rows:3,placeholder:'车票、住宿、要带的东西…'}));
    if(plan.sources.length)body.append(h('details',{class:'extra-fields'},h('summary',{text:'查看这份草案参考的网页'}),sourceLinks(plan.sources)));
    body.append(error,h('p',{class:'settings-text',text:'修改会自动暂存为草稿，点击「保存计划」后收进我的计划。'}));
    const actions=h('div',{class:'plan-footer'},button('放弃未保存的修改',async()=>{if(!confirm('放弃当前计划草稿？已保存的版本会保留。'))return;clearTimeout(saveTimer);await remove('settings','plan-draft');editing=null;await reload();tab='plans';go('inspiration',null,{replace:true});},'text-button danger'));
    if(state.plans.some(p=>p.id===plan.id))actions.append(button('删除这份计划',async()=>{if(!confirm('删除这份旅行计划？手账和其他省份设计会保留。'))return;clearTimeout(saveTimer);await writeStores(['plans','settings'],tx=>{tx.objectStore('plans').delete(plan.id);tx.objectStore('settings').delete('plan-draft');});editing=null;await reload();tab='plans';go('inspiration',null,{replace:true});toast('这份计划已移除');},'text-button danger'));
    body.append(actions);el.append(body);drawDays();
    async function savePlan(){
      if(busy)return;error.textContent='';
      if(!plan.destination.trim()){error.textContent='先写下这趟旅行的目的地。';body.querySelector('[aria-label="目的地"]').focus();return;}
      if(plan.startDate&&!validDate(plan.startDate)){error.textContent='请选择有效的出发日期。';return;}
      plan.destination=plan.destination.trim();plan.updatedAt=Date.now();
      if(!validPlan(plan,new Set(state.provinces.map(p=>p.code)))){error.textContent='计划内容不完整，请检查日期、每日安排和来源。';return;}
      busy=true;state.plannerBusy=true;save.disabled=true;clearTimeout(saveTimer);
      try{await writeStores(['plans','settings'],tx=>{tx.objectStore('plans').put(clone(plan));tx.objectStore('settings').delete('plan-draft');});editing=null;await reload();tab='plans';go('inspiration',null,{replace:true});toast('下一程的安排，已经收好');}catch(e){error.textContent='未能保存，草稿还在：'+e.message;}finally{busy=false;state.plannerBusy=false;save.disabled=false;}
    }
    function drawDays(){days.replaceChildren();plan.days.forEach((day,index)=>{
      const title=h('input',{'aria-label':`第${index+1}天主题`,maxlength:200,placeholder:'这一天，想怎么度过？',value:day.title});title.oninput=()=>{day.title=title.value;queue();};
      const section=h('section',{class:'itinerary-day','data-day':index},h('header',{class:'day-heading'},h('h2',{text:'第 '+(index+1)+' 天'}),h('div',{class:'order-actions'},orderButton('这天提前',index===0,()=>move(plan.days,index,-1)),orderButton('这天延后',index===plan.days.length-1,()=>move(plan.days,index,1)),button('删除这天',()=>{if(plan.days.length===1){toast('至少留一天，慢慢计划');return;}if(!confirm('移除这一天及当天安排？'))return;plan.days.splice(index,1);drawDays();queue();},'text-button danger'))),title);
      day.activities.forEach((activity,i)=>{
        const block=h('div',{class:'plan-activity'}),time=h('input',{'aria-label':`第${index+1}天安排${i+1}时间`,maxlength:200,placeholder:'09:00',value:activity.time}),place=h('input',{'aria-label':`第${index+1}天安排${i+1}地点`,maxlength:200,placeholder:'想去的地点',value:activity.place}),description=h('textarea',{'aria-label':`第${index+1}天安排${i+1}内容`,maxlength:20000,rows:2,placeholder:'做些什么，或留一点自由时间。',value:activity.description}),transport=h('input',{'aria-label':`第${index+1}天安排${i+1}交通`,maxlength:200,placeholder:'怎么过去（选填）',value:activity.transport});
        for(const [key,control]of [['time',time],['place',place],['description',description],['transport',transport]])control.oninput=()=>{activity[key]=control.value;queue();};
        block.append(h('div',{class:'activity-location'},time,place),description,transport,h('div',{class:'order-actions'},orderButton('这项提前',i===0,()=>move(day.activities,i,-1)),orderButton('这项延后',i===day.activities.length-1,()=>move(day.activities,i,1)),button('移除安排',()=>{day.activities.splice(i,1);drawDays();queue();},'text-button danger')));
        if(activity.sourceIds.length)block.append(sourceLinks(plan.sources,activity.sourceIds));section.append(block);
      });
      section.append(button('添加当天安排',()=>{if(day.activities.length>=20){toast('每天最多 20 项，留一点空白也很好');return;}day.activities.push(blankActivity());drawDays();queue();},'text-button','plus'));days.append(section);
    });}
    function move(array,index,delta){[array[index],array[index+delta]]=[array[index+delta],array[index]];drawDays();queue();}
    function orderButton(label,disabled,fn){const b=button(label,fn,'text-button');b.disabled=disabled;return b;}
  }
  function queue(){clearTimeout(saveTimer);state.planDraft=clone(editing);saveTimer=setTimeout(()=>persist(),300);}
  async function persist(){clearTimeout(saveTimer);if(!editing)return true;try{state.planDraft=clone(editing);await put('settings',{id:'plan-draft',value:clone(editing)});const entry={id:'plan-draft',value:clone(editing)};state.settings=state.settings.filter(s=>s.id!=='plan-draft').concat(entry);return true;}catch(e){toast('计划草稿暂存失败：'+e.message);return false;}}
  return {render,renderEditor,open,configure,cancel,persist};
}
