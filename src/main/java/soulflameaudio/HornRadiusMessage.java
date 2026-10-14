package soulflameaudio;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast whenever the sender's horn radius changes, so the party can draw it around them. Only
 * the sender's own client can read the radius they set, which is why it has to travel this way.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HornRadiusMessage extends PartyMemberMessage
{
	/** In tiles, or zero while the horn is not worn, not configured, or not shared. */
	private int radius;

	public HornRadiusMessage()
	{
	}

	public HornRadiusMessage(int radius)
	{
		this.radius = radius;
	}
}
