package soulflameaudio;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines the ground your horn would reach, so you can see who is inside it before blowing it, and
 * that of each party member wearing one, from the radius they shared.
 */
class SoulflameHornRadiusOverlay extends Overlay
{
	private final Client client;
	private final SoulflameHornAudioPlugin plugin;
	private final SoulflameHornAudioConfig config;

	@Inject
	SoulflameHornRadiusOverlay(Client client, SoulflameHornAudioPlugin plugin, SoulflameHornAudioConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Player localPlayer = client.getLocalPlayer();
		if (config.showHornRadius() && localPlayer != null && plugin.isHornEquipped())
		{
			draw(graphics, localPlayer, plugin.getHornRadius(), config.radiusColour());
		}

		if (config.partyIntegration() && config.showPartyRadius())
		{
			plugin.partyHornRadii().forEach((member, radius) -> draw(graphics, member, radius, config.partyRadiusColour()));
		}

		return null;
	}

	private void draw(Graphics2D graphics, Player player, int radius, Color outline)
	{
		if (radius <= 0)
		{
			return;
		}

		final Area area = reachableArea(player.getWorldLocation(), radius);
		if (area == null || area.isEmpty())
		{
			return;
		}

		OverlayUtil.renderPolygon(graphics, area, outline, fill(outline, config.radiusFillOpacity()),
			new BasicStroke((float) config.radiusBorderWidth()));
	}

	/** The outline's colour at a share of its opacity, so the two fade together. */
	static Color fill(Color outline, int fillPercent)
	{
		return new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), outline.getAlpha() * fillPercent / 100);
	}

	/**
	 * Unions the tiles individually rather than projecting one big square. Each tile is drawn at its
	 * own terrain height, so the shape stays true across stairs and slopes instead of skewing between
	 * four corners at wildly different heights.
	 */
	private Area reachableArea(WorldPoint centre, int radius)
	{
		if (centre == null)
		{
			return null;
		}

		final WorldView worldView = client.getTopLevelWorldView();
		final Area area = new Area();

		for (int dx = -radius; dx <= radius; dx++)
		{
			for (int dy = -radius; dy <= radius; dy++)
			{
				final LocalPoint tile = LocalPoint.fromWorld(worldView, centre.dx(dx).dy(dy));
				if (tile == null)
				{
					continue;
				}

				final Polygon poly = Perspective.getCanvasTilePoly(client, tile);
				if (poly != null)
				{
					area.add(new Area(poly));
				}
			}
		}

		return area;
	}
}
