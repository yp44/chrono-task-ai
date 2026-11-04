package com.chrono.task.persistence;

import com.chrono.task.model.Settings;
import com.chrono.task.model.TerminalShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStorageServiceTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty("chronotaskai.settings.file");
    }

    @Test
    void testTerminalShellsRoundTrip(@TempDir Path dir) throws Exception {
        System.setProperty("chronotaskai.settings.file", dir.resolve("settings.json").toString());
        SettingsStorageService service = new SettingsStorageService();

        Settings settings = new Settings();
        settings.setTerminalShells(List.of(new TerminalShell("Bash", "/bin/bash -l"), new TerminalShell("Sh", "/bin/sh")));
        service.save(settings);

        assertEquals(settings.getTerminalShells(), service.load().getTerminalShells());
    }

    @Test
    void testOldSettingsFileWithSingleShellStillLoads(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"terminalShell\":\"/bin/zsh\"}");
        System.setProperty("chronotaskai.settings.file", file.toString());

        Settings loaded = new SettingsStorageService().load();

        assertEquals("/bin/zsh", loaded.getTerminalShell());
        assertTrue(loaded.getTerminalShells().isEmpty());
    }
}
