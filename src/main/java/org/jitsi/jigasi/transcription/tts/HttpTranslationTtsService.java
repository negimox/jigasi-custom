package org.jitsi.jigasi.transcription.tts;

import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging2.Logger;
import org.jitsi.utils.logging2.LoggerImpl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP backed TTS client which posts JSON to the configured TTS
 * endpoint and returns the binary response.
 *
 * Configuration properties:
 * - org.jitsi.jigasi.transcription.tts.url (required)
 * - org.jitsi.jigasi.transcription.tts.authHeader (optional) e.g. "Bearer xxxxx"
 */
public class HttpTranslationTtsService implements TranslationTtsService
{
    private static final Logger logger = new LoggerImpl(HttpTranslationTtsService.class.getName());

    private final String ttsUrl;
    private final String authHeader;

    public HttpTranslationTtsService()
    {
        // Try to read from Jigasi configuration; fall back to system props/env
        String urlProp = null;
        String authProp = null;
        try
        {
            // Access may throw or return null when running outside OSGi
            if (JigasiBundleActivator.getConfigurationService() != null)
            {
                urlProp = JigasiBundleActivator.getConfigurationService()
                    .getString("org.jitsi.jigasi.transcription.tts.url", null);
                authProp = JigasiBundleActivator.getConfigurationService()
                    .getString("org.jitsi.jigasi.transcription.tts.authHeader", null);
            }
        }
        catch (Throwable t)
        {
            // ignore and fallback to system properties
        }

        if (urlProp == null || urlProp.isEmpty())
            urlProp = System.getProperty("org.jitsi.jigasi.transcription.tts.url");
        if (urlProp == null || urlProp.isEmpty())
            urlProp = System.getenv("JIGASI_TTS_URL");

        authProp = (authProp == null || authProp.isEmpty())
            ? System.getProperty("org.jitsi.jigasi.transcription.tts.authHeader")
            : authProp;
        if (authProp == null || authProp.isEmpty())
            authProp = System.getenv("JIGASI_TTS_AUTH");

        this.ttsUrl = urlProp;
        this.authHeader = authProp;
    }

    @Override
    public byte[] synthesize(String text, String langTag) throws Exception
    {
        if (ttsUrl == null)
        {
            logger.warn("TTS URL not configured (org.jitsi.jigasi.transcription.tts.url)");
            return null;
        }

        URL url = new URL(ttsUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (authHeader != null && !authHeader.isEmpty())
        {
            conn.setRequestProperty("Authorization", authHeader);
        }

        // Build simple JSON body using the openapi: input + response_format wav
        String providerLang = mapToProviderLang(langTag);
        StringBuilder body = new StringBuilder();
        body.append('{');
        body.append("\"input\":");
        body.append('"');
        body.append(escapeJson(text));
        body.append('"');
        if (providerLang != null)
        {
            body.append(",\"lang_code\":\"").append(escapeJson(providerLang)).append('\"');
        }
        else if (langTag != null)
        {
            logger.warn("TTS: unsupported language tag '" + langTag + "' - omitting lang_code");
        }
        body.append(",\"response_format\":\"wav\"");
        body.append('}');

        byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(out.length);

        try (OutputStream os = conn.getOutputStream())
        {
            os.write(out);
        }

        int rc = conn.getResponseCode();
        if (rc != 200)
        {
            InputStream err = conn.getErrorStream();
            String errMsg = "";
            if (err != null)
            {
                byte[] buf = err.readAllBytes();
                errMsg = new String(buf, StandardCharsets.UTF_8);
            }
            logger.warn("TTS request failed: rc=" + rc + " msg=" + errMsg);
            return null;
        }

        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            byte[] buff = new byte[8192];
            int r;
            while ((r = in.read(buff)) != -1)
            {
                baos.write(buff, 0, r);
            }
            return baos.toByteArray();
        }
        finally
        {
            conn.disconnect();
        }
    }

    private static String escapeJson(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * Map common i18n language tags to the provider's single-letter codes.
     * Returns null when no mapping exists (caller should omit lang_code).
     */
    private static String mapToProviderLang(String langTag)
    {
        if (langTag == null || langTag.isEmpty())
            return null;

        String t = langTag.toLowerCase().replace('_', '-');
        // primary tag and region if present
        String[] parts = t.split("-", 2);
        String primary = parts[0];
        String region = parts.length > 1 ? parts[1] : "";

        switch (primary)
        {
            case "en":
                // default to American English unless region indicates British
                if (region.contains("gb") || region.contains("uk") || region.contains("gb"))
                    return "b"; // British
                return "a"; // American
            case "ja":
            case "jp":
                return "j"; // Japanese
            case "zh":
            case "zh-cn":
            case "zh-hans":
                return "z"; // Mandarin Chinese
            case "es":
                return "e"; // Spanish
            case "fr":
                return "f"; // French
            case "hi":
            case "hi-in":
                return "h"; // Hindi
            case "it":
                return "i"; // Italian
            case "pt":
                // Brazilian Portuguese is supported (p). Default to 'p'.
                if (region.contains("br"))
                    return "p";
                return "p";
            default:
                return null;
        }
    }
}
