const {contextBridge, ipcRenderer} = require('electron');
let photoImportHandler;
const pendingPhotoImports = [];
ipcRenderer.on('noble:photo-import', (_event,file)=>{
  if (photoImportHandler) photoImportHandler(file);
  else pendingPhotoImports.push(file);
});

contextBridge.exposeInMainWorld('nobleNavigation', {
  openWorkspace: href => {
    if (typeof href === 'string' && href.length < 256) ipcRenderer.send('noble:workspace', href);
  },
  onPhotoDelivery: callback => {
    ipcRenderer.on('noble:photo-delivery', (_event, result) => callback(result));
  },
  onPhotoImport: callback => {
    photoImportHandler = callback;
    for (const file of pendingPhotoImports.splice(0)) callback(file);
  },
  onDesktopEditor: callback => {
    ipcRenderer.on('noble:desktop-editor', (_event, kind) => callback(kind));
  },
  decodePhonePhoto: (name,bytes) => ipcRenderer.invoke('noble:decode-phone-photo',name,bytes),
  saveVideoExport: (name,bytes) => ipcRenderer.invoke('noble:video-mcp-save',name,bytes),
  saveVideoProject: (name,bytes) => ipcRenderer.invoke('noble:video-mcp-save',name,bytes,'project'),
  photoPreferences: {
    read: () => ipcRenderer.invoke('noble:photo-preferences'),
    write: value => ipcRenderer.invoke('noble:photo-preferences', value),
  },
});
