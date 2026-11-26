package com.chrono.task.service;

import com.chrono.task.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

public class TimerServiceTest {

    private TimerService timerService;

    @BeforeEach
    public void setup() {
        timerService = new TimerService();
    }

    @Test
    public void testPauseResume() {
        Task task = new Task();
        timerService.setActiveTask(task);
        assertFalse(timerService.isPaused(), "Should not be paused initially");

        timerService.pause();
        assertTrue(timerService.isPaused(), "Should be paused after pause()");

        timerService.resume();
        assertFalse(timerService.isPaused(), "Should not be paused after resume()");
    }

    @Test
    public void testTaskSwitchResetsPause() {
        Task task1 = new Task();
        timerService.setActiveTask(task1);
        timerService.pause();
        assertTrue(timerService.isPaused());

        Task task2 = new Task();
        timerService.setActiveTask(task2);
        assertFalse(timerService.isPaused(), "Switching task should reset pause state");
    }

    /**
     * Clock moved by hand, for a {@link TimerService} without background ticker.
     */
    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Instant T0 = Instant.parse("2026-11-26T09:00:00Z");

    @Test
    public void testSessionOfTwoMinutesRecordsItsStartAsActivation() {
        MutableClock clock = new MutableClock(T0);
        TimerService timer = new TimerService(clock, false);
        Task task = new Task();

        timer.setActiveTask(task);
        clock.advance(Duration.ofSeconds(119));
        timer.tick();
        assertNull(task.getLastActivatedAt(), "Not an activation before 2 minutes");

        clock.advance(Duration.ofSeconds(1));
        timer.tick();
        assertEquals(T0, task.getLastActivatedAt(), "Session start recorded once 2 minutes are reached");

        clock.advance(Duration.ofMinutes(10));
        timer.tick();
        assertEquals(T0, task.getLastActivatedAt(), "Still the session start, not updated by later ticks");
        assertEquals(Duration.ofMinutes(12), task.getTotalTime());
    }

    @Test
    public void testShortSessionIsNotAnActivation() {
        MutableClock clock = new MutableClock(T0);
        TimerService timer = new TimerService(clock, false);
        Task task = new Task();
        Task other = new Task();

        timer.setActiveTask(task);
        clock.advance(Duration.ofSeconds(90));
        timer.setActiveTask(other); // misclick: switched back quickly
        assertNull(task.getLastActivatedAt());

        // Time of two short sessions is not summed up into an activation
        clock.advance(Duration.ofSeconds(90));
        timer.setActiveTask(task);
        clock.advance(Duration.ofSeconds(90));
        timer.tick();
        assertNull(task.getLastActivatedAt());
        assertEquals(Duration.ofSeconds(180), task.getTotalTime());
    }

    @Test
    public void testSessionEndedBeforeATickStillCounts() {
        MutableClock clock = new MutableClock(T0);
        TimerService timer = new TimerService(clock, false);
        Task task = new Task();

        timer.setActiveTask(task);
        clock.advance(Duration.ofMinutes(3));
        timer.pause(); // final tick on stop
        assertEquals(T0, task.getLastActivatedAt());
    }

    @Test
    public void testResumeStartsANewSession() {
        MutableClock clock = new MutableClock(T0);
        TimerService timer = new TimerService(clock, false);
        Task task = new Task();

        timer.setActiveTask(task);
        clock.advance(Duration.ofMinutes(5));
        timer.tick();
        assertEquals(T0, task.getLastActivatedAt());

        timer.pause();
        clock.advance(Duration.ofHours(1));
        Instant resumedAt = clock.instant();
        timer.resume();
        clock.advance(Duration.ofMinutes(1));
        timer.tick();
        assertEquals(T0, task.getLastActivatedAt(), "1 min after resume: not yet an activation");

        clock.advance(Duration.ofMinutes(1));
        timer.tick();
        assertEquals(resumedAt, task.getLastActivatedAt());
    }

    @Test
    public void testSwitchingTaskStartsANewSession() {
        MutableClock clock = new MutableClock(T0);
        TimerService timer = new TimerService(clock, false);
        Task first = new Task();
        Task second = new Task();

        timer.setActiveTask(first);
        clock.advance(Duration.ofMinutes(2));
        Instant switchedAt = clock.instant();
        timer.setActiveTask(second);
        clock.advance(Duration.ofMinutes(2));
        timer.tick();

        assertEquals(T0, first.getLastActivatedAt());
        assertEquals(switchedAt, second.getLastActivatedAt());
    }
}
