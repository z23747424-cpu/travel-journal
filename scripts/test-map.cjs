// Browser-engine checks of bundled map code. Public map servers are never contacted.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..');
const target=process.env.MAP_TEST_URL||'http://127.0.0.1:8765/';
const output=path.join(root,'.tools','map-qa');
fs.mkdirSync(output,{recursive:true});
const tile='<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256"><rect width="256" height="256" fill="#e7ece5"/><path d="M0 90H256M90 0V256M0 200H256M200 0V256" stroke="#d1dcd0"/><text x="12" y="36" fill="#59655e" font-size="13">LOCAL TEST TILE</text></svg>';
(async()=>{
  const browser=await chromium.launch({headless:true,...(process.env.MAP_TEST_BROWSER?{executablePath:process.env.MAP_TEST_BROWSER}:{})});
  const report=[];
  try {
    for(const viewport of [{width:390,height:720},{width:1024,height:650}]) {
      const context=await browser.newContext({viewport});
      const page=await context.newPage();
      const errors=[];page.on('pageerror',error=>errors.push(error.message));
      let intercepted=0;
      await page.route('https://tile.openstreetmap.org/**',route=>{intercepted++;return route.fulfill({status:200,contentType:'image/svg+xml',body:tile});});
      await page.addInitScript(()=>{window.mapEvents=[];window.AndroidMap={ready(){window.mapEvents.push({type:'ready'});},selectPoint(lat,lon){window.mapEvents.push({type:'point',lat,lon});},openNotes(ids){window.mapEvents.push({type:'notes',ids:JSON.parse(ids)});},cameraChanged(lat,lon,zoom){window.mapEvents.push({type:'camera',lat,lon,zoom});}};});
      await page.goto(target);
      await page.waitForFunction(()=>window.TravelMap&&window.mapEvents.some(e=>e.type==='ready'));
      await page.evaluate(()=>window.TravelMap.setNotes([
        {id:'one',title:'<img src=x onerror="window.pwned=1">',place:'西湖',latitude:30.2489,longitude:120.1481},
        {id:'two',title:'第二天',place:'西湖',latitude:30.2489,longitude:120.1481},
        {id:'sydney',title:'世界另一站',place:'悉尼',latitude:-33.8688,longitude:151.2093}
      ]));
      assert.ok(await page.locator('.travel-pin span').first().evaluate(el=>{const r=el.getBoundingClientRect();return document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===el;}),'note count must be above the pin drawing');
      await page.locator('.leaflet-marker-icon[title="西湖 · 2篇笔记"]').click();
      const selected=await page.evaluate(()=>window.mapEvents.filter(e=>e.type==='notes').at(-1));
      assert.deepEqual(selected.ids,['one','two']);
      await page.locator('#map').click({position:{x:90,y:300}});
      const point=await page.evaluate(()=>window.mapEvents.filter(e=>e.type==='point').at(-1));
      assert.ok(Number.isFinite(point.lat)&&Number.isFinite(point.lon));
      const before=await page.evaluate(()=>window.TravelMap.getCenter().zoom);
      await page.locator('.leaflet-control-zoom-in').click();
      await page.waitForFunction(z=>window.TravelMap.getCenter().zoom>z,before);
      await page.waitForFunction(()=>!document.querySelector('.leaflet-zoom-anim'));
      await page.evaluate(()=>window.TravelMap.configure({latitude:-33.8688,longitude:151.2093,zoom:12}));
      await page.locator('.leaflet-marker-icon[title="悉尼 · 1篇笔记"]').click();
      assert.deepEqual(await page.evaluate(()=>window.mapEvents.filter(e=>e.type==='notes').at(-1).ids),['sydney']);
      await page.evaluate(()=>window.TravelMap.chooseCenter());
      const world=await page.evaluate(()=>window.mapEvents.filter(e=>e.type==='point').at(-1));
      assert.ok(world.lat<0&&world.lon>140);
      assert.equal(await page.evaluate(()=>window.pwned),undefined);
      assert.equal(await page.locator('[onerror]').count(),0);
      await page.locator('#china').click();
      await page.screenshot({path:path.join(output,`map-${viewport.width}.png`)});
      assert.equal(errors.length,0,errors.join('\n'));
      report.push({viewport,pointSelection:true,multiNoteMarker:true,zoom:true,worldPoint:true,scriptInjectionRejected:true,fixtureTiles:intercepted,publicTileRequests:0});
      await context.close();
    }
    const page=await browser.newPage({viewport:{width:390,height:720}});
    await page.route('https://tile.openstreetmap.org/**',route=>route.abort());
    await page.goto(target);
    await page.locator('#street').click();
    await page.locator('#tile-status').waitFor({state:'visible'});
    await page.locator('#retry').click();
    report.push({tileFailureRecoveryControl:true,publicTileRequests:0});
    fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify({status:'PASS',checks:report,scope:'Bundled Leaflet component with local test tiles; no Android runtime or real tile rendering claimed.'},null,2));
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
