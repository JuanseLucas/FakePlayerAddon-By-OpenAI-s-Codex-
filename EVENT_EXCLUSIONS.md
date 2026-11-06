# Bot-triggered hostile event exclusions — 1.1.1

Fakeplayers and Chunkplayers cannot act as the player trigger for:

- Zombie siege initiation.
- Pillager patrol generation.
- Skeleton horse trap activation and its skeleton horsemen.

These exclusions apply to every Fakeplayer Chunkloader bot, independently of
natural spawning, mob entity ticking, position, and movement lock. No new
setting is required. Real players retain vanilla event eligibility, including
when a bot is closer to a skeleton trap horse than the real player.

## Patch scope

The patch filters bots only from the siege and patrol routines' local candidate
lists. The horse trap retains its original eligibility check and requires a
nearby living, non-spectator player other than a Chunkloader bot, using vanilla
strict distance comparison. It does not change the world's global player list,
vanilla mobcaps, spawn limits, or bot game modes.

Natural spawning configured ON remains functional. Chunk loading, random ticks,
block entity ticking, entity ticking, mob targeting, physical movement, locking,
and existing management controls retain their existing behavior. Lightning can
still create trap horses; the restriction is on a bot activating them. Existing
mobs and already-started events are not removed or cancelled.

Raids, zombie reinforcements, phantoms, spawners, portal-generated piglins, and
other spawning mechanisms are outside this patch. No hopper or minecart code
was changed: the reported collection issue was traced by the user to chunks
outside the bot's configured range.

## Verification

The separate `verification/events` harness runs real Minecraft 26.3 event
routines on a dedicated Fabric server. It uses occupied bed POIs to establish
a vanilla village, tests siege candidate selection, invokes actual patrol
spawning with fixed random seeds, and tests skeleton horse trap eligibility
and actual creation of four skeleton riders for a real-player trigger.

Coverage includes natural spawning ON/OFF, independent mob ticking ON/OFF,
real-player coexistence, spectator and dead-player rules, the strict ten-block
trap boundary, unchanged general player proximity, and unchanged vanilla
natural-spawning eligibility. See [VALIDATION.md](VALIDATION.md) and the
recorded before/after JSON results in `verification/results/`.

The 1.1.0 negative control reproduces all three bot triggers with natural
spawning ON. Its spawning-OFF controls already pass through the original
mod's world-player filtering. Version 1.1.1 makes the exclusions unconditional
at the three event sites, including when natural spawning is enabled.
