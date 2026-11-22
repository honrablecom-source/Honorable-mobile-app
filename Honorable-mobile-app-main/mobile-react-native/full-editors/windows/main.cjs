const {app, BrowserWindow, WebContentsView, Menu, ipcMain, dialog, session, utilityProcess, nativeTheme} = require('electron');
const path = require('node:path');
const {createInterface} = require('node:readline');
const {execFile, spawn} = require('node:child_process');
const {promisify} = require('node:util');
const {mkdtempSync, readFileSync, renameSync, statSync, writeFileSync} = require('node:fs');
const {mkdir,mkdtemp,readFile,rename,rmdir,stat,unlink,writeFile} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const nativeStudios = require('./native-studios.json');
const photoLimit = 64 * 1024 * 1024;
const photoExtensions = ['pcraft','psd','psb','ora','png','jpg','jpeg','webp','tif','tiff','bmp','gif','heic','heif'];

const smoke = process.argv.includes('--smoke-test') && process.env.NOBLE_SMOKE_RESULT;
const studioPort = Number(process.argv.find(arg=>arg.startsWith('--studio-port='))?.slice(14));
const port = smoke ? 4263 : Number.isInteger(studioPort) && studioPort >= 1024 && studioPort <= 65535 ? studioPort : 4184;
const origin = 'http://127.0.0.1:' + port;
const model = process.argv.find(argument => argument.startsWith('--model='))?.slice(8)
  || process.env.NOBLE_EDIT_MODEL || 'qwen3:4b';
const preload = path.join(__dirname, 'navigation.cjs');
const icon = path.join(__dirname, '..', '..', 'Noble.ico');
const workspaces = new Map();
const desktopEditors = new Map();
let studio, window, activeView;
let closing = false;
let choosingFile = false;
let desktopEditor;
let closeWhenEditorsFinish = false;
app.setAppUserModelId('Honorable.NobleStudio');
nativeTheme.themeSource = 'dark';

if (smoke) {
  app.setPath('userData', mkdtempSync(path.join(tmpdir(), 'noble-windows-smoke-')));
  app.commandLine.appendSwitch('use-angle', 'swiftshader');
  app.commandLine.appendSwitch('enable-unsafe-swiftshader');
} else {
  const profile = app.commandLine.getSwitchValue('user-data-dir');
  if (profile && path.isAbsolute(profile)) app.setPath('userData',profile);
}

function launchStudio() {
  return new Promise((resolve, reject) => {
    studio = utilityProcess.fork(path.join(__dirname, 'studio', 'full-editors', 'preview.mjs'), ['--port', String(port)], {
      env: {...process.env, NOBLE_EDIT_MODEL: model, NOBLE_WINDOWS_DESKTOP: '1'},
      stdio: 'pipe', serviceName: 'Noble Studio engine',
    });
    const timeout = setTimeout(() => reject(Error('The local editor did not start.')), 30000);
    let output = '';
    studio.stdout.on('data', chunk => {
      output += chunk.toString();
      if (output.includes(origin + '/')) {
        clearTimeout(timeout);
        resolve();
      }
    });
    studio.on('message', async message => {
      if (!['photo-review-request','video-edit-request'].includes(message?.type)) return;
      const video = message.type === 'video-edit-request';
      const reply = {type:video ? 'video-edit-result' : 'photo-review-result',id:message.id};
      const editor = workspaces.get(video ? 'video' : 'image');
      const open = video ? 'Open Noble Video Studio first.' : 'Open a photo in Noble Image Studio first.';
      try {
        if (!editor || (activeView !== editor && !(video && message.tool === 'video_export_status'))) throw Error(open);
        const args = JSON.stringify(video ? {tool:message.tool,args:message.args} : {brief:message.brief,finish:message.finish});
        const receiver = video ? 'nobleVideoMcp' : 'noblePhotoMcp';
        const result = await editor.webContents.executeJavaScript(
          'window.' + receiver + '?.(' + args + ') ?? {error:"The editor is still starting."}'
        );
        if (!video && activeView !== editor) throw Error('Return to Noble Image Studio to review the photo.');
        reply.result = result.result;
        reply.error = result.error;
      } catch (error) {
        reply.error = error.message;
      }
      if (!closing) studio.postMessage(reply);
    });
    studio.once('exit', () => {
      clearTimeout(timeout);
      reject(Error('The local editor could not start. Check whether port ' + port + ' is in use.'));
    });
  });
}

function fitWorkspace() {
  if (!activeView || !windowAvailable()) return;
  const [width, height] = window.getContentSize();
  activeView.setBounds({x:0, y:0, width, height});
}

function detachWorkspace() {
  if (!activeView) return;
  const view = activeView;
  activeView = undefined;
  if (!view.webContents.isDestroyed()) {
    view.webContents.setAudioMuted(true);
    view.webContents.executeJavaScript(
      "window.filmcraft?.request('ui.playback',{action:'stop'})"
    ).catch(() => {});
  }
  window.contentView.removeChildView(view);
}

