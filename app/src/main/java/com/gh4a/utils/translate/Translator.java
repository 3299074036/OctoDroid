package com.gh4a.utils.translate;

/**
 * Translation provider interface. Implementations call third-party translation APIs.
 */
public interface Translator {
    /**
     * Translate text from sourceLang to targetLang.
     *
     * @param text       text to translate (plain text, no HTML)
     * @param sourceLang source language code (e.g. "en", "auto")
     * @param targetLang target language code (e.g. "zh-CN")
     * @return translated text
     * @throws Exception if translation fails
     */
    String translate(String text, String sourceLang, String targetLang) throws Exception;

    /** Display name of this provider. */
    String getName();

    /**
     * Max number of concurrent translate requests this provider tolerates.
     * Default 5; providers with strict rate limits (e.g. Baidu free tier
     * at ~1 QPS) override this with a lower value.
     */
    default int getMaxParallelRequests() {
        return 5;
    }
}
