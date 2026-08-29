import 'dotenv/config'
import { app, shell, BrowserWindow, ipcMain } from 'electron'
import { join } from 'node:path'
import { signInWithMicrosoft, type DeviceCodePrompt, type MinecraftSession } from './services/msAuth'
import { fetchVersionManifest, fetchVersionDetail, type VersionDetail } from './services/mojangApi'
import { downloadVersion, type DownloadProgress, type GameFiles } from './services/gameFiles'
import { buildLaunchArgs, launchGame } from './services/launch'

const gameDir = join(app.getPath('userData'), 'minecraft')

// Single in-flight session/download — this app only ever runs one game
// instance at a time, so module-scope state is enough (no need for a
// store keyed by window/tab).
let currentSession: MinecraftSession | null = null
const downloadedVersions = new Map<string, { detail: VersionDetail; files: GameFiles }>()

function createWindow(): void {
  const mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#0b0e14',
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })

  mainWindow.on('ready-to-show', () => mainWindow.show())

  mainWindow.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url)
    return { action: 'deny' }
  })

  ipcMain.handle('auth:sign-in', async (event) => {
    const onPrompt = (prompt: DeviceCodePrompt) => {
      event.sender.send('auth:device-code', prompt)
    }
    currentSession = await signInWithMicrosoft(onPrompt)
    return currentSession
  })

  ipcMain.handle('mojang:list-versions', async () => {
    return fetchVersionManifest()
  })

  ipcMain.handle('game:download-version', async (event, versionId: string) => {
    const manifest = await fetchVersionManifest()
    const summary = manifest.versions.find((v) => v.id === versionId)
    if (!summary) throw new Error(`Unknown version: ${versionId}`)

    const detail = await fetchVersionDetail(summary.url)
    const files = await downloadVersion(detail, gameDir, (progress: DownloadProgress) => {
      event.sender.send('game:download-progress', progress)
    })

    downloadedVersions.set(versionId, { detail, files })
  })

  ipcMain.handle('game:launch', async (event, versionId: string) => {
    if (!currentSession) throw new Error('Not signed in.')
    const entry = downloadedVersions.get(versionId)
    if (!entry) throw new Error('Version not downloaded yet.')

    const args = buildLaunchArgs(entry.detail, entry.files, currentSession, {
      gameDir,
      javaPath: 'java',
      maxMemoryMb: 4096
    })
    const child = launchGame(args, { gameDir, javaPath: 'java', maxMemoryMb: 4096 })

    child.stdout?.on('data', (data: Buffer) => event.sender.send('game:log', data.toString()))
    child.stderr?.on('data', (data: Buffer) => event.sender.send('game:log', data.toString()))
    child.on('exit', (code) => event.sender.send('game:exit', code))
    child.on('error', (err) => event.sender.send('game:log', `[GlassClient] Failed to start Java: ${err.message}`))
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
