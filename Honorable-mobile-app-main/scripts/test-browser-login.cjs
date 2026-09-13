// Existing UI regression tests use the explicit development-only account route.
module.exports=async function testBrowserLogin(page){
 await page.waitForFunction(()=>typeof authState!=='undefined'&&authState!=='restoring');
 if(!await page.evaluate(()=>webConfig.developmentPurchases))throw Error('UI tests require the bundled development account service');
 await page.evaluate(async()=>{await webRequest('/account/dev/auth/token',{email:'ui-regression-'+crypto.randomUUID()});await acceptSession()});
 await page.waitForSelector('.a-home');
};
