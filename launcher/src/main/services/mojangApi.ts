/**
 * Mojang's public piston-meta endpoints — no auth required. Same source
 * every launcher (official or third-party) reads version info from.
 */

const VERSION_MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'

export interface VersionSummary {
  id: string
  type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha'
  url: string
  releaseTime: string
}

export interface VersionManifest {
  latest: { release: string; snapshot: string }
  versions: VersionSummary[]
}

export async function fetchVersionManifest(): Promise<VersionManifest> {
  const res = await fetch(VERSION_MANIFEST_URL)
  if (!res.ok) throw new Error(`Failed to fetch version manifest: ${res.status}`)
  return res.json()
}

export interface DownloadArtifact {
  sha1: string
  size: number
  url: string
}

export interface VersionDetail {
  id: string
  mainClass: string
  downloads: { client: DownloadArtifact }
  libraries: Array<{
    name: string
    downloads?: { artifact?: DownloadArtifact }
    rules?: Array<{ action: 'allow' | 'disallow'; os?: { name?: string } }>
  }>
  assetIndex: { id: string; url: string; sha1: string }
}

export async function fetchVersionDetail(versionUrl: string): Promise<VersionDetail> {
  const res = await fetch(versionUrl)
  if (!res.ok) throw new Error(`Failed to fetch version detail: ${res.status}`)
  return res.json()
}
