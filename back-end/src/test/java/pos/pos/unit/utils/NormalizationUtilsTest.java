package pos.pos.unit.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pos.pos.utils.NormalizationUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalizationUtilsTest {

    /*
     * =========================================
     * normalize()
     * =========================================
     */

    @Nested
    @DisplayName("normalize()")
    class Normalize {

        @Test
        @DisplayName("Should return trimmed value for normal string")
        void shouldReturnTrimmed() {
            assertEquals("hello", NormalizationUtils.normalize("  hello  "));
        }

        @Test
        @DisplayName("Should return value unchanged when no whitespace")
        void shouldReturnUnchanged_whenNoWhitespace() {
            assertEquals("hello", NormalizationUtils.normalize("hello"));
        }

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNull_forNull() {
            assertNull(NormalizationUtils.normalize(null));
        }

        @Test
        @DisplayName("Should return null for blank string")
        void shouldReturnNull_forBlank() {
            assertNull(NormalizationUtils.normalize("   "));
        }

        @Test
        @DisplayName("Should return null for empty string")
        void shouldReturnNull_forEmpty() {
            assertNull(NormalizationUtils.normalize(""));
        }

        @Test
        @DisplayName("Should trim only leading whitespace")
        void shouldTrimLeading() {
            assertEquals("hello", NormalizationUtils.normalize("   hello"));
        }

        @Test
        @DisplayName("Should trim only trailing whitespace")
        void shouldTrimTrailing() {
            assertEquals("hello", NormalizationUtils.normalize("hello   "));
        }

        @Test
        @DisplayName("Should preserve internal whitespace")
        void shouldPreserveInternalWhitespace() {
            assertEquals("hello world", NormalizationUtils.normalize("  hello world  "));
        }

        @Test
        @DisplayName("Should return null for tab-only string")
        void shouldReturnNull_forTabOnly() {
            assertNull(NormalizationUtils.normalize("\t"));
        }

        @Test
        @DisplayName("Should return null for newline-only string")
        void shouldReturnNull_forNewlineOnly() {
            assertNull(NormalizationUtils.normalize("\n"));
        }
    }

    /*
     * =========================================
     * normalizeLower()
     * =========================================
     */

    @Nested
    @DisplayName("normalizeLower()")
    class NormalizeLower {

        @Test
        @DisplayName("Should return lowercased trimmed value")
        void shouldReturnLowerTrimmed() {
            assertEquals("hello world", NormalizationUtils.normalizeLower("  Hello World  "));
        }

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNull_forNull() {
            assertNull(NormalizationUtils.normalizeLower(null));
        }

        @Test
        @DisplayName("Should return null for blank string")
        void shouldReturnNull_forBlank() {
            assertNull(NormalizationUtils.normalizeLower("   "));
        }

        @Test
        @DisplayName("Should return null for empty string")
        void shouldReturnNull_forEmpty() {
            assertNull(NormalizationUtils.normalizeLower(""));
        }

        @Test
        @DisplayName("Should lowercase already trimmed value")
        void shouldLowercase_alreadyTrimmed() {
            assertEquals("abc", NormalizationUtils.normalizeLower("ABC"));
        }

        @Test
        @DisplayName("Should use ROOT locale under Turkish default locale")
        void shouldUseRootLocale() {
            withDefaultLocale(Locale.forLanguageTag("tr-TR"), () -> {
                assertEquals("\u0131", "I".toLowerCase());
                assertEquals("i", NormalizationUtils.normalizeLower("I"));
            });
        }

        @Test
        @DisplayName("Should handle already lowercase string")
        void shouldHandleAlreadyLowercase() {
            assertEquals("hello", NormalizationUtils.normalizeLower("hello"));
        }
    }

    /*
     * =========================================
     * normalizeUpper()
     * =========================================
     */

    @Nested
    @DisplayName("normalizeUpper()")
    class NormalizeUpper {

        @Test
        @DisplayName("Should return uppercased trimmed value")
        void shouldReturnUpperTrimmed() {
            assertEquals("HELLO WORLD", NormalizationUtils.normalizeUpper("  hello world  "));
        }

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNull_forNull() {
            assertNull(NormalizationUtils.normalizeUpper(null));
        }

        @Test
        @DisplayName("Should return null for blank string")
        void shouldReturnNull_forBlank() {
            assertNull(NormalizationUtils.normalizeUpper("   "));
        }

        @Test
        @DisplayName("Should return null for empty string")
        void shouldReturnNull_forEmpty() {
            assertNull(NormalizationUtils.normalizeUpper(""));
        }

        @Test
        @DisplayName("Should uppercase already trimmed value")
        void shouldUppercase_alreadyTrimmed() {
            assertEquals("ABC", NormalizationUtils.normalizeUpper("abc"));
        }

        @Test
        @DisplayName("Should use ROOT locale under Turkish default locale")
        void shouldUseRootLocale() {
            withDefaultLocale(Locale.forLanguageTag("tr-TR"), () -> {
                assertEquals("\u0130", "i".toUpperCase());
                assertEquals("I", NormalizationUtils.normalizeUpper("i"));
            });
        }

        @Test
        @DisplayName("Should handle already uppercase string")
        void shouldHandleAlreadyUppercase() {
            assertEquals("HELLO", NormalizationUtils.normalizeUpper("HELLO"));
        }
    }

    @Nested
    @DisplayName("normalizePhone()")
    class NormalizePhone {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNull() {
            assertNull(NormalizationUtils.normalizePhone(null));
        }

        @Test
        @DisplayName("Should return null for blank input")
        void shouldReturnNullForBlank() {
            assertNull(NormalizationUtils.normalizePhone("   "));
        }

        @Test
        @DisplayName("Should remove spaces and common separators")
        void shouldRemoveSpacesAndCommonSeparators() {
            assertEquals("+495550100", NormalizationUtils.normalizePhone(" +49 (555) 01-00 "));
        }

        @Test
        @DisplayName("Should preserve digits and a leading plus sign")
        void shouldPreserveDigitsAndLeadingPlusSign() {
            assertEquals("+12025550100", NormalizationUtils.normalizePhone("+1.202.555.0100"));
        }
    }

    private void withDefaultLocale(Locale locale, Runnable assertion) {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            assertion.run();
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
