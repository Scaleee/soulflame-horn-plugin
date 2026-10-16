# Soulflame Horn

Sounds, overhead text and a radius overlay for the Soulflame horn's [Entice](https://oldschool.runescape.wiki/w/Soulflame_horn#Special_attack) special attack.

Entice lasts ten ticks and is gone after a single melee hit, so it is easy to waste without noticing.
This plugin tells you the moment you give it, get it, use it or let it run out.

It works for your whole RuneLite party too.
You see when your horn buffs a party member, whether they use it or waste it, and the outline of every member's horn radius on the ground.

## Features

**Sounds** for every part of the special attack, from blowing the horn to using or wasting the buff.

**A countdown** in an infobox, in seconds or game ticks, turning red as the buff runs out.

![The infobox counting the buff down](https://raw.githubusercontent.com/Scaleee/soulflame-horn-plugin/3c6a7988ea8ee5f8e0f554d9fc7919bf1810a4f2/media/soulflame-timer.gif)

**The horn's radius** drawn on the ground while you wear it, so you can see who it will reach before you blow it.
It follows the range you set on the horn itself.
Turn it on with **Draw Horn Radius** in the plugin's settings.

![The horn radius appearing as the horn is put on, and going as it comes off](https://raw.githubusercontent.com/Scaleee/soulflame-horn-plugin/3c6a7988ea8ee5f8e0f554d9fc7919bf1810a4f2/media/soulflame-radius.gif)

**Overhead text** above you and your party as buffs land, get used or go to waste.
Every line can be changed to your own, and an empty one goes back to the default.

![Horn Up! above the player blowing the horn and Buffed! above the ally it reached](https://raw.githubusercontent.com/Scaleee/soulflame-horn-plugin/3c6a7988ea8ee5f8e0f554d9fc7919bf1810a4f2/media/soulflame-overhead.gif)

**Party support.**
In a RuneLite party you see each member's horn radius in its own colour, and hear about it when someone wastes the buff you gave them.

![Your horn radius in green beside a party member's in red](https://raw.githubusercontent.com/Scaleee/soulflame-horn-plugin/3c6a7988ea8ee5f8e0f554d9fc7919bf1810a4f2/media/soulflame-party.gif)

## Events

| Event | Description |
| --- | --- |
| Player Sent Spec | You blow the horn and one or more players receive the buff. |
| Player Received Spec | Another player's horn buffs you. |
| Player Utilized Spec | You utilized the horn buff. |
| Player Missed Spec | You blow the horn and no players are in range. |
| Player Wasted Spec | You were buffed but didn't utilize it. |
| Party Wasted Spec | A party member was buffed, but did not utilize it. |

## Using your own sounds

Turn on **Use Custom Audio**, then give any sound a file of your own.
You can paste the path to a file, the path to a folder to play a random file from it each time, or just a file name from your `.runelite/soulflamehornaudio` folder.

`.wav`, `.mp3`, `.au` and `.aiff` files all work.
Leave a sound blank to keep the one that comes with the plugin.

## Playing with a party

The party features use RuneLite's built-in **Party** plugin.
Make sure it is turned on, then create or join a party from its panel in the sidebar and share the passphrase with your group.

Everyone in the group needs Soulflame Horn installed with **Share With Party** turned on in its settings.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
