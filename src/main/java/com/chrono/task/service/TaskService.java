package com.chrono.task.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import com.chrono.task.model.DataStore;
import com.chrono.task.model.Task;
import com.chrono.task.model.TaskSortMode;
import com.chrono.task.model.TaskStatus;
import com.chrono.task.persistence.StorageService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskService {

    private final StorageService storageService;

    @Getter
    private final ObservableList<Task> tasks;
    private final ScheduledExecutorService autoSaveScheduler;
    private final ObjectProperty<LocalDateTime> lastSaveTime = new SimpleObjectProperty<>();

    public TaskService(StorageService storageService) {
        this.storageService = storageService;
        this.tasks = FXCollections.observableArrayList();
        this.autoSaveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoSave-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void init() throws IOException {
        DataStore data = storageService.load();
        if (data.getTasks() != null) {
            tasks.setAll(data.getTasks());
            // Ensure they are sorted by order
            tasks.sort(Comparator.comparingInt(Task::getOrder));
        }

        // Schedule auto-save every 3 minutes
        autoSaveScheduler.scheduleAtFixedRate(this::saveSafely, 30, 3, TimeUnit.MINUTES);
    }

    public ObjectProperty<LocalDateTime> lastSaveTimeProperty() {
        return lastSaveTime;
    }

    public Task createTask(String description) {
        if (tasks.stream().anyMatch(t -> t.getDescription().equals(description))) {
            throw new IllegalArgumentException("Task with description '" + description + "' already exists");
        }
        Task task = Task.builder()
                .description(description)
                .order(tasks.size()) // Append to end
                .build();
        tasks.add(task);
        return task;
    }

    public void updateTaskDescription(Task task, String newDescription) {
        if (tasks.stream()
                .anyMatch(t -> !t.getId().equals(task.getId()) && t.getDescription().equals(newDescription))) {
            throw new IllegalArgumentException("Task with description '" + newDescription + "' already exists");
        }
        task.setDescription(newDescription);
    }

    public void updateTaskJiraUrl(Task task, String newUrl) {
        if (newUrl != null && !newUrl.isBlank()
                && tasks.stream().anyMatch(t -> !t.getId().equals(task.getId()) && newUrl.equals(t.getJiraUrl()))) {
            throw new IllegalArgumentException("Task with Jira URL '" + newUrl + "' already exists");
        }
        task.setJiraUrl(newUrl);
    }

    public void updateTaskSlackUrl(Task task, String newUrl) {
        task.setSlackUrl(newUrl);
    }

    /**
     * Filters tasks based on a query string against description, jiraUrl, or tags.
     */
    public List<Task> filter(String query, boolean showDoneTasks) {
        return filter(query, showDoneTasks, null);
    }

    /**
     * Same as {@link #filter(String, boolean)}, additionally keeping only the tasks worked on at {@code date}
     * (see {@link Task#hasActivityOn(LocalDate)}) when {@code date} is not null.
     */
    public List<Task> filter(String query, boolean showDoneTasks, LocalDate date) {
        return filter(query, showDoneTasks, date, false);
    }

    /**
     * Same as {@link #filter(String, boolean, LocalDate)}, additionally keeping only the top priority tasks
     * when {@code onlyTopPriorities} is true (all the criteria are combined with AND).
     */
    public List<Task> filter(String query, boolean showDoneTasks, LocalDate date, boolean onlyTopPriorities) {
        Stream<Task> stream = tasks.stream();
        if (onlyTopPriorities) {
            stream = stream.filter(Task::isTopPriority);
        }
        if (date != null) {
            stream = stream.filter(t -> t.hasActivityOn(date));
        }
        if (query != null && !query.trim().isBlank()) {
            String lowerQuery = query.toLowerCase();
            stream = stream.filter(t -> (t.getDescription() != null && t.getDescription().toLowerCase().contains(lowerQuery)) ||
                    (t.getJiraUrl() != null && t.getJiraUrl().toLowerCase().contains(lowerQuery)) ||
                    (t.getSlackUrl() != null && t.getSlackUrl().toLowerCase().contains(lowerQuery)) ||
                    (t.getMarkdownContent() != null && t.getMarkdownContent().toLowerCase().contains(lowerQuery)) ||
                    (t.getTaskHistory().values().stream()
                            .anyMatch(work -> work.getNote() != null
                                    && work.getNote().toLowerCase().contains(lowerQuery)))
                    ||
                    (t.getTags() != null
                            && t.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery))));
        }
        return stream.filter(t -> showDoneTasks ||
                        (t.getStatus() != com.chrono.task.model.TaskStatus.DONE && t.getStatus() != TaskStatus.TO_DELETE))
                .collect(Collectors.toList());
    }

    /**
     * Returns a sorted copy of {@code tasks} (expected in manual order, e.g. a {@link #filter} result).
     * {@link TaskSortMode#MANUAL}: unchanged order. {@link TaskSortMode#LAST_USED}: most recent
     * {@link Task#getLastActivatedAt()} first, never activated tasks at the bottom; ties keep the given order.
     */
    public static List<Task> sort(List<Task> tasks, TaskSortMode mode) {
        List<Task> sorted = new java.util.ArrayList<>(tasks);
        if (mode == TaskSortMode.LAST_USED) {
            // List.sort is stable: equal keys (incl. all the never activated tasks) keep the manual order
            sorted.sort(Comparator.comparing(Task::getLastActivatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return sorted;
    }

    /**
     * Moves {@code task} to the first position of the manual order (master list, filters ignored) and recomputes
     * the order with {@link #updateOrder} (DONE tasks stay at the end: a DONE task goes first among the DONE ones).
     * No change if it is already first.
     *
     * @throws IllegalArgumentException if {@code task} is {@code null} or not in the task list
     */
    public void moveToTop(Task task) {
        moveTo(task, true);
    }

    /**
     * Moves {@code task} to the last position of the manual order (master list, filters ignored) and recomputes
     * the order with {@link #updateOrder} (DONE tasks stay at the end: a non-DONE task goes last before them).
     * No change if it is already last.
     *
     * @throws IllegalArgumentException if {@code task} is {@code null} or not in the task list
     */
    public void moveToBottom(Task task) {
        moveTo(task, false);
    }

    private void moveTo(Task task, boolean top) {
        // Identity lookup: Task is a Lombok @Data (value-based equals)
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) == task) {
                index = i;
                break;
            }
        }
        if (task == null || index < 0) {
            throw new IllegalArgumentException("Task not found in the task list: "
                    + (task == null ? "null" : "'" + task.getDescription() + "'"));
        }
        List<Task> newOrder = new java.util.ArrayList<>(tasks);
        newOrder.remove(index);
        newOrder.add(top ? 0 : newOrder.size(), task);
        updateOrder(newOrder);
    }

    /**
     * Recomputes the manual order from {@code newOrder}: DONE tasks are moved to the end (stable: the relative
     * order is kept inside both groups), then the {@code order} fields are renumbered 0..n-1 and the master list
     * is updated (single change event, only if the order changed).
     */
    public void updateOrder(List<Task> newOrder) {
        List<Task> ordered = new java.util.ArrayList<>(newOrder.size());
        newOrder.stream().filter(t -> t.getStatus() != TaskStatus.DONE).forEach(ordered::add);
        newOrder.stream().filter(t -> t.getStatus() == TaskStatus.DONE).forEach(ordered::add);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setOrder(i);
        }
        if (!sameInstances(ordered, tasks)) {
            tasks.setAll(ordered);
        }
    }

    private static boolean sameInstances(List<Task> a, List<Task> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }

    public void saveSafely() {
        try {
            // Cleanup history for all tasks
            tasks.forEach(Task::cleanupHistory);

            // Create a snapshot to save
            DataStore store = new DataStore(List.copyOf(tasks.stream().filter(t -> t.getStatus() != TaskStatus.TO_DELETE).toList()));
            storageService.save(store);
            javafx.application.Platform.runLater(() -> lastSaveTime.set(LocalDateTime.now()));
            log.info("Auto-saved at {}", LocalDateTime.now());
        } catch (IOException e) {
            log.error("Failed to auto-save tasks", e);
        }
    }

    public void shutdown() {
        autoSaveScheduler.shutdown();
        saveSafely(); // Force save on exit
    }
}