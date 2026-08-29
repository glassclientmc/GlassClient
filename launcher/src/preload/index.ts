import { contextBridge, ipcRenderer } from 'electron'
import type { DeviceCodePrompt, MinecraftSession } from '../main/services/msAuth'
import type { VersionManifest } from '../main/services/mojangApi'

const api = {
  signIn: (): Promise<MinecraftSession> => ipcRenderer.invoke('auth:sign-in'),
  onDeviceCodePrompt: (callback: (prompt: DeviceCodePrompt) => void): (() => void) => {
    const listener = (_event: Electron.IpcRendererEvent, prompt: DeviceCodePrompt) => callback(prompt)
    ipcRenderer.on('auth:device-code', listener)
    return () => ipcRenderer.removeListener('auth:device-code', listener)
  },
  listVersions: (): Promise<VersionManifest> => ipcRenderer.invoke('mojang:list-versions')
}

contextBridge.exposeInMainWorld('api', api)

export type GlassClientApi = typeof api
