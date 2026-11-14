package io.github.sekerator.kingdomworld.core.guide;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The plain text of the guide, as read and searched: links as their text, numbers as their values and items as the resolver
 * names them (nothing where the name stands beside the icon anyway).
 */
public final class GuideText {
    private static final Pattern SPACES = Pattern.compile(" {2,}");
    /** A space left before punctuation where an icon stood. */
    private static final Pattern BEFORE_PUNCTUATION = Pattern.compile(" (?=[.,;:!?)»])");

    private GuideText() {
    }

    /**
     * What the plain text shows for an item and for a number of the configuration.
     *
     * @param itemName the name of an item by its id
     * @param number   the value a number shows
     */
    public record Resolver(Function<String, String> itemName, Function<GuideInline.Number, String> number) {
        /** Items by their ids and numbers as their tokens: for checks that need no game. */
        public static final Resolver RAW = new Resolver(id -> id, GuideInline.Number::token);
    }

    /**
     * The plain text of a piece of running text.
     */
    public static String plain(List<GuideInline> text, Resolver resolver) {
        StringBuilder result = new StringBuilder();
        for (GuideInline inline : text) {
            if (inline instanceof GuideInline.Text words) {
                result.append(words.text());
            } else if (inline instanceof GuideInline.Link link) {
                result.append(link.text());
            } else if (inline instanceof GuideInline.Item item) {
                result.append(resolver.itemName().apply(item.id()));
            } else if (inline instanceof GuideInline.Number number) {
                result.append(resolver.number().apply(number));
            }
        }
        return BEFORE_PUNCTUATION.matcher(SPACES.matcher(result).replaceAll(" ")).replaceAll("");
    }

    /**
     * The plain text of a whole page, its pieces joined by spaces.
     */
    public static String plain(GuidePage page, Resolver resolver) {
        StringBuilder result = new StringBuilder();
        for (List<GuideInline> text : page.texts()) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(plain(text, resolver));
        }
        return result.toString();
    }
}
