# Notes for the Fakeplayer Chunkloader developer

This is the developer handoff for the separate addon, currently based on
**Fakeplayer Chunkloader 2.1.0 / Fabric / Minecraft Java 26.3**. The user-facing
feature/removal list is in [README.md](README.md). This file explains where the
patches live, why they exist, and which assumptions matter when reviewing,
porting, or incorporating them upstream.

## Start here

1. [FakeplayerAddon](src/main/java/dev/fakeplayeraddon/FakeplayerAddon.java)
   registers the server/common networking, coordinate command extension, and
   ticket-owner cleanup. [FakeplayerAddonClient](src/main/java/dev/fakeplayeraddon/client/FakeplayerAddonClient.java)
   registers client receivers and the Active Bots keybind.
2. [ActiveNetworking](src/main/java/dev/fakeplayeraddon/active/ActiveNetworking.java)
   shows the intended integration pattern: authorize through the original mod,
   then delegate to its manager/screens rather than creating another manager.
3. [BotRelocationManagerMixin](src/main/java/dev/fakeplayeraddon/mixin/common/BotRelocationManagerMixin.java)
   is the main movement integration point; it rekeys the original live state.
4. [BotChunkTickets](src/main/java/dev/fakeplayeraddon/loading/BotChunkTickets.java)
   owns shared tickets. [RadiusManagerMixin](src/main/java/dev/fakeplayeraddon/mixin/common/RadiusManagerMixin.java)
   connects it to the original manager's lifecycle methods.
5. [MobTicking](src/main/java/dev/fakeplayeraddon/mob/MobTicking.java),
   [MobTickingStore](src/main/java/dev/fakeplayeraddon/mob/MobTickingStore.java), and
   [IndependentTicking](src/main/java/dev/fakeplayeraddon/mob/IndependentTicking.java)
   separate per-bot policy from existing vanilla simulation-ticket eligibility.

The original mod remains a runtime dependency. Its classes, configuration
records, permission logic, and GUI implementations are reused, not bundled.

## Source map

| Area | Location | Responsibility |
| --- | --- | --- |
| Active management | `active/`, `client/ActiveBots*` | F9 list, remote Configure/Delete, original list-screen inheritance and confirmation. |
| Mob ticking | `mob/MobTicking*`, `client/MobTicking*` | Independent mob flag, persistence, protected-area checks, original-screen toggle. |
| External loaders | `mob/IndependentTicking` | Read-only recognition of independently ticking vanilla tickets and scoped bypass. |
| Relocation/locking | `movement/`, `client/Movement*`, `client/CoordinateRow` | Commands, request handling, manager bridge, original-screen controls, physical lock state. |
| Coverage | `loading/` | Numeric radius bound and idempotent per-bot ticket ownership. |
| Packet records | `network/`, `active/*Payload` | Existing wire IDs, field order, and operation values. |
| Patches/bridges | `mixin/common/`, `mixin/client/` | Narrow hooks into original-mod internals and Minecraft methods. |
| Persistence checks | `src/test/` | Real-filesystem checks against the original bot record. |
| Minecraft integration checks | `verification/` | Separate test mods; none are bundled in the addon. |

All Java paths above are relative to `src/main/java/dev/fakeplayeraddon/` unless
otherwise stated. The complete mixin registration list is
[src/main/resources/fakeplayeraddon.mixins.json](src/main/resources/fakeplayeraddon.mixins.json).

## Dependency and mixin assumptions

- Inspected upstream: branch `fabric/26.3`, commit
  `1ffa15f8a216ae28556c988ff08cf8752a9a0436`.
- Required runtime: Minecraft 26.3, original mod 2.1.0, Java 25, Fabric Loader
  0.19.5+, and Fabric API 0.160.7+26.3 or an appropriate compatible build.
- The addon is pinned to that Minecraft/original-mod combination. Compatibility
  with another version cannot be established by changing metadata alone.
- Mixins targeting `de.chunkloader.*` use `remap = false` and the original
  method/field names. Minecraft targets use the current mapped game APIs.
- `@Shadow`, `@Accessor`, and `@Invoker` are bridges into the target. Their
  throwing stub bodies are intentional; they are not fallback implementations.
- Several hooks specify expected/minimum match counts. For example, patrol
  filtering must cover both list-size and list-selection reads, and the
  distance-despawn redirect covers both `removeWhenFarAway` calls. Re-audit
  those counts and descriptors when upstream methods change.
- Binding/injection correctness needs a real Fabric launch; successful Java
  compilation alone does not validate these hooks.

### Common/server mixins

