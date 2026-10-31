# Maintaining the Fabric voice port

This branch is a server-only Fabric text and voice bridge for Minecraft 26.3 and Simple Voice Chat 2.6.27. It is based on `codingsushi79/discordlinkplus` at `6360549041dfa0158deeed3365fce87fe67a61a8`.

Read [VOICE-PORT.md](VOICE-PORT.md) for setup and behavior. Runtime configuration is generated disabled by default; [examples/discordlinkplus-voice.json](examples/discordlinkplus-voice.json) is a credential-free template.

## Branch and patch series

The working branch is `fabric-26.3-voice`. The `upstream` remote points to the original GitHub repository. The original upstream commits and Bukkit sources remain in the history. `src/main` is retained for reference and excluded from the Fabric build; `src/fabric` contains the implementation that actually runs.

The port commits were reconstructed from the saved source snapshots on October 31, 2026. Their dates describe this reconstruction, not the time each change was originally written. The series separates:

1. Fabric/Loom build, Java toolchain, and bundled voice dependencies.
2. The initial two-way bridge, audio mixer, bot pool, room ownership, lifecycle, and tests.
3. Exact group channel names and exclusion of isolated groups.
4. The synthetic Discord group member, signed head skin, and speaking indicator.
5. Shared version properties, CI, example configuration, and this maintenance guide.

Annotated checkpoints identify the release source snapshots:

| Tag | Behavior | Automated checks |
| --- | --- | --- |
| `fabric-v1.0.0-mc26.3` | Initial Fabric voice bridge | 11 |
| `fabric-v1.0.1-mc26.3` | Exact names; isolated groups excluded | 13 |
| `fabric-v1.0.2-mc26.3` | Discord member and speaking head; matches deployed Java source | 17 |
| `fabric-v1.1.0-mc26.3` | Optional text chat, player webhooks, event messages, offline Skin Restorer skins | 31 |
| `fabric-v1.1.1-mc26.3` | Valid webhook creation name; sanitized failure diagnostics and traffic counters | 35 |
| `fabric-v1.2.0-mc26.3` | Server-icon webhook announcements and startup/shutdown notices | 39 |

The original version-property maintenance commit keeps the same Java implementation and resolved runtime dependency versions as 1.0.2. Version 1.1.0 adds text chat in separate relay-core, platform-integration, and setup-documentation commits. Release tags are fixed historical checkpoints; do not move them after rebasing.

## Build

Select a full JDK 25, then run:

```powershell
.\gradlew.bat build --console=plain
```

Linux/macOS:

```sh
./gradlew build --console=plain
```

Install `build/libs/DiscordLinkPlus-Voice-<modVersion>-fabric.<minecraftVersion>.jar`. The `unbundled` and `sources` artifacts are not installable releases. The shaded artifact includes JDA, Opus, and DAVE natives; Minecraft, Fabric API, and SVC stay external dependencies. The wrapper downloads Gradle 9.8.2.

Compatibility and library versions live in `gradle.properties`. The build expands the same compatibility values into `fabric.mod.json`; the compiled and declared SVC versions must stay aligned. Loom and Shadow plugin versions live in the `plugins` block of `build.gradle.kts`.

GitHub Actions runs `build` for Fabric branches and pull requests and uploads the installable JAR. The inherited Bukkit build/release workflows are preserved with `.upstream` suffixes so they do not run or publish Fabric artifacts to the original Bukkit Modrinth project. No publishing or Discord notification workflow is enabled for this port.

## Rebase onto upstream changes

Start with a clean working tree and preserve a local backup ref. In PowerShell:

```powershell
git switch fabric-26.3-voice
git fetch upstream --prune
$previousBase = git merge-base HEAD upstream/main
git branch backup/fabric-26.3-before-rebase
git log --oneline HEAD..upstream/main
git rebase upstream/main
git range-diff "$previousBase..backup/fabric-26.3-before-rebase" upstream/main..HEAD
.\gradlew.bat build --console=plain
```

Use a fresh backup name for each rebase. Resolve conflicts one commit at a time with `git add <resolved-files>` and `git rebase --continue`; `git rebase --abort` returns to the starting state. Keep the Fabric source-set exclusion and native/shading setup when resolving upstream build changes. Inspect changes to the renamed upstream workflows without re-enabling Bukkit publication for this branch.

Upstream is a Bukkit plugin, so rebasing preserves history and applies compatible changes but does not translate upstream Bukkit fixes into the Fabric implementation automatically. Review upstream chat, voice, config, and scheduler changes for equivalent changes needed in `src/fabric`. The account-linking gate and remote console commands remain outside this port.

## Port to another Minecraft/SVC version

Create a new branch from this port, for example `git switch -c fabric-next-voice`. Confirm compatible releases exist before updating `minecraftVersion`, `javaVersion`, `loaderVersion`, `fabricApiVersion`, and `voicechatVersion` in `gradle.properties`. Update Loom/Gradle if required by that Minecraft release. Keep the shared logic separate from compatibility edits in the new commits.

