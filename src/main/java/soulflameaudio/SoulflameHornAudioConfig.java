package soulflameaudio;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(SoulflameHornAudioConfig.GROUP)
public interface SoulflameHornAudioConfig extends Config
{
	String GROUP = "soulflamehornaudio";
	String RADIUS_COLOUR = "radiusMarkerColour";
	String RADIUS_FILL_OPACITY = "radiusFillOpacity";

	// The same for every sound's file setting
	String FILE_DESCRIPTION = "Leave blank for the default sound. Otherwise the full path to a .wav or .mp3 file, or to a folder to play one at random. "
		+ "A file name on its own is looked for in .runelite\\soulflamehornaudio.";

	@ConfigSection(
		name = "General",
		description = "Sound playback and which moments make a noise.",
		position = 0
	)
	String soundsSection = "sounds";

	@ConfigSection(
		name = "Custom Audio",
		description = "Play your own files instead of the bundled tones.",
		position = 2,
		closedByDefault = true
	)
	String customSection = "custom";

	@ConfigSection(
		name = "Overlay",
		description = "On-screen readouts for your own Entice buff.",
		position = 1
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Overhead Text",
		description = "Lines shown above players' heads. Always client side, so nobody outside the plugin sees them.",
		position = 3
	)
	String overheadSection = "overhead";

	@ConfigSection(
		name = "Party",
		description = "Share your horn events with your RuneLite party. Everyone involved needs this plugin.",
		position = 4,
		closedByDefault = true
	)
	String partySection = "party";

	// General

