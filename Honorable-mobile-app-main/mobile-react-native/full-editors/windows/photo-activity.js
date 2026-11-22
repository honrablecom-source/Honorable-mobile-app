const clock = new Intl.DateTimeFormat(undefined,{hour:'2-digit',minute:'2-digit',second:'2-digit'});
const point = value => Array.isArray(value) && value.length >= 2 && value.slice(0,2).every(Number.isFinite) ? value.slice(0,2).join(', ') : '';
const short = value => String(value ?? '').replace(/[\r\n]+/g,' ').slice(0,160);

export function actionDetail(id, args = {}) {
  const p = args.parameters ?? args;
  if (id === 'paint.stroke' && Array.isArray(p.points) && p.points.length) {
    const start = point(p.points[0]), end = point(p.points.at(-1));
    const geometry = p.points.every(v => point(v) === start) ? 'Single point ('+start+')' : p.points.length+' points · ('+start+') → ('+end+')';
    return [geometry,Number.isFinite(p.size) && p.size+' px',/^#[a-f0-9]{3,8}$/i.test(p.color) && p.color].filter(Boolean).join(' · ');
  }
  if (id === 'paint.gradient' && point(p.from) && point(p.to)) return 'From ('+point(p.from)+') to ('+point(p.to)+')';
  if (Number.isFinite(p.x) && Number.isFinite(p.y)) {
    return '('+p.x+', '+p.y+')'+(Number.isFinite(p.width) && Number.isFinite(p.height) ? ' · '+p.width+' × '+p.height+' px' : '');
  }
  if (Array.isArray(p.layers)) return p.layers.map(layer=>short(layer.name)).filter(Boolean).slice(0,8).join(', ');
  if (typeof p.data === 'string') return 'Preset import';
  if (Number.isFinite(p.percent)) return p.percent+'%';
  if (typeof p.name === 'string') return short(p.name.split(/[\\/]/).at(-1));
  if (/^#[a-f0-9]{3,8}$/i.test(p.color)) return p.color;
  return '';
}

export function geometryNote(id,p,width,height) {
  if (id === 'paint.stroke' && p.points?.length && point(p.points[0]) && p.points.every(v => point(v) === point(p.points[0]))) {
    return 'Brush dab at ('+point(p.points[0])+'). Use distinct points to draw a line.';
  }
  if (id === 'paint.gradient' && point(p.from) && point(p.from) === point(p.to)) {
    return 'The gradient endpoints coincide. Use distinct endpoints for a visible transition.';
  }
  if (id === 'select.rect' && (p.x<0 || p.y<0 || p.x+p.width>width || p.y+p.height>height)) {
    return 'The selection extends outside the '+width+' × '+height+' canvas. x and y specify its top-left corner.';
  }
}

function element(tag,content) {
  const node = document.createElement(tag);
  node.textContent = content;
  return node;
}

export function actionReply(id,title,result,outcome) {
  if(result?.status==='save_requested') return 'Prepared the '+String(result.format).toUpperCase()+' export. Choose a destination in the Save dialog.';
  if(result?.status==='picker_requested') return 'Opened the file picker.';
  if(id==='context') return result?.width ? 'Checked the '+result.width+' × '+result.height+' canvas and its layers.' : 'Checked the active document.';
  if(id==='document.pixel') return 'Sampled the canvas.';
  if(id==='layer.newLayer') return 'Created an editable layer.';
  if(id==='paint.stroke') return 'Painted a brush stroke.';
  if(id==='edit.undo') return 'Undid the previous edit.';
  if(outcome==='Preview') return 'Prepared a preview for review.';
  return title+' completed.';
}

export function mountPhotoActivity(panel,button,catalog,documentName,submitEdit,openReview) {
  panel.innerHTML = `
    <header class="activity-heading">
      <h2>Studio chat</h2><span data-count aria-live="off"></span>
      <button type="button" data-browse aria-expanded="false">Browse tools</button>
    </header>
    <div class="activity-body">
      <p data-empty>Ask for a photo adjustment, or connect an editing agent to follow its work here.</p>
      <div role="log" aria-label="Editing conversation" aria-live="off" data-events></div>
      <section data-library aria-label="Available photo tools" hidden>
        <input type="search" aria-label="Search photo tools" placeholder="Search by tool or command">
        <span data-matches role="status"></span>
        <ul aria-label="Matching tools"></ul><button type="button" data-more>Show more</button>
      </section>
    </div>
    <form class="chat-compose">
      <textarea rows="2" maxlength="1000" aria-label="Photo adjustment request" placeholder="Ask for a photo adjustment…"></textarea>
      <button type="submit" aria-label="Send request" title="Send request" disabled>↑</button>
    </form>`;
  const get = selector => panel.querySelector(selector);
  const browse=get('[data-browse]'), library=get('[data-library]'), events=get('[data-events]'), empty=get('[data-empty]');
  const search=get('input'), more=get('[data-more]'), matches=get('[data-matches]'), count=get('[data-count]');
  const byId = new Map(catalog.map(row => [row.route+'/'+row.id,row]));
  const body=get('.activity-body'), form=get('form'), draft=get('textarea'), send=form.querySelector('button');
  let total=0, running=0, limit=32, current, submitting=false, following=true;
  const retained=[];
  body.onscroll=()=>{if(library.hidden) following=body.scrollHeight-body.clientHeight-body.scrollTop<28;};
  function follow() {
    if(following && library.hidden && !panel.hidden) body.scrollTop=body.scrollHeight;
  }
  function begin(brief,author='You') {
    events.querySelector('[data-open-review]')?.remove();
    const turn=element('section','');
    turn.className='chat-turn';
    if(brief) {
      const user=element('div','');
      user.className='chat-request';
      user.append(element('small',author),element('p',String(brief).slice(0,1000)));
      turn.append(user);
    }
    const reply=element('div',''), heading=element('strong','Noble'), summary=element('p','Waiting for an editor action…');
    const steps=element('details',''), disclosure=element('summary','Tool activity'), list=element('ol','');
    reply.className='chat-reply';
    summary.className='chat-summary';
    summary.setAttribute('role','status');
    steps.append(disclosure,list);
    reply.append(heading,summary,steps);
    turn.append(reply);
    events.append(turn);
    current={turn,steps,disclosure,list,summary};
    while(events.children.length>100) events.firstElementChild.remove();
    empty.hidden=true;
    follow();
    return current;
  }
  function countSteps(group) {
    const n=group.list.childElementCount;
    group.disclosure.textContent=n+' tool '+(n===1?'step':'steps');
  }
  function updateSend(){send.disabled=submitting || !draft.value.trim();}
  draft.oninput=updateSend;
  draft.onkeydown=event=>{
    if(event.key==='Enter' && !event.shiftKey && !event.isComposing){
      event.preventDefault();
      if(!send.disabled) form.requestSubmit();
    }
  };
  form.onsubmit=async event=>{
    event.preventDefault();
    const brief=draft.value.trim();
    if(submitting || !brief) return;
    following=true;
    const group=begin(brief);
    draft.value='';submitting=true;updateSend();
    try {
      const reply=await track({id:'preview_photo_edit',label:'Prepare photo adjustment',source:'Noble AI',preview:true},()=>submitEdit(brief));
      if(!reply?.error && group===current){
        const review=element('button','Review adjustments');
        review.dataset.openReview='';
        review.type='button';review.onclick=openReview;
        group.turn.querySelector('.chat-reply').append(review);
        follow();
      }
    }catch{ /* The failed tool step already contains the editor's message. */ }
    finally{submitting=false;updateSend();}
  };
  function renderCatalog() {
    const terms=search.value.toLowerCase().trim().split(/[\s._]+/).filter(Boolean);
    const found=catalog.filter(row=>terms.every(word=>(row.name+' '+row.label+' '+row.id).toLowerCase().includes(word)));
    const list=library.querySelector('ul');
    list.replaceChildren();
    for (const row of found.slice(0,limit)) {
      const item=element('li','');
      item.append(element('span',row.label),element('code',row.name));
      list.append(item);
    }
    matches.textContent=found.length+' of '+catalog.length+' tools';
    more.hidden=found.length<=limit;
  }
  function renderCount() {
    button.textContent='Activity'+(running ? ' · '+running+' running' : total ? ' · '+total : '');
    count.textContent=library.hidden ? (total>100 ? 'Last 100 of '+total : total+' '+(total===1?'action':'actions')) : '';
    empty.hidden=Boolean(current) || !library.hidden;
  }
  browse.onclick=()=>{
    library.hidden=!library.hidden;
    events.hidden=!library.hidden;
    form.hidden=!library.hidden;
    browse.setAttribute('aria-expanded',String(!library.hidden));
    browse.textContent=library.hidden?'Browse tools':'Back to activity';
    get('h2').textContent=library.hidden?'Studio chat':'Photo tools';
    if(!library.hidden){ renderCatalog(); search.focus(); }
    body.scrollTop=library.hidden?body.scrollHeight:0;
    renderCount();
  };
  search.oninput=()=>{limit=32;renderCatalog();};
  more.onclick=()=>{limit+=32;renderCatalog();};
  renderCount();

  async function track({id,label,name,source,args,preview},operation) {
    const group=current ?? begin();
    const item=element('li',''), time=element('time',clock.format(new Date()));
    const info=element('div',''), detail=element('small',''), outcome=element('span','Running');
    const describe=actionDetail(id,args);
    const doc=short(documentName()).split(/[\\/]/).at(-1);
    const metadata=[source,name,doc,describe].filter(Boolean);
    detail.textContent=metadata.join(' · ');
    info.append(element('strong',label || id),detail);
    outcome.className='activity-outcome';
    item.dataset.status='running';
    item.append(info,outcome,time);
    group.list.append(item);
    retained.push({item,group});
    if(retained.length>100){
      const oldest=retained.shift();
      oldest.item.remove();
      if(!oldest.group.list.childElementCount && oldest.group!==current) oldest.group.turn.remove();
      else countSteps(oldest.group);
    }
    countSteps(group);
    const title=(label || id).replace(/[….]$/,'');
    group.summary.textContent=title+'…';
    follow();
    running++;total++;renderCount();
    const fail=error=>{
      item.dataset.status='failed';
      outcome.textContent='Failed';
      detail.textContent=[...metadata,short(error)].join(' · ');
      group.summary.textContent=title+' failed. '+short(error);
    };
    try {
      const response=await operation();
      if(response?.error) fail(response.error);
      else {
        const result=response?.result ?? response;
        item.dataset.status='complete';
        if(result?.status==='save_requested' || result?.status==='picker_requested') outcome.textContent='Dialog requested';
        else if(preview || result?.status==='awaiting_approval') outcome.textContent='Preview';
        else outcome.textContent='Done';
        if (Number.isSafeInteger(result?.revision)) {
          const rev=args?.revision;
          const revision=Number.isSafeInteger(rev) && rev!==result.revision ? rev+' → '+result.revision : result.revision;
          detail.textContent=[...metadata,'Rev '+revision].join(' · ');
        }
        if (result?.geometryNote) detail.append(' · '+result.geometryNote);
        group.summary.textContent=actionReply(id,title,result,outcome.textContent);
        if (id==='noble.photo.apply' || id==='edit.undo') events.querySelector('[data-open-review]')?.remove();
      }
      return response;
    } catch(error) {
      fail(error.message || error);
      throw error;
    } finally {running--;renderCount();follow();}
  }
  return {
    track,begin,reveal:()=>requestAnimationFrame(follow),
    external(input,operation) {
      const {route='proposal',id='preview_photo_edit',input:args} = input.args ?? {};
      if(route==='request') return operation();
      if(route==='proposal' && input.brief) begin(input.brief,'Connected client');
      const entry=byId.get(route+'/'+id);
      return track({id,name:entry?.name,label:entry?.label,source:'MCP',args,preview:route==='proposal'||route==='model'||route==='strength'},operation);
    },
  };
}
