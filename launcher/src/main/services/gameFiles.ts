import { createHash } from 'node:crypto'
import { createWriteStream } from 'node:fs'
import { mkdir, readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import type { VersionDetail, LibraryEntry } from './mojangApi'

export interface DownloadProgress {
  phase: 'client' | 'libraries' | 'assets'
  completed: number
  total: number
  currentFile: string
}

export interface GameFiles {
  clientJarPath: string
  libraryPaths: string[]
  assetsDir: string
  assetIndexId: string
}

function currentOsName(): 'windows' | 'osx' | 'linux' {
  if (process.platform === 'win32') return 'windows'
  if (process.platform === 'darwin') return 'osx'
  return 'linux'
}

function libraryAllowedOnPlatform(rules?: LibraryEntry['rules']): boolean {
  if (!rules || rules.length === 0) return true
  let allowed = false
  const os = currentOsName()
  for (const rule of rules) {
    const osMatches = !rule.os?.name || rule.os.name === os
    if (osMatches) allowed = rule.action === 'allow'
  }
  return allowed
}

async function fileMatchesHash(path: string, sha1: string): Promise<boolean> {
  try {
    const data = await readFile(path)
    return createHash('sha1').update(data).digest('hex') === sha1
  } catch {
    return false
  }
}

async function downloadFile(url: string, destPath: string, sha1?: string): Promise<void> {
  if (sha1 && (await fileMatchesHash(destPath, sha1))) return

  await mkdir(dirname(destPath), { recursive: true })
  const res = await fetch(url)
  if (!res.ok || !res.body) throw new Error(`Failed to download ${url}: ${res.status}`)

  await pipeline(Readable.fromWeb(res.body as import('node:stream/web').ReadableStream), createWriteStream(destPath))

  if (sha1) {
    const ok = await fileMatchesHash(destPath, sha1)
    if (!ok) throw new Error(`Hash mismatch after downloading ${url} — file may be corrupt, try again`)
  }
}

/** Runs `worker` over `items` with at most `concurrency` in flight at once. */
async function withConcurrency<T>(items: T[], concurrency: number, worker: (item: T) => Promise<void>): Promise<void> {
  let nextIndex = 0
  async function runNext(): Promise<void> {
    const i = nextIndex++
    if (i >= items.length) return
    await worker(items[i])
    return runNext()
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, runNext))
}

interface AssetIndex {
  objects: Record<string, { hash: string; size: number }>
}

/**
 * Downloads the client jar, applicable libraries, and all assets for a
 * version into `gameDir`, verifying SHA1 hashes and skipping files already
 * present and valid. Only targets modern (LWJGL 3 / post-1.19-ish)
 * versions where native libraries ship as regular classpath jars — older
 * versions that need a separate natives-extraction step aren't handled yet.
 */
export async function downloadVersion(
  detail: VersionDetail,
  gameDir: string,
  onProgress: (progress: DownloadProgress) => void
): Promise<GameFiles> {
  const clientJarPath = join(gameDir, 'versions', detail.id, `${detail.id}.jar`)
  onProgress({ phase: 'client', completed: 0, total: 1, currentFile: `${detail.id}.jar` })
  await downloadFile(detail.downloads.client.url, clientJarPath, detail.downloads.client.sha1)
  onProgress({ phase: 'client', completed: 1, total: 1, currentFile: `${detail.id}.jar` })

  const librariesDir = join(gameDir, 'libraries')
  const applicableLibraries = detail.libraries.filter(
    (lib) => libraryAllowedOnPlatform(lib.rules) && lib.downloads?.artifact?.path
  )
  const libraryPaths: string[] = []
  let librariesCompleted = 0
  await withConcurrency(applicableLibraries, 8, async (lib) => {
    const artifact = lib.downloads!.artifact!
    const destPath = join(librariesDir, artifact.path!)
    await downloadFile(artifact.url, destPath, artifact.sha1)
    libraryPaths.push(destPath)
    librariesCompleted++
    onProgress({ phase: 'libraries', completed: librariesCompleted, total: applicableLibraries.length, currentFile: lib.name })
  })

  const assetIndexPath = join(gameDir, 'assets', 'indexes', `${detail.assetIndex.id}.json`)
  await downloadFile(detail.assetIndex.url, assetIndexPath, detail.assetIndex.sha1)
  const assetIndex: AssetIndex = JSON.parse(await readFile(assetIndexPath, 'utf-8'))

  const objectsDir = join(gameDir, 'assets', 'objects')
  const objectEntries = Object.entries(assetIndex.objects)
  let assetsCompleted = 0
  await withConcurrency(objectEntries, 16, async ([name, obj]) => {
    const prefix = obj.hash.slice(0, 2)
    const destPath = join(objectsDir, prefix, obj.hash)
    const url = `https://resources.download.minecraft.net/${prefix}/${obj.hash}`
    await downloadFile(url, destPath, obj.hash)
    assetsCompleted++
    onProgress({ phase: 'assets', completed: assetsCompleted, total: objectEntries.length, currentFile: name })
  })

  return {
    clientJarPath,
    libraryPaths,
    assetsDir: join(gameDir, 'assets'),
    assetIndexId: detail.assetIndex.id
  }
}
