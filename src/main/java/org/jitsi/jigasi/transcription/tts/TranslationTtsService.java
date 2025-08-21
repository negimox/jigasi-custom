package org.jitsi.jigasi.transcription.tts;

/**
 * Minimal contract for a TTS service used to synthesize translated text to audio.
 */
public interface TranslationTtsService
{
    /**
     * Synthesize the given text and return raw audio bytes.
     * The returned bytes are the exact response body as received from the
     * remote service (e.g. WAV/PCM/OPUS). Caller is responsible for
     * interpreting/decoding them.
     *
     * @param text the text to synthesize
     * @param langTag BCP-47 or service-specific language tag (nullable)
     * @return audio bytes, or null on failure
     * @throws Exception on transport or other fatal errors
     */
    byte[] synthesize(String text, String langTag) throws Exception;
}
