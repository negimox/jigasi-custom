package org.jitsi.jigasi.transcription.test;

import org.jitsi.jigasi.transcription.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Small standalone test harness to exercise TranslationPlaybackManager.
 * Usage:
 *   mvn -Dexec.mainClass=org.jitsi.jigasi.transcription.test.TranslationPlaybackTest exec:java
 * Ensure the property org.jitsi.jigasi.transcription.tts.url is set in Jigasi's config
 * or as a system property when running to point at your TTS endpoint.
 */
public class TranslationPlaybackTest
{
    public static void main(String[] args)
    {
        // Create a dummy TranscriptionResult
        TranscriptionResult tr = new TranscriptionResult(
            null, UUID.randomUUID(), Instant.now(), false, "en-US", 0.0,
            new TranscriptionAlternative("Hello, this is a test", 1.0));

        TranslationResult translation = new TranslationResult(tr, "en", "Hello from the TTS test");

        TranslationPlaybackManager mgr = new TranslationPlaybackManager();
        mgr.notify(translation);

    System.out.println("Requested TTS for text: '" + translation.getTranslatedText() + "'");
    }
}