function routeWorkspace(target, fromEditor) {
  if (!windowAvailable() || target.origin !== origin) return false;
  closeWhenEditorsFinish = false;
  if ((fromEditor || desktopEditor) && target.pathname === '/desktop/home.html') {
    showHome();
    return true;
  }
  if (!fromEditor && ['/desktop/native/image','/desktop/native/video'].includes(target.pathname)) {
    void openDesktopEditor(target.pathname.split('/').at(-1)).catch(reportWorkspaceError);
    return true;
  }
  if (!fromEditor && ['/video/', '/image/'].includes(target.pathname)) {
    const kind = target.pathname.split('/')[1];
    let launch;
    if (target.searchParams.get('entry') === 'open') {
      launch = kind === 'image' ? openPhotosFromHome() : openVideoProjectFromHome();
    } else launch = showStudio(kind, target.href);
    launch.catch(reportWorkspaceError);
    return true;
  }
  return false;
}

function protectNavigation(contents, kind) {
  contents.setWindowOpenHandler(() => ({action:'deny'}));
  contents.on('will-navigate', (event, url) => {
    const target = new URL(url);
    if (target.origin !== origin || target.pathname !== (kind ? '/' + kind + '/' : '/desktop/home.html')) {
      event.preventDefault();
      routeWorkspace(target, Boolean(kind));
    }
  });
}

ipcMain.on('noble:workspace', (event, href) => {
  if (!windowAvailable() || typeof href !== 'string' || href.length >= 256) return;
  const isEditor = activeView?.webContents === event.sender;
  const isHome = !activeView && event.sender === window.webContents;
  if (!isHome && !isEditor) return;
  if (event.senderFrame !== event.sender.mainFrame) return;
  try {
    if (new URL(event.senderFrame.url).origin === origin) routeWorkspace(new URL(href), isEditor);
  } catch {
    // Ignore malformed requests from a renderer.
  }
});

