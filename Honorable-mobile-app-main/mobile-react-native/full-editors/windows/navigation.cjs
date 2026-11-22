const {contextBridge, ipcRenderer} = require('electron');

contextBridge.exposeInMainWorld('nobleNavigation', {
  openWorkspace: href => {
    if (typeof href === 'string' && href.length < 256) ipcRenderer.send('noble:workspace', href);
  },
  onPhotoDelivery: callback => {
    ipcRenderer.on('noble:photo-delivery', (_event, result) => callback(result));
  },
  saveVideoExport: (name,bytes) => ipcRenderer.invoke('noble:video-mcp-save',name,bytes),
  saveVideoProject: (name,bytes) => ipcRenderer.invoke('noble:video-mcp-save',name,bytes,'project'),
  photoPreferences: {
    read: () => ipcRenderer.invoke('noble:photo-preferences'),
    write: value => ipcRenderer.invoke('noble:photo-preferences', value),
  },
});
