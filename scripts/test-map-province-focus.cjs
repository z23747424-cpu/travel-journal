// Regression: focus a province without a map-sized filter or SVG focus outline.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const output=path.join(os.tmpdir(),'lvye-province-focus-qa');fs.mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.MAP_TEST_BROWSER||'C:/Users/ASUS/AppData/Local/ms-playwright/chromium-1228/chrome-win64/chrome.exe'});
 const errors=[];
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1.25});page.on('pageerror',e=>errors.push(e.message));
  await page.goto(process.env.TRAVEL_PREVIEW_URL||'http://127.0.0.1:8766/');await page.waitForFunction(()=>window.TravelPreview?.getState().cities===391);
  for(const code of ['630000','330000','820000']){
   const label=page.locator(`#label-layer [data-province="${code}"]`),b=await label.boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await page.waitForFunction(code=>TravelPreview.getState().province===code,code);await page.waitForTimeout(500);
   const background=await page.locator('#context-layer').evaluate(e=>({filter:getComputedStyle(e).filter,opacity:Number(getComputedStyle(e).opacity)}));
   assert.equal(background.filter,'none','Surrounding provinces should be subdued without a whole-map SVG filter.');
   assert.ok(background.opacity>=.35&&background.opacity<=.6,'Context contours should remain faintly visible.');
   const selected=await page.locator('.active-province').evaluate(e=>({fill:getComputedStyle(e).fill,opacity:getComputedStyle(e).opacity,filter:getComputedStyle(e).filter}));assert.equal(selected.opacity,'1');assert.equal(selected.filter,'none');
   assert.notEqual(selected.fill,'rgb(250, 248, 242)','Selected province should have a gentle, visible surface tint.');
   assert.equal(await page.locator('#context-layer [data-province="'+code+'"]').count(),0,'Do not draw the selected province again in the background.');
   await page.locator('#device-frame').screenshot({path:path.join(output,`province-${code}.png`)});
   if(code==='630000')await page.locator('#map-stage').screenshot({path:path.join(output,'qinghai-map.png')});
   // Explicit keyboard focus must stay thin in screen pixels at any map zoom.
   await page.keyboard.press('Tab');const neighbor=page.locator('#context-layer [data-province]').first();await neighbor.focus();
   assert.equal(await neighbor.evaluate(e=>getComputedStyle(e).outlineStyle),'none');
   for(let i=0;i<3;i++)await page.locator('#zoom-in').click();await neighbor.focus();assert.equal(await neighbor.evaluate(e=>getComputedStyle(e).outlineStyle),'none');
   await page.locator('#back-button').click();await page.waitForFunction(()=>TravelPreview.getState().province===null);await page.waitForTimeout(480);
   await page.waitForFunction(()=>getComputedStyle(document.querySelector('#context-layer')).opacity==='1');assert.equal(await page.locator('.province-name').count(),34);
  }
  await page.setViewportSize({width:390,height:844});const label=page.locator('#label-layer [data-province="630000"]'),b=await label.boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2);await page.waitForFunction(()=>TravelPreview.getState().province==='630000');await page.waitForTimeout(500);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:path.join(output,'narrow-qinghai.png'),fullPage:true});
  assert.deepEqual(errors,[]);console.log(JSON.stringify({status:'PASS',provinces:['青海','浙江','澳门'],subduedContext:true,clearSelectedProvince:true,noScaledFocusOutline:true,returnToNational:true,narrowNoOverflow:true,consoleErrors:errors,evidence:output},null,2));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
