(() => {
  if (window !== window.top || !['/desktop/home.html','/image/','/video/'].includes(location.pathname)) return;
  const socket = window.chrome.webview;
  const pending = new Map();
  let nextId = 0;
  let importPhoto;
  let importQueue = Promise.resolve();
  const pendingPhotos = [];
  let onDelivery, onEditor, onNativePhotoLoading;
  const send = message => socket.postMessage(message);
  const invoke = (action, data) => new Promise((resolve,reject) => {
    const id = ++nextId;
    pending.set(id,{resolve,reject});
    send({action,id,data});
  });

  async function download(url) {
    const response = await fetch(url);
    if (!response.ok) throw Error('Noble could not read the selected file.');
    return new Uint8Array(await response.arrayBuffer());
  }

  async function upload(kind,name,bytes) {
    const url = await invoke('prepare-upload',{kind,name});
    const response = await fetch(url,{method:'POST',body:bytes});
    if (!response.ok) throw Error(await response.text() || 'Noble could not process this file.');
    if (kind === 'decode') return new Uint8Array(await response.arrayBuffer());
    return response.json();
  }

  async function openPhoto(entry) {
    try {
      const bytes = entry.url && await download(entry.url);
      importPhoto({...entry,bytes});
    } catch (error) {
      importPhoto({name:entry.name,error:error.message});
    }
  }
  const queuePhoto = entry => { importQueue = importQueue.then(() => openPhoto(entry)); };

  async function openProject(entry) {
    try {
      const utf8 = await download(entry.url);
      let data = '';
      for (let i=0; i<utf8.length; i+=8192) data += String.fromCharCode(...utf8.subarray(i,i+8192));
      const end = Date.now() + 45000;
      while (!window.nobleProjectLauncher && Date.now() < end) await new Promise(resolve=>setTimeout(resolve,80));
      if (!window.nobleProjectLauncher) throw Error('Video Studio is still starting.');
      await window.nobleProjectLauncher.openLocalProject(entry.name,btoa(data));
    } catch (error) {
      send({action:'show-error',data:error.message});
    }
  }

  // The editor engines deliver finished files through local Blob links.
  document.addEventListener('click', async event => {
    if (!['/image/','/video/'].includes(location.pathname)) return;
    const link = event.target?.closest?.('a[download]');
    if (!link || !link.href.startsWith('blob:' + location.origin + '/')) return;
    event.preventDefault();
    const name = link.download;
    try {
      // Hold the bytes before the engine revokes its temporary URL.
      const bytes = await download(link.href);
      const url = await invoke('save-dialog',{name});
      if (!url) {
        onDelivery?.({file:name,state:'cancelled'});
        return;
      }
      const response = await fetch(url,{method:'POST',body:bytes});
      if (!response.ok) throw Error(await response.text());
      onDelivery?.({file:await response.json(),state:'completed'});
    } catch (error) {
      if (onDelivery) onDelivery({file:name,state:'interrupted'});
      else send({action:'show-error',data:error.message});
    }
  },true);

  socket.addEventListener('message', async ({data:message}) => {
    const {event,data,id} = message;
    if (event === 'reply') {
      const promise = pending.get(id);
      if (!promise) return;
      pending.delete(id);
      if (message.error) promise.reject(Error(message.error));
      else promise.resolve(data);
    } else if (event === 'recent-updated') { window.dispatchEvent(new Event('noble-recent-updated'));
    } else if (event === 'photo') {
      if (importPhoto) queuePhoto(data);
      else pendingPhotos.push(data);
    } else if (event === 'project') {
      await openProject(data);
    } else if (event === 'delivery') onDelivery?.(data);
    else if (event === 'desktop-editor') onEditor?.(data);
    else if (event === 'native-photo-loading') onNativePhotoLoading?.(data);
    else if (event === 'stop') {
      await window.filmcraft?.request('ui.playback',{action:'stop'});
    } else if (event === 'entry') {
      window.nobleProjectLauncher?.openEntry(data);
    } else if (event === 'mcp') {
      const video = data.type === 'video-edit-request';
      const receiver = video ? window.nobleVideoMcp : window.noblePhotoMcp;
      try {
        if (typeof receiver !== 'function') throw Error('The editor is still starting.');
        const args = video ? {tool:data.tool,args:data.args} : {brief:data.brief,finish:data.finish};
        send({action:'mcp-result',id,data:await receiver(args)});
      } catch (error) {
        send({action:'mcp-result',id,data:{error:error.message}});
      }
    }
  });
  Object.defineProperty(window,'nobleNavigation',{value:Object.freeze({
    listRecent: () => invoke('list-recent'),
    openRecent: id => invoke('open-recent',id),
    openWorkspace: href => send({action:'navigate',data:href}),
    onPhotoDelivery: callback => { onDelivery = callback; },
    onPhotoImport: callback => {
      importPhoto = callback;
      for (const entry of pendingPhotos.splice(0)) queuePhoto(entry);
    },
    onDesktopEditor: callback => { onEditor = callback; },
    onNativePhotoLoading: callback => { onNativePhotoLoading = callback; },
    decodePhonePhoto: (name,bytes) => upload('decode',name,bytes),
    saveVideoExport: (name,bytes) => upload('video',name,bytes),
    saveVideoProject: (name,bytes) => upload('project',name,bytes),
    photoPreferences: Object.freeze({
      read: () => invoke('photo-preferences'),
      write: value => invoke('photo-preferences',value),
    }),
  })});
})();
