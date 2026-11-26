package com.chrono.task.service;

import com.chrono.task.model.Task;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerService {

    private final ObjectProperty<Task> activeTask = new SimpleObjectProperty<>();
    private Instant startTime;
    /**
     * Start of the current timer session (set on start/resume, unlike {@link #startTime} which moves every tick).
     */
    private Instant sessionStart;
    /**
     * True once the current session has been recorded as the task's last activation.
     */
    private boolean sessionActivationRecorded;
    private final Clock clock;
    private final ScheduledExecutorService ticker;

    // Allows UI to observe "current session duration" to display ephemeral seconds
    // Or we simply update the task model periodically

    public TimerService() {
        this(Clock.systemDefaultZone(), true);
    }

    /**
     * @param autoTick false (tests) = no background ticker, {@link #tick()} is called by hand
     */
    TimerService(Clock clock, boolean autoTick) {
        this.clock = clock;
        if (!autoTick) {
            this.ticker = null;
            return;
        }
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Timer-Ticker");
            t.setDaemon(true);
            return t;
        });

        // Every second, update the active task's time history
        this.ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    public void setActiveTask(Task task) {
        if (activeTask.get() == task) {
            return; // Already active
        }

        // Stop previous
        if (activeTask.get() != null) {
            stopTimer();
        }

        // Start new
        activeTask.set(task);
        if (task != null) {
            paused.set(false);
            startTimer();
        }
    }

    private synchronized void startTimer() {
        startTime = clock.instant();
        sessionStart = startTime;
        sessionActivationRecorded = false;
    }

    private synchronized void stopTimer() {
        // Finalize time for the task being stopped
        tick();
        startTime = null;
        sessionStart = null;
    }

    synchronized void tick() {
        Task current = activeTask.get();
        if (current != null && startTime != null) {
            Instant now = clock.instant();
            Duration sessionDuration = Duration.between(startTime, now);

            // We add this small session chunk to the task and reset startTime to now
            // This prevents losing large chunks if crash, and allows real-time update
            current.addTime(LocalDate.now(clock), sessionDuration);
            startTime = now;

            // A session long enough counts as an activation ("Last used" sort); short ones (misclick) don't
            if (!sessionActivationRecorded && sessionStart != null
                    && Duration.between(sessionStart, now).compareTo(Task.ACTIVATION_MIN_DURATION) >= 0) {
                current.setLastActivatedAt(sessionStart);
                sessionActivationRecorded = true;
            }
        }
    }

    public ObjectProperty<Task> activeTaskProperty() {
        return activeTask;
    }

    private final javafx.beans.property.BooleanProperty paused = new javafx.beans.property.SimpleBooleanProperty(false);

    public javafx.beans.property.BooleanProperty pausedProperty() {
        return paused;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        if (activeTask.get() != null && !isPaused()) {
            stopTimer();
            paused.set(true);
        }
    }

    public void resume() {
        if (activeTask.get() != null && isPaused()) {
            startTimer();
            paused.set(false);
        }
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.shutdown();
        }
        stopTimer();
    }
}