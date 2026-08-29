import { spawn, type ChildProcess } from 'node:child_process'
import { join } from 'node:path'
import type { VersionDetail } from './mojangApi'
import type { GameFiles } from './gameFiles'
import type { MinecraftSession } from './msAuth'

/**
 * Prefers JAVA_HOME when set, rather than trusting whatever `java` an
 * older install may have put first on PATH (bitten by exactly this during
 * development — an old bundled JRE was shadowing a freshly installed JDK
 * until the shell/session restarted).
 */
export function resolveJavaPath(): string {
  const home = process.env.JAVA_HOME
  if (!home) return 'java'
  return join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
}

export interface LaunchOptions {
  gameDir: string
  javaPath: string
  maxMemoryMb: number
}

/**
 * Builds the JVM/game argument list for a modern (LWJGL 3) client — natives
 * ship as regular classpath jars for these versions, so no separate
 * `-Djava.library.path` / natives-extraction step is needed here. Doesn't
 * pass `--clientId`/`--xuid` (used for some Xbox social features, not
 * required for core gameplay) — a real gap versus the official launcher,
 * left out rather than faked.
 */
export function buildLaunchArgs(
  detail: VersionDetail,
  files: GameFiles,
  session: MinecraftSession,
  options: LaunchOptions
): string[] {
  const classpathSeparator = process.platform === 'win32' ? ';' : ':'
  const classpath = [...files.libraryPaths, files.clientJarPath].join(classpathSeparator)

  return [
    `-Xmx${options.maxMemoryMb}M`,
    '-cp',
    classpath,
    detail.mainClass,
    '--username',
    session.profile.name,
    '--version',
    detail.id,
    '--gameDir',
    options.gameDir,
    '--assetsDir',
    files.assetsDir,
    '--assetIndex',
    files.assetIndexId,
    '--uuid',
    session.profile.id,
    '--accessToken',
    session.minecraftAccessToken,
    '--userType',
    'msa',
    '--versionType',
    'release'
  ]
}

export function launchGame(args: string[], options: LaunchOptions): ChildProcess {
  return spawn(options.javaPath, args, { cwd: options.gameDir, stdio: 'pipe' })
}
