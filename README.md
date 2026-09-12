# MacroRecorder

Record what you do in Minecraft, then play it back.

MacroRecorder is a **client-side** Fabric mod. Start recording with a command, do whatever you want —
walk, mine, switch hotbar slots, look around, type in chat, run commands — then stop, give it a name,
and replay it whenever you like.

Nothing is installed on the server, and other players need nothing to see it work.

## Requirements

| | |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required |
| Java | 25 |

Client only. Putting the jar on a server does nothing.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder.
3. Drop `macromod-1.0.0.jar` in next to it.
4. Launch the game with the Fabric profile.

## Using it

| Command | What it does |
| --- | --- |
| `/macro` | Show this list in chat. |
| `/macro record` | Start recording. |
| `/macro stop` | Stop recording — or cancel a macro that is currently playing. |
| `/macro save <name>` | Save what you just recorded under a name. |
| `/macro play <name>` | Play a saved macro back. |
| `/macro play <name> --no-look` | Play it without moving your camera. |
| `/macro list` | Show everything you have saved, with how long each one runs. |
| `/macro delete <name>` | Delete a saved macro. |

Names can use letters, numbers, `_` and `-`, up to 32 characters. Press <kbd>Tab</kbd> after
`/macro play` or `/macro delete` to complete the name of a saved macro.

### A first macro

```
/macro record          walk to the farm, harvest a row, walk back
/macro stop            "Recording stopped: 207 ticks (10.4s), 7 events, 1 messages."
/macro save farmrun    "Saved 'farmrun' (207 ticks, 7 events)."
```

Then, standing where you started:

```
/macro play farmrun
```

You do not have to save straight away — `/macro stop` keeps the take in memory, so you can try it and
only name it if it was any good. It is replaced as soon as you stop the next recording, and lost when
you quit the game.

`/macro save` while still recording will stop the recording for you first.

## What gets recorded

- Movement — forward, back, strafing, jumping, sneaking, sprinting
- Attack and use, hotbar slots, inventory, drop, swap hands, pick block
- Where you are looking
- Chat messages and commands you send

`/macro` commands themselves are never recorded, so stopping a recording never ends up inside it.

## Good to know

**Keep your hands off the keyboard while a macro plays.** It replays by pressing the same keys you do,
so anything you press at the same time fights it. `/macro stop` cancels a playback at any time and
hands control straight back.

**It repeats your inputs, not the outcome.** A macro presses the same keys for the same number of
ticks — it does not know where you are or what is around you. Start a playback from the same spot,
facing the same way, and expect longer macros to drift. Short, self-contained sequences repeat best.

**Use `--no-look` if you want to keep your camera.** Handy for a macro that is really just a sequence
of typed commands.

**Toggle sneak and toggle sprint change how the game reads those keys.** A macro recorded with those
options on may behave differently if you turn them off, and vice versa.

**Check the rules before using it on a server.** Many servers restrict macros and automation. This mod
does not hide anything or bypass anything — but it is your account.

## Where macros live

Saved macros are plain JSON files in your Minecraft folder:

```
config/macromod/<name>.json
```

They are safe to copy between worlds and installations, and small enough to share — hand someone the
file and they can drop it into their own `config/macromod` folder and `/macro play` it.

## Something went wrong?

| Message | What it means |
| --- | --- |
| `Nothing to save. Record something with /macro record first.` | No recording has been made yet this session. |
| `No macro named '<name>'. See /macro list.` | Nothing saved under that name — check `/macro list` for the spelling. |
| `Already recording. Use /macro stop to finish.` | A recording is already running. |
| `You need to be in a world to record.` | Join a world first. |

If a macro plays but your character barely moves, you are most likely standing somewhere the recorded
path does not fit — walls, water, a drop. Replay it from where you recorded it.

## Contributing

See [CONTRIBUTORS.md](CONTRIBUTORS.md).

## License

MIT — see [LICENSE](LICENSE).
