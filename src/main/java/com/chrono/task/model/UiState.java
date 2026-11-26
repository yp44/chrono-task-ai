package com.chrono.task.model;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /**
     * "Sort by" combo of the Work tab. An unknown value (e.g. written by a newer version) reads as
     * {@link TaskSortMode#MANUAL}.
     */
    @Builder.Default
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
    private TaskSortMode taskSortMode = TaskSortMode.MANUAL;

    /**
     * Never null: {@link TaskSortMode#MANUAL} when missing.
     */
    public TaskSortMode getTaskSortMode() {
        return taskSortMode != null ? taskSortMode : TaskSortMode.MANUAL;
    }
}
