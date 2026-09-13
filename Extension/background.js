import {toggleShield,clearShield} from './shield.js';
import {loadDetails} from './details.js';
import {automaticIndex} from './automatic.js';
chrome.runtime.onMessage.addListener((message,sender,reply)=>{
  if(sender.id!==chrome.runtime.id)return;
  if(message.type==='nc-details'){
    loadDetails(message.id).then(data=>reply({data}),error=>reply({error:error.message}));return true;
  }
  if(message.type==='nc-auto'){
    automaticIndex(message.titles||message.title).then(data=>reply({data}),error=>reply({error:error.message}));return true;
  }
});
chrome.action.onClicked.addListener(async tab=>{
  try{await chrome.tabs.sendMessage(tab.id,{type:'nc-toggle'});}catch{/* Internal browser pages cannot be annotated. */}
});
chrome.runtime.onInstalled.addListener(()=>{
  chrome.contextMenus.removeAll(()=>{
    chrome.contextMenus.create({id:'nc-clean-reader',title:'Read without popups — Novel Companion',contexts:['page','action']});
    chrome.contextMenus.create({id:'nc-shield',title:'Toggle strict shield (blocks new tabs & off-site navigation)',contexts:['page','action']});
  });
});
chrome.contextMenus.onClicked.addListener(async(info,tab)=>{
  if(!tab?.id)return;
  if(info.menuItemId==='nc-shield'){
    try{await toggleShield(tab);}catch{await chrome.action.setBadgeText({tabId:tab.id,text:'ERR'});}return;
  }
  if(info.menuItemId!=='nc-clean-reader')return;
  const id=crypto.randomUUID();
  let snapshot;
  try{
    snapshot=await chrome.tabs.sendMessage(tab.id,{type:'nc-snapshot'});
    if(!snapshot?.text?.trim())throw Error('No loaded chapter text found.');
  }catch{
    snapshot={error:'Could not copy this page. Refresh the novel page, wait for the chapter text to load, then try again.'};
  }
  await chrome.storage.session.set({['reader-'+id]:snapshot});
  const readerTab=await chrome.tabs.create({url:chrome.runtime.getURL('clean-reader.html')+'#'+id});
  await chrome.storage.session.set({['reader-tab-'+readerTab.id]:'reader-'+id});
});

chrome.tabs.onRemoved.addListener(async tabId=>{
 await clearShield(tabId);
 const key='reader-tab-'+tabId;const stored=await chrome.storage.session.get(key);if(stored[key])await chrome.storage.session.remove([key,stored[key]]);
});

chrome.webNavigation.onCreatedNavigationTarget.addListener(async details=>{
 const key='shield-'+details.sourceTabId;
 const saved=(await chrome.storage.session.get(key))[key];
 if(saved){try{await chrome.tabs.remove(details.tabId);}catch{/* Tab already closed. */}}
});
