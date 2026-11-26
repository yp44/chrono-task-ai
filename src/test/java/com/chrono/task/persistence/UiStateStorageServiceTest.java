package com.chrono.task.persistence;

import com.chrono.task.model.UiState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiStateStorageServiceTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty(UiStateStorageService.STATE_FILE_PROPERTY);
        System.clearProperty("chronotaskai.settings.file");
    }

    @Test
    void testRoundTrip(@TempDir Path dir) throws Exception {
        // parent folder created on save
        File file = dir.resolve("sub/state.json").toFile();
        UiStateStorageService service = new UiStateStorageService(file);

        service.save(UiState.builder().focusTopPriorities(true).build());

        assertTrue(file.exists());
        assertTrue(service.load().isFocusTopPriorities());

        service.save(UiState.builder().focusTopPriorities(false).build());
        assertFalse(service.load().isFocusTopPriorities());
    }

    @Test
    void testMissingFileGivesDefaults(@TempDir Path dir) {
        UiState state = new UiStateStorageService(dir.resolve("none.json").toFile()).load();
        assertFalse(state.isFocusTopPriorities());
    }

    @Test
    void testInvalidJsonGivesDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("state.json");
        Files.writeString(file, "{ not json");
        assertFalse(new UiStateStorageService(file.toFile()).load().isFocusTopPriorities());
    }

    @Test
    void testUnknownFieldsIgnored(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("state.json");
        Files.writeString(file, "{ \"focusTopPriorities\": true, \"someFutureField\": 42 }");
        assertTrue(new UiStateStorageService(file.toFile()).load().isFocusTopPriorities());
    }

    @Test
    void testDefaultLocation(@TempDir Path dir) {
        assertEquals(new File(new File(System.getProperty("user.home"), ".chrono-task-ai"), "state.json"),
                UiStateStorageService.defaultFile());

        // Next to a test settings file, so it never touches the real state
        System.setProperty("chronotaskai.settings.file", dir.resolve("settings.json").toString());
        assertEquals(dir.resolve("state.json").toFile(), UiStateStorageService.defaultFile());

        // Explicit property wins
        System.setProperty(UiStateStorageService.STATE_FILE_PROPERTY, dir.resolve("other.json").toString());
        assertEquals(dir.resolve("other.json").toFile(), UiStateStorageService.defaultFile());
    }
}
