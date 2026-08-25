package io.github.dmitriyiliyov.idempify.core;

import java.time.*;

/**
 * A clock the test drives by hand: it stands still until {@link #advance(Duration)} is called, unless a step is
 * given, in which case every read moves it forward by that step.
 */
public final class TestClock extends Clock {

    public static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    private final ZoneId zone;
    private final Duration step;
    private Instant instant;

    private TestClock(Instant instant, Duration step, ZoneId zone) {
        this.instant = instant;
        this.step = step;
        this.zone = zone;
    }

    public static TestClock fixedAt(Instant instant) {
        return new TestClock(instant, Duration.ZERO, ZoneOffset.UTC);
    }

    public static TestClock standingStill() {
        return fixedAt(EPOCH);
    }

    public static TestClock steppingBy(Duration step) {
        return new TestClock(EPOCH, step, ZoneOffset.UTC);
    }

    public void advance(Duration amount) {
        instant = instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(instant, step, zone);
    }

    @Override
    public Instant instant() {
        Instant current = instant;
        instant = instant.plus(step);
        return current;
    }
}
