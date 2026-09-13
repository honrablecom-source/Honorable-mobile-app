export function shieldRule(id,tabId,hostname){
 return {id,priority:1,action:{type:'block'},condition:{tabIds:[tabId],excludedRequestDomains:[hostname],resourceTypes:['main_frame','sub_frame']}};
}
export async function toggleShield(tab){
 const key='shield-'+tab.id;const saved=(await chrome.storage.session.get(key))[key];
 if(saved){await chrome.declarativeNetRequest.updateSessionRules({removeRuleIds:[saved.ruleId]});await chrome.storage.session.remove(key);await chrome.action.setBadgeText({tabId:tab.id,text:''});return;}
 const url=new URL(tab.url);if(!/^https?:$/.test(url.protocol))return;
 const rules=await chrome.declarativeNetRequest.getSessionRules();let id=1;const used=new Set(rules.map(rule=>rule.id));while(used.has(id))id++;
 await chrome.declarativeNetRequest.updateSessionRules({addRules:[shieldRule(id,tab.id,url.hostname)]});
 await chrome.storage.session.set({[key]:{ruleId:id,hostname:url.hostname}});
 await chrome.action.setBadgeText({tabId:tab.id,text:'ON'});await chrome.action.setBadgeBackgroundColor({tabId:tab.id,color:'#2d5145'});
}
export async function clearShield(tabId){
 const key='shield-'+tabId;const saved=(await chrome.storage.session.get(key))[key];
 if(saved){await chrome.declarativeNetRequest.updateSessionRules({removeRuleIds:[saved.ruleId]});await chrome.storage.session.remove(key);}
}
