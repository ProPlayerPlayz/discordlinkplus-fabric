# Changelog

## 1.2.1-rc.1 — Fabric 26.3

First attachment-label prerelease in this fork, based on Fabric port 1.2.0.

- Replace incoming Discord upload URLs with `[Image]`, `[GIF]`, `[Video]`, and `[File: name]` labels, preserving captions and upload order.
- Replace recognized Tenor/Giphy GIF-picker links with `[GIF]`, preserving surrounding punctuation and Markdown. Ordinary links remain intact.
- Handle missing MIME types, extensionless uploads, long filenames, and long captions without losing attachment labels.
- Preserve existing Minecraft-to-Discord player chat, voice functionality, mod ID, and upstream attribution.

Requires Java 25, Minecraft 26.3, Fabric Loader 0.19.5 or newer, Fabric API, and Simple Voice Chat 2.6.27. Install the bundled JAR in the server's `mods` directory; `sources` and `unbundled` JARs are not installable releases.

Validation: 48 automated tests passed and the attachment checkpoint passed GitHub Actions. This release candidate still requires actual Discord round trips and voice checks on a disposable server. Role colours and reply previews are not included.

## Earlier versions

See [PORTING.md](PORTING.md) for the inherited Fabric port checkpoints and upstream history.
