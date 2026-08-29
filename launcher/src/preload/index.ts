import { contextBridge, ipcRenderer } from 'electron'
import type { DeviceCodePrompt, MinecraftSession } from '../main/services/msAuth'
import type { VersionManifest } from '../main/services/mojangApi'
import type { DownloadProgress } from '../main/services/gameFiles'

function onEvent<T>(channel: string, callback: (payload: T) => void): () => void {
  const listener = (_event: Electron.IpcRendererEvent, payload: T) => callback(payload)
  ipcRenderer.on(channel, listener)
  return () => ipcRenderer.removeListener(channel, listener)
}

const api = {
  signIn: (): Promise<MinecraftSession> => ipcRenderer.invoke('auth:sign-in'),
  onDeviceCodePrompt: (callback: (prompt: DeviceCodePrompt) => void) => onEvent('auth:device-code', callback),

  listVersions: (): Promise<VersionManifest> => ipcRenderer.invoke('mojang:list-versions'),

  downloadVersion: (versionId: string): Promise<void> => ipcRenderer.invoke('game:download-version', versionId),
  onDownloadProgress: (callback: (progress: DownloadProgress) => void) => onEvent('game:download-progress', callback),

  launchGame: (versionId: string): Promise<void> => ipcRenderer.invoke('game:launch', versionId),
  onGameLog: (callback: (line: string) => void) => onEvent('game:log', callback),
  onGameExit: (callback: (code: number | null) => void) => onEvent('game:exit', callback)
}

contextBridge.exposeInMainWorld('api', api)

export type GlassClientApi = typeof api
