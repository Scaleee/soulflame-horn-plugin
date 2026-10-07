package soulflameaudio;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Exercises custom audio resolution against real files, rather than a mocked filesystem. */
public class CustomAudioResolutionTest
{
	@Rule
	public final TemporaryFolder soundFolder = new TemporaryFolder();

	@Test
	public void anExactlyNamedFileIsFound() throws IOException
	{
		File wav = soundFolder.newFile("entice-sent.wav");

		assertEquals(wav, SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "entice-sent"));
	}

	@Test
	public void anEmptyFolderResolvesToNothing()
	{
		assertNull("with no files the bundled sound must be used",
			SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "entice-sent"));
	}

	@Test
	public void suffixedVariantsAreAllCandidates() throws IOException
	{
		soundFolder.newFile("entice-sent-one.wav");
		soundFolder.newFile("entice-sent-two.wav");

		for (int i = 0; i < 20; i++)
		{
			File picked = SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "entice-sent");
			assertNotNull(picked);
			assertTrue(picked.getName(), picked.getName().startsWith("entice-sent-"));
		}
	}

	@Test
	public void aDifferentEventsFileIsNotPickedUp() throws IOException
	{
		soundFolder.newFile("entice-whiff.wav");

		assertNull(SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "entice-sent"));
	}

	@Test
	public void aCustomNameIsMatchedAsGiven() throws IOException
	{
		File wav = soundFolder.newFile("yeah-baby.wav");

		assertEquals(wav, SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "yeah-baby"));
	}

	@Test
	public void matchingIgnoresCase() throws IOException
	{
		File wav = soundFolder.newFile("Entice-Sent.WAV");

		assertEquals(wav, SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "entice-sent"));
	}

	@Test
	public void anUnplayableFormatIsIgnored() throws IOException
	{
		soundFolder.newFile("entice-sent.mp3");

		assertNull("mp3 cannot be decoded, so it must fall back rather than fail",
			SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), "entice-sent"));
	}

	@Test
	public void anAbsolutePathToAFileIsUsedDirectly() throws IOException
	{
		File wav = soundFolder.newFile("anywhere.wav");

		assertEquals(wav, SoulflameHornAudioPlugin.pickOverride(soundFolder.getRoot(), wav.getAbsolutePath()));
	}

	@Test
	public void anAbsolutePathToAFolderPicksFromIt() throws IOException
	{
		File wav = soundFolder.newFile("whatever-name.wav");

		assertEquals(wav, SoulflameHornAudioPlugin.pickOverride(
			soundFolder.getRoot(), soundFolder.getRoot().getAbsolutePath()));
	}
}
