package com.chrono.task.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI state remembered between two launches (not settings): stored in {@code ~/.chrono-task-ai/state.json}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UiState {

    /**
     * "Focus: top priorities" filter checkbox of the Work tab.
     */
    private boolean focusTopPriorities;
}
