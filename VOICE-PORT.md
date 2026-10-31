# DiscordLink+ — Fabric 26.3 port

This is a text and voice Fabric port based on [codingsushi79/discordlinkplus](https://github.com/codingsushi79/discordlinkplus), upstream commit `6360549041dfa0158deeed3365fce87fe67a61a8`.

Version 1.1.0 adds optional text chat. See [CHAT-SETUP.md](CHAT-SETUP.md) to add it to an existing voice configuration, including offline Skin Restorer avatars. Chat is disabled by default. The original mod ID, voice configuration filename, and JAR naming prefix are retained.

## Behavior

- Each occupied public Simple Voice Chat group of type Normal or Open gets a temporary Discord voice channel, named exactly after the in-game group.
- Isolated groups never appear on Discord and never bridge audio, even if password-protected group bridging is enabled. An existing managed channel is removed if its group becomes isolated.
- Discord-only friends can join that channel and talk to the Minecraft group in both directions.
- Minecraft players use their ordinary SVC clients; they do not need Discord open.
- Ungrouped players are not bridged. Normal Minecraft proximity/group audio is unchanged.
- One bot bridges one group at a time. With one token, additional groups get channels and wait for that bot. A bot stays with its assigned group until that group empties. Add more unique bot tokens for simultaneous active groups.
- Channels are removed when their Minecraft group empties, including disconnects. Discord occupants do not keep an empty Minecraft group's channel alive.
- Password-protected groups are excluded unless `bridgePasswordGroups` is explicitly enabled. Enabling that setting does not reproduce the SVC password as Discord access permissions.
- Audio from multiple Minecraft speakers is mixed in 20 ms frames. Discord speakers are combined by JDA, downmixed to mono, encoded by SVC, and sent only to the matching group.
- Audio buffers are bounded; voice is not recorded to disk. Bot audio is excluded from Discord input to avoid feedback.
- Optional text chat relays public messages, player-name/avatar webhooks, and join/leave/death/advancement announcements through the first bot. See CHAT-SETUP.md. The Discord account-linking gate and remote console are not included.

## Requirements

Minecraft **26.3**, Java **25**, Fabric Loader **0.19.5+**, Fabric API, and Simple Voice Chat **2.6.27**. The same Java runtime must permit native libraries; on Java 25, `--enable-native-access=ALL-UNNAMED` suppresses native access warnings. The bundle includes JDA, Opus, and DAVE natives for Linux x64/ARM64, Windows x64, and macOS.

## Discord setup

1. Create a Discord application and bot, and invite it to your Discord server. Use a **bot token**, not a personal user token.
2. Give it **View Channels**, **Manage Channels**, **Connect**, **Speak**, and **Use Voice Activity** in the category used for temporary channels. It needs to see and join every channel it manages. Administrator permission is unnecessary.
3. Enable Developer Mode in Discord, then copy the **server ID** and the **category ID**. No permanent voice channel ID is required.
4. No Message Content or Server Members privileged intents are needed for this voice-only port.

## Install and configure

1. Stop the Minecraft server and back up any existing voice bridge configuration.
2. Put `DiscordLinkPlus-Voice-1.1.0-fabric.26.3.jar` in `mods/`, alongside Fabric API and SVC. Disable the old JAR when updating; only one version should be enabled.
3. Copy the provided `discordlinkplus-voice.json` to `config/discordlinkplus-voice.json`, or start once to generate a disabled template.
4. Enter `guildId`, `groupCategoryId`, and the token in `botTokens`. Change `enabled` to `true` when ready. Keep tokens private and out of source control/logs. Every bot in the list must be invited to the same server and have the category permissions above.
5. Start the server, or run `/discordvoice reload` after a configuration change. `/discordvoice status` shows bot gateway/voice state, waiting rooms, and audio frame/error counts. These commands require operator level 3 (admin) or the server console.
6. Create a public Normal or Open SVC group in Minecraft. A channel with the same name should appear within a few seconds. Join it from Discord, then test speech in both directions. Creating the channel does not by itself prove that audio is connected; check the status command. Groups with identical names remain separate channels, tracked internally by UUID. Existing managed channels are renamed to match their group's current name.

The mod keeps an ownership registry in `config/discordlinkplus-voice-channels.json`. Preserve it across restarts so the mod can reuse/clean up only its own temporary channels. Manually moving a temporary channel out of its recorded category detaches it from automatic management. Avoid reloads while changing voice groups; a reload briefly disconnects the bridge.

## Build and verification

With JDK 25 selected: `gradlew.bat build` (Windows) or `./gradlew build` (Linux). The installable shaded JAR is in `build/libs/`; do not install the `unbundled` or `sources` JAR.

Validated locally: 17 automated checks (mixing, clipping, stale/overloaded queues, endian conversion, separate-group routing, isolated-group exclusion, private-group routing, exact channel names, config validation). The initial build also passed shaded Opus and DAVE native initialization, packaged JAR startup on an isolated Fabric 0.19.5 / Minecraft 26.3 server, and SVC native encode/decode and both room audio conversion paths through a disposable test addon.

Also validated with the configured AxolotlVoice bot: real Discord login and DAVE voice connection; automatic channel creation for two SVC test groups; one bot serving the first group while the second waits; reassignment after the first group empties; automatic deletion of both temporary channels. Both test groups used local Carpet dummy players; no test audio was sent to the Discord channels.

The initial build was deployed on the Linux Axolotl server, with successful bot login. The user confirmed that live audio, channel creation, and channel deletion work. Discord REST also confirmed that the live group channel's parent is the configured Axolotl Forever World category. Version 1.0.1 adds exact names and isolated-group exclusion. This is a custom build, not an upstream supported release.

## Discord member indicator (1.0.2)

The active bridge adds a synthetic **Discord** member to its group's SVC roster and HUD. Its signed Discord head uses SVC's ordinary speaking outline when Discord audio arrives; the volume slider controls combined Discord audio. No extra client mod is needed. This entry is only player-info and SVC state packets: no server login, world entity, player slot, Tab listing, or saved playerdata. It is removed when the player leaves the group or the bridge stops. Waiting and isolated groups have no Discord member.

This integration is pinned to Minecraft 26.3 and SVC 2.6.27 because it uses their packet codecs. The head texture is bundled; no runtime MineSkin key or account is needed. Source: https://minecraft-heads.com/custom-heads/head/58280-discord ; signed property supplied by MineSkin. Texture hash: 739ee7154979b3f87735a1c8ac087814b7928d0576a2695ba01ed61631942045.

Validation: 17 unit tests pass, including signed/unlisted player-info packet codec round-trip and speech/silence hold behavior. Packaged Fabric server startup and native Opus conversion tests pass. On the existing unmodified Minecraft/SVC client, the Discord head loaded, its outline activated with a local silent audio packet stream, and the outline cleared after the stream stopped. The visual probe used a disposable loopback test world and sent no audio into Discord.

Version 1.1.0: 31 automated tests pass. The packaged JAR passed local offline server startup with Skin Restorer 2.11.0, live skin-property changes through its actual skin service, public/private/filtered message handling, join broadcasts, literal inbound delivery without echo, and the native SVC audio probe. Discord API behavior was exercised with in-memory JDA interfaces; no real Discord chat round trip or Axolotl deployment was performed for this release. See CHAT-SETUP.md for manual installation.
