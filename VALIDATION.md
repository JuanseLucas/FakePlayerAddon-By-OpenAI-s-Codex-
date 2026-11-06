# Validation record

## Version 1.2.0

JAR SHA-256: `dc4fad6143943d65bd5a0ecc382cbbf1114a823644cdc1b4b84b5e6a0e0d5cb5`

All **246 checks** pass against the release artifact: 15 persistence, 40
dedicated-server, 10 original restart, 14 mob-switch, 32 movement, 7
movement-restart, 43 rendered client/singleplayer, 27 hostile-event, 43 new
coverage, and 15 coverage/restart assertions. The latest runtime records are
in `verification/results/`; deliberately failing historical before-patch
records remain separate.

The coverage harness checks actual vanilla ticket storage and native hopper
and mob ticking for overlapping radius-four and radius-five bots. It covers
original and coordinate add commands, same-chunk rejection, radius actions
and command parsing above three, mixed mob-ticking flags, disable/restore,
shrink/grow, relocation, deletion, and overflow rejection. The initial pair
owns 99 distinct core chunks. Shared chunks continue ticking after either
owner changes; chunks with no remaining owner pause.

Radius 40 is tested with a small world border: legal ticket levels, clipping,
continued native ticking, original config loading, and actual restart all
pass. After restart, the existing radius actions work while coverage overlaps.
Two spawning-ON bots at offsets (3,1) report the vanilla union of **354**
eligible chunks; disabling spawning on one returns **289**, and on both **0**.
The independent mob-switch regression remains at 250 counted monsters.

A rendered integrated server increases the original SD buttons to radius 6
and decreases again through actual packets. A remote edge hopper ticks with
host render distance 2 and simulation distance 5. All prior client menu,
movement, locking, remote configuration/deletion, and original F8 checks pass.

See [COVERAGE.md](COVERAGE.md) for scope and practical bounds. These checks do
not benchmark an entire radius-40 world, establish a recommended maximum, or
exercise multiple external LAN clients. Bot ticket logic is shared between
dedicated and integrated servers. No environment configuration was changed
or published.


## Version 1.1.1

JAR SHA-256: `6ce193b38b3fb8f330b0a922c8d8df497aa6ab881d7ce58b8a2a34f2052e089e`

The Java 25/Fabric build and all **182 checks** pass against 1.1.1: 15
persistence, 40 dedicated-server, 10 original restart, 14 mob-switch, 32
movement, 7 movement-restart, 37 rendered client/singleplayer, and 27 new
hostile-event assertions. Current runtime records are in `verification/results/`.

The new event harness exercises the actual vanilla siege setup routine with
occupied village bed POIs; actual patrol generation with fixed seeds outside
the village; and the skeleton trap proximity goal, including four actual
skeleton riders spawned for a real-player trigger. All three exclude bots
with natural spawning ON and OFF, while the real-player positive controls
remain functional. Horse-trap boundary, spectator, dead-player, and mixed
bot/real-player controls pass. Global proximity still recognizes a bot, and
vanilla spawning eligibility remains unchanged. See [EVENT_EXCLUSIONS.md](EVENT_EXCLUSIONS.md).

The same event harness run with 1.1.0 records expected failures in
`events-before-patch.json`; `events-after-patch.json` contains the passing
1.1.1 result. These deliberately failing historical controls are not included
in the 182 passing checks. The existing mob-switch regression again reports
250 counted monsters and 289 eligible chunks before and after bot activation.
Both restart suites use fully stopped and restarted dedicated servers.

No hopper/minecart patch was needed: the user identified collection chunks
outside the bot radius. No environment configuration was changed or published.


## Version 1.1.0

JAR SHA-256: `19b763f5512bbe99e34bdaf9013625e8edaf358802e54f512d11fcfbe5e78b7a`

The Java 25/Fabric build and fifteen persistence checks pass. Runtime results
were recorded against the 1.1.0 release artifact at release time and have now
been refreshed for 1.2.0 in `verification/results/`.
All **155 checks** pass: 15 persistence, 40 dedicated-server, 10 original
restart, 14 mob-switch, 32 movement, 7 movement-restart, and 37 rendered
client/singleplayer checks. Both restart suites use a fully stopped and
restarted dedicated server.

Movement coverage includes same-chunk and negative-coordinate relocation,
preserved live identity and configuration, safe source-chunk reuse, occupied
and out-of-bounds rejection, locked and unlocked physical interactions,
removal of punch/sneak-click deletion, permission checks in both collision
directions, absolute/relative spawn commands, and vanilla spawning eligibility
after movement. The rendered client exercises the three input fields,
validation, input retention during refresh, Reset, remote Set, and Lock/Unlock
with actual server acknowledgements. See [MOVEMENT.md](MOVEMENT.md).

