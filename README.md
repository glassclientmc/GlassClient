# GlassClient

A Minecraft launcher + client mod suite in the spirit of Lunar Client
(FPS boost, HUD, cosmetics, PvP utilities) — with one hard rule: no
telemetry, no data sale, no ads. See [ARCHITECTURE.md](ARCHITECTURE.md) for
the full technical plan and [docs/privacy-policy.md](docs/privacy-policy.md)
for the policy this project is actually held to.

## Status

- **`launcher/`** — Electron + React + TypeScript app. Builds cleanly
  (`npm run build` verified) and runs (`npm run dev` verified). Full
  Microsoft device-code auth → Xbox Live → XSTS chain is implemented and
  confirmed working end-to-end against the real APIs. Blocked on the very
  last step: `api.minecraftservices.com/authentication/login_with_xbox`
  returns 403 "Invalid app registration" for any newly created Azure app —
  Microsoft requires manual approval for new apps to use the Minecraft API.
  Submitted the approval request at `https://aka.ms/mce-reviewappid` on
  2026-08-29 (client ID `71f42801-625c-45a5-8c21-4348ea488c4d`) — waiting to
  hear back. Everything downstream of that (profile fetch, version download,
  launch) is unaffected by this and can be built while waiting.
- **`client-mod/`** — Java + Gradle + SpongePowered Mixin project. Written
  but **unverified** — this machine had only Java 8 and no Gradle installed,
  so nothing here has been compiled. See [client-mod/README.md](client-mod/README.md)
  for what's needed to actually build it.
- Public repo: [github.com/glassclientmc/GlassClient](https://github.com/glassclientmc/GlassClient)

## Next steps

1. **Waiting on Mojang/Microsoft's app approval** (see above) — once that
   comes through, `npm run dev` in `launcher/` should complete a real
   sign-in and show your Minecraft profile.
2. While waiting: implement the actual download-and-launch step (fetch
   version detail, download client jar + libraries + assets, verify hashes,
   spawn the JVM) — this is the next real milestone and doesn't depend on
   the pending approval.
3. Separately, whenever you're ready to start on `client-mod`: install
   **JDK 21** (this machine only has Java 8) and Gradle.

## Repo layout

```
GlassClient/
  launcher/       Electron + React + TypeScript launcher app
  client-mod/     Java + Gradle, SpongePowered Mixin client mods
  docs/           privacy policy, design notes
  ARCHITECTURE.md full technical plan, including the legal/policy notes
```
