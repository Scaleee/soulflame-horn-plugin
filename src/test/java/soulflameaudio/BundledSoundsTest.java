package soulflameaudio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The bundled sounds are looked up by classpath path at runtime, so a resource that moved or failed
 * to decode would only surface as silence in game.
 */
public class BundledSoundsTest
{
	@Test
	public void appliedSoundIsBundledAndDecodable() throws Exception
	{
		assertDecodable(SoulflameHornAudioPlugin.APPLIED_SOUND);
	}

	@Test
	public void consumedSoundIsBundledAndDecodable() throws Exception
	{
		assertDecodable(SoulflameHornAudioPlugin.CONSUMED_SOUND);
	}

	@Test
	public void whiffSoundIsBundledAndDecodable() throws Exception
	{
		assertDecodable(SoulflameHornAudioPlugin.WHIFF_SOUND);
	}

	@Test
	public void fadedSoundIsBundledAndDecodable() throws Exception
	{
		assertDecodable(SoulflameHornAudioPlugin.FADED_SOUND);
	}

	@Test
	public void allyWastedSoundIsBundledAndDecodable() throws Exception
	{
		assertDecodable(SoulflameHornAudioPlugin.ALLY_WASTED_SOUND);
	}

	@Test
	public void sentSoundIsBundledAndDecodable() throws Exception
	{
		assertDecodable(SoulflameHornAudioPlugin.SENT_SOUND);
	}

	private static void assertDecodable(String stem) throws Exception
	{
		String path = "/soulflameaudio/" + stem + ".wav";
		InputStream resource = SoulflameHornAudioPlugin.class.getResourceAsStream(path);
		assertNotNull("missing bundled resource " + path, resource);

		try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(resource)))
		{
			assertTrue("empty audio stream " + path, audio.getFrameLength() > 0);
		}
	}
}
