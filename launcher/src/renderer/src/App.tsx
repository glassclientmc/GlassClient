import { useEffect, useState } from 'react'
import './App.css'

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

type Status = 'idle' | 'awaiting-code' | 'signing-in' | 'signed-in' | 'error'

function App(): React.JSX.Element {
  const [status, setStatus] = useState<Status>('idle')
  const [prompt, setPrompt] = useState<DeviceCodePrompt | null>(null)
  const [session, setSession] = useState<MinecraftSession | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [releaseCount, setReleaseCount] = useState<number | null>(null)

  useEffect(() => {
    return window.api.onDeviceCodePrompt((p) => {
      setPrompt(p)
      setStatus('awaiting-code')
    })
  }, [])

  async function handleSignIn(): Promise<void> {
    setError(null)
    setStatus('signing-in')
    try {
      const result = await window.api.signIn()
      setSession(result)
      setStatus('signed-in')

      const manifest = await window.api.listVersions()
      setReleaseCount(manifest.versions.filter((v) => v.type === 'release').length)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setStatus('error')
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>GlassClient</h1>
        <p className="tagline">A Minecraft launcher that doesn't sell you.</p>
      </header>

      {status === 'idle' && (
        <button className="primary" onClick={handleSignIn}>
          Sign in with Microsoft
        </button>
      )}

      {status === 'awaiting-code' && prompt && (
        <div className="device-code">
          <p>{prompt.message}</p>
          <p className="code">{prompt.userCode}</p>
          <button onClick={() => window.open(prompt.verificationUri)}>
            Open {prompt.verificationUri}
          </button>
        </div>
      )}

      {status === 'signing-in' && !prompt && <p>Starting sign-in…</p>}

      {status === 'signed-in' && session && (
        <div className="profile">
          <p>
            Signed in as <strong>{session.profile.name}</strong>
          </p>
          {releaseCount !== null && (
            <p className="muted">{releaseCount} release versions available.</p>
          )}
        </div>
      )}

      {status === 'error' && error && (
        <div className="error">
          <p>{error}</p>
          <button onClick={() => setStatus('idle')}>Try again</button>
        </div>
      )}

      <footer className="app-footer">
        <p className="muted">No analytics. No ads. No data sold, ever.</p>
      </footer>
    </div>
  )
}

export default App
