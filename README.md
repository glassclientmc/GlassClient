# GlassClient

A Minecraft launcher + client mod suite in the spirit of Lunar Client
(FPS boost, HUD, cosmetics, PvP utilities) — with one hard rule: no
telemetry, no data sale, no ads. See [ARCHITECTURE.md](ARCHITECTURE.md) for
the full technical plan and [docs/privacy-policy.md](docs/privacy-policy.md)
for the policy this project is actually held to.

## Status

Early scaffold. Not runnable end-to-end yet.

- **`launcher/`** — Electron + React + TypeScript app. Builds cleanly
  (`npm run build` verified). Microsoft device-code auth and Mojang version
  manifest fetching are implemented against the real APIs, but auth is
  untested end-to-end because it needs an Azure AD app registration (client
  ID) — see below. Downloading + launching the actual game isn't wired up
  yet.
- **`client-mod/`** — Java + Gradle + SpongePowered Mixin project. Written
  but **unverified** — this machine had only Java 8 and no Gradle installed,
  so nothing here has been compiled. See [client-mod/README.md](client-mod/README.md)
  for what's needed to actually build it.

## Next steps to get a working end-to-end demo (launcher only, no mods yet)

1. Install **Node.js** (already have it) is done; for the mod side you'll
   separately need **JDK 21** — the Java 8 on this machine won't work for
   either Minecraft itself or `client-mod`.
2. Create a free Azure AD app registration at portal.azure.com → App
   registrations → New registration → public client, no redirect URI needed
   for the device-code flow. Copy its Application (client) ID.
3. In `launcher/`, create a `.env` (already gitignored) or set the
   `MS_AUTH_CLIENT_ID` environment variable to that client ID.
4. `cd launcher && npm run dev` — sign in with a real Microsoft/Minecraft
   account, confirm the device-code flow completes and your profile shows up.
5. From there: implement the actual download-and-launch step (fetch version
   detail, download client jar + libraries + assets, verify hashes, spawn
   the JVM) — this is the next real milestone, not yet built.

## Repo layout

```
GlassClient/
  launcher/       Electron + React + TypeScript launcher app
  client-mod/     Java + Gradle, SpongePowered Mixin client mods
  docs/           privacy policy, design notes
  ARCHITECTURE.md full technical plan, including the legal/policy notes
```
