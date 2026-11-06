# Fakeplayer Chunkloader Addon

A separate Fabric addon for **Minecraft Java 26.3** and **Fakeplayer Chunkloader
2.1.0**. Supports singleplayer, LAN worlds, and dedicated servers. **Java 25**
is required. The original mod and Fabric API remain required dependencies.

**Latest version: 1.2.0**

- [Download the addon JAR](dist/fakeplayer-chunkloader-addon-26.3-1.2.0.jar)
- [Download the ZIP containing the JAR](dist/fakeplayer-chunkloader-addon-26.3-1.2.0.zip)
- [Checksums](dist/SHA256SUMS)

This page lists everything added, fixed, removed, and deliberately retained
across all addon releases. Detailed behavior and test records are linked below.

**For the original mod developer:** start with [DEVELOPER_NOTES.md](DEVELOPER_NOTES.md)
for the source map, mixin targets, data/threading invariants, compatibility
assumptions, and build/test/formatting instructions.

## Everything added

### Active Bots management menu

- A configurable **Active Bots** keybind, default **F9**, under
  Controls → Key Binds → Miscellaneous.
- Opens from anywhere, including far from bots and across dimensions.
- Lists only active Fakeplayers and Chunkplayers, with names, chunk coordinates,
  block coordinates, and dimensions.
- Reuses the original Disabled Bots menu's layout, appearance, search,
  scrolling, action layout, and Close control.
- **Configure** opens the selected bot's original configuration screen,
  including when the bot is in another dimension. Original controls remain
  available, with the addon's additions inside that same screen.
- **Delete** uses the original permanent-deletion functionality and confirmation.
- Original use permissions and ownership checks apply to remote actions.

The Active Bots menu has **Configure, Delete, and Close**. It has no Edit or
Restore button. The separate original **F8 Disabled Bots menu retains its Edit,
Restore, Delete, Close, search, and existing interface**.

### Independent per-bot Mob Entity Ticking

- Adds **Enable/Disable Mob Entity Ticking** inside the original bot
  configuration screen.
- Keeps existing villagers, iron golems, zombies, silverfish, and other mobs
  ticking and executing AI within the bot's configured simulation area.
- Works in Chunkplayer mode with **natural mob spawning OFF** and no real
  player nearby, including in an otherwise empty dimension.
- Protects mobs in enabled areas from the original mod's frozen-mob cleanup
  and from the distance-despawn case covered by the addon. Vanilla Peaceful
  removal and cleanup outside the applicable area retain their normal behavior.
- Handles passenger mobs, including villagers mounted in boats.
- Saves the setting independently for each bot. Rename, disable/restore,
  relocation, world reload, and server restart preserve it.
- In overlapping coverage, an ON bot keeps mobs active even if another
  covering bot has this setting OFF. Independent vanilla entity-ticking
  tickets and real players retain their own ticking behavior.

**Migration defaults:** the setting starts ON for existing Fakeplayers and OFF
for existing Chunkplayers, matching their prior behavior. Enable it explicitly
on an industrial Chunkplayer when needed. Later natural-spawning changes do
not reset the saved mob-ticking setting.

The toggle does **not** enable natural spawning, add spawn-eligible chunks,
raise the mobcap ceiling, or mark protected mobs persistent. Eligible existing
monsters continue counting toward vanilla mobcaps, including silverfish used
in mob switches.

### Coordinate movement inside the original configuration

- **Move bot to:** with separate **X, Y, and Z** input boxes.
- **Reset** clears those inputs without moving the bot.
- **Set** validates integer block coordinates and moves the selected bot.
- Empty, incomplete, invalid, or overflowing input cannot be submitted normally.
  Server validation rejects positions outside the dimension's height/world
  border or in a chunk occupied by another active or disabled bot.
- Works remotely, including when configuring a bot from a different dimension.
  The destination remains in the **bot's current dimension**.
- X/Z use the center of the selected block; Y is the bot's feet position.
- Reuses the original action panel's search, scrolling, and button style.
  Search for **Movement** to show the movement controls together.
- Input and focus survive normal configuration refreshes. Hidden/offscreen
  fields do not remain active over other controls.
- Relocation keeps the same live entity and UUID, preserving the original
  configuration, owner, name/appearance settings, radius, spawning settings,
  mob-target setting, mob-ticking flag, and movement lock.
