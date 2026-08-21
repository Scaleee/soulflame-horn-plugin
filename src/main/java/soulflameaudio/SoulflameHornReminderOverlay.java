package soulflameaudio;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Says why the radius outline is missing. The horn reports no radius until it has been configured,
 * and the configure option only appears while it is equipped, so without this the overlay would
 * simply draw nothing and leave no clue as to why.
 */
class SoulflameHornReminderOverlay extends OverlayPanel
{
	private static final Color BACKGROUND = new Color(120, 20, 20, 110);
	private static final Color TEXT = new Color(255, 225, 225);
	private static final String MESSAGE = "Right-click your horn to set its radius";

	private final SoulflameHornAudioPlugin plugin;
	private final SoulflameHornAudioConfig config;

	@Inject
	SoulflameHornReminderOverlay(SoulflameHornAudioPlugin plugin, SoulflameHornAudioConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panelComponent.setBackgroundColor(BACKGROUND);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showHornRadius() || !plugin.isHornEquipped() || plugin.isHornRadiusConfigured())
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(MESSAGE)
			.color(TEXT)
			.build());

		return super.render(graphics);
	}
}