These integration points need direct source/API review:

| File | Version-sensitive integration |
| --- | --- |
| `DiscordRoster.java` | Minecraft player-info packet fields/order and public codec; SVC `PlayerStatePacket`, `RemovePlayerStatePacket`, and `NetManager` internals |
| `DiscordRosterTest.java` | Signed/unlisted profile metadata and exact player-info codec round trip |
| `DiscordVoiceMod.java` | Fabric lifecycle/tick/command events; Minecraft command permissions; target-version startup log |
| `SvcPlugin.java` | SVC server lifecycle, microphone, group, and voice reconnect events |
| `RoomAudio.java` | SVC static channel and Opus APIs; JDA PCM/audio callbacks |
| `VoiceEngine.java` | SVC group/connection APIs and JDA category/channel/voice lifecycle |
| `ChatEvents.java` | Fabric accepted-public-chat/global-message events, chat type/filter masks, and literal system-message delivery |
| `ChatBridge.java` | JDA message events, intents, text channels, and webhook builders; shares the first bot |
| `ChatRelay.java`, `ChatText.java` | Bounded cross-thread messages, formatting, event selection, and active texture-property avatar URLs |
| `build.gradle.kts`, `gradle.properties`, `fabric.mod.json` | Matching compile/runtime versions, Java, natives, packaging, and metadata |

The Discord head requires no client addon. Its synthetic SVC member UUID is also the static audio channel UUID, allowing existing clients to use their normal speaking outline and volume slider. The profile is unlisted, and no Minecraft player entity is spawned. Preserve recipient-only group scoping and removal/reconnect handling when porting. SVC internals are deliberately pinned to the exact reviewed version; do not merely widen the version constraint without checking its protocol and UI behavior.

After compilation and automated tests, use a separate test world to verify:

- Packaged server startup, native Opus initialization, both audio directions, and DAVE voice connection on the target host.
- Normal/Open groups, isolated and password-protected groups, exact names, and category permission inheritance.
- More groups than bots, reassignment, last-player departure, channel deletion, and reload/restart recovery.
- A real SVC client showing the Discord head and speaking outline, volume adjustment, group switching, voice reconnect, bridge loss, and no Tab entry or world entity.

The 1.0.2 source passed local native/audio probes and a real unmodified-client head/highlight check. The user subsequently confirmed the deployed indicator works. Future compatibility versions need their own checks; a successful rebase alone is not proof of runtime compatibility.

Version 1.1.0 additionally passed 31 unit checks, packaged startup, and a local offline Fabric 26.3 / Skin Restorer 2.11.0 probe. The probe applied two different skin properties through Skin Restorer's actual `SkinService.applySkin` method to the same offline profile, then dispatched public chat events and checked the resulting avatar URLs. It also checked private/filtered chat exclusion, join broadcasts, literal inbound delivery, and echo prevention. Tests used in-memory Discord interfaces and no live Discord messages. A real Discord round trip remains part of manual installation verification. No Axolotl server files or configuration were changed for this release.

Never commit tokens, live configs, channel registries, world/player data, logs, or test server directories. Use a private external deployment config and keep the example disabled.

## Restore the portable repository

The accompanying Git bundle contains full upstream history, the Fabric branch, and the annotated source checkpoints:

```sh
git clone --branch fabric-26.3-voice DiscordLinkPlus-Voice-Fabric-26.3.git.bundle discordlinkplus-fabric
cd discordlinkplus-fabric
git remote rename origin bundle
git remote add upstream https://github.com/codingsushi79/discordlinkplus.git
git remote add origin https://github.com/k1R4/discordlinkplus-fabric.git
git fetch upstream
```

The published fork is [k1R4/discordlinkplus-fabric](https://github.com/k1R4/discordlinkplus-fabric), on branch `fabric-26.3-voice`. The working clone uses this fork as `origin` and the original repository as `upstream`. Push the Fabric branch and `fabric-*` tags explicitly; the fork's `main` branch is preserved for upstream history. All port commits include `Co-authored-by: Codex <noreply@openai.com>`.

Webhook names must follow [Discord's name restrictions](https://docs.discord.com/developers/resources/webhook#create-webhook), including the reserved `discord` and `clyde` substrings. Version 1.1.1 tests actual new-webhook setup through the JDA adapter instead of assuming an existing webhook. Its diagnostic formatter unwraps asynchronous exceptions and reports Discord error codes without including request URLs, tokens, response bodies, or chat text.

Version 1.2.0 keeps the Discord connection alive until the Minecraft SERVER_STOPPING handler, even if the SVC stopped event arrives first. Startup notices are consumed once per server start; reload-close and actual shutdown are distinct paths. ChatBridge.close(true) gives a priority stop announcement a bounded five-second send before VoiceEngine shuts down JDA. Preserve this ordering when porting. Server icons are read/uploaded on the chat worker; player messages override avatars per message and system messages inherit the webhook default. All 39 automated tests pass; the release has not been deployed automatically.
