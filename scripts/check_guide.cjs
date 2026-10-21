const fs = require('fs');
const path = require('path');
const {pathToFileURL} = require('url');
const {chromium} = require('playwright');
(async()=>{
  const root=path.resolve(__dirname,'..');
  const out=path.join(root,'build','guide-qa');fs.mkdirSync(out,{recursive:true});
  const browser=await chromium.launch({headless:true,...(process.env.ECHO_BROWSER_EXECUTABLE?{executablePath:process.env.ECHO_BROWSER_EXECUTABLE}:{})});
  const results=[];
  for(const [name,width,height,colorScheme] of [['wide',1440,1000,'light'],['narrow',390,844,'light'],['dark',1440,1000,'dark']]){
    const page=await browser.newPage({viewport:{width,height},colorScheme});
    const errors=[];page.on('pageerror',e=>errors.push(String(e)));
    await page.goto(pathToFileURL(path.join(root,'artifacts','echo-guide.html')).href);
    await page.locator('img').evaluateAll(imgs=>Promise.all(imgs.map(i=>i.decode())));
    const state=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,images:[...document.images].map(i=>({src:i.getAttribute('src'),ok:i.complete&&i.naturalWidth>0})),links:[...document.querySelectorAll('a[href]')].map(a=>a.getAttribute('href'))}));
    if(state.scroll>width) throw new Error(name+' document overflow');
    if(state.images.some(i=>!i.ok)) throw new Error('Missing image');
    for(const link of state.links){if(link.startsWith('#')){if(!await page.locator(link).count())throw new Error('Bad anchor '+link);}else if(!fs.existsSync(path.resolve(root,'artifacts',link)))throw new Error('Missing link '+link);}
    await page.screenshot({path:path.join(out,name+'.png'),fullPage:true});
    if(await page.locator('#setup').count())await page.locator('#setup').screenshot({path:path.join(out,'setup-'+name+'.png')});
    results.push({name,...state,errors});await page.close();
  }
  await browser.close();fs.writeFileSync(path.join(out,'checks.json'),JSON.stringify(results,null,2));
  console.log(JSON.stringify(results.map(r=>({name:r.name,width:r.width,scroll:r.scroll,images:r.images,errors:r.errors})),null,2));
})().catch(e=>{console.error(e);process.exit(1)});
