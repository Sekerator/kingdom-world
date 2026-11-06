package io.github.sekerator.kingdomworld.client.screen;

import net.minecraft.network.chat.Component;

/**
 * A screen that shows the server's answers to its requests in its own status line.
 */
public interface ResultListener {

    void onResult(boolean success, Component message);
}