| Mixin | Target and important hooks | Purpose |
| --- | --- | --- |
| `RadiusManagerMixin` | `ChunkloaderManager`: radius setter/map data, radius adjustment, overlap queries, ticket add/remove | Remove the old radius/coverage restrictions while retaining occupied-center rejection; delegate ticket ownership. |
| `RadiusConfigMixin` | `ChunkloaderConfig.load`, `updateEntryChunkRadius` | Extend both current/legacy load clamps and the write clamp. Patching only the GUI loses radii on reload. |
| `RadiusCommandMixin` | `ChunkloaderCommand.registerCommands`, `setRadius` | Extend both aliases' argument ranges and handler validation/error text. |
| `BotRelocationManagerMixin` | `ChunkloaderManager`, `createProfile` and `MovableBots` bridge | Preserve/rekey live state on movement and prevent a moved UUID from being stolen at the freed source chunk. |
| `FakePlayerMovementMixin` | `ChunkloaderFakePlayer.tick`, `hurtServer`, push/portal behavior | Anchor locked bots, run unlocked physics, suppress damage-based disable/delete, keep physical movement in one dimension. |
| `BotInteractionMixin` | `Player.attack`, `interactOn` via `@WrapMethod` | Prevent the original punch/sneak-click actions and use original configuration opening. |
| `BotCollisionMixin` | `Entity.push(Entity)` | Enforce lock/ownership regardless of which collision participant receives the push call. |
| `MobTickingConfigMixin` | Original config add/update, spawning-mode update, removal | Initialize defaults before mode changes; migrate/remove independent flags with the bot key. |
| `MobTickingManagerMixin` | Manager construction/path validation, spawning-mode change, mob-tick classification, cleanup removal, tick-control predicate | Connect the independent flag to original behavior, refresh vanilla registration, and preserve independent loaders. |
| `MobTickingLevelMixin` | `ServerLevel.tickNonPassenger`, `tickPassenger`, priority 1100 | Pause mobs when their applicable bot flags are OFF, preserving real-player and independent-ticket exemptions. |
| `MobDistanceDespawnMixin` | `Mob.checkDespawn` calls to `removeWhenFarAway` | Protect covered mobs without making them persistent or bypassing Peaceful removal. |
| `IndependentTickingLevelMixin` | `ServerLevel.tickChunk`, `tickNonPassenger`, `shouldTickBlocksAt` via `@WrapMethod` | Scope the independent-ticket exception around the actual original tick/guard calls. |
| `BotSiegeMixin` | `VillageSiege.tryToSetupSiege` player list | Filter this mod's bots only at siege initiation. |
| `BotPatrolMixin` | `PatrolSpawner.tick`, both player-list reads | Filter bots before candidate counting and selection. |
| `BotSkeletonTrapMixin` | `SkeletonTrapGoal.canUse` proximity call | Retain the original false result and require an eligible nearby real player; a closer bot must not hide that player. |
| `ChunkTicketAccess` | `ServerChunkCache.ticketStorage` | Read ticket storage and release specifically owned tickets. |
| `TicketStorageAccess` | `TicketStorage.tickets` | Inspect existing ticket provenance without changing cap calculations. |
| `ActiveTargetsAccess` | `ChunkloaderManager.activeTargets` | Distinguish actually active bots from merely enabled configuration records. |
| `UpstreamNetworkingAccess` | Private original networking helpers | Reuse ownership, open-screen matching, and original action cooldowns. |

### Client mixins

| Mixin | Target/hooks | Purpose |
| --- | --- | --- |
| `DisabledScreenAccess` | Original disabled screen's filtered rows and scroll offset | Map inherited button positions back to the active-list rows. |
| `DisabledScreenTextMixin` | Two renderer string constants | Change labels only when the instance is `ActiveBotsScreen`. Normal F8 instances return their original labels. |
| `MobTickingScreenMixin` | Original `ChunkMapScreen` action list and lifecycle | Add/query/update the independent mob toggle using the original layout. |
| `MovementScreenMixin` | Original screen's action rebuild/layout, init/data updates, coordinate rendering | Add XYZ/Reset/Set/Lock controls, retain input/focus, enforce clipping, and display actual block coordinates. |
| `RadiusScreenMixin` | Original radius readout rendering | Remove the obsolete `/3` maximum; the existing +/- controls are reused. |

## Important flows and invariants

### Remote configuration and packet handling

Active Configure calls `sendOpenChunkMap` and the original `buildChunkMapData`.
That registers the original open-map session, so original rename, skin, radius,
and other action packets keep using their original handlers.

Addon mutation requests run on the server thread. A client-supplied chunk key
is not sufficient authorization: reuse `canModifyEntry` and, for movement/mob
configuration, the matching original open-map session. Preserve action cooldowns.
The Active list uses its own payload so a delayed F8 response cannot overwrite
active rows.

