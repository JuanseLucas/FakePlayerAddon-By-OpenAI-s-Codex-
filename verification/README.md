# Runtime verification harnesses

These separate Fabric test mods exercise the addon in actual Minecraft 26.3.
They are not bundled in the addon JAR. Recorded passing results are in `results/`:
40 dedicated checks, 10 original real-restart checks, and 37 rendered
client/singleplayer checks. The root project's JUnit suite also runs 15
persistence checks. Version 1.1.0 adds 32 movement checks and a separate real
movement-restart run with 7 checks under `movement/`.

Version 1.0.1 adds 14 independent-loader/mob-switch checks under `mobcap/`.
Run that project with the same jar properties and isolated server preparation
as the dedicated project, on a fresh world. Its `run/mobcap-results.json`
must report `passed: true`. The recorded `mobcap-before-patch.json` deliberately
contains the expected failure from addon 1.0.0, not a current failing test.
The existing 14 mob-switch checks also run against 1.1.0. All 155 checks pass
including the root persistence assertions.

Use Java 25 and first build the addon as described in the root README. Each
harness accepts `-PchunkloaderJar=/absolute/path/to/original.jar` and optionally
`-PaddonJar=/absolute/path/to/addon.jar`; the addon defaults to the root build
output. Fabric API, Loader, and Minecraft are resolved through Loom.

## Dedicated server

Use only the dedicated harness's generated `run/` test world: the harness
creates and deletes fixture bots. Copy `server.properties.example` to
`dedicated/run/server.properties`, and accept Minecraft's EULA for that isolated
test server if you agree to it. From the repository root run:

```sh
./gradlew -p verification/dedicated runServer -PchunkloaderJar=/absolute/path/to/original.jar
./gradlew -p verification/dedicated runServer -PrestartCheck=true -PchunkloaderJar=/absolute/path/to/original.jar
```

The first run writes `dedicated/run/runtime-results.json` and stops the server.
The second uses that same world, verifies real restart restoration, writes
`restart-results.json`, and stops. Check the JSON status as well as Gradle's
exit status: the test runner stops the server even when an assertion fails.

## Client and singleplayer

After both dedicated runs have stopped, copy `dedicated/run/world/` into
`client/run/saves/addon-smoke/`. Launch with:

```sh
./gradlew -p verification/client runClient -PchunkloaderJar=/absolute/path/to/original.jar
```

The client checks rendered menus, opens the copied singleplayer world, tests
cross-dimension configuration and deletion through actual packets, writes
`client/run/client-results.json`, and exits. It needs a working display and
Minecraft-compatible graphics driver. The original validation used a temporary
Xorg display and Mesa lavapipe; these machine-specific tools are not bundled.

The copied world's bots are test fixtures. Use the harness world, not a personal
save. Result JSON must report success; a normal process exit alone is insufficient.

## Movement and locking

Use a separate generated test world under `movement/run/`, with its own EULA
and server properties (copy `movement/server.properties.example`). It creates
and moves fixture bots; do not point it at a personal save. Run sequentially:

```sh
./gradlew -p verification/movement runServer -PchunkloaderJar=/absolute/path/to/original.jar
./gradlew -p verification/movement runServer -PrestartCheck=true -PchunkloaderJar=/absolute/path/to/original.jar
```

The first writes `movement-results.json` and `movement-before-restart.json`
inside its run directory. The second reloads the same world and writes
`movement-restart-results.json`. Check `passed: true` in both result records.
It verifies persisted physical coordinates and original settings, lock and
mob flags, automatic body restoration, marker association, and movement after
restart. The client harness also tests coordinate and lock controls through
actual integrated-server packets.
