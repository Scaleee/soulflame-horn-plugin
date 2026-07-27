package soulflameaudio;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/** Counts down the seconds left on the Entice buff, next to the horn's own icon. */
class SoulflameHornInfoBox extends InfoBox
{
	private final SoulflameHornAudioPlugin plugin;

	SoulflameHornInfoBox(BufferedImage image, Plugin owner, SoulflameHornAudioPlugin plugin)
	{
		super(image, owner);
		this.plugin = plugin;
	}

	@Override
	public String getText()
	{
		return plugin.getEnticeCountdown();
	}

	/** Warns on the real remaining time, so the colour does not change with the chosen unit. */
	@Override
	public Color getTextColor()
	{
		return plugin.getEnticeSecondsRemaining() <= 2 ? Color.RED : Color.WHITE;
	}

	@Override
	public boolean render()
	{
		return plugin.isInfoBoxEnabled() && plugin.isEnticeActive();
	}

	@Override
	public String getTooltip()
	{
		return "Entice: next melee attack is guaranteed to hit";
	}
}
