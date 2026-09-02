import 'dotenv/config'
import { app, shell, BrowserWindow, ipcMain } from 'electron'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { signInWithMicrosoft, type DeviceCodePrompt, type MinecraftSession } from './services/msAuth'
import { fetchVersionManifest, fetchVersionDetail, type VersionDetail } from './services/mojangApi'
import { downloadVersion, type DownloadProgress, type GameFiles } from './services/gameFiles'
import { buildLaunchArgs, launchGame, resolveJavaPath } from './services/launch'

const gameDir = join(app.getPath('userData'), 'minecraft')

/**
 * client-mod isn't packaged into production builds yet (still an early,
 * single-Minecraft-version feature set — see client-mod/README.md), so this
 * only resolves in a dev checkout where the sibling client-mod/ directory
 * actually exists and has been built. Falls back to vanilla (undefined)
 * otherwise rather than failing the launch.
 */
function resolveModJarPath(): string | undefined {
  const devJarPath = join(
    __dirname,
    '../../../client-mod/build/libs/glassclient-mod-0.1.0-all.jar'
  )
  return existsSync(devJarPath) ? devJarPath : undefined
}

/**
 * Mojang's *shipped* client jar is obfuscated — mixins written against clean
 * Mojang-mapped names (net.minecraft.client.gui.Gui etc.) can't find their
 * targets in it directly, since those names don't exist in the obfuscated
 * jar under that spelling (confirmed via a real ClassNotFoundException at
 * runtime — see client-mod/README.md). Forge/Fabric solve this with a whole
 * runtime remapping pipeline; we sidestep it by running the same
 * deobfuscated jar client-mod's mixins were compiled against instead of the
 * obfuscated one, whenever both the mod jar and a matching mojmap jar exist
 * for the selected version. Only 1.21.8 has one right now.
 */
function resolveMojmapJarPath(versionId: string): string | undefined {
  const jarPath = join(
    __dirname,
    `../../../client-mod/libs/minecraft-${versionId}-mojmap.jar`
  )
  return existsSync(jarPath) ? jarPath : undefined
}

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
    icon: join(__dirname, `../../build/icon.${process.platform === 'win32' ? 'ico' : 'png'}`),
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

    const modJarPath = resolveModJarPath()
    const mojmapJarPath = resolveMojmapJarPath(versionId)
    // Only actually attach the agent when we also have a deobfuscated jar to
    // run it against for this exact version — attaching it against the
    // normal obfuscated jar would just fail every mixin target lookup (see
    // resolveMojmapJarPath's comment).
    const modsActive = modJarPath && mojmapJarPath

    const launchOptions = {
      gameDir,
      javaPath: resolveJavaPath(),
      maxMemoryMb: 4096,
      modJarPath: modsActive ? modJarPath : undefined
    }
    const files = modsActive ? { ...entry.files, clientJarPath: mojmapJarPath } : entry.files
    const args = buildLaunchArgs(entry.detail, files, currentSession, launchOptions)
    const child = launchGame(args, launchOptions)

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
