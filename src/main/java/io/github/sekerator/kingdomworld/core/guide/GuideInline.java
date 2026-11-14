package io.github.sekerator.kingdomworld.core.guide;

import java.util.Objects;

/**
 * A piece of the running text of a page of the guide: plain or emphasized words, a link to another page, the icon of an item
 * or a number taken from the configuration of the server.
 */
public sealed interface GuideInline {

    /**
     * Words of the text, {@code bold} for the emphasized ones ({@code **like this**}).
     */
    record Text(String text, boolean bold) implements GuideInline {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A link to the page {@code target} shown as {@code text} ({@code [text](target)}).
     */
    record Link(String target, String text) implements GuideInline {
        public Link {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * The icon of the item {@code id} ({@code {item:minecraft:iron_axe}}), its name and tooltip shown on hover.
     */
    record Item(String id) implements GuideInline {
        public Item {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * A number from the configuration of the server ({@code {cfg:territory.baseMaxChunks}}): the value of the server the player
     * plays on, or the default without one.
     *
     * @param path     the path of the value, its sections and name joined by dots
     * @param index    for a table of numbers (a value such as {@code "1, 2, 3"}), the number taken from it counted from 1; 0 for
     *                 the whole value
     * @param operator {@code '*'} or {@code '/'} to scale the value by {@code operand}, or 0 to show it as it is
     */
    record Number(String path, int index, char operator, double operand) implements GuideInline {
        public Number {
            Objects.requireNonNull(path, "path");
        }

        /**
         * The value as written in the text, for messages and checks.
         */
        public String token() {
            StringBuilder token = new StringBuilder("{cfg:").append(path);
            if (index > 0) {
                token.append('#').append(index);
            }
            if (operator != 0) {
                token.append(operator).append(GuideNumbersFormat.plain(operand));
            }
            return token.append('}').toString();
        }
    }
}
