const bounded=(s,n)=>typeof s==='string'&&s.length<=n;
export function blankActivity(){return {id:crypto.randomUUID(),time:'',place:'',description:'',transport:'',sourceIds:[]};}
export function blankDay(){return {id:crypto.randomUUID(),title:'',activities:[blankActivity()]};}
export function blankPlan(){const now=Date.now();return {id:crypto.randomUUID(),title:'',destination:'',origin:'',startDate:'',budget:'',summary:'',notes:'',provinceCode:null,status:'planned',days:[blankDay()],sources:[],aiGenerated:false,createdAt:now,updatedAt:now};}
export function validPlan(p,provinces){
  if(!p||!bounded(p.id,160)||!p.id||!bounded(p.destination,160)||!p.destination.trim()||!['planned','draft'].includes(p.status)||p.provinceCode!=null&&!provinces.has(p.provinceCode))return false;
  for(const k of ['title','origin','startDate','budget','summary','notes'])if(!bounded(p[k]??'',k==='summary'||k==='notes'?20000:200))return false;
  if(p.startDate){const d=new Date(p.startDate+'T12:00:00Z');if(!/^\d{4}-\d{2}-\d{2}$/.test(p.startDate)||isNaN(d)||d.toISOString().slice(0,10)!==p.startDate)return false;}
  if(typeof p.aiGenerated!=='boolean'||!Number.isFinite(p.createdAt)||!Number.isFinite(p.updatedAt)||!Array.isArray(p.days)||!p.days.length||p.days.length>30||!Array.isArray(p.sources)||p.sources.length>30)return false;
  const sourceIds=new Set();
  for(const s of p.sources){try{const u=new URL(s.url);if(!bounded(s.id,160)||sourceIds.has(s.id)||!bounded(s.title,500)||!bounded(s.url,2000)||!['https:','http:'].includes(u.protocol)||u.username||u.password)return false;sourceIds.add(s.id);}catch{return false;}}
  const ids=new Set();
  for(const day of p.days){if(!day||!bounded(day.id,160)||ids.has(day.id)||!bounded(day.title,200)||!Array.isArray(day.activities)||day.activities.length>20)return false;ids.add(day.id);
    for(const a of day.activities){if(!a||!bounded(a.id,160)||ids.has(a.id)||!['time','place','description','transport'].every(k=>bounded(a[k],k==='description'?20000:200))||!Array.isArray(a.sourceIds)||a.sourceIds.length>30||!a.sourceIds.every(i=>typeof i==='string'&&sourceIds.has(i)))return false;ids.add(a.id);}
  }
  return true;
}