The existing mob-switch regression still reports 250 counted monsters before
and after enabling a nearby spawning bot, with 289 vanilla eligible chunks
both times. An independent loader clock continues ticking. No original mod
classes or assets are bundled in the release JAR.


## Version 1.0.1

JAR SHA-256: `6f480fe7abcca1049a4b2dfcdaf8369ce0578f23aa2c338daeecac5792dea2b0`

The 1.0.1 build, ten persistence checks, 40 dedicated-server checks, ten real
restart checks, 14 new mob-switch/independent-loader regression checks, and all 23 rendered
client/singleplayer checks pass.
See [MOBCAP_FIX.md](MOBCAP_FIX.md) and `verification/results/` for the diagnosis
and before/after records. Current downloads and checksums are in `dist/`.

The detailed 1.0.0 record below is retained as release history. The runtime records were rerun on 1.0.1 at release time; the current records
are now from 1.2.0, except the deliberately failing historical regression controls.

## Original 1.0.0 release record

Verified on Minecraft Java 26.3, Java 25, Fabric Loader 0.19.5, Fabric API
0.160.7+26.3, and Fakeplayer Chunkloader 2.1.0 from upstream commit
`1ffa15f8a216ae28556c988ff08cf8752a9a0436`.

Release: `dist/fakeplayer-chunkloader-addon-26.3-1.0.0.jar`

SHA-256: `72a26f757c69305607d10a86438f4b6c0b796191509ba5083cb544051f93662c`

## Build and persistence

The complete Gradle build passes. The JUnit suite executes ten real-filesystem
checks against the original bot record: migration defaults; spawning
independence; per-bot reload; rename/disable preservation; dimension isolation;
coordinate migration; deletion and coordinate reuse; corrupt-data retention;
unsupported-version retention; failed-write rollback. All ten pass.

## Dedicated server

An actual Fabric dedicated server passed 40 runtime checks. Villagers, iron
golems, zombies, and silverfish with natural spawning OFF and mob ticking ON
survive the original cleanup interval, execute server AI, and remain active
beyond the empty-dimension inactivity timeout. Mounted villagers tick while ON
and freeze while OFF. Radius and dimension isolation also pass.

Distance-despawn protection works with a distant real-player instance. Vanilla
despawning outside the protected area and Peaceful removal are preserved. The
actual DistanceManager reports **zero** natural-spawning eligible chunks for
the spawning-OFF bot. OFF→ON→OFF spawning transitions correctly add and remove
eligibility. Existing zombies and silverfish contribute to vanilla monster
counts. Changing mob ticking changes neither spawnable-chunk count nor
existing-monster counts.

A separate real server restart passed ten checks: original coordinates,
dimension, radius, enabled state, spawning setting, and independent mob setting
persist; the original manager automatically reactivates the saved bot; spawning
eligibility remains zero; all four mob types execute AI after restart.

## Client and singleplayer

An actual rendered Minecraft client using Mesa lavapipe passed 23 checks against
the release artifact. These cover original F8 buttons, active action bounds,
inherited search, in-flight disabled-list response isolation, original deletion
confirmation and cancellation, key registration, and configuration toggle.

The same run opened a real singleplayer world. The assigned Active key opened
the list through actual client/server packets. From the Overworld, Configure
opened a Nether bot's original configuration screen; the independent mob toggle
received its server acknowledgement; the original natural-spawning option
worked without changing the mob flag. Confirming remote deletion removed that
Nether bot and preserved the other active bot.

These are the automated scenarios exercised, not exhaustive coverage of every
world, mod combination, or original configuration option.

## Implementation scope

The original mod is a dependency; its classes and assets are not bundled. The
F8 screen retains its implementation. Two compatibility corrections support the
requested behavior in 2.1.0: use the original world-directory helper for
restore-path validation, and refresh vanilla player registration after changing
the original spawning option. No configuration files are moved or reformatted,
and no vanilla mobcap calculations are changed.

No environment settings were changed or published during the resumed build.

## Reproduction in this workspace

```sh
/workspace/tools/build-addon.sh
/workspace/tools/run-gradle.sh -p /workspace/integration runServer --no-daemon
/workspace/tools/run-gradle.sh -p /workspace/integration runServer -PrestartCheck=true --no-daemon
```

The dedicated harness is isolated from the addon. Its result files are
`/workspace/integration/run/runtime-results.json` and `restart-results.json`.
The client harness and result are under `/workspace/client-smoke`. It requires
a test display and the locally extracted lavapipe driver; those are test
infrastructure and are not part of the downloadable addon.
