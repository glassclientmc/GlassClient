import { useEffect, useMemo, useState } from 'react'
import './App.css'
import logo from './assets/logo.png'

type DeviceCodePrompt = {
  userCode: string
  verificationUri: string
  expiresInSeconds: number
  message: string
}

type MinecraftSession = {
  minecraftAccessToken: string
  profile: { id: string; name: string }
}

type VersionSummary = { id: string; type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha'; releaseTime: string }
type VersionManifest = { latest: { release: string; snapshot: string }; versions: VersionSummary[] }

type DownloadProgress = { phase: 'client' | 'libraries' | 'assets'; completed: number; total: number; currentFile: string }

type AuthStatus = 'idle' | 'awaiting-code' | 'signing-in' | 'signed-in' | 'error'
type PlayStatus = 'idle' | 'downloading' | 'launching' | 'running' | 'error'

const phaseLabel: Record<DownloadProgress['phase'], string> = {
  client: 'Downloading client',
  libraries: 'Downloading libraries',
  assets: 'Downloading assets'
}

function App(): React.JSX.Element {
  const [authStatus, setAuthStatus] = useState<AuthStatus>('idle')
  const [prompt, setPrompt] = useState<DeviceCodePrompt | null>(null)
  const [session, setSession] = useState<MinecraftSession | null>(null)
  const [authError, setAuthError] = useState<string | null>(null)

  const [manifest, setManifest] = useState<VersionManifest | null>(null)
  const [selectedVersion, setSelectedVersion] = useState<string>('')

  const [playStatus, setPlayStatus] = useState<PlayStatus>('idle')
  const [progress, setProgress] = useState<DownloadProgress | null>(null)
  const [playError, setPlayError] = useState<string | null>(null)
  const [log, setLog] = useState<string[]>([])

  const releaseVersions = useMemo(
    () => manifest?.versions.filter((v) => v.type === 'release').slice(0, 30) ?? [],
    [manifest]
  )

  useEffect(() => {
    return window.api.onDeviceCodePrompt((p) => {
      setPrompt(p)
      setAuthStatus('awaiting-code')
    })
  }, [])

  useEffect(() => {
    return window.api.onDownloadProgress(setProgress)
  }, [])

  useEffect(() => {
    return window.api.onGameLog((line) => setLog((prev) => [...prev.slice(-200), line]))
  }, [])

  useEffect(() => {
    return window.api.onGameExit(() => setPlayStatus('idle'))
  }, [])

  async function handleSignIn(): Promise<void> {
    setAuthError(null)
    setAuthStatus('signing-in')
    try {
      const result = await window.api.signIn()
      setSession(result)
      setAuthStatus('signed-in')

      const list = await window.api.listVersions()
      setManifest(list)
      setSelectedVersion(list.latest.release)
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : String(err))
      setAuthStatus('error')
    }
  }

  async function handlePlay(): Promise<void> {
    if (!selectedVersion) return
    setPlayError(null)
    setLog([])
    try {
      setPlayStatus('downloading')
      await window.api.downloadVersion(selectedVersion)
      setProgress(null)

      setPlayStatus('launching')
      await window.api.launchGame(selectedVersion)
      setPlayStatus('running')
    } catch (err) {
      setPlayError(err instanceof Error ? err.message : String(err))
      setPlayStatus('error')
    }
  }

  const avatarUrl = session ? `https://crafatar.com/avatars/${session.profile.id}?size=64&overlay` : null

  return (
    <div className="shell">
      <div className="ambient-glow glow-a" />
      <div className="ambient-glow glow-b" />

      <aside className="sidebar">
        <img className="logo" src={logo} alt="GlassClient" />
        <button className="nav-item active" title="Home">
          ⌂
        </button>
        <button className="nav-item" disabled title="Mods (coming soon)">
          ◆
        </button>
        <button className="nav-item" disabled title="Cosmetics (coming soon)">
          ✦
        </button>
        <button className="nav-item" disabled title="Settings (coming soon)">
          ⚙
        </button>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="brand">
            <strong>GlassClient</strong>
          </div>
          {session && avatarUrl && (
            <div className="profile-chip">
              <img src={avatarUrl} alt="" />
              <span>{session.profile.name}</span>
            </div>
          )}
        </header>

        <div className="stage">
          <div className="hero-card">
            {authStatus === 'idle' && (
              <>
                <h1 className="hero-title">Welcome</h1>
                <p className="hero-sub">Sign in with the Microsoft account that owns Minecraft.</p>
                <button className="btn btn-primary" onClick={handleSignIn}>
                  Sign in with Microsoft
                </button>
              </>
            )}

            {authStatus === 'awaiting-code' && prompt && (
              <>
                <h1 className="hero-title">Enter this code</h1>
                <p className="hero-sub">{prompt.message}</p>
                <div className="device-code-value">{prompt.userCode}</div>
                <button className="btn btn-ghost" onClick={() => window.open(prompt.verificationUri)}>
                  Open {prompt.verificationUri}
                </button>
              </>
            )}

            {authStatus === 'signing-in' && !prompt && (
              <>
                <h1 className="hero-title">Starting sign-in…</h1>
              </>
            )}

            {authStatus === 'error' && authError && (
              <>
                <h1 className="hero-title">Sign-in failed</h1>
                <div className="error-banner">{authError}</div>
                <button className="btn btn-ghost" onClick={() => setAuthStatus('idle')}>
                  Try again
                </button>
              </>
            )}

            {authStatus === 'signed-in' && session && (
              <>
                <h1 className="hero-title">Ready to play</h1>
                <p className="hero-sub">Signed in as {session.profile.name}</p>

                {playStatus === 'error' && playError && <div className="error-banner">{playError}</div>}

                <select
                  className="version-select"
                  value={selectedVersion}
                  disabled={playStatus === 'downloading' || playStatus === 'launching'}
                  onChange={(e) => setSelectedVersion(e.target.value)}
                >
                  {releaseVersions.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.id}
                      {v.id === manifest?.latest.release ? ' (latest)' : ''}
                    </option>
                  ))}
                </select>

                <button
                  className="btn btn-primary"
                  onClick={handlePlay}
                  disabled={playStatus === 'downloading' || playStatus === 'launching' || playStatus === 'running'}
                >
                  {playStatus === 'downloading' && 'Downloading…'}
                  {playStatus === 'launching' && 'Launching…'}
                  {playStatus === 'running' && 'Running'}
                  {(playStatus === 'idle' || playStatus === 'error') && 'Play'}
                </button>

                {playStatus === 'downloading' && progress && (
                  <>
                    <div className="progress-track">
                      <div
                        className="progress-fill"
                        style={{ width: `${progress.total ? (progress.completed / progress.total) * 100 : 0}%` }}
                      />
                    </div>
                    <div className="progress-label">
                      <span>
                        {phaseLabel[progress.phase]} ({progress.completed}/{progress.total})
                      </span>
                      <span>{progress.currentFile}</span>
                    </div>
                  </>
                )}

                {log.length > 0 && <div className="log-console">{log.join('')}</div>}
              </>
            )}
          </div>
        </div>
      </div>

      <p className="footer-note">No analytics. No ads. No data sold, ever.</p>
    </div>
  )
}

export default App
