package soulflameaudio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import jaco.mp3.resources.BitstreamException;
import jaco.mp3.resources.Decoder;
import jaco.mp3.resources.DecoderException;
import jaco.mp3.resources.Frame;
import jaco.mp3.resources.SampleBuffer;
import jaco.mp3.resources.SoundStream;

/**
 * RuneLite's audio player only reads what java.sound decodes, which excludes mp3, and the plugin hub
 * does not let plugins use java.sound themselves. So an mp3 is decoded once with the decoder in
 * RuneLite's jaco-mp3-player, which is JLayer's in plain Java, into a 16-bit PCM .wav that the player
 * can read, and kept for next time.
 */
final class Mp3ToWav
{
	private static final String MP3 = ".mp3";
	private static final int WAV_HEADER_BYTES = 44;

	private Mp3ToWav()
	{
	}

	static boolean isMp3(File file)
	{
		return file.getName().toLowerCase().endsWith(MP3);
	}

	/**
	 * The .wav for this mp3 in the cache folder, converting it first if it is not there yet. The name
	 * changes whenever the mp3 does, so an edited file is decoded again.
	 */
	static File convert(File mp3, File cacheDir) throws IOException
	{
		final File wav = new File(cacheDir, cachedName(mp3));
		if (wav.isFile())
		{
			return wav;
		}

		if (!cacheDir.isDirectory() && !cacheDir.mkdirs())
		{
			throw new IOException("Could not create " + cacheDir);
		}

		// Written beside its final name and moved into place, so a half-written file is never read
		// and two conversions racing each other cannot interleave.
		final File partial = File.createTempFile("decoding-", ".part", cacheDir);
		try
		{
			decode(mp3, partial);
			Files.move(partial.toPath(), wav.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return wav;
		}
		finally
		{
			Files.deleteIfExists(partial.toPath());
		}
	}

	private static String cachedName(File mp3)
	{
		final String name = mp3.getName();
		final String stem = name.substring(0, name.length() - MP3.length());
		final long fingerprint = (mp3.getAbsolutePath().hashCode() * 31L + mp3.length()) * 31L + mp3.lastModified();
		return stem + "-" + Long.toHexString(fingerprint) + ".wav";
	}

	/** Streams the decoded samples straight to disk, so a long file is never held in memory. */
	private static void decode(File mp3, File wav) throws IOException
	{
		int sampleRate = 0;
		int channels = 0;
		long dataBytes = 0;

		try (InputStream in = new BufferedInputStream(new FileInputStream(mp3));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(wav)))
		{
			// Filled in once the sizes are known.
			out.write(new byte[WAV_HEADER_BYTES]);

			final SoundStream stream = new SoundStream(in);
			final Decoder decoder = new Decoder();
			byte[] bytes = null;
			try
			{
				for (Frame frame = stream.readFrame(); frame != null; frame = stream.readFrame())
				{
					final SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(frame, stream);
					if (sampleRate == 0)
					{
						sampleRate = samples.getSampleFrequency();
						channels = samples.getChannelCount();
					}

					// The decoder reuses one fixed-size buffer for every frame, so one byte array matches it.
					final short[] pcm = samples.getBuffer();
					if (bytes == null)
					{
						bytes = new byte[pcm.length * 2];
					}

					final int count = samples.getBufferLength();
					for (int i = 0; i < count; i++)
					{
						bytes[2 * i] = (byte) pcm[i];
						bytes[2 * i + 1] = (byte) (pcm[i] >> 8);
					}
					out.write(bytes, 0, count * 2);
					dataBytes += count * 2L;

					stream.closeFrame();
				}
			}
			catch (BitstreamException | DecoderException e)
			{
				throw new IOException("Could not decode " + mp3, e);
			}
		}

		if (dataBytes == 0)
		{
			throw new IOException(mp3 + " has no audio in it");
		}

		writeHeader(wav, sampleRate, channels, dataBytes);
	}

	/** The canonical 44-byte header of a 16-bit PCM .wav, which is all the player needs. */
	private static void writeHeader(File wav, int sampleRate, int channels, long dataBytes) throws IOException
	{
		final int blockAlign = channels * 2;
		try (RandomAccessFile file = new RandomAccessFile(wav, "rw"))
		{
			file.writeBytes("RIFF");
			writeInt(file, (int) (36 + dataBytes));
			file.writeBytes("WAVE");
			file.writeBytes("fmt ");
			writeInt(file, 16);
			writeShort(file, 1);
			writeShort(file, channels);
			writeInt(file, sampleRate);
			writeInt(file, sampleRate * blockAlign);
			writeShort(file, blockAlign);
			writeShort(file, 16);
			file.writeBytes("data");
			writeInt(file, (int) dataBytes);
		}
	}

	private static void writeInt(RandomAccessFile file, int value) throws IOException
	{
		writeShort(file, value);
		writeShort(file, value >> 16);
	}

	private static void writeShort(RandomAccessFile file, int value) throws IOException
	{
		file.write(value);
		file.write(value >> 8);
	}
}
