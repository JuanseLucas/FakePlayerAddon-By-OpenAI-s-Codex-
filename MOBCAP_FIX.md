# Mob-switch compatibility fix — addon 1.0.1

## Diagnosis

Fakeplayer Chunkloader 2.1.0's `ServerWorldTickChunkMixin` cancels chunk,
entity, and block-entity ticking outside real-player/bot ranges whenever
`ChunkloaderManager.shouldControlTicksInDimension` sees any active bot in that
dimension. It does not account for independent vanilla simulation tickets.

This stops independently loaded hopper clocks and can interrupt the machinery
that refreshes a portal chunkloader. A switch's monsters then stop contributing
to the mobcap if their chunks unload. This is consistent with the reported
BetterF3/spawning behavior. The exact reported world's 250→70 sequence was not
replayed; the independent clock failure was reproduced and fixed.

The original `ServerChunkCacheSpawnCountMixin` also contains a spawn-count
modifier, but its optional injection targets obsolete method descriptors and
does not match Minecraft 26.3. It was not activated or replaced by this patch.

## Patch

`IndependentTicking` reads existing simulation tickets and their propagated
vanilla levels. Forced, portal, ender-pearl, and other independent simulation
tickets retain their normal block/entity ticking, including when they overlap
a bot area. The original global guards are bypassed only for an independently
eligible chunk and only during that chunk's ticking call. Nested calls restore
the previous context in `finally`.

The bot's own tickets and vanilla player-simulation tickets are excluded from
this independent-ticket exemption. The original real-player exemption remains
in place. The addon's OFF guard and the original cleanup also respect independent
entity-ticking tickets, so changing a bot option does not freeze or delete
independently ticking mobs in an overlap.

The patch adds no tickets or spawning eligibility and does not hook
`NaturalSpawner`, change cap formulas, mark mobs persistent, or force the cap
to an arbitrary number. The configured bot radii and spawning-OFF eligibility
rules remain covered by the existing regression suite.

## Regression evidence

An isolated actual Minecraft 26.3 server with the original mod and addon 1.0.0
reproduced the failure: an independent hopper transferred items before bot
activation, then stopped at 59 items; its block-ticking gate changed true→false.
The 250 buffered silverfish stayed counted because that baseline test held
their chunks loaded independently; it did not simulate a complete portal
loader expiring after its clock stopped.

With addon 1.0.1, 14 checks pass. The external hopper progresses 59→54 and
the block-ticking gate stays true. All 250 buffered silverfish stay counted.
An actual portal-ticket neighbor clock and an independently loaded clock inside
the bot footprint continue ticking. Overlapping independently ticking mobs
survive the bot's OFF option and cleanup.

A non-FPC ServerPlayer fixture registered in vanilla's distance tracker at the
bot position tests nearby-player overlap: eligible chunks stay 289→289,
without shrinking or double counting. The actual server spawning pipeline
uses that same vanilla count. Removing that fixture and turning bot spawning
OFF returns eligibility to zero.

Before/after records are in `verification/results/mobcap-before-patch.json`
and `mobcap-after-patch.json`. The former intentionally records the reproduced
failure. Run the `verification/mobcap` test mod on a fresh isolated world,
using the same original-mod/addon jar properties documented for the dedicated
harness. Its result is `run/mobcap-results.json`; check its success field.

The 40 existing dedicated checks, 10 real-restart checks, and 10 persistence
checks and 23 rendered client/singleplayer checks also pass with 1.0.1.
These checks do not guarantee compatibility with
every third-party mod that changes ticking or mobcaps.
