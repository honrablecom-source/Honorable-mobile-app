(() => {
  if (globalThis.__novelCompanion) return;
  globalThis.__novelCompanion = true;
  const attr='data-novel-companion';
  let observer, timer, matcher, lookup, total=0, host, lastFocus;
  const excluded='a,button,input,textarea,select,option,script,style,noscript,pre,code,svg,canvas,nav,header,footer,[contenteditable]:not([contenteditable="false"]),[hidden],[aria-hidden="true"],[data-novel-companion]';
  const style=document.createElement('style');style.setAttribute(attr,'style');style.textContent=`mark[${attr}]{background:#e0edff!important;color:inherit!important;border-bottom:2px solid #729ddd!important;border-radius:3px!important;padding:0 1px!important;cursor:pointer!important}mark[${attr}="place"]{background:#ddf2e8!important;border-color:#65a58e!important}mark[${attr}="chapter"]{background:#fff0c7!important;border-color:#c5a04e!important}mark[${attr}]:focus-visible{outline:2px solid #876722!important}`;
  let readingLayout;
  const dockProperties=['position','inset','width','height','min-width','max-width','min-height','max-height','margin','box-sizing','overflow','contain'];
  function resizeReadingPane(){
    if(!readingLayout)return;
    const narrow=innerWidth<760;
    const sidebarWidth=Math.min(430,Math.round(innerWidth*0.38));
    const pane=document.body;
    pane.style.setProperty('width',narrow?'100%':`calc(100% - ${sidebarWidth}px)`,'important');
    pane.style.setProperty('height',narrow?'58dvh':'100dvh','important');
    if(host){host.style.setProperty('--nc-sidebar-width',sidebarWidth+'px');host.style.setProperty('--nc-dock-height',narrow?'42dvh':'100dvh');}
  }
  function reserveReadingPane(anchor){
    const pane=document.body;
    readingLayout={x:scrollX,y:scrollY,bodyX:pane.scrollLeft,bodyY:pane.scrollTop,styles:dockProperties.map(name=>[name,pane.style.getPropertyValue(name),pane.style.getPropertyPriority(name)])};
    const styles={position:'fixed',inset:'0 auto auto 0','min-width':'0','max-width':'none','min-height':'0','max-height':'none',margin:'0','box-sizing':'border-box',overflow:'auto',contain:'layout paint'};
    for(const [name,value] of Object.entries(styles))pane.style.setProperty(name,value,'important');
    resizeReadingPane();pane.scrollTop=readingLayout.y||readingLayout.bodyY;pane.scrollLeft=readingLayout.x||readingLayout.bodyX;
    anchor.scrollIntoView({block:'nearest',inline:'nearest'});
    window.addEventListener('resize',resizeReadingPane);
  }
  function releaseReadingPane(){
    if(!readingLayout)return;
    const pane=document.body, saved=readingLayout, x=pane.scrollLeft,y=pane.scrollTop;
    readingLayout=null;window.removeEventListener('resize',resizeReadingPane);
    for(const [name,value,priority] of saved.styles){if(value)pane.style.setProperty(name,value,priority);else pane.style.removeProperty(name);}
    pane.scrollTop=saved.bodyY;pane.scrollLeft=saved.bodyX;
    window.scrollTo(x,y);
  }
  function close(){if(host){host.remove();host=null;releaseReadingPane();lastFocus?.focus({preventScroll:true});}}
  function card(entry,anchor){
    close();lastFocus=anchor;host=document.createElement('div');host.setAttribute(attr,'card');
    host.style.cssText='position:fixed!important;inset:0!important;z-index:2147483647!important;pointer-events:none!important;';
    const currentHost=host;
    const shadow=host.attachShadow({mode:'open'});
    const css=document.createElement('style');css.textContent=`
      *{box-sizing:border-box}aside{pointer-events:auto;position:absolute;right:0;top:0;bottom:0;width:var(--nc-sidebar-width,430px);overflow-y:auto;overscroll-behavior:contain;background:#faf8f2;color:#243b33;border-left:1px solid #d6ddd3;box-shadow:-16px 0 55px #10281c25;font:14px/1.7 system-ui}
      @media(max-width:759px){aside{top:auto;height:var(--nc-dock-height,42dvh);width:100%;border-left:0;border-top:1px solid #d6ddd3;box-shadow:0 -8px 25px #10281c15}.portrait{width:min(220px,100%);height:auto;aspect-ratio:1}}
      header{position:sticky;top:0;background:#faf8f2f5;z-index:1;border-bottom:1px solid #dce1d6;padding:17px 25px;display:flex;justify-content:space-between;align-items:center}.brand{font-size:10px;letter-spacing:2px;font-weight:700}button{border:1px solid #d5dcd2;border-radius:50%;width:30px;height:30px;background:white;font-size:20px;cursor:pointer;color:#345545}.body{padding:25px}h2{font:32px/1.2 Georgia,serif;margin:16px 0 10px}h3{font-size:11px;letter-spacing:1.6px;text-transform:uppercase;margin:26px 0 10px;color:#5c7469}small{font-size:11px;color:#647568}.kind{color:#426ca8;font-size:10px;letter-spacing:2px;font-weight:700}.portrait{width:min(300px,100%);height:auto;aspect-ratio:1;border-radius:0;background:#e6eae0;overflow:hidden;display:grid;place-items:center;margin-bottom:20px;color:#7c8d7b;font:64px Georgia}.portrait img{width:100%;height:100%;object-fit:cover;background:#e6eae0}.chapter{border:1px solid #d8dfd1;border-radius:10px;padding:14px 16px;margin:22px 0;background:#eef1e8}.chapter strong{display:block;font-size:14px}.chapter small{display:block}p{margin:10px 0;white-space:pre-line}a{color:#315e4a;text-underline-offset:3px;margin-right:12px}dl{margin:0}dt{font-size:11px;color:#708074;margin-top:12px}dd{margin:2px 0;font-size:14px}.sources{border-top:1px solid #dce1d6;margin-top:25px;padding-top:18px;font-size:11px}.role-tags{display:flex;flex-wrap:wrap;gap:6px;margin:12px 0}.role-tag{font:600 11px system-ui;border-radius:20px;padding:6px 10px;color:#315c93;background:#e0edff}.role-editor{font:12px/1.8 system-ui;margin:10px 0}.role-editor summary{cursor:pointer;color:#526b60}.role-editor label{display:inline-flex;gap:6px;align-items:center;margin:6px 12px 0 0}.role-editor input{accent-color:#426ca8}.role-note{display:block;font-size:10px;color:#647568}.power-notes{width:100%;min-height:90px;resize:vertical;border:1px solid #d6ddd3;border-radius:8px;background:white;padding:10px;font:13px/1.6 system-ui;color:#243b33}.save-notes{width:auto;height:auto;border-radius:6px;padding:8px 12px;font-size:12px;margin-top:8px}.spoiler{font-size:11px;color:#8b6740}button:focus-visible,a:focus-visible{outline:3px solid #739ed5;outline-offset:3px}`;
    const el=(tag,text,cls)=>{const node=document.createElement(tag);if(text)node.textContent=text;if(cls)node.className=cls;return node;};
    const panel=el('aside');panel.setAttribute('role','dialog');panel.setAttribute('aria-label',entry.name+' information');
    const header=el('header');header.append(el('span','NOVEL COMPANION','brand'));const dismiss=el('button','×');dismiss.setAttribute('aria-label','Close information');dismiss.onclick=close;header.append(dismiss);
    const body=el('div',null,'body');const portrait=el('div',null,'portrait');portrait.setAttribute('aria-label','No portrait available');
    const imageCredit=el('div');const title=el('h2',entry.name);const chapter=el('div',null,'chapter');chapter.append(el('small','FIRST APPEARANCE · CHAPTER'),el('strong','Not available from current sources'),el('small','A first-appearance work is not a chapter number.'));
    const facts=el('dl');const biography=el('div');biography.append(el('p','Loading expanded information…'));biography.setAttribute('aria-live','polite');
    const sources=el('div',null,'sources');
    const link=(parent,label,url)=>{if(!url||!/^https:\/\/(www\.wikidata\.org|en\.wikipedia\.org|creativecommons\.org)\//.test(url))return;const a=el('a',label);a.href=url;a.target='_blank';a.rel='noopener noreferrer';parent.append(a);};
    body.append(portrait,imageCredit,el('span',entry.kind==='place'?'PLACE GUIDE':'CHARACTER GUIDE','kind'),title,el('p',entry.description),el('p','May contain story spoilers','spoiler'),chapter,el('h3','At a glance'),facts,el('h3','Background & story'),biography,sources);
    const power=el('section');
    if(entry.kind==='character'){
      power.append(el('h3','Power scale · Current chapter'));
      const chapterContext=(document.querySelector('#chapter-title,.chapter-title,article h1,main h1,h1')?.textContent||document.title||'Current chapter').trim();
      power.append(el('small',chapterContext,'role-note'));
      const scale=el('p','Power scale: Not confirmed for this chapter.');
      const excerpts=el('div','No sourced ability details found.');excerpts.className='ability-excerpts';
      power.append(scale,el('h3','Abilities & weaknesses'),excerpts);
      const edit=el('details',null,'role-editor');edit.append(el('summary','Add your power / rank notes'));
      const noteLabel=el('label','Current-chapter power notes · include a source');
      const textarea=el('textarea');textarea.className='power-notes';textarea.maxLength=3000;textarea.setAttribute('aria-label','Power and rank notes');noteLabel.append(textarea);
      const save=el('button','Save notes','save-notes');const state=el('small','Saved for this chapter only; these are your notes, not verified rankings.','role-note');state.setAttribute('role','status');
      const chapterURL=new URL(globalThis.__ncSourceURL||location.href);chapterURL.hash='';
      const key='nc-power-chapter:'+String(entry.bookId||novelFromURL()||'unknown')+':'+entry.id+':'+chapterURL.href;
      textarea.disabled=true;save.disabled=true;
      chrome.storage.local.get(key).then(value=>{textarea.value=typeof value[key]==='string'?value[key]:'';}).catch(()=>{state.textContent='Saved notes could not be loaded.';}).finally(()=>{textarea.disabled=false;save.disabled=false;});
      save.onclick=async()=>{save.disabled=true;try{if(textarea.value.trim())await chrome.storage.local.set({[key]:textarea.value.trim()});else await chrome.storage.local.remove(key);state.textContent='Power notes saved for this chapter on this browser.';}catch{state.textContent='Could not save notes. Please try again.';}finally{save.disabled=false;}};
      edit.append(noteLabel,save,state);power.append(edit);title.after(power);
    }
    if(entry.kind==='chapter'){
      portrait.remove();imageCredit.remove();chapter.remove();facts.previousElementSibling?.remove();facts.remove();
      body.querySelector('.kind').textContent='CURRENT CHAPTER';
      biography.previousElementSibling.textContent='Chapter summary';
      sources.replaceChildren(el('p','Local extractive summary: selected sentences from the loaded chapter, kept in story order. May miss context. No chapter text is uploaded.'));
    }
    if(entry.kind==='character'){
      const options=[['mc','MC · Main Character'],['fl','FL · Female Lead'],['ml','ML · Male Lead'],['side','Side Character'],['antagonist','Antagonist'],['support','Supporting Character'],['mentor','Mentor'],['love','Love Interest']];
      const labels=new Map(options);
      const tags=el('div',null,'role-tags');tags.setAttribute('aria-label','Character role tags');
      const editor=el('details',null,'role-editor');editor.append(el('summary','Edit role tags'));
      const note=el('small','Your tags are saved for this character in this novel.','role-note');editor.append(note);
      title.after(tags,editor);
      const key='nc-roles:'+String(entry.bookId||novelFromURL()||'unknown')+':'+entry.id;
      let chosen=[];
      function renderTags(){tags.replaceChildren();for(const role of chosen)tags.append(el('span',labels.get(role),'role-tag'));if(!chosen.length)tags.append(el('span','Role unconfirmed','role-tag'));}
      const inputs=[];
      for(const [value,label] of options){const wrapper=el('label');const input=el('input');input.type='checkbox';input.value=value;input.disabled=true;wrapper.append(input,document.createTextNode(label));editor.append(wrapper);inputs.push(input);
        input.addEventListener('change',async()=>{
          chosen=inputs.filter(i=>i.checked).map(i=>i.value);renderTags();inputs.forEach(i=>i.disabled=true);
          try{if(chosen.length)await chrome.storage.local.set({[key]:chosen});else await chrome.storage.local.remove(key);note.textContent='Saved on this browser for this novel.';}
          catch{note.textContent='Could not save these tags. Please try again.';}
          finally{inputs.forEach(i=>i.disabled=false);}
        });
      }
      renderTags();
      (async()=>{try{const saved=(await chrome.storage.local.get(key))[key];chosen=Array.isArray(saved)?saved.filter(value=>labels.has(value)):[];renderTags();inputs.forEach(input=>input.checked=chosen.includes(input.value));}catch{note.textContent='Saved tags could not be loaded.';}finally{inputs.forEach(input=>input.disabled=false);}})();
    }
    if(entry.aliases?.length){facts.append(el('dt','Also known as'),el('dd',entry.aliases.join(', ')));}
    link(sources,'Wikidata ↗',entry.source);link(sources,'Wikipedia ↗',entry.wikipedia);if(entry.kind!=='chapter')sources.append(el('p','Wikidata structured data: CC0.'));
    panel.append(header,body);shadow.append(css,panel);document.documentElement.append(host);reserveReadingPane(anchor);dismiss.focus({preventScroll:true});
    async function populate(){
      try{
        const response=entry.localDetails?{data:entry.localDetails}:await chrome.runtime.sendMessage({type:'nc-details',id:entry.id});
        if(currentHost!==host)return;
        if(response?.error)throw Error(response.error);
        const detail=response?.data||{};
        if(detail.image?.url?.startsWith('https://upload.wikimedia.org/')){const img=el('img');img.alt=entry.name+' — source illustration';img.referrerPolicy='no-referrer';img.src=detail.image.url;img.onerror=()=>{portrait.replaceChildren();};portrait.replaceChildren(img);portrait.removeAttribute('aria-label');link(imageCredit,'Image source & license ↗',detail.image.source);}
        for(const fact of detail.facts||[]){facts.append(el('dt',fact.label),el('dd',fact.value));}
        if(!facts.children.length)facts.append(el('dd','No additional structured facts available.'));
        biography.replaceChildren();for(const paragraph of (detail.biography||'No expanded biography is available for this entry. Follow the source links for more information.').split(/\n\n+/)){biography.append(el('p',paragraph));}
        if(entry.kind==='character'){
          const abilitySentences=(detail.biography||'').match(/[^.!?\n]+[.!?]+(?:[”’"']|$)?/g)||[];
          const selected=abilitySentences.filter(text=>/\b(abilities|ability|powers|powerful|strength|weakness|magic|combat|technique|transformation|stamina|durability)\b/i.test(text)).slice(0,4);
          if(selected.length){const box=power.querySelector('.ability-excerpts');box.replaceChildren(el('small','General Wikipedia abilities · may include later-chapter spoilers; not a current-chapter ranking'));for(const text of selected)box.append(el('p',text.trim()));}
        }
        if(detail.warning)biography.prepend(el('p',detail.warning));
        if(detail.wikipedia){link(sources,'Read full article & history ↗',detail.wikipedia);link(sources,'CC BY-SA 4.0','https://creativecommons.org/licenses/by-sa/4.0/');sources.append(el('p','Biography excerpt from Wikipedia contributors; formatting adapted. Images have their own licenses.'));}
      }catch(error){if(currentHost===host)biography.replaceChildren(el('p',error.message||'Details unavailable. Please try again.'));}
    }
    populate();
  }
  document.addEventListener('keydown',e=>{if(e.key==='Escape')close();});
  function chapterSummary(){
    const root=document.querySelector('.chapter-content,#chapter-content,.reading-content,article,main,[role="main"]');
    if(!root)return 'No chapter text was found on this page.';
    const clone=root.cloneNode(true);clone.querySelectorAll('script,style,nav,header,footer,aside,form,button,[hidden],[aria-hidden="true"],h1,h2,h3').forEach(node=>node.remove());
    const text=(clone.textContent||'').replace(/\s+/g,' ').trim().slice(0,100000);
    const sentences=(text.match(/[^.!?]+(?:[.!?]+[”’"']?|$)/g)||[]).map(s=>s.trim()).filter(s=>s.length>35);
    if(sentences.length<3)return 'Not enough chapter text is loaded to make a useful summary.';
    const words=s=>s.toLowerCase().match(/[a-z]{4,}/g)||[];
    const stop=new Set(['that','this','with','from','have','were','there','their','they','would','could','about','which','when','what','into','been','them','then']);
    const frequency=new Map();for(const word of words(text)){if(!stop.has(word))frequency.set(word,(frequency.get(word)||0)+1);}
    const ranked=sentences.map((text,index)=>({text,index,score:words(text).reduce((sum,w)=>sum+Math.min(frequency.get(w)||0,12),0)/Math.sqrt(text.length)}));
    const chosen=ranked.sort((a,b)=>b.score-a.score).slice(0,Math.min(5,Math.max(2,Math.ceil(sentences.length/4)))).sort((a,b)=>a.index-b.index);
    return chosen.map(s=>s.text).join('\n\n');
  }
  function highlightTitle(){
    if(autoPaused)return;
    const heading=document.querySelector('#chapter-title,article h1,main h1,.chapter-title,h1');
    if(!heading||heading.querySelector(`[${attr}]`)||heading.closest('a,button,[contenteditable="true"]'))return;
    if(!titleCandidates().length&&!lookup)return;
    const mark=document.createElement('mark');mark.setAttribute(attr,'chapter');mark.tabIndex=0;mark.setAttribute('role','button');mark.setAttribute('aria-label','Show current chapter summary');
    while(heading.firstChild)mark.append(heading.firstChild);
    heading.append(mark);
    const open=()=>card({id:'chapter',name:mark.textContent,kind:'chapter',description:'Summary of the text currently loaded on this page, not the whole novel.',localDetails:{biography:chapterSummary()}},mark);
    mark.onclick=open;mark.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();open();}};
    if(!style.isConnected)document.documentElement.append(style);
  }
  function remove(){observer?.disconnect();clearTimeout(timer);close();document.querySelectorAll(`mark[${attr}]`).forEach(mark=>{const parent=mark.parentNode;if(mark.getAttribute(attr)==='chapter')mark.replaceWith(...mark.childNodes);else mark.replaceWith(document.createTextNode(mark.textContent));parent.normalize();});style.remove();total=0;}
  function scan(){
    observer?.disconnect();
    const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:node=>node.parentElement&&!node.parentElement.closest(excluded)&&node.textContent.trim()?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT});
    const nodes=[];while(walker.nextNode())nodes.push(walker.currentNode);
    for(const node of nodes){if(total>=3000)break;const text=node.textContent;matcher.lastIndex=0;let match,offset=0;const fragment=document.createDocumentFragment();let changed=false;
      while((match=matcher.exec(text))&&total<3000){const start=match.index+match[1].length;const name=match[2];const entry=lookup.get(name.toLocaleLowerCase('en'));if(!entry)continue;
        fragment.append(document.createTextNode(text.slice(offset,start)));const mark=document.createElement('mark');mark.setAttribute(attr,entry.kind);mark.tabIndex=0;mark.setAttribute('role','button');mark.setAttribute('aria-label',`${name}: show ${entry.kind} information`);mark.textContent=name;mark.onclick=()=>card(entry,mark);mark.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();card(entry,mark);}};fragment.append(mark);offset=start+name.length;changed=true;total++;
      }if(changed){fragment.append(document.createTextNode(text.slice(offset)));node.replaceWith(fragment);}
    }
    observer?.observe(document.body,{childList:true,subtree:true,characterData:true});
  }
  function highlight(entries){remove();lookup=new Map();const ambiguous=new Set();for(const entry of entries){for(const name of [entry.name,...entry.aliases]){const key=name.trim().toLocaleLowerCase('en');if(key.length<3||key.length>120)continue;if(lookup.has(key)&&lookup.get(key).id!==entry.id){ambiguous.add(key);continue;}lookup.set(key,entry);}}for(const key of ambiguous)lookup.delete(key);
    if(!lookup.size)return {count:0};const escape=s=>s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');matcher=new RegExp(`(^|[^\\p{L}\\p{N}_])(${[...lookup.keys()].sort((a,b)=>b.length-a.length).map(escape).join('|')})(?=$|[^\\p{L}\\p{N}_])`,'giu');document.documentElement.append(style);
    observer=new MutationObserver(records=>{if(records.some(r=>!r.target.parentElement?.closest(`[${attr}]`)&& (r.type==='characterData'||![...r.addedNodes,...r.removedNodes].every(n=>n.nodeType===1&&n.hasAttribute(attr))))){clearTimeout(timer);timer=setTimeout(scan,250);}});scan();highlightTitle();return {count:total,capped:total>=3000};
  }
  function title(){
    const meta=document.querySelector('meta[property="og:title"]')?.content;
    let value=meta||document.title||'';
    value=value.split(/\s+[|–—]\s+/)[0].replace(/\s*[-:]?\s*chapter\s+\d+.*$/i,'').trim();
    if(!value||/^chapter\b/i.test(value)){
      const segments=new URL(globalThis.__ncSourceURL||location.href).pathname.split('/').filter(Boolean).filter(s=>!/^chapter[-_\d]|^\d+$|^(book|novel|read|chapter)s?$/i.test(s));
      try{value=decodeURIComponent(segments.at(-1)||'').replace(/[-_]/g,' ').replace(/\.html?$/,'');}catch{value='';}
    }return value.slice(0,160);
  }
  let autoPaused=false, autoGeneration=0, currentURL=location.href;
  function novelFromURL(){
    let segments;
    const sourceURL=new URL(globalThis.__ncSourceURL||location.href);
    for(const key of ['novel','book','story']){const value=sourceURL.searchParams.get(key);if(value&&/[a-z]/i.test(value))return value.replace(/[-_]/g,' ').slice(0,160);}
    try{segments=new URL(globalThis.__ncSourceURL||location.href).pathname.split('/').filter(Boolean).map(decodeURIComponent);}catch{return '';}
    const marker=segments.findIndex(part=>/^(novels?|books?|stories|fiction)$/i.test(part));
    let candidate='';
    if(marker>=0){
      const after=segments.slice(marker+1).filter(part=>!/^\d+$/.test(part));
      candidate=after.find(part=>!/^chapter(?:[-_ ]|$)/i.test(part))||'';
    }else{
      const chapter=segments.findIndex(part=>/^chapter[-_ ]?\d/i.test(part));
      if(chapter>0)candidate=segments[chapter-1];
      else if(segments.length&&/[-_]chapter[-_ ]?\d/i.test(segments.at(-1)))candidate=segments.at(-1);
    }
    return candidate.replace(/\.html?$/i,'').replace(/[-_]chapter[-_ ]?\d.*$/i,'').replace(/[_-]\d+$/,'').replace(/[-_]/g,' ').trim().slice(0,160);
  }
  function titleCandidates(){
    const urlTitle=novelFromURL();
    const chapter=document.querySelector('.chapter-content,#chapter-content,.reading-content,article,main');
    const bookMetadata=document.querySelector('meta[property="book:title"],meta[name="book-title"]')?.content;
    // Ordinary pages are not sent to the literary lookup service.
    if(!urlTitle&&!bookMetadata&&!(chapter&&/\b(chapter|novel|webnovel)\b/i.test(document.title+' '+chapter.querySelector('h1,h2')?.textContent)))return [];
    const raw=[urlTitle,bookMetadata,document.querySelector('meta[property="og:title"]')?.content,document.title,document.querySelector('h1')?.textContent];
    const clean=value=>(value||'').replace(/\s*[|–—]\s*.*$/,'').replace(/\s*[-:]?\s*chapter\s*\d+.*$/i,'').replace(/^read\s+/i,'').replace(/\s+(?:online|free)$/i,'').trim();
    const candidates=raw.map(clean).filter(value=>value.length>=3&&value.length<=160&&!/^(chapter|home|read|novel|untitled)(?:\s*\d*)?$/i.test(value));
    return [...new Map(candidates.map(value=>[value.toLowerCase(),value])).values()].slice(0,3);
  }
  async function autoHighlight(){
    const generation=++autoGeneration;
    const candidates=titleCandidates();
    if(autoPaused||!candidates.length)return;
    highlightTitle();
    try{
      const response=await chrome.runtime.sendMessage({type:'nc-auto',title:candidates[0],titles:candidates});
      if(generation!==autoGeneration||autoPaused)return;
      if(response?.data?.entries?.length)highlight(response.data.entries);
    }catch{/* Reading remains available if the service or extension is unavailable. */}
  }
  chrome.runtime.onMessage.addListener((message,sender,reply)=>{
    if(message.type==='nc-snapshot'){
      const candidates=[...document.querySelectorAll('article,main,[role="main"],.chapter-content,#chapter-content,.reading-content')];
      const root=candidates.sort((a,b)=>b.innerText.length-a.innerText.length)[0]||document.body;
      const clone=root.cloneNode(true);
      clone.querySelectorAll('script,style,noscript,iframe,object,embed,nav,header,footer,aside,form,button,input,textarea,[hidden],[aria-hidden="true"],[data-novel-companion="card"]').forEach(el=>el.remove());
      const blocks=[...clone.querySelectorAll('p,h1,h2,h3,h4,li,blockquote')].filter(el=>!el.parentElement?.closest('p,li,blockquote')).map(el=>el.textContent.trim()).filter(Boolean);
      const text=(blocks.length?blocks.join('\n\n'):clone.textContent).slice(0,500000);
      reply({title:document.title,url:location.href,text});
    }
    if(message.type==='nc-title')reply({title:title(),candidates:titleCandidates()});
    if(message.type==='nc-remove'){autoPaused=true;++autoGeneration;remove();reply({ok:true});}
    if(message.type==='nc-highlight')reply(highlight(message.entries));
    if(message.type==='nc-toggle'){autoPaused=!autoPaused;if(autoPaused){++autoGeneration;remove();}else autoHighlight();reply({paused:autoPaused});}
  });
  // Detect same-document chapter navigation without injecting code into the site.
  setInterval(()=>{if(location.href!==currentURL){currentURL=location.href;++autoGeneration;remove();autoHighlight();}else highlightTitle();},1000);
  autoHighlight();
})();
