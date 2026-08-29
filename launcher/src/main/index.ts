import 'dotenv/config'
import { app, shell, BrowserWindow, ipcMain } from 'electron'
import { join } from 'node:path'
import { signInWithMicrosoft, type DeviceCodePrompt } from './services/msAuth'
import { fetchVersionManifest } from './services/mojangApi'

function createWindow(): void {
  const mainWindow = new BrowserWindow({
    width: 960,
    height: 640,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })

  mainWindow.on('ready-to-show', () => mainWindow.show())

  // Open real links in the OS browser, never in-app.
  mainWindow.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url)
    return { action: 'deny' }
  })

  ipcMain.handle('auth:sign-in', async (event) => {
    const onPrompt = (prompt: DeviceCodePrompt) => {
      event.sender.send('auth:device-code', prompt)
    }
    return signInWithMicrosoft(onPrompt)
  })

  ipcMain.handle('mojang:list-versions', async () => {
    return fetchVersionManifest()
  })

  if (!app.isPackaged && process.env['ELECTRON_RENDERER_URL']) {
    mainWindow.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(() => {
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