- Chunk tickets, manager mappings, marker associations, visualization settings,
  and open configuration sessions follow the moved bot.
- Explicit Set saves the original configuration immediately. Physical movement
  also updates the bot's saved block position and controlled chunk.

### Persistent Lock / Unlock controls

- Adds **Unlock bot / Lock bot** inside the same original configuration screen.
- Existing and newly created bots default to **locked**.
- Locked bots stay anchored to their saved block position, stop riding and
  moving, and ignore punches.
- Unlocked bots run normal server physics, including gravity, and can be pushed
  or moved with authorized player punches and player-model collisions.
- Player-driven movement retains ownership/use permission checks in both
  collision directions. Punch knockback does not damage the bot.
- Relocking stops movement and anchors the bot again.
- The lock setting is independent per bot and survives disable/restore and
  server restarts.

### Larger radii through the existing radius buttons

- The original **Radius +1 / Radius -1** buttons for Chunkplayers and
  **SD +1 / SD -1** buttons for Fakeplayers now work above radius 3.
- **No additional radius button, screen, or separate radius setting is added.**
- Removes the obsolete `/3` maximum from the visible configuration readout.
- Extends the existing `/fakeplayer setradius` and `/fp setradius` commands.
- Preserves larger values in the original `chunkRadius` field across save,
  reload, disable/restore, and actual server restart.
- Preserves the original mode-specific loading/block-ticking margins and
  minimum spawning-mode simulation radius.

A radius of **r** covers **(2r + 1) × (2r + 1)** core chunks; radius 4 means
9×9. Bot tickets work independently of the LAN host's render/simulation
settings. They do not change what a client can render at its own view distance.

There is no radius-3 gameplay cap. The numeric ceiling is the world's span,
**3,750,000 chunks**, and ticket placement is clipped to the current world
border and Minecraft's horizontal bounds. Actual usable radius depends on
CPU, memory, and terrain-generation cost; it is not an unlimited-capacity
promise. The original configuration map remains a viewport of at most 33×33
cells, which does not limit actual coverage.

### Overlapping bot coverage

- Bots can be created in **different chunks inside another bot's loading area**.
- Removes coverage-overlap rejection from creation and radius increases.
- Applies to the original no-coordinate add command, coordinate add commands,
  their aliases, and the existing configuration radius actions.
- Combined coverage is the union of the bots' areas. Shared chunks tick once.
- Each bot keeps its own chunk ownership and settings. Moving, resizing,
  switching spawning mode, disabling, restoring, or deleting one bot does not
  remove tickets still required by another.
- Releasing the final owner removes that bot-owned contribution; independent
  loaders and real-player tickets remain untouched.
- **One bot per chunk per dimension remains enforced**, including disabled
  records. Multiple bot entities in the same chunk were not implemented.

### Coordinate arguments for bot creation

The original no-argument command remains. Coordinate forms add vanilla
absolute, relative (`~`), and local (`^`) position support:

```mcfunction
/fakeplayer add
/fakeplayer add 100 64 -200
/fp add ~5 ~ ~-2
/execute in minecraft:the_nether run fakeplayer add 100 70 -200
/fakeplayer setradius BotName 8
/fp setradius BotName 12
```

Creation uses the command's execution dimension, original permission and
bot-count limits, owner assignment where applicable, and spawn facing.
Coverage overlap is allowed; occupied-chunk placement remains rejected.

## Everything fixed

- **Independent chunkloader interference:** the original mod's global tick
  guards no longer stop chunks that have their own qualifying vanilla
  simulation tickets. This includes forced chunks, portal-ticket areas,
  ender-pearl tickets, and their applicable propagated block/entity ranges.
- **Loader clocks and mob-switch compatibility:** independent hopper clocks
  and loader machinery continue ticking after bot activation, avoiding the
  reproduced ticking failure that could interrupt a portal loader. Buffered
  silverfish remain eligible for vanilla monster counting while their chunks
  remain appropriately loaded. The exact original reported world's 250→70
  sequence was not replayed; the independent-clock failure was reproduced,
  and regression tests retain 250 counted monsters. No mobcap value is forced.
- **Spawning-mode transitions:** changing the original natural-spawning option
  refreshes vanilla player registration. OFF→ON→OFF correctly adds/removes
  eligibility without changing the independent mob-ticking flag.