ipcMain.handle('noble:video-mcp-save', async (event,name,bytes,kind = 'video') => {
  const view = workspaces.get('video');
  if (!view || view.webContents !== event.sender || event.senderFrame !== event.sender.mainFrame ||
      new URL(event.senderFrame.url).origin !== origin || new URL(event.senderFrame.url).pathname !== '/video/') {
    throw Error('Only Noble Video Studio can save this file.');
  }
  if (kind !== 'video' && kind !== 'project') throw Error('Unknown Noble file format.');
  const project = kind === 'project';
  const limit = project ? 32 : 100;
  if (typeof name !== 'string' || !ArrayBuffer.isView(bytes) || bytes.byteLength < 16 || bytes.byteLength > limit*1024*1024) {
    throw Error(`Noble could not save this file. Maximum size is ${limit} MB.`);
  }
  const buffer = Buffer.from(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  if (project) {
    const input = JSON.parse(buffer.toString('utf8'));
    if (input?.format !== 'filmcraft.project' || !input.project?.items) throw Error('Noble did not return a valid project.');
  } else if (buffer.toString('ascii',4,8) !== 'ftyp') throw Error('Noble did not return a valid MP4.');
  const root = path.join(app.getPath('videos'),project ? 'Noble Projects' : 'Noble Exports');
  await mkdir(root,{recursive:true});
  const folder = await mkdtemp(path.join(root,project ? 'Project-' : 'Export-'));
  const base = name.replace(/[^\p{L}\p{N} _-]/gu,' ').trim().slice(0,80) || (project ? 'Project' : 'Video');
  const output = path.join(folder,'Noble - '+base+(project ? '.fcproj' : '.mp4'));
  const incomplete = path.join(folder,'save.part');
  await writeFile(incomplete,buffer,{flag:'wx'});
  await rename(incomplete,output);
  return output;
});

function isPhotoWorkspace(event) {
  const view = workspaces.get('image');
  if (!view || view.webContents !== event.sender || event.senderFrame !== event.sender.mainFrame) return false;
  const source = new URL(event.senderFrame.url);
  return source.origin === origin && source.pathname === '/image/';
}

ipcMain.handle('noble:photo-preferences', (event, value) => {
  if (!isPhotoWorkspace(event)) throw Error('Photo workspace settings are unavailable.');
  const file = path.join(app.getPath('userData'),'photo-workspace.json');
  if (value === undefined) {
    try {
      if (statSync(file).size > 2048) return null;
      return JSON.parse(readFileSync(file,'utf8'));
    } catch (error) {
      if (error.code === 'ENOENT' || error instanceof SyntaxError) return null;
      throw error;
    }
  }
  const {dock,width,density,theme,canvasOnly} = value ?? {};
  if (!['left','right'].includes(dock) || !Number.isInteger(width) || width < 240 || width > 440 ||
      !['compact','comfortable','large'].includes(density) || !['warm','neutral'].includes(theme) ||
      typeof canvasOnly !== 'boolean') throw Error('Invalid photo workspace settings.');
  const pending = file + '.tmp';
  writeFileSync(pending,JSON.stringify({dock,width,density,theme,canvasOnly}),'utf8');
  renameSync(pending,file);
  return true;
});

function nativeStudioPath(kind, field = 'executable') {
  const release = nativeStudios[kind];
  return path.join(process.resourcesPath,'native',kind,release.directory,release[field]);
}

function sendEditor(editor,command) {
  if (editor?.process.stdin.writable) editor.process.stdin.write(command+'\n');
}

function showDesktopChrome(kind) {
  desktopEditor = kind;
  if (!windowAvailable()) return;
  window.webContents.send('noble:desktop-editor',kind ?? null);
  if (kind) window.setTitle('Noble Studio — Full '+(kind === 'image' ? 'photo' : 'video')+' tools');
}

function hideDesktopEditor() {
  if (!desktopEditor) return;
  sendEditor(desktopEditors.get(desktopEditor),'hide');
  showDesktopChrome();
}

async function openDesktopEditor(kind) {
  if (!windowAvailable() || closeWhenEditorsFinish) return;
  const executable = nativeStudioPath(kind);
  const host = path.join(__dirname,'NobleEditorHost.exe');
  for (const file of [host,executable]) {
    if (!await stat(file).then(entry=>entry.isFile(),()=>false)) throw Error('Full desktop tools are not installed.');
  }
  if (!windowAvailable() || closeWhenEditorsFinish) return;
  hideDesktopEditor();
  detachWorkspace();
  showDesktopChrome(kind);
  let editor = desktopEditors.get(kind);
  if (!editor) {
    const config = path.join(app.getPath('userData'),'desktop-editor-'+kind);
    const parent = window.getNativeWindowHandle().readBigUInt64LE().toString(16);
    const args = kind === 'image' ? ['--new-instance','--in-window-menus'] : ['--empty','--data-dir',config];
    const processHost = spawn(host,[parent,'54',executable,...args],{
      windowsHide:true, stdio:['pipe','pipe','pipe'],
      env:{...process.env,...(kind === 'image' ? {PHOTOCRAFT_CONFIG_DIR:config} : {})},
    });
    editor = {process:processHost};
    desktopEditors.set(kind,editor);
    processHost.stdin.on('error',()=>{});
    let startupError = '';
    processHost.stderr.on('data',line=>{startupError = (startupError+line).slice(-800);});
    editor.ready = new Promise((resolve,reject)=>{
      const lines = createInterface({input:processHost.stdout});
      lines.on('line',line=>{
        if (line === 'ready') resolve();
      });
      processHost.once('error',reject);
      processHost.once('exit',code=>reject(Error(startupError.trim() || 'The editor host stopped with code '+code+'.')));
    });
    processHost.once('close',()=>{
      desktopEditors.delete(kind);
      if (!windowAvailable()) return;
      if (closeWhenEditorsFinish && desktopEditor === kind) closeDesktopEditors();
      else if (desktopEditor === kind) showHome();
    });
  }
  try {
    await editor.ready;
    if (desktopEditor === kind && windowAvailable()) sendEditor(editor,'show');
  } catch (error) {
    if (desktopEditor === kind) showHome();
    throw error;
  }
}

function requestDesktopEditorClose(event) {
  if (!desktopEditors.size) return false;
  event.preventDefault();
  closeWhenEditorsFinish = true;
  closeDesktopEditors();
  return true;
}

function closeDesktopEditors() {
  const kind = desktopEditors.has(desktopEditor) ? desktopEditor : desktopEditors.keys().next().value;
  if (!kind) {
    window.close();
    return;
  }
  detachWorkspace();
  showDesktopChrome(kind);
  const editor = desktopEditors.get(kind);
  void editor.ready.then(()=>{
    if (!closeWhenEditorsFinish || desktopEditor !== kind) return;
    sendEditor(editor,'show');
    sendEditor(editor,'close');
  }).catch(()=>{});
}

ipcMain.handle('noble:decode-phone-photo', async (event,name,bytes) => {
  if (!windowAvailable() || !isPhotoWorkspace(event) || workspaces.get('image') !== activeView) {
    throw Error('Open Noble Image Studio to import this photo.');
  }
  const extension = typeof name === 'string' ? path.extname(name).toLowerCase() : '';
  if (!['.heic','.heif'].includes(extension) || !ArrayBuffer.isView(bytes) || !bytes.byteLength || bytes.byteLength > photoLimit) {
    throw Error('Choose an HEIC or HEIF photo under 64 MB.');
  }
  const decoder = nativeStudioPath('image','cli');
  if (!await stat(decoder).then(file=>file.isFile(),()=>false)) throw Error('This installation is missing the PhotoCraft HEIC decoder.');
  const scratch = await mkdtemp(path.join(tmpdir(),'noble-photo-import-'));
  const input = path.join(scratch,'photo'+extension);
  const output = path.join(scratch,'photo.png');
  try {
    await writeFile(input,bytes,{flag:'wx'});
    try {
      await promisify(execFile)(decoder,['convert',input,output,'--no-gpu'],{
        cwd:path.dirname(decoder),windowsHide:true,timeout:45000,maxBuffer:65536,
      });
    } catch {
      throw Error('This HEIC or HEIF photo could not be decoded. Try the original in Noble’s full photo workspace.');
    }
    const converted = await stat(output);
    if (!converted.size || converted.size > photoLimit) {
      throw Error('This photo expands beyond 64 MB. Open the original in Noble’s full photo workspace.');
    }
    return await readFile(output);
  } finally {
    await Promise.all([input,output].map(file=>unlink(file).catch(()=>{})));
    await rmdir(scratch).catch(()=>{});
  }
});

function reportWorkspaceError(error) {
  if (!windowAvailable()) return;
  if (smoke) return fail(error);
  dialog.showErrorBox('Noble Studio', 'Could not open the workspace. Try opening it again.\n\n' + error.message);
}

function windowAvailable() {
  return !closing && window && !window.isDestroyed();
}

async function pickFiles(options, onFiles) {
  if (!windowAvailable() || choosingFile) return;
  choosingFile = true;
  try {
    const {canceled,filePaths} = await dialog.showOpenDialog(window,options);
    if (!canceled && filePaths.length && windowAvailable()) await onFiles(filePaths);
  } finally {
    choosingFile = false;
  }
}

function pickFile(options,onFile) {
  return pickFiles(options,([file])=>onFile(file));
}

function openPhotosFromHome() {
  return pickFiles({
    title:'Open photos in Noble',
    properties:['openFile','multiSelections'],
    filters:[{name:'Photos and projects',extensions:photoExtensions},{name:'All files',extensions:['*']}],
  },async files=>{
    if (files.length > 12) throw Error('Choose up to 12 photos per import.');
    const contents = await showStudio('image');
    let total = 0;
    for (const file of files) {
      if (!windowAvailable() || contents.isDestroyed()) return;
      const name = path.basename(file);
      try {
        const info = await stat(file);
        if (!info.isFile() || !info.size || info.size > photoLimit) throw Error('Choose a nonempty image or project up to 64 MB.');
        if (total+info.size > 2*photoLimit) throw Error('Choose fewer photos for a batch over 128 MB.');
        const bytes = await readFile(file);
        if (!bytes.length || bytes.length > photoLimit || total+bytes.length > 2*photoLimit) throw Error('Choose smaller photos for this batch.');
        total += bytes.length;
        if (!contents.isDestroyed()) contents.send('noble:photo-import',{name,bytes});
      } catch (error) {
        if (!contents.isDestroyed()) contents.send('noble:photo-import',{name,error:error.message});
      }
    }
  });
}

function openVideoProjectFromHome() {
  return pickFile({
    title:'Open a Noble video project',
    properties:['openFile'],
    filters:[{name:'Noble video project',extensions:['fcproj']}],
  },openLocalVideoProject);
}

async function openLocalVideoProject(file) {
  if (path.extname(file).toLowerCase() !== '.fcproj' || statSync(file).size > 64 * 1024 * 1024) {
    throw Error('Choose a Noble video project file smaller than 64 MB.');
  }
  const contents = await showStudio('video');
  await contents.executeJavaScript(`
    (async () => {
      const deadline = Date.now() + 45000;
      while (!window.nobleProjectLauncher) {
        if (Date.now() > deadline) throw Error('Video Studio did not finish loading.');
        await new Promise(resolve => setTimeout(resolve, 80));
      }
      await window.nobleProjectLauncher.openLocalProject(${JSON.stringify(path.basename(file))},
        ${JSON.stringify(readFileSync(file).toString('base64'))});
    })()
  `);
}
async function showStudio(kind, url = origin + '/' + kind + '/?empty&webgl') {
  if (!windowAvailable()) throw Error('Noble Studio is closing.');
  hideDesktopEditor();
  let view = workspaces.get(kind);
  const fresh = !view;
  if (fresh) {
    view = new WebContentsView({webPreferences:{preload, sandbox:true, contextIsolation:true, nodeIntegration:false, backgroundThrottling:!smoke}});
    view.setBackgroundColor('#101317');
    workspaces.set(kind, view);
    protectNavigation(view.webContents, kind);
    view.webContents.on('render-process-gone', (_event, details) => {
      if (details.reason === 'clean-exit' || !windowAvailable()) return;
      if (activeView === view) showHome();
      workspaces.delete(kind);
      if (!view.webContents.isDestroyed()) view.webContents.close();
      reportWorkspaceError(Error(kind === 'video' ? 'Video Studio stopped.' : 'Image Studio stopped.'));
    });
  }
  if (activeView !== view) {
    detachWorkspace();
    activeView = view;
    window.contentView.addChildView(view);
    fitWorkspace();
  }
  window.setTitle(kind === 'video' ? 'Noble Studio — Video' : 'Noble Studio — Image');
  try {
    if (fresh) await view.webContents.loadURL(url);
    if (windowAvailable() && activeView === view) {
      view.webContents.setAudioMuted(false);
      view.webContents.focus();
      const entry = new URL(url).searchParams.get('entry');
      if (!fresh && kind === 'video' && ['agent','story'].includes(entry)) {
        await view.webContents.executeJavaScript("window.nobleProjectLauncher?.openEntry(" + JSON.stringify(entry) + ")");
      }
    }
  } catch (error) {
    if (windowAvailable() && activeView === view) showHome();
    workspaces.delete(kind);
    if (!view.webContents.isDestroyed()) view.webContents.close();
    throw error;
  }
  return view.webContents;
}
function copyExportForSharing() {
  return pickFile({
    title: 'Choose an exported file to share',
    properties: ['openFile'],
    filters: [{name:'Exported media', extensions:['mp4','webm','png','jpg','jpeg','webp','gif','wav']}],
  },async file=>{
    const powershell = path.join(process.env.SystemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
    await promisify(execFile)(powershell, [
      '-NoProfile', '-NonInteractive', '-STA', '-Command',
      '$ErrorActionPreference = "Stop"; Set-Clipboard -LiteralPath $env:NOBLE_SHARE_SOURCE',
    ], {
      windowsHide:true,
      timeout:10000,
      env:{...process.env, NOBLE_SHARE_SOURCE:file},
    });
    if (windowAvailable()) await dialog.showMessageBox(window, {
      type:'info',
      title:'File copied',
      message:'Paste the file into the app or folder where you want to share it.',
    });
  });
}
function showHome() {
  if (!windowAvailable()) return;
  hideDesktopEditor();
  detachWorkspace();
  window.setTitle('Noble Studio');
  window.webContents.focus();
}
async function runSmokeTest() {
  const videoFixture = process.env.NOBLE_SMOKE_VIDEO ? readFileSync(process.env.NOBLE_SMOKE_VIDEO).toString('base64') : null;
  const routes = await window.webContents.executeJavaScript(`
    (async () => {
      const folder = document.querySelector('#folder');
      const picked = new DataTransfer();
      async function solidPhoto(color) {
        const canvas = document.createElement('canvas');
        const context = canvas.getContext('2d');
        context.fillStyle = color;
        context.fillRect(0, 0, canvas.width, canvas.height);
        return await new Promise(resolve => canvas.toBlob(resolve));
      }
      picked.items.add(new File([await solidPhoto('red')], 'lamp.png', {type:'image/png'}));
      picked.items.add(new File([await solidPhoto('blue')], 'desk.png', {type:'image/png'}));
      const fixture = ${JSON.stringify(videoFixture)};
      if (fixture) {
        const bytes = Uint8Array.from(atob(fixture), value => value.charCodeAt(0));
        picked.items.add(new File([bytes], 'blue-motion.mp4', {type:'video/mp4'}));
      }
      folder.files = picked.files;
      folder.dispatchEvent(new Event('change'));
      const query = document.querySelector('#query');
      query.value = 'lamp';
      query.dispatchEvent(new Event('input'));
      const matches = document.querySelectorAll('#results button');
      if (matches.length !== 1 || matches[0].textContent !== 'lamp.png') throw Error('Library search failed');
      matches[0].click();
      await document.querySelector('#preview img').decode();
      document.querySelector('#description').value = 'a solid red image';
      document.querySelector('#find-photos').click();
      const deadline = Date.now() + 60000;
      while (document.querySelector('#find-photos').disabled) {
        if (Date.now() > deadline) throw Error('Photo search timed out');
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      const status = document.querySelector('#status').textContent;
      if (!status.startsWith(fixture ? 'Ranked 3 files' : 'Ranked 2 files') ||
          document.querySelector('#results button').textContent !== 'lamp.png') {
        throw Error('Photo search ranking failed: ' + status);
      }
      return [...document.querySelectorAll('.editors a')].filter(link => link.origin === location.origin && ['/video/','/image/'].includes(link.pathname)).map(link => link.href);
    })()
  `);
  if (routes.length !== 2) throw Error('Missing Studio entry points');
  await showStudio('image', routes.find(route=>new URL(route).pathname === '/image/'));
  const video = await showStudio('video', routes.find(route=>new URL(route).pathname === '/video/'));
  const evidence = await video.executeJavaScript(`
    (async () => {
      const started = Date.now();
      while (!window.filmcraft?.info?.().startupMs) {
        if (Date.now() - started > 45000) throw Error('FilmCraft did not start');
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      const toolbar = document.querySelector('#noble-workspace-bar');
      while (!document.body.classList.contains('noble-video-workspace') || toolbar.querySelector('.workspace-actions').inert || !window.nobleProjectLauncher) {
        if (Date.now() - started > 55000) throw Error('Studio commands did not start: ' + toolbar.querySelector('[role=status]').textContent);
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      const editorCanvas = document.querySelector('#filmcraft_canvas');
      if (!['[data-action=import]','[data-action=export]','#cinematic-open','#cinematic-changes']
          .every(selector => toolbar.querySelector(selector)?.getBoundingClientRect().width > 0) ||
          getComputedStyle(editorCanvas).clipPath === 'none' ||
          document.elementFromPoint(10, toolbar.offsetHeight - 2) === editorCanvas) {
        throw Error('Desktop workspace controls overlap the editor');
      }
      toolbar.querySelector('#cinematic-changes').click();
      if (document.querySelector('#changes-tab').getAttribute('aria-selected') !== 'true') throw Error('AI changes did not open');
      document.querySelector('#cinematic [data-close]').click();
      toolbar.querySelector('[popovertarget="noble-cut-tools"]').click();
      document.querySelector('#noble-agent-open').click();
      if (!document.querySelector('#noble-agent').open) throw Error('The editing agent did not open');
      document.querySelector('#noble-agent [data-close]').click();
      const exportAction = toolbar.querySelector('[data-action=export]');
      exportAction.click();
      const delivery = document.querySelector('#video-delivery');
      const deliveryDeadline = Date.now() + 18000;
      while (!delivery.open && Date.now() < deliveryDeadline) await new Promise(resolve=>setTimeout(resolve,70));
      if (!delivery.open) throw Error('Export dialog did not open');
      delivery.querySelector('[data-advanced]').click();
      const toggleDeadline = Date.now() + 18000;
      while (exportAction.getAttribute('aria-pressed') !== 'true' && Date.now() < toggleDeadline) {
        await new Promise(resolve => setTimeout(resolve, 70));
      }
      if (exportAction.getAttribute('aria-pressed') !== 'true') throw Error('Export command did not open export mode');
      exportAction.click();
      const backDeadline = Date.now() + 18000;
      while (exportAction.getAttribute('aria-pressed') !== 'false' && Date.now() < backDeadline) {
        await new Promise(resolve => setTimeout(resolve, 70));
      }
      if (exportAction.getAttribute('aria-pressed') !== 'false') throw Error('Export command did not return to edit mode');
      const canvas = document.createElement('canvas');
      canvas.width = 160;
      canvas.height = 90;
      const context = canvas.getContext('2d');
      context.fillStyle = '#245885';
      context.fillRect(0, 0, 160, 90);
      const png = await new Promise(resolve => canvas.toBlob(resolve));
      const startPanel = document.querySelector('.workspace-start');
      if (startPanel.hidden) throw Error('Empty Studio did not show the project starter');
      const starterMedia = new DataTransfer();
      starterMedia.items.add(new File([png], 'launcher-test.png', {type:'image/png'}));
      const starterPicker = toolbar.querySelector('input[accept="video/*,image/*"]');
      starterPicker.files = starterMedia.files;
      starterPicker.dispatchEvent(new Event('change'));
      const launchDeadline = Date.now() + 20000;
      while (!startPanel.hidden && Date.now() < launchDeadline) {
        await new Promise(resolve => setTimeout(resolve, 80));
      }
      if (!startPanel.hidden) throw Error('Starter did not create a timeline: ' + startPanel.textContent);
      const firstTimeline = await filmcraft.execute('sequence.inspect');
      if (!firstTimeline.video[0].items.some(item => item.name === 'launcher-test.png')) {
        throw Error('The first project did not place the user media');
      }
      const mcpFet = await fetch('/mcp', {
        method:'POST',
        headers:{'Content-Type':'application/json','MCP-Protocol-Version':'2026-07-28'},
        body:JSON.stringify({jsonrpc:'2.0',id:'smoke-tools',method:'tools/list'}),
      });
      const toolList = (await mcpFet.json()).result?.tools ?? [];
      if (!toolList.some(tool => tool.name === 'draft_cinematic_edit')) throw Error('MCP editing tools are unavailable');
      toolbar.querySelector('[popovertarget="noble-cut-tools"]').click();
      document.querySelector('#story-open').click();
      const storyboard = document.querySelector('#storyboard');
      const picked = new DataTransfer();
      for (const name of ['middle.png', 'first.png', 'last.png']) {
        picked.items.add(new File([png], name, {type:'image/png'}));
      }
      const picker = storyboard.querySelector('input[type=file]');
      picker.files = picked.files;
      picker.dispatchEvent(new Event('change'));
      storyboard.querySelector('[aria-label="Move up first.png"]').click();
      for (const [index, input] of [...storyboard.querySelectorAll('input[type=number]')].entries()) {
        input.value = index + 2;
        input.dispatchEvent(new Event('input'));
      }
      const starts = [...storyboard.querySelectorAll('ol output')].map(output => output.textContent);
      if (starts.join(',') !== '0:00,0:02,0:05') throw Error('Incorrect storyboard start times');
      const calls = [];
      for (const method of ['execute','importFiles']) {
        const invoke = filmcraft[method].bind(filmcraft);
        filmcraft[method] = async (...args) => {
          calls.push(method === 'execute' ? args[0] : method);
          const result = await invoke(...args);
          calls.push('done');
          return result;
        };
      }
      storyboard.querySelector('[data-build]').click();
      const storyDeadline = Date.now() + 35000;
      while (storyboard.open) {
        if (Date.now() > storyDeadline) throw Error('Storyboard did not build: ' + storyboard.querySelector('[role=status]').textContent + ' ' + calls.join(','));
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      const story = await filmcraft.execute('sequence.inspect');
      if (story.durationFrames !== 216 || story.video[0].items.map(item => item.durationFrames).join(',') !== '48,72,96') {
        throw Error('Incorrect storyboard timeline timing');
      }
      await filmcraft.execute('file.saveAs', {path:'/exports/desktop-smoke.fcproj'});
      const project = await filmcraft.readFile('/exports/desktop-smoke.fcproj');
      const reopened = await filmcraft.openProject(new File([project], 'desktop-smoke.fcproj', {type:'application/json'}));
      const restored = await filmcraft.execute('sequence.inspect');
      if (reopened.missingMedia || restored.durationFrames !== 216 ||
          restored.video[0].items.map(item => item.durationFrames).join(',') !== '48,72,96') {
        throw Error('Could not restore the storyboard project');
      }
      const job = await filmcraft.execute('file.exportMedia', {format:'h264',path:'/exports/desktop-smoke.mp4',width:180,height:320,fps:24,bitrateKbps:300,audio:false});
      const end = Date.now() + 75000;
      for (;;) {
        const result = (await filmcraft.execute('jobs.list')).find(entry => entry.id === job.job && entry.finished);
        if (result) {
          if (result.error || result.result?.error) throw Error('MP4 export failed');
          break;
        }
        if (Date.now() > end) throw Error('MP4 export timed out');
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      const bytes = await filmcraft.readFile('/exports/desktop-smoke.mp4');
      if (bytes.length < 500 || String.fromCharCode(...bytes.slice(4, 8)) !== 'ftyp') throw Error('Missing MP4');
      return {agentPanel:true, storyboard:true, storyboardReopened:true, timeline:true, mp4Bytes:bytes.length};
    })()
  `);
  const wasPlaying = await video.executeJavaScript("filmcraft.request('ui.playback',{action:'play'}).then(value => value.playing)");
  if (!wasPlaying) throw Error('Playback did not start before testing Home');
  const before = await video.executeJavaScript("filmcraft.execute('sequence.inspect')");
  showHome();
  const homeState = await window.webContents.executeJavaScript("document.querySelector('#query').disabled === false");
  const returned = await showStudio('video');
  const stoppedOnReturn = await returned.executeJavaScript(`
    (async () => {
      const deadline = Date.now() + 4000;
      while (Date.now() < deadline) {
        if (!(await filmcraft.request('ui.inspect')).playback.playing) return true;
        await new Promise(resolve => setTimeout(resolve, 80));
      }
      return false;
    })()
  `);
  if (!stoppedOnReturn) throw Error('Playback kept running after returning Home');
  showHome();
  await showStudio('video');
  const startedOnReturn = await returned.executeJavaScript("filmcraft.request('ui.inspect').then(state => state.playback.playing)");
  if (startedOnReturn) throw Error('Leaving Home started a stopped timeline');
  const after = await returned.executeJavaScript("filmcraft.execute('sequence.inspect')");
  if (!homeState || returned !== video || before.id !== after.id || before.durationFrames !== after.durationFrames) {
    throw Error('Switching to Home discarded the active workspace');
  }
  const projectFile = path.join(app.getPath('userData'), 'launcher-reopen.fcproj');
  const projectBytes = await video.executeJavaScript("filmcraft.readFile('/exports/desktop-smoke.fcproj').then(bytes=>Array.from(bytes))");
  writeFileSync(projectFile, Buffer.from(projectBytes));
  showHome();
  await openLocalVideoProject(projectFile);
  const reopenedFromHome = await video.executeJavaScript("filmcraft.execute('sequence.inspect')");
  if (reopenedFromHome.durationFrames !== 216) throw Error('The Home open command lost the saved timeline');
  showHome();
  await showStudio('video', origin + '/video/?empty&webgl&entry=agent');
  const agentShortcut = await video.executeJavaScript("document.querySelector('#cinematic').open");
  if (!agentShortcut) throw Error('The Home AI shortcut did not open the agent');
  await video.executeJavaScript("document.querySelector('#cinematic [data-close]').click()");
  writeFileSync(smoke, JSON.stringify({...evidence, workspacePreserved:true, toolbar:true, editorModeSwitch:true, projectStarter:true, playbackStopsOnHome:true, nativeProjectRelay:true, aiShortcut:true, mcpDiscovery:true, library:true, semanticPhotoSearch:true, semanticVideoSearch:Boolean(videoFixture), imageStudioPage:true, version:app.getVersion()}));
  app.quit();
}

function fail(error) {
  if (closing) return;
  if (smoke) writeFileSync(smoke, JSON.stringify({error:error.message}));
  else dialog.showErrorBox('Noble Studio', error.message);
  app.exit(1);
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!windowAvailable() || choosingFile || (!window.isVisible() && !window.isMinimized())) return;
    if (window.isMinimized()) window.restore();
    window.show();
    window.focus();
  });
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', event => {
    if (requestDesktopEditorClose(event)) return;
    closing = true;
    studio?.kill();
  });
  app.whenReady().then(async () => {
    await launchStudio();
    if (closing) return;
    const local = session.defaultSession;
    local.setPermissionRequestHandler((_contents, _permission, done) => done(false));
    local.setPermissionCheckHandler(() => false);
    local.on('will-download', (_event, item, contents) => {
      if (contents !== workspaces.get('image')?.webContents) return;
      const source = new URL(item.getURL());
      if (source.protocol !== 'blob:' || source.origin !== origin) return;
      const file = path.basename(item.getFilename());
      item.setSaveDialogOptions({
        title: path.extname(file).toLowerCase() === '.pcraft' ? 'Save Noble photo project' : 'Export photo',
        defaultPath: path.join(app.getPath('pictures'), file),
      });
      item.once('done', (_done, state) => {
        if (!contents.isDestroyed()) contents.send('noble:photo-delivery', {file:path.basename(item.getSavePath()) || file, state});
      });
    });
    local.webRequest.onBeforeRequest(({url}, done) => {
      const parsed = new URL(url);
      done({cancel: parsed.origin !== origin && !['data:', 'blob:'].includes(parsed.protocol)});
    });
    window = new BrowserWindow({
      title:'Noble Studio', icon, backgroundColor:'#101317',
      width:1360, height:900, minWidth:780, minHeight:520, show:false, autoHideMenuBar:true,
      webPreferences:{preload, sandbox:true, contextIsolation:true, nodeIntegration:false},
    });
    window.on('resize', fitWorkspace);
    window.on('close', event => {
      if (!requestDesktopEditorClose(event)) closing = true;
    });
    window.on('closed', () => {
      for (const view of workspaces.values()) {
        if (!view.webContents.isDestroyed()) view.webContents.close();
      }
      workspaces.clear();
      activeView = undefined;
    });
    window.webContents.on('page-title-updated', event => event.preventDefault());
    protectNavigation(window.webContents);
    Menu.setApplicationMenu(Menu.buildFromTemplate([
      {label:'Studio', submenu:[
        {label:'Home and media library', accelerator:'Ctrl+Shift+H', click:() => showHome()},
        {label:'Video Studio', click:() => showStudio('video').catch(reportWorkspaceError)},
        {label:'Image Studio', click:() => showStudio('image').catch(reportWorkspaceError)},
        {label:'Full photo tools', click:() => openDesktopEditor('image').catch(reportWorkspaceError)},
        {label:'Full video tools', click:() => openDesktopEditor('video').catch(reportWorkspaceError)},
        {type:'separator'}, {role:'quit'},
      ]},
      {label:'Share', submenu:[{label:'Copy exported file', click:() => {
        copyExportForSharing().catch(() => {
          if (windowAvailable()) dialog.showErrorBox('Noble Studio', 'The file could not be copied to the Windows clipboard.');
        });
      }}]},
      {role:'editMenu'},
      {label:'Help', submenu:[{label:'Editing agent setup', click:() => dialog.showMessageBox(window, {
        title:'Editing agent setup', message:'Configured model: ' + model,
        detail:'Start local Ollama and run ollama pull ' + model + ' if the model is missing. Then open Cinematic editing agent in Video Studio. Use --model=NAME when launching NobleStudio.exe to choose a different local model.',
      })}]},
    ]));
    await window.loadURL(origin + '/desktop/home.html');
    if (!windowAvailable()) return;
    if (smoke && process.env.NOBLE_SMOKE_SHOW !== '1') {
      window.setOpacity(0);
      window.setSkipTaskbar(true);
      window.showInactive();
    } else window.show();
    if (smoke) await runSmokeTest();
  }).catch(fail);
}