	@ConfigItem(
		keyName = "enableSounds",
		name = "Enable Sounds",
		description = "Master switch for all audio. Turn off to rely on the overhead text alone. The individual sounds below are ignored while this is off.",
		section = soundsSection,
		position = 0
	)
	default boolean enableSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "volume",
		name = "Volume",
		description = "Playback volume as a percentage.",
		section = soundsSection,
		position = 1
	)
	@Range(max = 200)
	default int volume()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "playSentSound",
		name = "Player Sent Spec",
		description = "Play a sound when you blow the horn and it reaches at least one ally.",
		section = soundsSection,
		position = 2
	)
	default boolean playSentSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playAppliedSound",
		name = "Player Received Spec",
		description = "Play a sound when the Entice buff lands on you, whether you blew the horn or an ally did.",
		section = soundsSection,
		position = 3
	)
	default boolean playAppliedSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playConsumedSound",
		name = "Player Utilized Spec",
		description = "Play a sound when you cash the buff in on a hit.",
		section = soundsSection,
		position = 4
	)
	default boolean playConsumedSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playWhiffSound",
		name = "Player Missed Spec",
		description = "Play a sound when you blow the horn with nobody in range to hear it.",
		section = soundsSection,
		position = 5
	)
	default boolean playWhiffSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playFadedSound",
		name = "Player Wasted Spec",
		description = "Play a sound when your buff expires unused.",
		section = soundsSection,
		position = 6
	)
	default boolean playFadedSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playAllyWastedSound",
		name = "Party Wasted Spec",
		description = "Play a sound when a party member wastes a buff you gave them. Needs the party settings.",
		section = soundsSection,
		position = 7
	)
	default boolean playAllyWastedSound()
	{
		return true;
	}

	// Custom Audio

	@ConfigItem(
		keyName = "useCustomSounds",
		name = "Use Custom Audio",
		description = "Play your own files instead of the bundled tones. Supports .wav, .mp3, .au and .aiff. The fields below only take effect while this is on.",
		section = customSection,
		position = 0
	)
	default boolean useCustomSounds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sentFile",
		name = "Player Sent Spec File",
		description = FILE_DESCRIPTION,
		section = customSection,
		position = 1
	)
	default String sentFile()
	{
		return "";
	}

	@ConfigItem(
		keyName = "appliedFile",
		name = "Player Received Spec File",
		description = FILE_DESCRIPTION,
		section = customSection,
		position = 2
	)
	default String appliedFile()
	{
		return "";
	}

	@ConfigItem(
		keyName = "consumedFile",
		name = "Player Utilized Spec File",
		description = FILE_DESCRIPTION,
		section = customSection,
		position = 3
	)
	default String consumedFile()
	{
		return "";
	}

	@ConfigItem(
		keyName = "whiffFile",
		name = "Player Missed Spec File",
		description = FILE_DESCRIPTION,
		section = customSection,
		position = 4
	)
	default String whiffFile()
	{
		return "";
	}

	@ConfigItem(
		keyName = "fadedFile",
		name = "Player Wasted Spec File",
		description = FILE_DESCRIPTION,
		section = customSection,
		position = 5
	)
	default String fadedFile()
	{
		return "";
	}

	@ConfigItem(
		keyName = "allyWastedFile",
		name = "Party Wasted Spec File",
		description = FILE_DESCRIPTION,
		section = customSection,
		position = 6
	)
	default String allyWastedFile()
	{
		return "";
	}

	// Overlay

	@ConfigItem(
		keyName = "showInfobox",
		name = "Display Buff Infobox",
		description = "Show the horn's icon with a countdown while your Entice buff is active.",
		section = overlaySection,
		position = 0
	)
	default boolean showInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "countdownUnit",
		name = "Countdown Units",
		description = "Count the remaining buff down in seconds or in game ticks. Ten ticks is six seconds.",
		section = overlaySection,
		position = 1
	)
	default CountdownUnit countdownUnit()
	{
		return CountdownUnit.SECONDS;
	}

	@ConfigItem(
		keyName = "showHornRadius",
		name = "Draw Horn Radius",
		description = "Outline the ground your horn reaches while it is equipped, so you can see who is inside it. Follows the radius you set on the horn itself.",
		section = overlaySection,
		position = 2
	)
	default boolean showHornRadius()
	{
		return false;
	}

	@ConfigItem(
		keyName = RADIUS_COLOUR,
		name = "Radius Colour",
		description = "Colour of the horn radius. Its opacity fades the outline and the fill together.",
		section = overlaySection,
		position = 3
	)
	@Alpha
	default Color radiusColour()
	{
		return new Color(0, 255, 0);
	}

	@ConfigItem(
		keyName = RADIUS_FILL_OPACITY,
		name = "Radius Fill Opacity",
		description = "How strongly the area is shaded, as a share of the radius colour's opacity.",
		section = overlaySection,
		position = 4
	)
	@Range(max = 100)
	@Units(Units.PERCENT)
	default int radiusFillOpacity()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "radiusBorderWidth",
		name = "Radius Border Width",
		description = "Width of the horn radius outline.",
		section = overlaySection,
		position = 5
	)
	default double radiusBorderWidth()
	{
		return 2;
	}

	// Overhead Text

	@ConfigItem(
		keyName = "showOwnOverheadText",
		name = "Draw Above Self",
		description = "Show your own lines above your own head. Works alone, no party needed.",
		section = overheadSection,
		position = 0
	)
	default boolean showOwnOverheadText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAllyOverheadText",
		name = "Draw Above Party Members",
		description = "Show lines above party members' heads as they are buffed, spend it and waste it. Needs the party settings.",
		section = overheadSection,
		position = 1
	)
	default boolean showAllyOverheadText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useOthersDialogue",
		name = "Use Their Dialogue",
		description = "Show each party member's own wording above their head instead of yours. Off by default, so everything you see is what you configured here.",
		section = overheadSection,
		position = 2
	)
	default boolean useOthersDialogue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sentOverheadText",
		name = "Player Sent Spec",
		description = "Line shown when someone blows the horn and it reaches an ally. Leave blank for none.",
		section = overheadSection,
		position = 3
	)
	default String sentOverheadText()
	{
		return "Horn up!";
	}
	@ConfigItem(
		keyName = "appliedOverheadText",
		name = "Player Received Spec",
		description = "Line shown when the Entice buff lands on someone. Leave blank for none.",
		section = overheadSection,
		position = 4
	)
	default String appliedOverheadText()
	{
		return "Buffed!";
	}

	@ConfigItem(
		keyName = "spentOverheadText",
		name = "Player Utilized Spec",
		description = "Line shown when the buff is cashed in on a hit. Leave blank for none.",
		section = overheadSection,
		position = 5
	)
	default String spentOverheadText()
	{
		return "Used it!";
	}

	@ConfigItem(
		keyName = "whiffOverheadText",
		name = "Player Missed Spec",
		description = "Line shown when the horn is blown with nobody in range. Leave blank for none.",
		section = overheadSection,
		position = 6
	)
	default String whiffOverheadText()
	{
		return "Nobody heard that...";
	}

	@ConfigItem(
		keyName = "wastedOverheadText",
		name = "Player Wasted Spec",
		description = "Line shown when the buff expires unused. Leave blank for none.",
		section = overheadSection,
		position = 7
	)
	default String wastedOverheadText()
	{
		return "I wasted it!";
	}

	@ConfigItem(
		keyName = "partyWastedOverheadText",
		name = "Party Wasted Spec",
		description = "Line shown above a party member who wasted a buff you gave them. Leave blank for none.",
		section = overheadSection,
		position = 8
	)
	default String partyWastedOverheadText()
	{
		return "Wasted your buff!";
	}

	// Party

	@ConfigItem(
		keyName = "partyIntegration",
		name = "Share With Party",
		description = "Broadcast your horn events to your RuneLite party, and react to theirs. Everyone involved needs this plugin and the same party.",
		section = partySection,
		position = 0
	)
	default boolean partyIntegration()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showPartyRadius",
		name = "Draw Party Radius",
		description = "Outline the ground a party member's horn reaches while they wear it, using the radius they set. They need this plugin with Share With Party on.",
		section = partySection,
		position = 1
	)
	default boolean showPartyRadius()
	{
		return true;
	}

	@ConfigItem(
		keyName = "partyRadiusColour",
		name = "Party Radius Colour",
		description = "Colour of party members' horn radius. Its opacity fades the outline and the fill together, which take their fill opacity and border width from your own radius.",
		section = partySection,
		position = 2
	)
	@Alpha
	default Color partyRadiusColour()
	{
		return new Color(0, 200, 255);
	}

}