Movement operation values are **0=query, 1=Set coordinates, 2=set lock**.
`MovementState` echoes the operation. A delayed query reply must not clear the
client's pending write or typed coordinates. The mob-ticking request uses
**-1=query, 0=OFF, 1=ON**. Active operations are serialized enum values; preserve
their existing ordering. Payload IDs/field order were not changed by the cleanup.

`MovementNetworking.handleRequest` and `MovementCommands.addAtCoordinates`
were extracted during the readability pass. They preserve the prior checks,
messages, execution dimension, packet responses, and original-manager calls;
registration and server-thread scheduling remain in `init`.

### Bot identity, movement, and damage

The persistent key is **dimension + center chunk X/Z**. Names can change.
The ticket helper uses **ServerLevel + packed center chunk** for the same
identity. Multiple bots in one chunk are intentionally unsupported; enabling
that requires a broader persistent-ID redesign across the original manager,
config, sessions, and addon stores.

Relocation preflights bounds, destination occupancy, and chunk loading. It then
copies every field of the original immutable `ChunkloaderTarget`, migrates the
addon flags, rekeys manager/body/marker/visualization/tab-list state, updates
original configuration/tickets, refreshes vanilla chunk registration, invalidates
the map cache, and retargets current viewers. An explicit Set also stops motion,
positions the body, and flushes the original config save. Physical movement uses
the same bridge with `teleport = false`, following the body's existing position.

A live move retains its entity/UUID. The original UUID is coordinate-derived,
so `createProfile` avoids an online UUID collision when another bot is created
at the moved bot's freed source. This is **not** a promise of UUID stability
across restart: the original manager recreates profiles from saved coordinates.

Dummy connections do not provide the normal network-driven player physics
path. Unlocked bots call `doTick()` once per server tick; the tick guard avoids
double execution. Vanilla player attacks ordinarily hand knockback to the
client and reset server velocity; the bot interaction wrapper retains the
server-side knockback needed for a dummy client.

Use `@WrapMethod` for the attack/sneak-interaction interception. Another HEAD
injection did not reliably prevent the original mod's deletion callback from
running first. Collision protection must cover both `body.push(player)` and
`player.push(body)`.

Current behavior deliberately suppresses health damage and the original
physical disable/delete handlers for managed bots. Locked bots stop riding and
anchor; unlocked bots permit authorized player movement and normal physics.
Portal use is disabled, and movement stays in the bot's current dimension.
These are existing addon behaviors, not changes introduced by this cleanup.

### Per-bot settings

The original config remains authoritative for coordinates, radius, owner,
spawning, appearance, and enabled state. The addon's sibling
`mob_entity_ticking.json` uses format version 1 with per-key entries containing
`dimension`, `chunkX`, `chunkZ`, `mobEntityTicking`, and optional `movementLocked`.

The first observed record snapshots `allowMobSpawning` as the initial mob flag;
it does not keep following that option. Thus a bot created with spawning ON and
later switched OFF retains its saved mob flag. Missing lock flags default true.
Mode changes initialize/snapshot before changing the original record.

Rename/disable/restore preserve a key; movement migrates it; deletion removes
it so a reused location starts fresh. The store retains corrupt/unsupported
files rather than overwriting them. Supported failed writes roll back store
memory. Original config and addon flags are separate files, **not a cross-file
transaction**; disk-failure recovery should not be described as fully atomic.
Server stop clears the mob-setting store map and ticket-ownership map. The
read-only independent-ticket cache uses weak `ServerLevel` keys.

### Shared tickets and large radii

`BotChunkTickets` is server-thread state. Per-world maps store each bot's
loading/simulation rectangles and reference counts for the original ticket
types. First ownership adds a native ticket; final release removes it.
Registering the same area twice is idempotent. Shared chunks therefore retain
the other owners when a bot is resized, moved, disabled, or deleted.

Both loading **and** simulation tickets use ticking level **31**. A simulation
ticket paired only with a FULL-status loading ticket (33) was insufficient for
native mob/hopper ticking in testing. Fixed per-covered-chunk levels also avoid
negative levels when radii exceed the normal propagation depth. The original
mode-specific loading margins and minimum spawning-mode radius are preserved.

Rectangles are clipped to the current border and horizontal world bounds.
`BotRadius.MAX` is a numeric world-span ceiling, not a performance promise.
Ticket acquisition and several original area scans grow with area. Large
radius requests can be expensive; this patch does not implement asynchronous
terrain generation, request budgeting, or a measured hardware-specific cap.
A moving border's changed clipping is recomputed when an area is registered
again; no continuous border-resize reconciliation loop is provided.

