package soulflameaudio;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast by the player who received an Entice buff, naming whoever gave it to them so only that
 * player reacts. The sender's own identity arrives separately via {@link #getMemberId()}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnticeStatusMessage extends PartyMemberMessage
{
	private EnticeStatus status;
	private String casterName;

	/** The sender's own choice of overhead line for this status, or null to show nothing. */
	private String overheadText;

	public EnticeStatusMessage()
	{
	}

	public EnticeStatusMessage(EnticeStatus status, String casterName, String overheadText)
	{
		this.status = status;
		this.casterName = casterName;
		this.overheadText = overheadText;
	}
}
