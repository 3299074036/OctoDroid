package com.gh4a.utils.translate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Translator interface defaults, exercised through a hand-written stub. */
public class TranslatorTest {

    private static class StubTranslator implements Translator {
        private final String mName;

        StubTranslator(String name) {
            mName = name;
        }

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            return "[" + targetLang + "]" + text;
        }

        @Override
        public String getName() {
            return mName;
        }
    }

    @Test
    public void getMaxParallelRequests_defaultIsFive() {
        assertEquals(5, new StubTranslator("Stub").getMaxParallelRequests());
    }

    @Test
    public void translate_stubEchoesWithTargetLang() throws Exception {
        assertEquals("[zh-CN]Hello",
                new StubTranslator("Stub").translate("Hello", "en", "zh-CN"));
    }

    @Test
    public void getName_returnsConstructorName() {
        assertEquals("Stub", new StubTranslator("Stub").getName());
        assertEquals("Other", new StubTranslator("Other").getName());
    }
}
