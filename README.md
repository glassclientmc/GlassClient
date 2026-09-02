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
- **`client-mod/` — the mixin pipeline works end to end, confirmed
  2026-09-02, against real Minecraft (1.21.8).** Two real, visually
  confirmed features running in-game: a Lunar-styled HUD overlay
  (FPS + coordinates, small text on a semi-transparent box) and a real
  in-game settings screen (Right Shift, Lunar's own default key) to toggle
  them. Getting here took finding and fixing seven separate real bugs —
  a broken Mixin phase transition, wrong class-name format, Mojang's
  shipped jar being obfuscated, ARGB alpha handling, a non-deterministic
  Gradle/annotation-processor interaction, missing transitive compile
  deps, and a real game crash in Minecraft's own blur-background code.
  Full writeup of each in [client-mod/README.md](client-mod/README.md).
  The launcher auto-detects and wires in the mod jar (via `-javaagent`)
  whenever both it and a matching deobfuscated Minecraft jar exist for the
  selected version — currently just 1.21.8, not whatever's "latest"
  (Mojang hasn't published official mappings for the 26.x line yet).
- Public repo: [github.com/glassclientmc/GlassClient](https://github.com/glassclientmc/GlassClient)

## Next steps

1. `client-mod`: more feature mixins on top of the now-proven pipeline, in
   the order [ARCHITECTURE.md](ARCHITECTURE.md) lays out — keystrokes/CPS
   display next, then cosmetics, PvP overlays, performance tweaks. See
   [client-mod/README.md](client-mod/README.md)'s "Next milestone" section.
2. Consider auto-detecting/bundling the right JDK per Minecraft version
   rather than relying on whatever `JAVA_HOME` happens to point at — right
   now switching between Minecraft versions with different Java
   requirements means manually repointing `JAVA_HOME`.
3. Revisit whether to build real obfuscation-aware remapping (so client-mod
   can target whatever's actually "latest" instead of being pinned to
   1.21.8) once Mojang publishes mappings for a newer line, or once that
   gap actually blocks something real.

## Repo layout

```
GlassClient/
  launcher/       Electron + React + TypeScript launcher app
  client-mod/     Java + Gradle, SpongePowered Mixin client mods
  docs/           privacy policy, design notes
  ARCHITECTURE.md full technical plan, including the legal/policy notes
```
