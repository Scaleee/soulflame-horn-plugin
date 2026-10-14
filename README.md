# Soulflame Horn

A RuneLite plugin for the Soulflame horn's **Entice** special attack.

Entice is easy to waste.
It lasts ten ticks, it is spent by a single melee hit, and nothing on screen tells you it is running out.
In a group it is worse: you can blow the horn and have no idea whether anyone used what you gave them.

This plugin makes the whole cycle audible and visible.

- **Sounds** for every stage, from blowing the horn to spending or wasting the buff.
- **An infobox** counting the buff down, in seconds or ticks.
- **A radius overlay** showing exactly which tiles your horn reaches, before you blow it.
- **Overhead text** above each player as they are buffed, spend it or waste it.
- **Party integration** so you find out when someone squanders a buff you gave them.

Every sound can be replaced with your own audio, and every line of text is yours to write.

## The six events

| Event | When |
| --- | --- |
| **Player Sent Spec** | You blow the horn and it reaches at least one ally. |
| **Player Received Spec** | An ally's horn buffs you. |
| **Player Utilized Spec** | You cash the buff in on a hit. |
| **Player Missed Spec** | You blow the horn with nobody in range to hear it. |
| **Player Wasted Spec** | Your buff expires unused. |
| **Party Wasted Spec** | A party member wastes a buff you gave them. |

Each one has its own sound, its own audio file setting and its own overhead line, named identically across all three sections.

Triggers come from the game messages the server sends you, so a stranger blowing a horn nearby will never set it off.

## Settings

### General

Master **Enable Sounds** switch, a volume slider, and a toggle per event.
Turn sounds off entirely if you would rather rely on the overhead text alone.

### Overlay

| Setting | Default | Effect |
| --- | --- | --- |
| Display Buff Infobox | on | The horn's icon with a live countdown, turning red near the end. |
| Countdown Units | Seconds | Count down in seconds or in game ticks. |
| Draw Horn Radius | off | Outline the ground your horn reaches, while it is equipped. |
| Radius Colour | green | Colour of the radius. Its opacity fades the outline and the fill together. |
| Radius Fill Opacity | 30% | How strongly the area is shaded, as a share of the radius colour's opacity. |
| Radius Border Width | 2 | Width of the outline. |

The radius follows the horn's own configuration, so right-clicking the horn to change its reach updates the outline immediately.
Tiles are drawn individually and unioned, so the shape stays true across stairs and slopes.

### Custom Audio

Turn on **Use Custom Audio**, then point each event at your own file.
Every field accepts:

- a **full path** to an audio file,
- a **full path to a folder**, and one file from it is picked at random,
- or a **bare name** looked up in `~/.runelite/soulflamehornaudio/`.

Quotes around a path are fine, so a path from Windows' **Copy as path** can be pasted as it is.
Leave a field blank to keep the bundled sound.
If nothing matches, the bundled sound plays, so a typo degrades to the default rather than to silence.

For variety, add a suffix after a dash and drop in as many files as you like:

```
entice-sent-yeah-baby.wav
entice-sent-groovy.wav
entice-sent-shagadelic.wav
```

Anything matching `entice-sent-*.wav` is a candidate, and one is chosen each time.

**Formats:** `.wav`, `.au` and `.aiff`.
Java's sound system cannot decode mp3 or ogg, and a decoder would mean a third party dependency the plugin hub's standard build cannot carry.
Convert first:

```
ffmpeg -i clip.mp3 -acodec pcm_s16le -ar 44100 entice-sent-groovy.wav
```

Only the bundled sounds ship with the plugin.
Anything in your sound folder stays on your machine, which makes it the right home for audio you do not hold the rights to redistribute.

### Overhead Text

Client side only, so nobody outside the plugin sees a thing.

| Setting | Default | Effect |
| --- | --- | --- |
| Draw Above Self | on | Your lines above your own head. Works alone, no party needed. |
| Draw Above Party Members | on | Lines above party members as their buffs come and go. |
| Use Their Dialogue | off | Show each player's own wording instead of yours. |

By default what you see is what **you** wrote, for everyone.
Turn on **Use Their Dialogue** to see each player's own wording instead, with yours covering anyone who left a line blank.

Text arriving from another client is stripped of markup and capped at 80 characters before it is drawn.

### Party

One switch, **Share With Party**.
Everyone involved needs this plugin and the same RuneLite party.

Each client reports only what it can see for certain about itself, and nothing is sent while the switch is off.

## Building

Use **Java 11**.
That is what the plugin hub builds with, and RuneLite's Guice 4.1 does not survive a newer JDK: on 17 the client dies during startup with a `ProvisionException` whose real message is itself unreadable, because Guice crashes while formatting it.

```
./gradlew build
```

| Task | Does |
| --- | --- |
| `build` | Compiles and runs the tests. |
| `previewSounds` | Plays all six bundled sounds through RuneLite's own `AudioPlayer`, without starting the client. |
| `run` | Launches RuneLite in developer mode with the plugin loaded. |

`run` accepts a few optional properties:

```
./gradlew run -Pheap=2048m -Pprofile=MyProfile -Pcreds=credentials-alt.properties
```

`-Pcreds` points the client at a different credentials file inside `~/.runelite`, which is how two accounts can be run side by side for testing the party features.

## Testing with a Jagex account

`gradlew run` starts RuneLite at the old username and password login, which a Jagex account cannot use.

Sideloading the built jar is **not** a way around this.
`PluginManager.loadSideLoadPlugins` requires developer mode, and developer mode is computed as:

```java
developerMode = optionSet.has("developer-mode")
                && System.getProperty("runelite.launcher.version") == null
```

The launcher always sets that property, so `--developer-mode` is silently discarded for any client it starts, including every Jagex launcher path.
That is deliberate on RuneLite's part, and it means the only routes to running this on a Jagex account are building it yourself or installing it from the plugin hub.

To build it yourself, have the launcher write its credentials to disk for the IDE-launched client to reuse.
Requires launcher 2.6.3 or newer.

1. Run **RuneLite (configure)**.
2. Add `--insecure-write-credentials` to the Client arguments field and save.
3. Launch RuneLite once through the Jagex launcher, which writes `~/.runelite/credentials.properties`.
4. `gradlew run` now logs in as that account.
5. When finished, delete the file and remove the client argument.

`credentials.properties` grants direct access to the account and bypasses the password entirely.
Never commit or share it, and delete it once testing is done.
To invalidate it early, use "End sessions" in the account settings on runescape.com.

## How detection works

| Event | Signal |
| --- | --- |
| Sent | `ChatMessage` containing "you encourage nearby allies" |
| Received | `ChatMessage` containing "encourages you with their soulflame horn" |
| Utilized | `HitsplatApplied` where `isMine()` is true, on an actor that is not you |
| Missed | `ChatMessage` containing "you blow your horn into the wind" |
| Wasted | `ChatMessage` containing "soulflame horn's effect fades away" |

Every one of those strings was captured verbatim from a live session rather than guessed.
Matching is done on lowercased substrings because the real messages carry colour tags.

Expiry uses the server's own fade message rather than counting ticks.
The server sends it **only** when the buff went unused, never after it is spent, which was confirmed across twelve casts over two sessions: every cast resolved as exactly one of spent or wasted, and the plugin's own spend detection agreed with the server every time.
A ten tick counter is kept purely as a backstop, so a missed message cannot leave the buff armed and wrongly report a spend later.

Damage you take carries the same "mine" hitsplat tint as damage you deal, which is why the actor is checked against the local player.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
