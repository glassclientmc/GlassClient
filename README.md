# GlassClient

A Minecraft launcher + client mod suite in the spirit of Lunar Client
(FPS boost, HUD, cosmetics, PvP utilities) — with one hard rule: no
telemetry, no data sale. See [ARCHITECTURE.md](ARCHITECTURE.md) for the full
technical plan and [docs/privacy-policy.md](docs/privacy-policy.md) for the
policy this project is actually held to.

## Status

- **`launcher/` — fully working end to end, confirmed 2026-09-01.** Sign in
  with Microsoft → Xbox Live → XSTS → Minecraft Services all succeed for
  real (Mojang approved API access on 2026-09-01, ~3 days after request),
  the download pipeline pulls and hash-verifies the real client jar +
  libraries, and clicking Play actually spawns vanilla Minecraft and it
  runs. This is the real, complete "launcher" half of the project. One
  version-specific gotcha worth knowing: the latest Minecraft release
  (26.2) needs **JDK 25**, not 21 — the launcher spawns whatever JDK
  `JAVA_HOME` points at, so that needs to be current for whichever version
  is selected.
- **`client-mod/`** — Java + Gradle + SpongePowered Mixin project. JDK 21 +
  Gradle wrapper are set up and the build genuinely works, including a
  from-scratch custom `IMixinService` (Forge/Fabric's built-in ones don't
  work standalone) that correctly boots with zero errors. **Blocked**: the
  actual mixin bytecode transformation doesn't fire yet — a real, narrowed-
  down bug, not a "haven't tried" gap. Full writeup, what's been ruled out,
  and the leading suspect (with a link to the exact Mixin source in
  question) are in [client-mod/README.md](client-mod/README.md).
- Public repo: [github.com/glassclientmc/GlassClient](https://github.com/glassclientmc/GlassClient)

## Next steps

1. `client-mod`: JDK 21 + Gradle bootstrap work correctly, but the actual
   mixin bytecode transformation doesn't fire yet — see
   [client-mod/README.md](client-mod/README.md) for the detailed writeup
   and leading suspect. This is the next real blocker for adding any actual
   FPS/HUD/cosmetics/PvP features.
2. Once that's fixed: get `ExampleHudMixin` actually working against a real
   Minecraft class (needs the Mojang-mapped compile-time jar, see
   [client-mod/README.md](client-mod/README.md)), then wire the built mod
   jar into `launch.ts`'s classpath/`-javaagent` args so Play actually loads
   it.
3. Consider auto-detecting/bundling the right JDK per Minecraft version
   rather than relying on whatever `JAVA_HOME` happens to point at — right
   now switching between Minecraft versions with different Java
   requirements means manually repointing `JAVA_HOME`.

## Repo layout

```
GlassClient/
  launcher/       Electron + React + TypeScript launcher app
  client-mod/     Java + Gradle, SpongePowered Mixin client mods
  docs/           privacy policy, design notes
  ARCHITECTURE.md full technical plan, including the legal/policy notes
```
