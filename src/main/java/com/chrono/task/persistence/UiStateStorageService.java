package com.chrono.task.persistence;

import com.chrono.task.model.UiState;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads/writes the {@link UiState} ({@code state.json}). Location, by priority:
 * <ol>
 * <li>system property {@code chronotaskai.state.file};</li>
 * <li>{@code state.json} next to the file of the {@code chronotaskai.settings.file} property, so a test settings
 * file never touches the real state;</li>
 * <li>{@code ~/.chrono-task-ai/state.json}.</li>
 * </ol>
 */
@Slf4j
public class UiStateStorageService {

    public static final String STATE_FILE_PROPERTY = "chronotaskai.state.file";
    private static final String STATE_FILE_NAME = "state.json";

    @Getter
    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();

    public UiStateStorageService() {
        this(defaultFile());
    }

    public UiStateStorageService(File file) {
        this.file = file;
    }

    static File defaultFile() {
        String stateFile = System.getProperty(STATE_FILE_PROPERTY);
        if (stateFile != null && !stateFile.isBlank()) {
            return new File(stateFile);
        }
        String settingsFile = System.getProperty("chronotaskai.settings.file");
        if (settingsFile != null && !settingsFile.isBlank()) {
            return new File(new File(settingsFile).getAbsoluteFile().getParentFile(), STATE_FILE_NAME);
        }
        return new File(new File(System.getProperty("user.home"), ".chrono-task-ai"), STATE_FILE_NAME);
    }

    /**
     * Never fails: a missing or unreadable file gives the default state.
     */
    public UiState load() {
        if (!file.exists()) {
            return new UiState();
        }
        try {
            UiState state = mapper.readValue(file, UiState.class);
            return state != null ? state : new UiState();
        } catch (IOException e) {
            // Handled case (corrupt/hand-edited file): message only, no stack trace
            log.warn("Can't read UI state from {}, using defaults: {}", file.getAbsolutePath(), e.getMessage());
            return new UiState();
        }
    }

    public void save(UiState state) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Can't create folder " + parent.getAbsolutePath());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, state);
    }
}
