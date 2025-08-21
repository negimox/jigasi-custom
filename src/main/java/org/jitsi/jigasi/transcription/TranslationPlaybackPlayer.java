package org.jitsi.jigasi.transcription;

import org.jitsi.jigasi.sounds.SoundNotificationManager;
import org.jitsi.utils.logging2.Logger;
import org.jitsi.utils.logging2.LoggerImpl;
import net.java.sip.communicator.service.protocol.Call;
import org.jitsi.service.neomedia.MediaStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper which converts WAV bytes to Ogg Opus using an external ffmpeg process
 * and injects the resulting Ogg Opus stream into the provided Call's
 * MediaStream using SoundNotificationManager.injectOpusStream.
 *
 * This is a pragmatic approach that avoids implementing an Opus encoder in
 * Java. ffmpeg must be available on the PATH.
 */
public class TranslationPlaybackPlayer
{
    private static final Logger logger = new LoggerImpl(TranslationPlaybackPlayer.class.getName());
    private static final ExecutorService exec = Executors.newCachedThreadPool();

    /**
     * Convert WAV bytes to Ogg Opus and inject into call's MediaStream.
     */
    public static void playWavBytes(Call call, byte[] wavBytes)
    {
        if (call == null || wavBytes == null || wavBytes.length == 0)
        {
            logger.warn("No call or audio to play");
            return;
        }

        MediaStream stream = null;
        try
        {
            stream = org.jitsi.jigasi.sounds.SoundNotificationManager.getMediaStream(call);
            if (stream == null)
            {
                logger.warn("Cannot find media stream to inject playback");
                return;
            }

            final MediaStream streamF = stream;
            final byte[] bytesF = wavBytes;

            exec.submit(() ->
            {
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i",
                    "-",
                    "-c:a",
                    "libopus",
                    "-b:a",
                    "32k",
                    "-vbr",
                    "on",
                    "-f",
                    "ogg",
                    "-"
                );
                pb.redirectErrorStream(true);
                Process p = null;
                try
                {
                    p = pb.start();

                    // write wav bytes to ffmpeg stdin
                    try (OutputStream os = p.getOutputStream())
                    {
                        os.write(bytesF);
                        os.flush();
                    }

                    // read ffmpeg stdout (ogg opus) and inject
                    try (InputStream in = p.getInputStream())
                    {
                        long injectedSsrc = org.jitsi.jigasi.sounds.SoundNotificationManager.injectOpusStream(streamF, in);
                        // register SSRC so the transcriber ignores our injected audio
                        org.jitsi.jigasi.transcription.Transcriber.addInjectedSsrc(injectedSsrc);
                    }
                }
                catch (Throwable t)
                {
                    logger.error("Playback failed", t);
                }
                finally
                {
                    if (p != null)
                    {
                        try { p.destroy(); } catch (Throwable ignored) {}
                    }
                }
            });
        }
        catch (Throwable t)
        {
            logger.error("Error during playback", t);
        }
    }
}
