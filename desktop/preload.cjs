const { contextBridge } = require("electron");

contextBridge.exposeInMainWorld("vidGravityDesktop", Object.freeze({
  platform: process.platform,
  appName: "VidGravity",
  cloudConnected: true
}));
