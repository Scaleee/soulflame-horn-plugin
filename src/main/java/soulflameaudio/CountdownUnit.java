package soulflameaudio;

/** How the remaining Entice time is counted down on screen. */
public enum CountdownUnit
{
	SECONDS("Seconds", "s"),
	TICKS("Ticks", "t");

	private final String label;
	private final String suffix;

	CountdownUnit(String label, String suffix)
	{
		this.label = label;
		this.suffix = suffix;
	}

	String getSuffix()
	{
		return suffix;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
