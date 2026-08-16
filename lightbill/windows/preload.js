const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('lightbill',{getConfig:()=>ipcRenderer.invoke('config:get'),setServer:s=>ipcRenderer.invoke('config:set',s),reset:()=>ipcRenderer.invoke('config:reset')});
