# GlassClient — Architecture & Roadmap

A Minecraft launcher + client-side mod suite in the spirit of Lunar Client
(FPS boost, HUD, cosmetics, PvP utilities) with one hard rule: **no telemetry,
no data sale, no ads.** Whatever analytics exist must be opt-in, minimal, and
disclosed.

## Why this is two separate hard projects, not one

"A launcher like Lunar Client" is actually two independent engineering
efforts glued together:

1. **The launcher** (`/launcher`) — an app that logs you into your Microsoft
   account, downloads the right Minecraft version + libraries, and starts the
   JVM with our mods on the classpath. This is well-documented, testable
   without touching Minecraft's internals, and realistic to build solo.
2. **The client mod suite** (`/client-mod`) — code that runs *inside* the
   Minecraft process and changes its behavior (HUD, FPS optimizations, PvP
   info overlays, cosmetics rendering). This is genuinely hard: Minecraft's
   jar is obfuscated, mods have to be bytecode-woven in via
   [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) rather than
   edited as source, and every new Minecraft version can break every mixin.
   This is the part that is a full-time job for Lunar Client's engineering
   team — treat it as a multi-month effort, built one small mixin at a time,
   not a single sprint.

Building the launcher first is deliberate: it's a complete, shippable,
testable product on its own (basically a privacy-respecting alternative to
the official Minecraft Launcher), and it's the foundation the mod suite
plugs into later.

## Repo layout

```
GlassClient/
  launcher/       Electron + React + TypeScript app
  client-mod/     Java + Gradle project, SpongePowered Mixin
  docs/           design notes, privacy policy draft, legal notes
  ARCHITECTURE.md this file
```

## Launcher (`/launcher`)

Stack: Electron (main process, Node/TS) + React/Vite (renderer) + TypeScript
throughout.

Responsibilities:
- **Auth** — Microsoft OAuth 2.0 device-code flow → Xbox Live token → XSTS
  token → Minecraft Services API token → profile (username/UUID/skin). This
  is the same flow the official launcher and every third-party launcher
  (MultiMC, Prism) use since Mojang accounts were retired. Needs an Azure AD
  app registration (client ID) — free, but you (the account holder) have to
  create it at portal.azure.com. **Blocker: I need you to create this and
  give me the client ID before auth can be tested end-to-end.**
- **Version/asset management** — fetch Mojang's public
  `piston-meta` version manifest, download the client jar + libraries +
  assets for the selected version, verify against published SHA1 hashes,
  cache locally.
- **Launch** — build the JVM command line (classpath = vanilla jar + our mod
  jar + libraries, main class, auth args) and spawn the process.
- **Mod/cosmetics UI** — toggle mods on/off, browse cosmetics (self-hosted,
  not sold data — a cosmetic store is a legitimate, privacy-safe revenue
  model if you want monetization later).
- **Explicitly not included**: any analytics SDK, ad SDK, or third-party
  tracking pixel. If we ever want crash reporting, it has to be self-hosted
  and opt-in with a clear toggle.

This is the part we can build and test right now without Minecraft-internals
risk.

## Client mod suite (`/client-mod`)

Stack: Java 17/21 (matching modern Minecraft), Gradle, SpongePowered Mixin
(standalone, not via Forge/Fabric — matches your choice to inject the way
Lunar does).

High-level pipeline:
1. A Java agent (`-javaagent:glassclient.jar`) or a small bootstrap main
   class attaches before Minecraft's main class runs.
2. `MixinBootstrap.init()` sets up the Mixin environment; we register our
   `mixins.glassclient.json` config.
3. Mixins are written against **Mojang's official mappings** (published
   alongside each release since 1.14.4 — this is what makes it feasible to
   target vanilla directly without a full deobfuscation pipeline like older
   clients needed).
4. After mixins are registered, we reflectively invoke Minecraft's real
   `main()` — from that point on, our mixed-in code runs as part of the
   normal game loop.
5. First real milestones, roughly in order of difficulty:
   - HUD overlay (FPS counter, coordinates) — mixes into the render/HUD
     class, easiest possible first mixin, good pipeline smoke test.
   - Keystrokes/CPS display — input handling mixin.
   - Cosmetics rendering — hook into player entity render, draw a cape/hat
     model, backed by our own asset service (not Lunar's).
   - PvP info overlays (reach, hit-boxes) — render-layer mixins over
     entities.
   - Performance mods (render distance culling, particle limits) — touches
     more core rendering code, higher regression risk, do this once the
     pipeline is proven on the easier mods.

**Version support is a real cost**: every mixin is written against one
Minecraft version's mappings. Supporting multiple versions (like Lunar does)
means either maintaining per-version mixin sets or picking one version to
support well first (recommend: pick the current latest release, prove the
whole pipeline works, then decide whether multi-version support is worth it).

## Legal / policy notes (read before shipping)

- Distributing *your own mod code* is fine. Distributing *Mojang's game
  files* is not — the launcher must download them from Mojang's official
  endpoints per-user (using their own licensed account), the same way every
  legitimate third-party launcher (MultiMC, Prism, Lunar) does. Never bundle
  Minecraft jars in this repo or in installer artifacts.
- Follow the [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
  — no cheat features that give an unfair advantage in ways that violate
  those guidelines (e.g. no reach/killaura-style automation; HUD info
  overlays and cosmetics are the same category Lunar/Badlion already ship
  and are broadly accepted).
- Some servers' anti-cheat will flag any non-vanilla client regardless of
  intent — worth a disclosure in the launcher UI, not something to hide.
- Write the actual privacy policy (`/docs/privacy-policy.md`) early and keep
  it true — the entire point of this project is that it's actually
  trustworthy, not just marketed as such.

## Immediate next steps

1. Scaffold `/launcher` (Electron + React + TS shell, no auth wired yet).
2. Scaffold `/client-mod` (Gradle + Mixin dependency, one placeholder mixin).
3. You create an Azure AD app registration → give me the client ID → I wire
   up real Microsoft auth in the launcher.
4. First real end-to-end milestone: launcher signs you in, downloads vanilla
   Minecraft, launches it with zero mods — proves the launcher works before
   any mod-injection complexity gets added.
