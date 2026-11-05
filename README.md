# Fakeplayer Chunkloader Addon

A separate client-and-server Fabric addon for Minecraft Java **26.3** and
**Fakeplayer Chunkloader 2.1.0**. Java **25** is required.

Download the [addon JAR](dist/fakeplayer-chunkloader-addon-26.3-1.1.0.jar) or
[ZIP containing the JAR](dist/fakeplayer-chunkloader-addon-26.3-1.1.0.zip).
Checksums are in [dist/SHA256SUMS](dist/SHA256SUMS).

**1.1.0 adds coordinate movement and persistent bot locking inside the original configuration screen.**
See [movement controls and commands](MOVEMENT.md). The independent-loader and
mob-switch fixes from 1.0.1 remain included; see [diagnosis and regression evidence](MOBCAP_FIX.md).

## Validation

The full Java 25/Fabric build and all fifteen persistence checks pass. Actual
Minecraft 26.3 dedicated-server checks verify mob AI and cleanup protection,
mounted-mob ON/OFF behavior, empty-dimension ticking, vanilla monster counting,
zero natural-spawning eligibility with spawning OFF, both spawning-mode
transitions, and a real server restart. A real rendered client checks the active
menu against the original F8 layout. See [VALIDATION.md](VALIDATION.md) for the
exact results and scope.
The [verification harnesses and recorded results](verification/README.md) are
included for reproducing the runtime checks.

## Installation

Install the addon alongside Fabric API and the original Fakeplayer Chunkloader
on both the client and server. Singleplayer uses the same installation in the
client's `mods` directory. The original mod is required and is not bundled.

The Active Bots key defaults to **F9** and is configurable in Minecraft's
Controls → Key Binds → Miscellaneous. It
opens the active list across dimensions. Configure opens the original bot
configuration screen; Delete uses its permanent-deletion confirmation. Original
permissions and ownership checks apply. The original F8 disabled-bot menu keeps
its existing behavior.

The original configuration screen also includes **Move bot to:** with separate
X/Y/Z boxes, **Reset**, **Set**, and **Unlock bot / Lock bot**. Reset clears the
inputs; Set moves the bot within its current dimension. Bots default to locked.
Unlocked bots can be moved by authorized punches or player collisions. Physical
punching and sneak-right-clicking no longer disable or delete bots; use the
configuration and management menus. Both normal and sneaking right-click open
the original configuration screen.

Create a bot at specific coordinates with `/fakeplayer add 100 64 -200` or
`/fp add ~5 ~ ~`. The original `/fakeplayer add` still works.

The bot configuration screen gains an independent mob entity ticking control.
Natural spawning is still controlled by the original mod's spawning option.
Existing configurations are read through the original mod; addon settings are
stored separately next to the world's chunkloader configuration.
On first migration the independent setting defaults to ON for existing
Fakeplayers and OFF for existing Chunkplayers, preserving their previous tick
behavior until explicitly changed. Later changes to natural spawning leave the
saved setting unchanged.

Two compatibility corrections support these requirements in version 2.1.0:
the manager's restore-path validation uses the same world-directory helper as
its existing configuration loader, and changing the original spawning option
refreshes vanilla player registration. No configuration files are moved or
reformatted, and vanilla mobcap calculations remain unchanged.

## Build

Use JDK 25 and the included Gradle wrapper. Supply the original, matching
Fakeplayer Chunkloader jar as a compile-time dependency:

```sh
./gradlew build -PchunkloaderJar=/absolute/path/to/fakeplayerchunkloader26.3-fabric-2.1.0.jar
```

The addon jar is written under `build/libs/`. The original mod and its assets are
not included. `build` runs the JUnit persistence suite; `verifyMobTickingStore`
also invokes that suite. It checks persistence,
spawning independence, migration, and failed-write rollback. A source checkout
of the dependency can also be built separately:

- Source: <https://github.com/ilyasnsir1/Fakeplayer-Chunkloader/tree/fabric/26.3>
- Inspected commit: `1ffa15f8a216ae28556c988ff08cf8752a9a0436`
- Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.160.7+26.3,
  Fabric Loom 1.17.21, Gradle 9.6.0.

The original project is All Rights Reserved. This addon's MIT license covers
only its own code.

## Runtime verification checklist

These checks describe the acceptance criteria. The automated subset and exact
completed runtime checks are recorded in VALIDATION.md.

1. Open F8 before and after installing the addon. Compare search, Edit, Restore,
   Delete, confirmation, Close, scrolling, and colors.
2. Create active and disabled bots in the Overworld, Nether, and End. Bind Active
   Bots in Controls, then open it far away in each dimension. Only active bots
   should appear with the correct coordinates and dimensions.
3. Search and scroll in the active list. Configure a distant bot and exercise
   every original option, including radius, spawning, visibility, rename, skins,
   visualization, and disable. Compare with physical right-click configuration.
4. Delete a bot remotely: cancel once, confirm once, restart, and confirm only
   the selected bot was permanently removed. Verify original ownership and
   server permissions also apply to remote actions.
5. Set a Chunkplayer to natural spawning OFF and mob entity ticking ON. Put
   villagers, an iron golem, zombies, and silverfish inside its simulation
   radius. Leave the area and dimension for longer than the original cleanup
   interval. Check AI progress and survival; repeat with a distant real player
   elsewhere in the same dimension.
6. Verify radius boundaries, different dimensions at identical coordinates, and
   overlapping bot areas. Cleanup outside an enabled bot's area should retain
   its original behavior. Passenger mobs and vanilla Peaceful behavior also
   need verification.
7. Compare natural-spawning eligibility and the global mobcap with identical
   player positions and bot spawning settings before and after changing only
   mob entity ticking. The toggle must not add eligible chunks or raise the
   cap. Existing silverfish should still contribute to the vanilla monster cap
   when normally eligible to count; do not mark them persistent to preserve them.
8. Set different mob-ticking values for two bots. Toggle natural spawning,
   disable/restore the bots, rename them, move a disabled bot using the existing
   F8 editor, reload the world, and restart the server. Each bot's independent
   setting and original configuration should survive.
9. Repeat on an integrated singleplayer server and a dedicated Fabric server.
   Joining a different world or server must not reuse another world's settings.