- **World restoration:** restore-path validation uses the same world-directory
  helper as the original configuration loader, allowing saved bots to restore
  consistently in their world.
- **Shared ticket removal:** per-bot ticket ownership prevents identical shared
  tickets from being removed when only one overlapping owner changes.
  Repeated registration is idempotent rather than accumulating phantom owners.
- **Large-radius ticking readiness:** loading and simulation tickets use valid
  ticking levels rather than subtracting an arbitrarily large radius into
  invalid levels. Native hoppers and mobs continue ticking in extended areas.
- **Movement synchronization:** relocation updates the original manager,
  tickets, marker associations, vanilla player chunk registration, and open
  configuration sessions so the original controls continue targeting that bot.
- **Moved-bot UUID collisions:** a replacement at a freed source chunk receives
  a distinct UUID if the moved live bot still holds the UUID originally derived
  from that chunk. This prevents the replacement from disconnecting the moved bot.
- **Coordinate readouts:** the original configuration now displays the bot's
  actual block X/Z rather than only its chunk-origin block coordinates.
- **Menu response isolation:** an in-flight disabled-list response cannot
  replace the Active Bots menu's active rows. Configuration sessions, ownership,
  and action cooldowns apply to remote requests.
- **Movement input refreshes:** pending writes are distinguished from state
  queries, and normal screen refreshes retain coordinate input/focus. Server
  failures return an error acknowledgement rather than leaving movement pending.
- **Setting storage:** settings are isolated per world and bot, follow coordinate
  changes, and are cleared on permanent deletion so reused coordinates do not
  inherit the deleted bot's flags. Failed supported writes roll back in-memory
  changes; malformed or unsupported addon-setting files are retained rather
  than silently overwritten.

## Everything removed, blocked, or changed

| Original behavior or restriction | Current behavior |
| --- | --- |
| Punching a bot disables or deletes it | Removed. Locked bots ignore punches; authorized punches move unlocked bots without damaging them. |
| Sneak-right-click deletes a bot | Removed. Normal and sneaking right-click open the original configuration screen. |
| Physical damage invokes the original disable/delete handler | Intercepted for registered bots; mob/environmental damage does not invoke those actions or reduce bot health through that handler. Use the management/configuration controls. |
| Bots qualify as zombie-siege player triggers | Blocked for all Fakeplayers and Chunkplayers, with spawning ON or OFF. |
| Bots qualify as pillager-patrol player candidates | Blocked for all Fakeplayers and Chunkplayers, with spawning ON or OFF. |
| Bot proximity activates skeleton horse traps | Blocked. An eligible real player can still activate a trap, including with a closer bot present. |
| Radius is capped at 3 / 7×7 core chunks | Removed from the existing controls, server validation, commands, and configuration loading; world/numeric/resource bounds still apply. |
| Creating a bot inside another bot's coverage is rejected | Allowed when the new bot occupies a different chunk. |
| Increasing a radius into another bot's coverage is rejected | Allowed; shared ticket ownership preserves both areas. |
| Natural spawning OFF necessarily leaves existing mobs frozen or subject to the original cleanup | The independent Mob Entity Ticking option can keep them active and protected within its area. This is not a global cleanup removal. |
| Active-menu Edit / Restore actions | The new active menu omits Edit and uses Configure in place of Restore. The original F8 menu retains its actions. |

**Movement restrictions:** bots do not travel through portals. Coordinate Set
moves within the bot's existing dimension, and locked bots cannot be pushed or
remain riding. Cross-dimension bot relocation was not added.

The three event exclusions affect **bot triggers only**. Real players retain
vanilla eligibility. Lightning can still create skeleton trap horses; the
patch prevents bot activation. Existing mobs and already-started events are
not removed or cancelled by this event patch.

## What remains available or unchanged

- The original mod is still the bot manager and remains a required dependency.
  Its code/assets are not bundled or replaced by a copied management system.
- The original F8 Disabled Bots interface and existing deletion confirmations.
- Original configuration options, including natural spawning, mob targeting,
  name/visibility, skins, visualization, facing, disable/restore, and management.
- Original use/ownership checks and the original bot-count limit.
- Vanilla mobcap calculations, eligible existing-monster counting, and natural
  spawning for bots whose spawning setting is ON. Overlapping spawning areas
  use vanilla union counting rather than counting shared chunks twice.
