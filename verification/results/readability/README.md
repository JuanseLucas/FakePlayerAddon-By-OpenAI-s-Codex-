# Source cleanup verification

These records apply to the developer-readability cleanup following commit
`e85d902`. The released 1.2.0 JAR/ZIP and their full 246-check validation records
remain unchanged. This is not another gameplay release or a claim that the
entire 246-check suite was rerun during formatting.

The cleaned source passed **133 focused checks**:

| Check group | Count | Evidence |
| --- | --- | --- |
| Filesystem persistence through the root JUnit suite | 15 | `./gradlew build` passes; root suite prints `PASS: 15 mob ticking persistence checks`. |
| Dedicated-server movement and coordinate commands | 32 | [movement.json](movement.json) |
| Dedicated-server overlapping coverage and radius behavior | 43 | [coverage.json](coverage.json) |
| Rendered client/integrated server, including movement/lock packets and existing radius buttons | 43 | [client-singleplayer.json](client-singleplayer.json) |

The pinned formatter check passes, and all six runtime harness projects compile.
Their compiled classes match the pre-cleanup harness classes when debug metadata
is omitted. No test assertions were added or removed in this pass.

Of the **57 production classes**, **55** match the released artifact after
excluding source/debug metadata. Only `MovementCommands` and
`MovementNetworking` change executable structure: their nested handlers were
extracted into named private methods, with the same behavior and public/wire
contracts. The runtime checks above cover those command and packet paths.

See [summary.json](summary.json) for counts, class-comparison scope, and the
retained release artifact's SHA-256. Development and compatibility notes are
in [DEVELOPER_NOTES.md](../../../DEVELOPER_NOTES.md).
