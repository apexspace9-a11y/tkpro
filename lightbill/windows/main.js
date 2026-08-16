const {app,BrowserWindow,ipcMain,shell} = require('electron');
const path=require('path');const fs=require('fs');
let win;
const cfgPath=()=>path.join(app.getPath('userData'),'config.json');
function readCfg(){try{return JSON.parse(fs.readFileSync(cfgPath(),'utf8'))}catch{return {}}}
function saveCfg(c){fs.writeFileSync(cfgPath(),JSON.stringify(c,null,2),'utf8')}
function safeServer(v){try{const u=new URL(String(v||'').trim());return u.protocol==='https:'?u.origin:null}catch{return null}}
function create(){win=new BrowserWindow({width:1280,height:820,minWidth:900,minHeight:650,show:false,backgroundColor:'#f4f7fb',webPreferences:{preload:path.join(__dirname,'preload.js'),contextIsolation:true,nodeIntegration:false,sandbox:true}});win.removeMenu();win.once('ready-to-show',()=>win.show());const cfg=readCfg(),server=safeServer(cfg.server);if(server)loadServer(server);else win.loadFile(path.join(__dirname,'renderer','index.html'));win.webContents.setWindowOpenHandler(({url})=>{if(url.startsWith('https://')||url.startsWith('momo://'))shell.openExternal(url);return {action:'deny'}});win.webContents.on('will-navigate',(e,url)=>{const allowed=safeServer(readCfg().server);if(allowed && url.startsWith(allowed))return;if(url.startsWith('https://')||url.startsWith('momo://'))shell.openExternal(url);e.preventDefault()})}
function loadServer(server){const u=safeServer(server);if(!u)return false;saveCfg({server:u});win.loadURL(u+'/');return true}
ipcMain.handle('config:get',()=>readCfg());ipcMain.handle('config:set',(_,server)=>loadServer(server));ipcMain.handle('config:reset',()=>{saveCfg({});win.loadFile(path.join(__dirname,'renderer','index.html'));return true});
app.whenReady().then(create);app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)create()});
