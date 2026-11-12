package io.github.sekerator.kingdomworld.core.territory;

import java.util.UUID;

/**
 * Notified after a province changes hands: one event for the whole province, however many chunks it has.
 */
@FunctionalInterface
public interface ProvinceListener {

    /**
     * @param dimension the dimension of the provinces
     * @param province  the number of the province
     * @param oldOwner  the kingdom that held it, or {@code null}
     * @param newOwner  the kingdom that holds it now, or {@code null}
     */
    void onProvinceChanged(String dimension, int province, UUID oldOwner, UUID newOwner);
}
