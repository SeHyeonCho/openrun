package com.openrun.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class MutableClock extends Clock {

    private volatile Instant now;
    private final ThreadLocal<Instant> arrivedAt = new ThreadLocal<>();

    public MutableClock(Instant now) {
        this.now = now;
    }

    public void setInstant(Instant now) {
        this.now = now;
    }

    public void setArrivalForCurrentThread(Instant arrival) {
        arrivedAt.set(arrival);
    }

    public void clearArrivalForCurrentThread() {
        arrivedAt.remove();
    }

    @Override
    public Instant instant() {
        Instant arrival = arrivedAt.get();
        return arrival != null && arrival.isAfter(now) ? arrival : now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
