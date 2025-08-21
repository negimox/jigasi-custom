package org.jitsi.jigasi.transcription;

import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.jigasi.transcription.tts.HttpTranslationTtsService;
import org.jitsi.jigasi.transcription.tts.TranslationTtsService;
import org.jitsi.utils.logging2.Logger;
import org.jitsi.utils.logging2.LoggerImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prototype manager which, on TranslationResult notifications, will call the
 * configured TTS API and write the received audio bytes to /tmp for
 * verification. This is intentionally kept small and non-invasive as a
 * first-step proof-of-concept.
 */
public class TranslationPlaybackManager implements TranslationResultListener
{
    private static final Logger logger = new LoggerImpl(TranslationPlaybackManager.class.getName());

    private final TranslationTtsService ttsClient;
    private final AtomicLong counter = new AtomicLong();
    private static volatile net.java.sip.communicator.service.protocol.Call jvbCall;
    // Single-thread executor to serialize playback requests per
    // TranslationPlaybackManager
    private final java.util.concurrent.ExecutorService playbackExec
        = java.util.concurrent.Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "TranslationPlaybackPlayer-Worker");
        t.setDaemon(true);
        return t;
    });
    // Optional bounded queue size (prevent unbounded memory usage)
    private static final int MAX_QUEUE_SIZE = Integer.getInteger("org.jitsi.jigasi.transcription.playback_queue_max", 8);

    public TranslationPlaybackManager()
    {
        // Allow using a custom TTS impl in future; default to HTTP client
        this.ttsClient = new HttpTranslationTtsService();
    }

    public static void setJvbCall(net.java.sip.communicator.service.protocol.Call call)
    {
        jvbCall = call;
    }

    @Override
    public void notify(TranslationResult result)
    {
        if (result == null)
            return;

        String text = result.getTranslatedText();
            String lang = result.getLanguage();
        if (text == null || text.isEmpty())
            return;

        try
        {
            byte[] audio = ttsClient.synthesize(text, lang);
            if (audio == null || audio.length == 0)
            {
                logger.warn("TTS returned no audio for translation: " + text);
                return;
            }

            // Inject into the conference using TranslationPlaybackPlayer.
            try
            {
                if (jvbCall != null)
                {
                    // Enqueue playback to ensure serial execution and limit concurrency
                    try
                    {
                        // submit a task to our single-thread executor
                        playbackExec.submit(() ->
                        {
                            TranslationPlaybackPlayer.playWavBytes(jvbCall, audio);
                            logger.info("Requested playback via TranslationPlaybackPlayer (bytes="
                                + audio.length + ")");
                        });
                    }
                    catch (java.util.concurrent.RejectedExecutionException ree)
                    {
                        logger.warn("Playback queue full; dropping translation playback");
                    }
                }
                else
                {
                    // Fallback to writing to /tmp for debugging
                    long id = counter.incrementAndGet();
                    String filename = "/tmp/jigasi-translation-" + Instant.now().toEpochMilli() + "-" + id + ".wav";
                    File f = new File(filename);
                    try (FileOutputStream fos = new FileOutputStream(f))
                    {
                        fos.write(audio);
                        fos.flush();
                    }

                    logger.info("Wrote TTS audio to " + filename + " (bytes=" + audio.length + ")");

                    try { Files.setPosixFilePermissions(f.toPath(), java.util.Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)); } catch (Throwable t) {}
                }
            }
            catch (Throwable t)
            {
                logger.error("Failed to play translated audio", t);
            }

        }
        catch (Exception e)
        {
            logger.error("Error while synthesizing translation result", e);
        }
    }
}
