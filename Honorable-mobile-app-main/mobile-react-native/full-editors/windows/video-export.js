export function mountVideoExport({editor,button,start,lookup,advanced,failed}) {
  const dialog = document.createElement('dialog');
  dialog.id = 'video-delivery';
  dialog.className = 'studio-dialog';
  dialog.setAttribute('aria-labelledby','video-delivery-title');
  dialog.innerHTML = `
    <header class="studio-dialog-heading"><h2 id="video-delivery-title">Export video</h2><button type="button" data-close aria-label="Close export">×</button></header>
    <form>
      <div class="studio-dialog-body">
        <p data-export-empty hidden>Open an editable timeline with media to export an MP4.</p>
        <div data-export-settings>
          <section class="delivery-source"><h3>Source sequence</h3><div class="delivery-summary"><strong data-export-name></strong><span data-export-specs></span></div></section>
          <section class="delivery-output"><h3>Output settings</h3><dl><div><dt>Format</dt><dd>MP4</dd></div><div><dt>Destination</dt><dd class="delivery-destination">Videos / Noble Exports</dd></div></dl>
          <fieldset><legend>Quality</legend>
            <label><input type="radio" name="quality" value="standard" checked> <span><strong>Standard</strong><small>Smaller file for sharing</small></span></label>
            <label><input type="radio" name="quality" value="high"> <span><strong>High</strong><small>More detail, larger file</small></span></label>
          </fieldset>
          </section>
        </div>
        <progress max="1" value="0" hidden aria-label="Video export progress"></progress>
        <p role="status"></p>
      </div>
      <footer class="studio-dialog-footer">
        <button type="button" data-advanced>Advanced export</button>
        <button type="submit" data-primary>Export MP4</button>
      </footer>
    </form>`;
  document.body.append(dialog);
  const get = selector=>dialog.querySelector(selector);
  const form=get('form'), status=get('[role=status]'), progress=get('progress');
  const submit=get('[type=submit]'), settings=get('[data-export-settings]'), empty=get('[data-export-empty]');
  if (!window.nobleNavigation) get('.delivery-destination').textContent='Browser downloads';
  let timeline;
  let busy=false;

  async function open() {
    const {activeSequence} = await editor.execute('project.inspect');
    timeline = activeSequence ? await editor.execute('sequence.inspect',{item:activeSequence}) : null;
    const hasMedia = Boolean(timeline?.duration);
    empty.hidden = hasMedia;
    settings.hidden = !hasMedia;
    submit.disabled = busy || !hasMedia;
    get('[data-advanced]').disabled = busy;
    if (hasMedia) {
      const {width,height,frame_rate:rate} = timeline.settings;
      const fps = Math.round(rate.num/rate.den*100)/100;
      get('[data-export-name]').textContent=timeline.name;
      get('[data-export-specs]').textContent=width+' × '+height+' · '+fps+' fps · '+(timeline.duration/254016000000).toFixed(1)+' s';
    }
    if (!busy) {
      status.textContent='';
      progress.hidden=true;
    }
    dialog.showModal();
  }
  get('[data-close]').onclick=()=>dialog.close();
  dialog.addEventListener('close',()=>button.focus());
  get('[data-advanced]').onclick=()=>{
    dialog.close();
    void advanced();
  };
  form.onsubmit=async event=>{
    event.preventDefault();
    if (busy || !timeline?.duration) return;
    const quality=form.elements.quality.value;
    busy=true;
    submit.disabled=get('[data-advanced]').disabled=get('fieldset').disabled=true;
    progress.value=0;
    progress.hidden=false;
    status.textContent='Starting export…';
    try {
      let job=await start(timeline.id,quality);
      while (job.status==='exporting') {
        await new Promise(resolve=>setTimeout(resolve,500));
        job=lookup(job.job);
        progress.value=job.progress || 0;
      }
      if (job.status!=='finished') throw Error(job.error || 'The video export could not finish.');
      progress.value=1;
      status.textContent='Saved '+job.path.split(/[\\/]/).at(-1);
    } catch (error) {
      progress.hidden=true;
      status.textContent=error.message;
    } finally {
      busy=false;
      submit.disabled=!timeline?.duration;
      get('[data-advanced]').disabled=get('fieldset').disabled=false;
    }
  };
  return ()=>void open().catch(failed);
}
