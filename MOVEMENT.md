# Bot movement and locking — 1.1.0

Open a bot's existing configuration screen by right-clicking it, including
while sneaking, or through Active Bots → Configure. The controls use the
original action panel, search, scrolling, and button style. Search for
**Movement** to find the full group.

## Coordinate controls

**Move bot to:** contains separate X, Y, and Z boxes. Enter integer block
coordinates, then press **Set**. **Reset** clears the three inputs without
moving the bot. Incomplete or invalid input disables Set. Server validation
rejects positions outside the dimension's height/world border and chunks
occupied by another active or disabled bot.

Set moves the selected bot within its own dimension, even when you configure
it from another dimension. X/Z place its feet at the center of that block.
The original coordinate readout shows the bot's actual block position. This
control does not transfer bots between dimensions.

The existing live bot and UUID are retained during relocation. Its original
configuration, ownership, radius, spawning setting, mob-target setting, and
addon settings follow it. The original manager's tickets and marker mappings
move with it. Explicit Set saves the original configuration immediately.
Creating another bot at a freed source chunk receives a distinct UUID if the
moved live bot still holds that chunk's original generated identity.

## Lock / Unlock

Bots default to **locked**, including bots loaded from existing saves. Press
**Unlock bot** to allow normal server physics, authorized punch knockback, and
player-model pushing. Their saved block position and controlled chunk follow
their movement. **Lock bot** stops motion and anchors the bot to its saved block
position. The setting survives disable/restore and server restarts.

Punches no longer disable or delete a bot in either state. Sneak-right-click
also no longer deletes it. Normal and sneaking right-click both open the
original configuration. Use the existing configuration Disable action or
management-menu Delete confirmation when needed. Ownership/use permissions
still apply to configuration and player-driven movement.

## Spawn command

The original no-argument command remains available. Coordinates extend it:

```mcfunction
/fakeplayer add 100 64 -200
/fp add ~5 ~ ~-2
/execute in minecraft:the_nether run fakeplayer add 100 70 -200
```

The command uses its execution dimension and supports vanilla absolute,
relative (`~`), and local (`^`) block-position arguments. It retains the
original permission, creation limits, and owner assignment. Since 1.2.0,
coverage may overlap between bots in different chunks; an active or disabled
bot already occupying the destination chunk still prevents creation.

Bots do not travel through portals. Coordinate Set and physical movement keep
the bot in its current dimension.

## Compatibility and validation

Movement locking is an optional field in the addon's separate
`mob_entity_ticking.json`; older addon settings remain readable. The original
mod's configuration format is unchanged. Mob ticking remains independent of
natural spawning. Movement refreshes vanilla player chunk registration;
vanilla mobcap calculations are unchanged.

Recorded tests cover the real dedicated-server movement/interaction path,
a real restart, and the rendered singleplayer screen with actual movement
and lock packets. Existing mob AI, cleanup, menu, and mob-switch regressions
also run against 1.1.0. See [VALIDATION.md](VALIDATION.md) and
[the runtime harnesses](verification/README.md).
