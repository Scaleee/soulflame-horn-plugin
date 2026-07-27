package soulflameaudio;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SoulflameHornAudioLauncher
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SoulflameHornAudioPlugin.class);
		RuneLite.main(args);
	}
}