- Spawning-OFF bots add zero natural-spawn eligible chunks. Increasing loading
  radius or enabling mob ticking does not independently raise the cap.
- Raids, zombie reinforcements, phantoms, spawners, portal-generated piglins,
  and other spawning mechanisms were not disabled by the three-event patch.
- Vanilla Peaceful removal and behavior outside protected ticking areas.
- Existing coordinates, dimensions, configurations, and save formats. Addon
  mob-ticking and movement-lock flags live separately in the world's
  `mob_entity_ticking.json`; radius uses the existing `chunkRadius` field.

**Hopper minecarts:** no special minecart pickup or inventory patch was added.
They use the original non-mob entity ticking path; the Mob Entity Ticking
toggle specifically controls mobs.
The reported collection problem was identified as machinery outside the bot's
range. Minecarts, item transport, extracting hoppers, and controlling redstone
must all have appropriate ticking coverage.

## Installation and upgrades

Install Fabric API, Fakeplayer Chunkloader **2.1.0 for Minecraft 26.3**, and this
addon on the client and server. For singleplayer/LAN, the host client runs the
integrated server; install the matching client mods on participating clients.
Replace the previous addon JAR instead of keeping multiple addon versions.

Old configurations remain readable. Keep this addon installed when using
custom radii above 3: reverting to an older version can clamp them back to 3.

## Release history

| Version | Additions, fixes, and behavior changes |
| --- | --- |
| **1.0.0** | F9 Active Bots menu; remote original configuration and confirmed deletion; independent per-bot mob ticking, cleanup/distance-despawn protection, persistence, and restore/spawning-registration compatibility corrections. |
| **1.0.1** | Independent-loader ticking and mob-switch compatibility fixes; original global guards respect qualifying external vanilla tickets. |
| **1.1.0** | X/Y/Z movement with Reset/Set; coordinate add commands; persistent Lock/Unlock and physical movement; punch/sneak-click disable/delete removal; moved-bot identity protection, coordinate readout and configuration-session synchronization. |
| **1.1.1** | Excludes bots from triggering zombie sieges, pillager patrols, and skeleton horse traps, independently of spawning/mob-ticking settings. |
| **1.2.0** | Extended existing radius buttons and commands; persistent radii above 3; different-chunk overlapping placement and radius growth; shared ticket ownership, valid large-radius ticking readiness, world-border clipping, and integrated-server distance verification. |

## Validation and detailed documentation

**246 checks passed** for 1.2.0: 15 persistence, 40 dedicated-server, 10 original
restart, 14 mob-switch, 32 movement, 7 movement-restart, 43 rendered
client/singleplayer, 27 hostile-event, 43 coverage, and 15 coverage/restart checks.

These cover real Minecraft execution and server restarts, with recorded scope:
full radius-four/five overlap scenarios; radius 40 with a small world border;
and a remote radius-six edge hopper with host render distance 2 and simulation
distance 5. They are not a benchmark of an entire radius-40 world or a test of
every third-party mod combination or multiple external LAN clients.

- [Validation record and exact scope](VALIDATION.md)
- [Reproducible runtime harnesses and recorded results](verification/README.md)
- [Mob-switch diagnosis and independent-loader fix](MOBCAP_FIX.md)
- [Movement and locking](MOVEMENT.md)
- [Hostile-event exclusions](EVENT_EXCLUSIONS.md)
- [Custom radius, overlap, and LAN behavior](COVERAGE.md)

## Build and licensing

Use JDK 25 and the included Gradle wrapper. Supply the original matching mod JAR:

```sh
./gradlew build -PchunkloaderJar=/absolute/path/to/fakeplayerchunkloader26.3-fabric-2.1.0.jar
```

Output is under `build/libs/`. `build` runs the JUnit persistence suite;
`verifyMobTickingStore` also invokes it. Runtime harnesses are separate test mods
and are not shipped in the addon JAR.

- Original source: <https://github.com/ilyasnsir1/Fakeplayer-Chunkloader/tree/fabric/26.3>
- Inspected upstream commit: `1ffa15f8a216ae28556c988ff08cf8752a9a0436`
- Verified toolchain: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API
  0.160.7+26.3, Fabric Loom 1.17.21, Gradle 9.6.0, Java 25.

The original project is **All Rights Reserved**. This addon's MIT license covers
only its own code.
