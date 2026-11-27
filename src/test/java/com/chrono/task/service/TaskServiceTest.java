package com.chrono.task.service;

import com.chrono.task.model.Task;
import java.time.LocalDate;
import com.chrono.task.persistence.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskServiceTest {

    private TaskService service;

    @BeforeEach
    void setup() {
        StorageService storageMock = Mockito.mock(StorageService.class);
        service = new TaskService(storageMock);
    }

    @Test
    void testCreateTask() {
        Task t = service.createTask("Foo");
        assertEquals("Foo", t.getDescription());
        assertEquals(0, t.getOrder());
        assertEquals(1, service.getTasks().size());
    }

    @Test
    void testReorder() {
        Task t1 = service.createTask("A");
        Task t2 = service.createTask("B");

        // Swap
        service.updateOrder(List.of(t2, t1));

        assertEquals(0, t2.getOrder());
        assertEquals(1, t1.getOrder());
        assertEquals("B", service.getTasks().getFirst().getDescription());
    }

    @Test
    void testFilter() {
        Task t1 = service.createTask("Buy Milk");
        Task t2 = service.createTask("Walk Dog");

        // Basic description match
        assertEquals(1, service.filter("Milk", true).size());
        assertEquals(t1, service.filter("Milk", true).getFirst());

        // Case insensitivity
        assertEquals(1, service.filter("milk", true).size());

        // Empty query
        assertEquals(2, service.filter("", true).size());

        // No match
        assertEquals(0, service.filter("Cat", true).size());

        // Markdown match
        t2.setMarkdownContent("Remember to buy poop bags");
        assertEquals(2, service.filter("buy", true).size());
        assertEquals(1, service.filter("bags", true).size());
        assertEquals(t2, service.filter("bags", true).getFirst());

        // Daily note match
        t1.setDailyNote(LocalDate.now(), "Got some organic milk");
        assertEquals(1, service.filter("organic", true).size());
        assertEquals(t1, service.filter("organic", true).getFirst());

        // Jira URL match
        t2.setJiraUrl("https://jira.example.com/browse/DOG-123");
        assertEquals(1, service.filter("DOG-123", true).size());
        assertEquals(t2, service.filter("DOG-123", true).getFirst());
    }

    @Test
    void testReorderWithSelf() {
        service.createTask("Task 1");
        service.createTask("Task 2");

        // Simulate what Drag operations do often: pass the list itself or a list that
        // is the same instance
        // TaskService.updateOrder calls tasks.setAll(newOrder)
        // If newOrder == tasks, setAll clears it first.

        service.updateOrder(service.getTasks());

        assertFalse(service.getTasks().isEmpty(), "Tasks list should not be empty after reordering with itself");
        assertEquals(2, service.getTasks().size());
    }

    @Test
    void testDuplicateDescriptionOnCreate() {
        service.createTask("Task A");
        assertThrows(IllegalArgumentException.class, () -> service.createTask("Task A"));
    }

    @Test
    void testDuplicateDescriptionOnUpdate() {
        Task t1 = service.createTask("Task A");
        Task t2 = service.createTask("Task B");

        assertThrows(IllegalArgumentException.class, () -> service.updateTaskDescription(t2, "Task A"));

        // Self update should be fine
        assertDoesNotThrow(() -> service.updateTaskDescription(t1, "Task A"));
    }

    @Test
    void testDuplicateJiraUrlOnUpdate() {
        Task t1 = service.createTask("Task A");
        service.updateTaskJiraUrl(t1, "http://jira.com/1");

        Task t2 = service.createTask("Task B");
        assertThrows(IllegalArgumentException.class, () -> service.updateTaskJiraUrl(t2, "http://jira.com/1"));

        // Unique url is fine
        assertDoesNotThrow(() -> service.updateTaskJiraUrl(t2, "http://jira.com/2"));
    }

    @Test
    void testFilterByDate() {
        LocalDate day = LocalDate.of(2023, 1, 1);
        Task worked = service.createTask("Buy Milk");
        worked.setTime(day, java.time.Duration.ofMinutes(10));
        Task other = service.createTask("Buy Bread");
        other.setTime(day.plusDays(1), java.time.Duration.ofMinutes(10));
        Task done = service.createTask("Done Task");
        done.setTime(day, java.time.Duration.ofMinutes(10));
        done.setStatus(com.chrono.task.model.TaskStatus.DONE);

        // No date: same as before
        assertEquals(3, service.filter("", true, null).size());

        // Date only (done tasks shown)
        assertEquals(List.of(worked, done), service.filter("", true, day));

        // Combined with the "show completed" flag
        assertEquals(List.of(worked), service.filter("", false, day));

        // Combined with the text query
        assertEquals(List.of(worked), service.filter("milk", true, day));
        assertTrue(service.filter("bread", true, day).isEmpty());

        // Day without any activity
        assertTrue(service.filter("", true, day.plusDays(10)).isEmpty());
    }

    @Test
    void testSortLastUsed() {
        Task a = service.createTask("A");
        Task b = service.createTask("B");
        Task c = service.createTask("C");
        Task d = service.createTask("D");
        Task e = service.createTask("E");
        java.time.Instant t0 = java.time.Instant.parse("2026-11-20T09:00:00Z");
        b.setLastActivatedAt(t0);
        d.setLastActivatedAt(t0.plusSeconds(3600));
        e.setLastActivatedAt(t0); // tie with b: manual order kept
        List<Task> manual = List.copyOf(service.getTasks());

        // Manual: unchanged copy
        List<Task> sortedManual = TaskService.sort(manual, com.chrono.task.model.TaskSortMode.MANUAL);
        assertEquals(manual, sortedManual);
        assertNotSame(manual, sortedManual);

        // Last used: most recent first, ties and never activated tasks in manual order, the latter at the bottom
        List<Task> neverActivated = manual.stream().filter(t -> t == a || t == c).toList();
        List<Task> activatedAtT0 = manual.stream().filter(t -> t == b || t == e).toList();
        List<Task> expected = new java.util.ArrayList<>();
        expected.add(d);
        expected.addAll(activatedAtT0);
        expected.addAll(neverActivated);
        assertEquals(expected, TaskService.sort(manual, com.chrono.task.model.TaskSortMode.LAST_USED));

        // The master list is never reordered
        assertEquals(manual, service.getTasks());
    }

    @Test
    void testSortLastUsedAfterFilters() {
        Task milk = service.createTask("Buy Milk");
        Task bread = service.createTask("Buy Bread");
        Task dog = service.createTask("Walk Dog");
        milk.setLastActivatedAt(java.time.Instant.parse("2026-11-20T09:00:00Z"));
        bread.setLastActivatedAt(java.time.Instant.parse("2026-11-21T09:00:00Z"));
        dog.setLastActivatedAt(java.time.Instant.parse("2026-11-22T09:00:00Z"));
        bread.setStatus(com.chrono.task.model.TaskStatus.DONE);

        List<Task> filtered = service.filter("buy", false, null, false);
        assertEquals(List.of(milk), TaskService.sort(filtered, com.chrono.task.model.TaskSortMode.LAST_USED));
        assertEquals(List.of(bread, milk),
                TaskService.sort(service.filter("buy", true, null, false), com.chrono.task.model.TaskSortMode.LAST_USED));
    }

    @Test
    void testFilterTopPriorities() {
        LocalDate day = LocalDate.of(2023, 1, 1);
        Task milk = service.createTask("Buy Milk");
        milk.setTopPriority(true);
        milk.setTime(day, java.time.Duration.ofMinutes(10));
        Task bread = service.createTask("Buy Bread");
        bread.setTime(day, java.time.Duration.ofMinutes(10));
        Task dog = service.createTask("Walk Dog");
        dog.setTopPriority(true);
        Task done = service.createTask("Done Task");
        done.setTopPriority(true);
        done.setStatus(com.chrono.task.model.TaskStatus.DONE);

        // Off: same as before
        assertEquals(List.of(milk, bread, dog), service.filter("", false, null, false));

        // Alone (done tasks shown or not)
        assertEquals(List.of(milk, dog, done), service.filter("", true, null, true));
        assertEquals(List.of(milk, dog), service.filter("", false, null, true));

        // Combined with the text query
        assertEquals(List.of(milk), service.filter("buy", false, null, true));
        assertTrue(service.filter("bread", false, null, true).isEmpty());

        // Combined with the day filter
        assertEquals(List.of(milk), service.filter("", false, day, true));
    }

    @Test
    void testMoveToTop() {
        Task a = service.createTask("A");
        Task b = service.createTask("B");
        Task c = service.createTask("C");
        Task d = service.createTask("D");

        service.moveToTop(c);

        assertEquals(List.of(c, a, b, d), service.getTasks());
        assertOrderFieldsMatchPositions();
    }

    @Test
    void testMoveToBottom() {
        Task a = service.createTask("A");
        Task b = service.createTask("B");
        Task c = service.createTask("C");
        Task d = service.createTask("D");

        service.moveToBottom(b);

        assertEquals(List.of(a, c, d, b), service.getTasks());
        assertOrderFieldsMatchPositions();
    }

    @Test
    void testMoveIgnoresFilters() {
        Task a = service.createTask("A");
        Task done = service.createTask("Done");
        done.setStatus(com.chrono.task.model.TaskStatus.DONE);
        Task c = service.createTask("C");

        // "Done" is hidden by the filter but still counts: the task goes after it
        service.moveToBottom(a);
        assertEquals(List.of(done, c, a), service.getTasks());
        assertEquals(List.of(c, a), service.filter("", false));
    }

    @Test
    void testMoveAlreadyInPlaceChangesNothing() {
        Task a = service.createTask("A");
        Task b = service.createTask("B");
        Task c = service.createTask("C");

        service.moveToTop(a);
        assertEquals(List.of(a, b, c), service.getTasks());
        service.moveToBottom(c);
        assertEquals(List.of(a, b, c), service.getTasks());
        assertOrderFieldsMatchPositions();
    }

    @Test
    void testMoveUnknownTaskThrows() {
        Task a = service.createTask("A");
        Task b = service.createTask("B");
        // Same id/description as "A" but another instance, not in the list
        Task stranger = Task.builder().id(a.getId()).description(a.getDescription()).build();

        assertThrows(IllegalArgumentException.class, () -> service.moveToTop(stranger));
        assertThrows(IllegalArgumentException.class, () -> service.moveToBottom(stranger));
        assertThrows(IllegalArgumentException.class, () -> service.moveToTop(null));
        assertThrows(IllegalArgumentException.class, () -> service.moveToBottom(null));

        assertSame(a, service.getTasks().get(0));
        assertSame(b, service.getTasks().get(1));
        assertEquals(2, service.getTasks().size());
    }

    private void assertOrderFieldsMatchPositions() {
        for (int i = 0; i < service.getTasks().size(); i++) {
            assertEquals(i, service.getTasks().get(i).getOrder());
        }
    }

    @Test
    void testSlackUrl() {
        Task t = service.createTask("Slack Task");
        service.updateTaskSlackUrl(t, "https://slack.com/archives/C12345");
        assertEquals("https://slack.com/archives/C12345", t.getSlackUrl());

        // Check filter
        assertEquals(1, service.filter("archives/C12345", true).size());
        assertEquals(t, service.filter("archives/C12345", true).getFirst());
    }
}