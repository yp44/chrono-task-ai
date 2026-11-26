package com.chrono.task.persistence;

import com.chrono.task.model.DataStore;
import com.chrono.task.model.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class JsonStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveAndLoad() throws IOException {
        Path jsonFile = tempDir.resolve("test_data.json");
        JsonStorageService service = new JsonStorageService(jsonFile.toString());

        DataStore ds = new DataStore();
        Task t1 = Task.builder().description("Test1").build();
        t1.setTime(LocalDate.now(), Duration.ofMinutes(30));
        ds.getTasks().add(t1);

        service.save(ds);

        assertTrue(jsonFile.toFile().exists());

        DataStore loaded = service.load();
        assertEquals(1, loaded.getTasks().size());
        Task t2 = loaded.getTasks().getFirst();
        assertEquals("Test1", t2.getDescription());
        assertEquals(30, t2.getTotalTime().toMinutes());
        assertFalse(t2.isTopPriority());
    }

    @Test
    void testTopPriorityRoundTrip() throws IOException {
        Path jsonFile = tempDir.resolve("priority.json");
        JsonStorageService service = new JsonStorageService(jsonFile.toString());

        DataStore ds = new DataStore();
        ds.getTasks().add(Task.builder().description("Urgent").topPriority(true).build());
        ds.getTasks().add(Task.builder().description("Later").build());
        service.save(ds);

        DataStore loaded = service.load();
        assertTrue(loaded.getTasks().get(0).isTopPriority());
        assertFalse(loaded.getTasks().get(1).isTopPriority());
    }

    @Test
    void testOldDataFileWithoutTopPriority() throws IOException {
        Path jsonFile = tempDir.resolve("old.json");
        java.nio.file.Files.writeString(jsonFile, """
                { "tasks": [ { "id": "1", "description": "Old task", "status": "TODO" } ] }
                """);

        DataStore loaded = new JsonStorageService(jsonFile.toString()).load();
        assertEquals("Old task", loaded.getTasks().getFirst().getDescription());
        assertFalse(loaded.getTasks().getFirst().isTopPriority());
        assertNull(loaded.getTasks().getFirst().getLastActivatedAt());
    }

    @Test
    void testLastActivatedAtRoundTrip() throws IOException {
        Path jsonFile = tempDir.resolve("activation.json");
        JsonStorageService service = new JsonStorageService(jsonFile.toString());
        java.time.Instant when = java.time.Instant.parse("2026-11-26T08:15:30.123Z");

        DataStore ds = new DataStore();
        ds.getTasks().add(Task.builder().description("Used").lastActivatedAt(when).build());
        ds.getTasks().add(Task.builder().description("Never used").build());
        service.save(ds);

        DataStore loaded = service.load();
        assertEquals(when, loaded.getTasks().get(0).getLastActivatedAt());
        assertNull(loaded.getTasks().get(1).getLastActivatedAt());
    }
}