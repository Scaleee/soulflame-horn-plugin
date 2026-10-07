package soulflameaudio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Decodes real mp3 files, made with LAME, and reads the result back the way RuneLite's audio player
 * does, through java.sound. Both are 0.4 seconds of a sine tone.
 */
public class Mp3ToWavTest
{
	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void aMonoMp3BecomesAPlayableWav() throws Exception
	{
		final File wav = Mp3ToWav.convert(fixture("tone-mono.mp3"), folder.newFolder("decoded"));

		assertPlayable(wav, 22050, 1);
	}

	@Test
	public void aStereoMp3BecomesAPlayableWav() throws Exception
	{
		final File wav = Mp3ToWav.convert(fixture("tone-stereo.mp3"), folder.newFolder("decoded"));

		assertPlayable(wav, 44100, 2);
	}

	@Test
	public void aDecodedFileIsReusedRatherThanDecodedAgain() throws Exception
	{
		final File mp3 = fixture("tone-mono.mp3");
		final File decoded = folder.newFolder("decoded");

		final File first = Mp3ToWav.convert(mp3, decoded);
		assertTrue(first.setLastModified(0));
		final File second = Mp3ToWav.convert(mp3, decoded);

		assertEquals(first, second);
		assertEquals("the cached copy is not written again", 0, second.lastModified());
	}

	@Test
	public void anEditedMp3IsDecodedAgain() throws Exception
	{
		final File mp3 = fixture("tone-mono.mp3");
		final File decoded = folder.newFolder("decoded");

		final File before = Mp3ToWav.convert(mp3, decoded);
		Files.copy(getClass().getResourceAsStream("/soulflameaudio/tone-stereo.mp3"), mp3.toPath(), StandardCopyOption.REPLACE_EXISTING);
		assertTrue(mp3.setLastModified(mp3.lastModified() + 10_000));
		final File after = Mp3ToWav.convert(mp3, decoded);

		assertNotEquals(before, after);
		assertPlayable(after, 44100, 2);
	}

	@Test
	public void aFileThatIsNotAnMp3IsRefusedAndLeavesNothingBehind() throws Exception
	{
		final File fake = folder.newFile("not-really.mp3");
		Files.write(fake.toPath(), "this is not audio".getBytes());
		final File decoded = folder.newFolder("decoded");

		try
		{
			Mp3ToWav.convert(fake, decoded);
			fail("garbage must not turn into a .wav");
		}
		catch (IOException expected)
		{
			// the plugin plays the bundled sound instead
		}

		final String[] left = decoded.list();
		assertEquals("no partial file is left in the cache", 0, left == null ? 0 : left.length);
	}

	@Test
	public void onlyMp3sAreConverted()
	{
		assertTrue(Mp3ToWav.isMp3(new File("Horn.MP3")));
		assertFalse(Mp3ToWav.isMp3(new File("horn.wav")));
		assertFalse(Mp3ToWav.isMp3(new File("mp3.wav")));
	}

	private File fixture(String name) throws IOException
	{
		final File copy = new File(folder.getRoot(), name);
		try (InputStream in = getClass().getResourceAsStream("/soulflameaudio/" + name))
		{
			Files.copy(in, copy.toPath());
		}
		return copy;
	}

	private static void assertPlayable(File wav, int sampleRate, int channels) throws Exception
	{
		try (AudioInputStream audio = AudioSystem.getAudioInputStream(wav))
		{
			final AudioFormat format = audio.getFormat();
			assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
			assertEquals(sampleRate, (int) format.getSampleRate());
			assertEquals(channels, format.getChannels());
			assertEquals(16, format.getSampleSizeInBits());
			assertFalse(format.isBigEndian());

			// LAME pads the start and end a little, so roughly 0.4 seconds rather than exactly.
			final double seconds = audio.getFrameLength() / format.getFrameRate();
			assertTrue("decoded " + seconds + "s", seconds > 0.35 && seconds < 0.55);
		}
	}
}
