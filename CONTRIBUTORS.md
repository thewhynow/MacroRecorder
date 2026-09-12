# Contributing to MacroRecorder

A client-side Fabric mod for Minecraft 26.2 that records per-tick input and replays it.
Mod id `macromod`, package `com.thewhynow.macromod`, client-only (`"environment": "client"`).

## Getting set up

```bash
./gradlew build      # -> build/libs/macromod-1.0.0.jar
./gradlew runClient  # dev client with the mod loaded (first run downloads MC + assets)
```

Requires **JDK 25**. Versions live in `gradle.properties`; check
[fabricmc.net/develop](https://fabricmc.net/develop) before bumping them.

| | |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.160.0+26.2 |
| Loom | 1.17-SNAPSHOT (plugin id `net.fabricmc.fabric-loom`) |
| Gradle | 9.5.1 · Java 25 |

## Read this first: 26.x is unobfuscated

Minecraft 26.x ships **without obfuscation**. Yarn mappings stop at 1.21.11 and Fabric reports
intermediary `0.0.0` for 26.x, so:

- there is **no `mappings` line in `build.gradle`** — that is deliberate, do not "fix" it;
- everything is written against **official Mojang names**: `Minecraft` (not `MinecraftClient`),
  `LocalPlayer` (not `ClientPlayerEntity`), `KeyMapping` (not `KeyBinding`), `Component` (not `Text`);
- Fabric API renamed things to match — `fabric-key-mapping-api-v1` (was `fabric-key-binding-api-v1`),
  and `ClientCommandManager` is gone in favour of `ClientCommands.literal/argument`.

Most tutorials and Stack Overflow answers you will find are written for 1.21 and will send you down
the wrong path. When in doubt about an API, read the real jar:

```bash
javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar net.minecraft.client.KeyMapping
```

## Layout

```
src/main/java/com/thewhynow/macromod/
├── MacroModClient.java        entrypoint: wires tick, message and connection events + commands
├── command/MacroCommands.java the /macro tree, feedback text, name suggestions
├── macro/Macro.java           Macro / Frame / MacroInput / Message records
├── macro/MacroStorage.java    Gson I/O under config/macromod, name validation
├── macro/TrackedKeys.java     which key mappings are recorded, and name -> mapping lookup
├── record/MacroRecorder.java  captures a take (END_CLIENT_TICK)
├── play/MacroPlayer.java      replays a take (START_CLIENT_TICK)
└── mixin/KeyMappingMixin.java reports physical key transitions to the recorder
```

## How it works

**Recording** runs on `END_CLIENT_TICK`, after the player's input tick, so the movement state it reads
is the one vanilla actually used. Each tick it writes a `Frame` only if something changed:

- *movement* — the seven booleans of `LocalPlayer.input.keyPresses`, stored as a snapshot;
- *action keys* — press/release deltas keyed by `KeyMapping.getName()` (e.g. `key.attack`), so a macro
  keeps working after the player rebinds a key;
- *look* — yaw/pitch whenever they move by more than a hundredth of a degree;
- *messages* — chat and commands from `ClientSendMessageEvents`, minus our own `macro …` commands.

**Playback** runs on `START_CLIENT_TICK`, at the head of `Minecraft.tick()` — before `handleKeybinds()`
and before the player's input tick — so state written there takes effect the same tick. It drives the
vanilla key mappings; it does not fake packets or teleport the player.

### Two invariants that are easy to break

1. **Discrete taps come from the mixin, not from polling.** A key pressed and released between two
   ticks is invisible to a once-per-tick `isDown()` poll — a real hotbar keypress was silently lost
   this way during development. `KeyMappingMixin` hooks `KeyMapping.set`, which both the keyboard and
   mouse handlers call on every physical transition. A tick-end reconciliation pass still compares
   against `isDown()`, because vanilla can release keys without going through `set` (opening a screen,
   losing focus). Only genuine transitions are recorded — typing the letter `e` in chat fires a
   *release* for the inventory key, which must not be mistaken for input.
2. **Playback re-asserts held state every tick**, not just on delta frames. Vanilla — or the player's
   own keyboard — can clear key state at any moment, and a missed re-assert on a sparse macro leaves
   the player walking forever, or frozen, for the rest of the replay. On a press, playback also calls
   `KeyMapping.click(boundKey)`: vanilla starts an action on `consumeClick()` and continues it while
   `isDown()`, so both are needed.

## Macro format

One pretty-printed JSON file per macro in `config/macromod/`. Frames are sparse: a tick with no change
produces no frame, and `keys` is omitted when movement is unchanged. A key tapped and released inside
one tick appears in `pressed` and `released` of the same frame.

```json
{
  "version": 1,
  "name": "demo",
  "lengthTicks": 207,
  "look": true,
  "frames": [
    { "tick": 0,  "yaw": 0.0, "pitch": 0.0 },
    { "tick": 29, "keys": { "forward": true, "backward": false, "left": false,
                            "right": false, "jump": false, "shift": false, "sprint": false } },
    { "tick": 70, "pressed": ["key.hotbar.5"], "released": ["key.hotbar.5"] },
    { "tick": 140, "messages": [{ "kind": "CHAT", "text": "hello" }] }
  ]
}
```

Bump `Macro.CURRENT_VERSION` on any breaking change to this shape; `MacroStorage.load` already refuses
files written by a newer version. Macros are shared between players, so treat the format as public:
adding an optional field is fine, repurposing an existing one is not.

## Testing a change

There are no automated tests — the interesting behaviour only exists inside a running client. To check
a change by hand:

1. `./gradlew runClient`, enter a world.
2. `/macro record`, walk, tap a hotbar slot, swing, type a chat line, `/macro stop`, `/macro save demo`.
3. Read `run/config/macromod/demo.json` — the frames should match what you did, with no key entries
   you did not press.
4. Move somewhere else, `/macro play demo`, and watch it repeat.

Worth exercising whenever you touch recording or playback:

- a **quick tap** of a hotbar key (the sub-tick case that polling misses);
- a **held** key across many ticks;
- `/macro stop` **mid-playback** — the player must come to a stop, not keep walking;
- a chat message and a command in the same macro;
- `/macro play` on a name that does not exist, and `/macro record` twice.

Useful tricks: **F2 takes a screenshot into `run/screenshots/`**, which captures in-game state
(selected hotbar slot, chat history, facing) for a before/after comparison. Hand-writing a JSON file
into `run/config/macromod/` is the easiest way to test playback of something specific — a long camera
sweep, say — without performing it first.

## Style

Match the surrounding code: tabs for indentation, Fabric/Minecraft naming, a blank line before
`return`/`if` blocks that follow a statement. Comments explain *why* something is done — especially
anything that depends on vanilla's tick ordering or key handling, since that is where this mod is
fragile. There is no linter in the build; keep `./gradlew build` warning-free.

Mixins are a last resort here. `KeyMappingMixin` exists because no public API reports sub-tick key
transitions — if you can do something through Fabric API or public Minecraft methods instead, do that.

## Known limits

- This replays *input*, not *state*. Long macros drift if position, world or server lag differ from
  recording time.
- Real key events fight playback, so the player must not type during a replay.
- Toggle-sneak and toggle-sprint change how vanilla reads those keys; a macro recorded with one
  setting may behave differently with the other.
- Nothing is recorded above 20 Hz — the tick rate is the resolution limit, for mouse look too.
