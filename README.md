> **Fabric voice port branch:** This branch builds DiscordLink+ Voice for Fabric 26.3 / SVC 2.6.27. See [VOICE-PORT.md](VOICE-PORT.md) for setup and [PORTING.md](PORTING.md) for the commit series, build, and rebase workflow. The original Bukkit README follows for provenance.

# DiscordLink+

A **Paper/Bukkit/Folia-compatible** Minecraft plugin that bridges your server with Discord — similar to [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) — with account linking and **Simple Voice Chat** group bridging.

## Features

### Discord chat bridge
- Minecraft chat → Discord text channel
- Discord messages → Minecraft chat
- Optional join/leave and death notifications

### Discord account linking (optional gate)
1. Player tries to join the server
2. If linking is enabled and they are not linked, they are blocked with a **numeric code**
3. They DM that code to the Discord bot
4. The bot links their Discord account to their Minecraft UUID, saves it, and assigns the configurable **Verified** role

### Simple Voice Chat ↔ Discord voice bridge
- Any **public** Simple Voice Chat group (no password) gets a matching Discord voice channel
- In-game players in that group are heard on Discord
- Discord users in the bridged channel are heard in-game by group members
- Empty groups can auto-delete their Discord channels (configurable)

## Requirements

- **Paper**, **Spigot**, or **Folia** server on **Minecraft 26.1.2** (Java 25 recommended for Discord voice/DAVE)
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) plugin (for voice bridging)
- A Discord bot with these intents enabled in the [Developer Portal](https://discord.com/developers/applications):
  - Message Content
  - Server Members
  - Direct Messages

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/DiscordLinkPlus-1.0.2.jar`

## CI/CD (GitHub Actions)

The **Build** workflow (`.github/workflows/build.yml`) runs on pushes and PRs to `main`/`master`, compiles the plugin, and uploads the JAR as a workflow artifact.

The **Release** workflow (`.github/workflows/release.yml`) publishes to Modrinth, uploads the JAR to the GitHub Release, and notifies Discord when you publish a GitHub Release (or run the workflow manually).

#### One-time setup

1. Generate a [Modrinth PAT](https://modrinth.com/settings/pats) with **Create versions** scope
2. Add GitHub repository secret **`MODRINTH_TOKEN`**
3. (Optional) Add **`DISCORD_WEBHOOK_URL`** for release announcements

#### Cutting a release

1. Bump the version in `gradle.properties` and push to `main`
2. Create a GitHub Release with tag `v<version>` (e.g. `v1.0.2`) — the Release workflow builds, publishes to Modrinth, attaches the JAR, and pings Discord

Or run **Actions → Release → Run workflow** with a version (and optional changelog) to publish without creating a GitHub Release first.

## Setup

1. Create a Discord application/bot and copy the token
2. Invite the bot to your guild with permissions to:
   - Read/send messages in your chat channel
   - Manage roles (for Verified)
   - Manage channels + Connect/Speak (for voice bridging)
3. Copy IDs (enable Developer Mode in Discord → right-click → Copy ID):
   - Guild ID
   - Chat channel ID
   - Verified role ID
   - Voice category ID (recommended)
4. Place the JAR in `plugins/` and start the server
5. Edit `plugins/DiscordLink+/config.yml`
6. Run `/mcdiscord reload`

### Linking flow (players)
1. Join the server → receive kick message with code (if not linked)
2. DM the code to the bot on Discord
3. Rejoin the server

In-game commands:
- `/mcdiscord link` — generate a link code without reconnecting
- `/mcdiscord unlink` — remove your link
- `/mcdiscord status` — check link status

Ops bypass the link requirement by default.

### Voice bridge
1. Install **Simple Voice Chat** on the server (and clients)
2. Open UDP port **24454** (or your configured SVC port)
3. Set `voice-bridge.server-host` / `server-port` in config for reference
4. Create or join a **public** SVC group in-game — a Discord VC named `VC <group name>` appears

## Folia support

This plugin sets `folia-supported: true` and uses [FoliaScheduler](https://github.com/CJCrafter/FoliaScheduler) for region-safe task scheduling.

## License

MIT
