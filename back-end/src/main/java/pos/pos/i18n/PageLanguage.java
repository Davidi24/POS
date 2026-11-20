package pos.pos.i18n;

import org.springframework.web.util.HtmlUtils;

import java.util.function.Function;

/**
 * The language of a page the server shows in a browser (password and email links, guest booking pages; user,
 * 2026-11-18): the one picked on the page ({@code ?lang=}), else the person's own, else the browser's, else English.
 * Every such page has the four languages at its top, English always among them.
 */
public final class PageLanguage {

    private PageLanguage() { }

    public static String pick(String picked, String own, String acceptLanguage) {
        if (picked != null && Words.LANGUAGES.contains(picked.trim().toLowerCase())) return picked.trim().toLowerCase();
        if (own != null && Words.LANGUAGES.contains(own.trim().toLowerCase())) return own.trim().toLowerCase();
        String browser = Words.fromBrowser(acceptLanguage);
        return browser == null ? "en" : browser;
    }

    /** "English · Italiano · Español · Shqip", the one in use in bold; [hrefFor] gives each one's link. */
    public static String switcher(String current, Function<String, String> hrefFor) {
        StringBuilder html = new StringBuilder("<nav class='langs' aria-label='Language'>");
        for (int i = 0; i < Words.LANGUAGES.size(); i++) {
            String code = Words.LANGUAGES.get(i);
            if (i > 0) html.append(" · ");
            if (code.equals(current)) {
                html.append("<b lang='").append(code).append("'>").append(Words.nativeName(code)).append("</b>");
            } else {
                html.append("<a lang='").append(code).append("' hreflang='").append(code).append("' href='")
                        .append(HtmlUtils.htmlEscape(hrefFor.apply(code))).append("'>").append(Words.nativeName(code)).append("</a>");
            }
        }
        return html.append("</nav>").toString();
    }

    /** The switcher's look, for a page's style sheet. */
    public static final String STYLE = ".langs{font-size:13px;color:#747572;text-align:right;margin:0 0 10px}"
            + ".langs a{color:#4f7942;text-decoration:none}.langs b{color:#222426}";
}
