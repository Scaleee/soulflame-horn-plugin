package soulflameaudio;

import net.runelite.client.audio.AudioPlayer;

/**
 * Plays both bundled sounds through the same AudioPlayer the plugin uses, for auditioning them
 * without launching the client. Run with: gradlew previewSounds
 */
public class SoundPreview
{
	public static void main(String[] args) throws Exception
	{
		AudioPlayer audioPlayer = new AudioPlayer();

		for (String stem : new String[]{
			SoulflameHornAudioPlugin.SENT_SOUND,
			SoulflameHornAudioPlugin.APPLIED_SOUND,
			SoulflameHornAudioPlugin.CONSUMED_SOUND,
			SoulflameHornAudioPlugin.FADED_SOUND,
			SoulflameHornAudioPlugin.WHIFF_SOUND,
			SoulflameHornAudioPlugin.ALLY_WASTED_SOUND})
		{
			System.out.println("Playing " + stem);
			audioPlayer.play(SoulflameHornAudioPlugin.class, "/soulflameaudio/" + stem + ".wav", 0f);
			Thread.sleep(1200);
		}
	}
}
