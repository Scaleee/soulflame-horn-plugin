package soulflameaudio;

/** Everything that can happen around an Entice buff, from the point of view of one player. */
public enum EnticeStatus
{
	/** We blew the horn and it reached at least one ally. */
	SENT,
	/** An ally's horn buffed us. */
	RECEIVED,
	/** We spent the buff on a hit. */
	SPENT,
	/** We blew the horn with nobody in range to hear it. */
	MISSED,
	/** We let the buff expire unused. */
	WASTED
}
