// Verify that the actual bundled China geometry renders without any external map service.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const output=path.resolve(__dirname,'../.tools/map-offline-qa');fs.mkdirSync(output,{recursive:true});
const art='<svg xmlns="http://www.w3.org/2000/svg" width="180" height="160"><path d="M18 130 71 33 127 130Z" fill="#6b8a72" stroke="#f7f5ef" stroke-width="5"/><path d="m97 130 31-65 35 65Z" fill="#a8b69b" stroke="#f7f5ef" stroke-width="5"/><circle cx="140" cy="27" r="17" fill="#d7a36c"/><path d="M24 139q36-15 62 0t67 0" fill="none" stroke="#587b83" stroke-width="7"/></svg>';
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.MAP_TEST_BROWSER?{executablePath:process.env.MAP_TEST_BROWSER}:{})});
 const results=[];
 try{
  for(const viewport of [{width:390,height:520},{width:1024,height:650}]){
   const page=await browser.newPage({viewport});let remoteAttempts=0;const errors=[];
   page.on('pageerror',e=>{errors.push(e.message);console.error('Map runtime error: '+e.message);});
   await page.route('**/*',r=>{
    const url=new URL(r.request().url());
    if(!['127.0.0.1','localhost'].includes(url.hostname)){remoteAttempts++;return r.abort();}
    if(url.pathname.startsWith('/photos/'))return r.fulfill({contentType:'image/svg+xml',body:art});
    return r.continue();
   });
   await page.addInitScript(()=>{window.events=[];window.AndroidMap={ready(){},selectPoint(lat,lon){window.events.push({type:'point',lat,lon});},openNotes(ids){window.events.push({type:'notes',ids:JSON.parse(ids)});},cameraChanged(){},moveNote(id,lat,lon){window.events.push({type:'move',id,lat,lon});}};});
   await page.goto(process.env.MAP_TEST_URL||'http://127.0.0.1:8765/');
   await page.waitForFunction(()=>window.TravelMap);
   assert.equal(remoteAttempts,0,'默认完整地图不得依赖远端瓦片');
   assert.ok(await page.locator('.china-region').count()>=34,'必须实际画出省级地图，而不是空白背景');
   const names=await page.evaluate(()=>window.TRAVEL_CHINA.features.map(f=>f.properties.name));
   for(const name of ['台湾省','海南省','香港特别行政区','澳门特别行政区'])assert.ok(names.includes(name),name);
   await page.evaluate(()=>window.TravelMap.setNotes([
    {id:'art',title:'杭州足迹 · 插画示例',place:'杭州',latitude:30.2489,longitude:120.1481,visited:true,illustration:'/photos/example.png',illustrationSize:32},
    {id:'plan',title:'还没去过的地方',place:'大理',latitude:25.6892,longitude:100.1615,visited:false}
   ]));
   assert.equal(await page.locator('.region-330000').getAttribute('data-visited'),'true');
   assert.equal(await page.locator('.region-530000').getAttribute('data-visited'),'false');
   const bright=await page.locator('.region-330000').evaluate(e=>getComputedStyle(e).fill);
   const dark=await page.locator('.region-530000').evaluate(e=>getComputedStyle(e).fill);
   assert.notEqual(bright,dark);
   await page.evaluate(()=>window.TravelMap.setNotes([{id:'art',title:'杭州',place:'杭州',latitude:30.2489,longitude:120.1481,visited:false,illustration:'/photos/example.png',illustrationSize:32}]));
   assert.equal(await page.locator('.region-330000').getAttribute('data-visited'),'false');
   await page.evaluate(()=>window.TravelMap.setNotes([
    {id:'art',title:'杭州足迹 · 插画示例',place:'杭州',latitude:30.2489,longitude:120.1481,visited:true,illustration:'/photos/example.png',illustrationSize:32},
    {id:'plan',title:'还没去过的地方',place:'大理',latitude:25.6892,longitude:100.1615,visited:false}
   ]));
   await page.locator('.map-artwork img').waitFor({state:'visible'});
   assert.ok(await page.locator('.map-artwork img').evaluate(e=>e.complete&&e.naturalWidth>0));
   assert.ok(await page.locator('.map-artwork img').evaluate(e=>e.getBoundingClientRect().width<=32));
   await page.locator('.map-artwork').click();
   assert.deepEqual(await page.evaluate(()=>window.events.filter(e=>e.type==='notes').at(-1).ids),['art']);
   const marker=await page.locator('.map-artwork').boundingBox();
   await page.mouse.move(marker.x+marker.width/2,marker.y+marker.height/2);await page.mouse.down();
   await page.mouse.move(marker.x+marker.width/2+55,marker.y+marker.height/2+25,{steps:12});await page.mouse.up();
   const moved=await page.evaluate(()=>window.events.filter(e=>e.type==='move').at(-1));
   assert.equal(moved.id,'art');assert.ok(Number.isFinite(moved.lat)&&Number.isFinite(moved.lon));
   const before=await page.evaluate(()=>window.TravelMap.getCenter().zoom);
   await page.locator('.leaflet-control-zoom-in').click();
   await page.waitForFunction(z=>window.TravelMap.getCenter().zoom>z,before);
   await page.waitForFunction(()=>!document.querySelector('.leaflet-zoom-anim'));
   await page.locator('#china').click();
   await page.waitForTimeout(280);
   await page.screenshot({path:path.join(output,`china-${viewport.width}.png`)});
   assert.equal(remoteAttempts,0,'缩放全图也不应请求远端地图');
   await page.locator('#street').click();
   await page.waitForFunction(()=>!document.getElementById('tile-status').hidden);
   assert.ok(remoteAttempts>0);assert.ok(await page.locator('.china-region').count()>=34);
   assert.equal(errors.length,0,errors.join('\n'));
   results.push({viewport,provinceShapes:await page.locator('.china-region').count(),defaultExternalRequests:0,illustrationRendered:true,compactIllustration:true,visitedRegionBright:true,plannedRegionDark:true,turningVisitOffDimsRegion:true,illustrationDrag:true,offlineZoom:true,streetFailureRetainsMap:true});
   await page.close();
  }
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(results,null,2));console.log(JSON.stringify({status:'PASS',results,scope:'Actual bundled geometry and a clearly labeled illustration fixture; no external tiles or Android device used.'},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
