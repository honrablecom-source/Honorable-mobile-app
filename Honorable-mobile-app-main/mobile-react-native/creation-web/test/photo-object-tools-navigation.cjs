/** Open the actual user-facing disclosure before accessing an existing photo action. */
async function photoTool(page,name){
 const action=page.getByRole('button',{name,exact:true,includeHidden:true});
 const details=action.locator('xpath=ancestor::details[contains(@class,"photo-object-tools")]');
 if(await details.count()&&await details.getAttribute('open')===null)await details.locator('summary').click();
 return action;
}
module.exports={photoTool};