Do not confuse the original map's 33×33 viewport with actual loading coverage.
Radius limits existed in manager actions, commands, save loading, and config
updates as well as the visible controls; all relevant clamps must remain aligned.

### Mob ticking, external loaders, and mobcaps

`MobTicking.covers` means an enabled bot with its independent flag ON covers the
chunk. OFF does not negate another bot's ON. The original cleanup removal and
mob-tick guards respect that union; passenger mobs are handled separately.
Distance-despawn protection changes the applicable `removeWhenFarAway` decision,
not persistence or monster-category accounting. Peaceful removal remains.

The original mod's dimension-wide tick guard previously ignored independent
vanilla loaders. `IndependentTicking` reads existing, unexpired simulation
tickets and their propagated eligibility. It excludes vanilla player-simulation
tickets and the original mod's bot-ticket flag **`1 << 30`**. Real players keep
the original separate exemption. Changing that flag requires auditing both sides.

A per-tick/per-level cache stores eligibility. The compact chunk mask uses bit
0 for computed, bit 1 for block ticking, and bit 2 for entity ticking. Wrappers
set a ThreadLocal exception only during the relevant original tick call and
restore the previous value in `finally`; nested calls must not leak a global
manager exemption.

No `NaturalSpawner` cap formula or arbitrary mob count is installed. Spawning
mode changes and relocation refresh vanilla player registration; loading and
mob ticking alone do not add natural-spawn eligibility. Existing silverfish
remain eligible to count under vanilla rules. The original optional
`ServerChunkCacheSpawnCountMixin` has obsolete descriptors for this game build;
the addon does not activate or replace that dormant injection.

The reproduced regression was the independent loader-clock ticking failure.
The original user's exact 250→70 world sequence was not replayed. Preserve that
distinction when discussing the diagnosis; see [MOBCAP_FIX.md](MOBCAP_FIX.md).

### Hostile events

Filter only `ChunkloaderFakePlayer` candidates at the three event sites. Do
not globally change `ServerLevel.players`, remove all fakeplayer implementations,
change bot game modes, or switch off the corresponding real-player events.
Sieges/patrols use filtered local lists; horse traps retain vanilla alive,
non-spectator, and strict distance rules. Raids, reinforcements, phantoms,
spawners, and portal-generated piglins are outside this patch. Existing events
are not retroactively cancelled.

## Building, formatting, and checking changes

Use JDK 25 and supply the original matching JAR:

```sh
./gradlew build -PchunkloaderJar=/absolute/path/to/fakeplayerchunkloader26.3-fabric-2.1.0.jar
scripts/format-java.sh --check
scripts/format-java.sh --write
```

Formatting uses pinned **google-java-format 1.32.0**, AOSP four-space style,
with a verified SHA-256. The script downloads the formatter into ignored
`.gradle/formatters/` on first use. It needs Bash, Git, curl, Java, and
`sha256sum` or `shasum`. `JAVA_HOME` selects Java; `GOOGLE_JAVA_FORMAT_JAR` can
select an already downloaded copy of that exact formatter. Normal Gradle builds
do not depend on this formatter. Prefer explicit imports; the formatter sorts
and removes unused imports but does not expand a new wildcard automatically.

The 1.2.0 release passed **246 checks** with its published artifact, including
actual dedicated/integrated servers and restarts. See [VALIDATION.md](VALIDATION.md)
and [verification/README.md](verification/README.md) for commands and limitations.
Use only isolated harness worlds: tests create/delete bots and the coverage
harness changes its test world border. Deliberately failing historical
before-patch JSON files are negative controls, not current regressions.

During this readability pass, gameplay constants, packet layouts, save formats,
and mixin target names/descriptors were kept intact. Java formatting, explicit
imports, intent comments, and the two handler extractions are the source changes.
The published 1.2.0 JAR/ZIP in `dist/` remain the original validated binaries;
there is no replacement gameplay release in this commit. Rebuilding from the
cleaned source changes debug line metadata and the extracted handler layouts.

The cleanup's own check record is separate from the historical full-release
results, under [verification/results/readability](verification/results/readability).

## If incorporating this upstream

The addon-specific mixins/accessors can become direct manager/screen methods
where appropriate. Preserve the authorization/session checks, one-bot-per-chunk
identity, complete record/map migration, shared ticket ownership, independent
mob flag, and scoped event exceptions. These are the behavior contracts;
particular bridge names and callback plumbing need not become your public API.

Useful future review areas are exposed upstream APIs for relocation and ticket
ownership, ticket/area-scan performance at large radii, border-resize updates,
and failure recovery across the original/addon files. These are integration
considerations, not fixes silently included in the readability pass.

This repository's MIT license covers its own addon code. The original mod is
All Rights Reserved, required separately, and is not bundled here.
