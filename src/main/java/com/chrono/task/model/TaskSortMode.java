package com.chrono.task.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Order of the task list in the Work tab ("Sort by" combo).
 */
public enum TaskSortMode {

    /**
     * Drag &amp; drop order of the master task list (default).
     */
    @JsonEnumDefaultValue
    MANUAL("Manual order"),

    /**
     * Most recently activated first (see {@link Task#getLastActivatedAt()}); never activated tasks at the bottom,
     * in manual order.
     */
    LAST_USED("Last used");

    private final String label;

    TaskSortMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Label displayed by the combo box.
     */
    @Override
    public String toString() {
        return label;
    }
}
