# Validation record

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
