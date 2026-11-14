package io.github.sekerator.kingdomworld.core.guide;

/**
 * A mistake in a file of the guide, with the number of its line.
 */
public class GuideFormatException extends IllegalArgumentException {
    public GuideFormatException(String message) {
        super(message);
    }
}
