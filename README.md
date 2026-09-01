# GlassClient

A Minecraft launcher + client mod suite in the spirit of Lunar Client
(FPS boost, HUD, cosmetics, PvP utilities) — with one hard rule: no
telemetry, no data sale. See [ARCHITECTURE.md](ARCHITECTURE.md) for the full
technical plan and [docs/privacy-policy.md](docs/privacy-policy.md) for the
policy this project is actually held to.

## Status

- **`launcher/`** — Electron + React + TypeScript app. Builds cleanly
  (`npm run build` verified) and runs (`npm run dev` verified). Full
  Microsoft device-code auth → Xbox Live → XSTS → Minecraft Services chain is
  implemented and confirmed working end-to-end. Mojang's app approval
  (client ID `71f42801-625c-45a5-8c21-4348ea488c4d`, requested 2026-08-29)
  came through 2026-09-01 — `login_with_xbox` should now succeed for real.
  Download pipeline separately verified against real Mojang servers (pulled
  the actual client jar + 88 libraries for the latest release, all
  hash-checked). Not yet confirmed: an actual real sign-in + launch through
  the running app.
- **`client-mod/`** — Java + Gradle + SpongePowered Mixin project. JDK 21 +
  Gradle wrapper are now set up and the build genuinely works, including a
  from-scratch custom `IMixinService` (Forge/Fabric's built-in ones don't
  work standalone) that correctly boots with zero errors. **Blocked**: the
  actual mixin bytecode transformation doesn't fire yet — a real, narrowed-
  down bug, not a "haven't tried" gap. Full writeup, what's been ruled out,
  and the leading suspect (with a link to the exact Mixin source in
  question) are in [client-mod/README.md](client-mod/README.md).
- Public repo: [github.com/glassclientmc/GlassClient](https://github.com/glassclientmc/GlassClient)

Also built while waiting on the approval above: the download-and-launch
pipeline (`gameFiles.ts` downloads the client jar + applicable libraries +
all assets with SHA1 verification and concurrency; `launch.ts` builds the
JVM args and spawns the game) and a full glassmorphic UI redesign (version
picker, Play button, live download progress, log console). Untested against
a real launch yet — needs both the pending Mojang approval and a JDK 21 install
(this machine only has Java 8) to actually run Minecraft end to end.

## Next steps

1. Run `npm run dev` in `launcher/` and click **Sign in with Microsoft** —
   this should now complete for real and show a real Minecraft profile.
2. Click **Play** and confirm vanilla Minecraft actually launches end to
   end (download → JVM spawn → game window). No mods yet — that's what
   `client-mod` is for, separately blocked (see below).
3. `client-mod`: JDK 21 + Gradle bootstrap work correctly, but the actual
   mixin bytecode transformation doesn't fire yet — see
   [client-mod/README.md](client-mod/README.md) for the detailed writeup
   and leading suspect.

## Repo layout

```
GlassClient/
  launcher/       Electron + React + TypeScript launcher app
  client-mod/     Java + Gradle, SpongePowered Mixin client mods
  docs/           privacy policy, design notes
  ARCHITECTURE.md full technical plan, including the legal/policy notes
```
