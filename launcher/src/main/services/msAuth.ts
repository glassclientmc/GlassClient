/**
 * Microsoft device-code auth -> Xbox Live -> XSTS -> Minecraft Services.
 * Same flow every legitimate third-party launcher (MultiMC, Prism) uses
 * since Mojang accounts were retired. Requires an Azure AD app registration
 * (public client, no secret) — set MS_AUTH_CLIENT_ID before this will work.
 */

const CLIENT_ID = process.env.MS_AUTH_CLIENT_ID ?? ''
const SCOPE = 'XboxLive.signin offline_access'

export interface DeviceCodePrompt {
  userCode: string
  verificationUri: string
  expiresInSeconds: number
  message: string
}

export interface MinecraftSession {
  minecraftAccessToken: string
  profile: {
    id: string
    name: string
  }
}

interface DeviceCodeResponse {
  device_code: string
  user_code: string
  verification_uri: string
  expires_in: number
  interval: number
  message: string
}

interface MsTokenResponse {
  access_token: string
  refresh_token?: string
  expires_in: number
  error?: string
  error_description?: string
}

async function requestDeviceCode(): Promise<DeviceCodeResponse> {
  const res = await fetch('https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: CLIENT_ID, scope: SCOPE })
  })
  if (!res.ok) throw new Error(`Device code request failed: ${res.status} ${await res.text()}`)
  return res.json()
}

async function pollForToken(deviceCode: string, intervalSeconds: number, expiresInSeconds: number): Promise<MsTokenResponse> {
  const deadline = Date.now() + expiresInSeconds * 1000
  let interval = intervalSeconds

  while (Date.now() < deadline) {
    await sleep(interval * 1000)

    const res = await fetch('https://login.microsoftonline.com/consumers/oauth2/v2.0/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
        client_id: CLIENT_ID,
        device_code: deviceCode
      })
    })
    const body: MsTokenResponse = await res.json()

    if (res.ok) return body
    if (body.error === 'authorization_pending') continue
    if (body.error === 'slow_down') {
      interval += 5
      continue
    }
    throw new Error(`Microsoft auth failed: ${body.error} — ${body.error_description}`)
  }

  throw new Error('Device code expired before the user signed in.')
}

async function xboxLiveAuth(msAccessToken: string): Promise<{ token: string; uhs: string }> {
  const res = await fetch('https://user.auth.xboxlive.com/user/authenticate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      Properties: {
        AuthMethod: 'RPS',
        SiteName: 'user.auth.xboxlive.com',
        RpsTicket: `d=${msAccessToken}`
      },
      RelyingParty: 'http://auth.xboxlive.com',
      TokenType: 'JWT'
    })
  })
  if (!res.ok) throw new Error(`Xbox Live auth failed: ${res.status} ${await res.text()}`)
  const body = await res.json()
  return { token: body.Token, uhs: body.DisplayClaims.xui[0].uhs }
}

async function xstsAuth(xblToken: string): Promise<{ token: string; uhs: string }> {
  const res = await fetch('https://xsts.auth.xboxlive.com/xsts/authorize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      Properties: { SandboxId: 'RETAIL', UserTokens: [xblToken] },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT'
    })
  })
  const body = await res.json()
  if (!res.ok) {
    if (body.XErr === 2148916233) throw new Error('This Microsoft account has no Xbox Live profile — create one at xbox.com first.')
    if (body.XErr === 2148916238) throw new Error('This Microsoft account is a child account and needs to be added to a Microsoft Family group.')
    throw new Error(`XSTS auth failed: ${res.status} ${JSON.stringify(body)}`)
  }
  return { token: body.Token, uhs: body.DisplayClaims.xui[0].uhs }
}

async function minecraftLogin(xstsToken: string, uhs: string): Promise<string> {
  const res = await fetch('https://api.minecraftservices.com/authentication/login_with_xbox', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identityToken: `XBL3.0 x=${uhs};${xstsToken}` })
  })
  if (!res.ok) throw new Error(`Minecraft login failed: ${res.status} ${await res.text()}`)
  const body = await res.json()
  return body.access_token
}

async function fetchProfile(minecraftAccessToken: string): Promise<{ id: string; name: string }> {
  const res = await fetch('https://api.minecraftservices.com/minecraft/profile', {
    headers: { Authorization: `Bearer ${minecraftAccessToken}` }
  })
  if (res.status === 404) throw new Error('This Microsoft account does not own Minecraft.')
  if (!res.ok) throw new Error(`Profile fetch failed: ${res.status} ${await res.text()}`)
  const body = await res.json()
  return { id: body.id, name: body.name }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * Runs the full sign-in flow. `onPrompt` fires once we have a code the user
 * needs to enter at microsoft.com/link — show it in the UI immediately.
 */
export async function signInWithMicrosoft(onPrompt: (prompt: DeviceCodePrompt) => void): Promise<MinecraftSession> {
  if (!CLIENT_ID) {
    throw new Error(
      'MS_AUTH_CLIENT_ID is not set. Create a free Azure AD app registration ' +
        '(portal.azure.com -> App registrations -> New, public client, ' +
        'no redirect URI needed for device code flow) and set its Application ' +
        '(client) ID as the MS_AUTH_CLIENT_ID env var.'
    )
  }

  const deviceCode = await requestDeviceCode()
  onPrompt({
    userCode: deviceCode.user_code,
    verificationUri: deviceCode.verification_uri,
    expiresInSeconds: deviceCode.expires_in,
    message: deviceCode.message
  })

  const msToken = await pollForToken(deviceCode.device_code, deviceCode.interval, deviceCode.expires_in)
  const xbl = await xboxLiveAuth(msToken.access_token)
  const xsts = await xstsAuth(xbl.token)
  const minecraftAccessToken = await minecraftLogin(xsts.token, xsts.uhs)
  const profile = await fetchProfile(minecraftAccessToken)

  return { minecraftAccessToken, profile }
}
