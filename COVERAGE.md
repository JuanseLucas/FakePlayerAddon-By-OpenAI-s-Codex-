# Custom radius and overlapping coverage — 1.2.0

Use the existing **Radius +1 / Radius -1** buttons for Chunkplayers or
**SD +1 / SD -1** for Fakeplayers in the original bot configuration screen.
They now work beyond radius 3. No new radius button, screen, or setting is added.
The obsolete `/3` maximum is removed from the configuration readout.

A radius of r covers a square of `(2r + 1) × (2r + 1)` core chunks. Radius 4
therefore covers 9×9 core chunks. The original mod's mode-specific extra loading
and block-ticking margins and minimum spawning-mode simulation radius remain.
The existing command also accepts larger radii:

```mcfunction
/fakeplayer setradius BotName 8
/fp setradius BotName 12
```

The existing configuration map remains a bounded viewport, at most 33×33 cells;
that viewport does not limit actual bot coverage.

## Overlap and ownership

Bots may be created in different chunks inside another bot's loading area.
This works through the original `/fakeplayer add`, the coordinate form added
in 1.1.0, and their `/fp` aliases. Radius increases can overlap other bots too.
The one-bot-per-chunk rule remains, including disabled bot records.

Coverage is the union of the bots' areas. Shared chunks tick once. Each bot
owns its contribution to the original mod's chunk ticket types; changing or
removing one bot releases only its ownership. Overlapping bots retain their
own coordinates, radius, owner, and settings. Disable/restore, movement,
radius changes, mode changes, deletion, and world restart preserve the union.

If any covering bot enables mob entity ticking, an overlapping bot with that
option OFF does not freeze or delete those protected mobs. Natural spawning
continues using vanilla rules. Spawning-OFF bots contribute zero natural-spawn
eligible chunks, and a larger radius does not independently enlarge the
vanilla natural-spawning eligibility around a spawning-ON bot. The mobcap
formula and the three hostile-event exclusions from 1.1.1 are unchanged.

## LAN worlds and practical bounds

The addon operates on the integrated server in singleplayer and LAN worlds.
Bot tickets are independent of the host client's render and simulation distance.
Other LAN players retain their normal view, simulation, and spawning behavior.
Install the matching addon and original mod on clients as required previously.

There is no radius-3 gameplay cap. Valid radii are nonnegative and numerically
bounded by the span of Minecraft's world, 3,750,000 chunks. Ticket placement is
clipped to the current world border and Minecraft's horizontal world bounds.
This is not a promise that a computer can simulate the entire world: memory,
CPU, and initial terrain generation determine the practical radius. Chunk count
grows quadratically. Increase the radius according to the host's capacity.

Large radii remain in the original `chunkRadius` configuration field and survive
reload and restart. Old saves remain compatible; the separate mob-ticking and
movement-lock settings retain their existing format. Reverting to an older
addon/mod can clamp large radii back to 3, so keep the current addon installed
when using those saves.

## Verification scope

The isolated coverage harness checks actual ticket ownership and native hopper
and mob ticking for overlapping radius-four and radius-five areas, including
all ownership transitions. It checks radius 40 with a small world border to
verify legal ticket levels, clipping, configuration loading, and real restart
without generating a large test world. It does not benchmark an entire
radius-40 area or establish a recommended maximum for a particular machine.

The rendered integrated-server harness uses the original radius buttons through
actual packets, increases to radius 6, and checks a remote hopper at that edge
with host render distance 2 and simulation distance 5. See
[VALIDATION.md](VALIDATION.md) and [verification instructions](verification/README.md).
