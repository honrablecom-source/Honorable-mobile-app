(async()=>{
 const status=document.getElementById('status');
 try{
  const id=location.hash.slice(1);
  if(!/^[\da-f-]{36}$/.test(id))throw Error('No chapter selected. Right-click the novel page and choose “Read without popups — Novel Companion”.');
  const key='reader-'+id;
  const snapshot=(await chrome.storage.session.get(key))[key];
  if(!snapshot)throw Error('This reading copy has expired. Open a new copy from the original novel page.');
  if(snapshot.error)throw Error(snapshot.error);
  document.getElementById('source').value=snapshot.url;
  document.getElementById('chapter-title').textContent=snapshot.title;
  for(const text of snapshot.text.split(/\n\n+/)){const p=document.createElement('p');p.textContent=text;document.getElementById('chapter').append(p);}
  globalThis.__ncSourceURL=snapshot.url;
  const script=document.createElement('script');script.src='content.js';document.body.append(script);
  status.textContent='Known names are highlighted automatically when the URL matches a supported novel. You can close the original site tab.';
  document.getElementById('copy').onclick=async()=>{try{await navigator.clipboard.writeText(snapshot.url);status.textContent='Original novel link copied.';}catch{document.getElementById('source').select();status.textContent='Select the link and copy it with Ctrl+C or Command+C.';}};
 }catch(error){status.textContent=error.message;document.getElementById('copy').disabled=true;}
})();
