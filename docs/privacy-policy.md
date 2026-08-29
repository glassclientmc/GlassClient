# GlassClient Privacy Policy (draft)

This is a draft, written early on purpose — the whole point of GlassClient is
that this document stays true as the software grows, not that it sounds good
once and drifts. Revisit it every time a feature touches user data.

## What we collect

- **Nothing, by default.** No analytics SDK, no ad SDK, no crash reporter is
  bundled or enabled out of the box.
- **Microsoft/Xbox/Minecraft auth tokens** — handled entirely between your
  device and Microsoft's/Mojang's own servers via the standard device-code
  OAuth flow. GlassClient's own servers (if any exist later, e.g. for a
  cosmetics store) never see your Microsoft password or auth tokens; they
  stay in the Electron main process only.
- **Local settings** (window size, selected Minecraft version, enabled mods)
  — stored only on your own machine.

## What we will never do

- Sell or share user data with third parties, for advertising or any other
  purpose.
- Bundle third-party ad or tracking SDKs.
- Silently enable telemetry by default — anything added later must be
  opt-in, with a clear toggle, off until you turn it on.

## If we ever add optional telemetry or crash reporting

It will be:
- Off by default.
- Self-hosted, not routed through a third-party analytics vendor.
- Documented here, specifically, before it ships — not folded into a vague
  "may collect usage data" clause.

## Third-party services this software talks to

- `login.microsoftonline.com`, `user.auth.xboxlive.com`,
  `xsts.auth.xboxlive.com`, `api.minecraftservices.com` — required to sign
  you into your own Microsoft/Minecraft account. Governed by Microsoft's own
  privacy policy for that traffic, not ours.
- `piston-meta.mojang.com` and related Mojang download endpoints — required
  to download the actual Minecraft game files you're licensed to run.

No other network calls exist in this codebase as of this draft. Keep this
list accurate as code is added.
