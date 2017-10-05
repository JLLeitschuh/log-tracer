/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.md
 */

package dk.dbc.kafka.logformat;

import org.slf4j.event.Level;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.function.Predicate;

public class LogEventFilter implements Predicate<LogEvent> {
    private OffsetDateTime from, until;
    private String appID, host, env;
    private Level loglevel;
    private int numberOfExitEvents;

    public LogEventFilter setFrom(Date from) {
        this.from = OffsetDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault());
        return this;
    }

    public OffsetDateTime getFrom() {
        return from;
    }

    public LogEventFilter setUntil(Date until) {
        this.until = OffsetDateTime.ofInstant(until.toInstant(), ZoneId.systemDefault());
        return this;
    }

    public LogEventFilter setAppID(String appID) {
        this.appID = appID;
        return this;
    }

    public LogEventFilter setHost(String host) {
        this.host = host;
        return this;
    }

    public LogEventFilter setEnv(String env) {
        this.env = env;
        return this;
    }

    public LogEventFilter setLoglevel(Level loglevel) {
        this.loglevel = loglevel;
        return this;
    }

    public int getNumberOfExitEvents() {
        return numberOfExitEvents;
    }

    @Override
    public boolean test(LogEvent logEvent) {
        boolean allowed = true;

        if (from != null && (logEvent.getTimestamp() == null || logEvent.getTimestamp().isBefore(from))) {
            allowed = false;
        }

        if (until != null && (logEvent.getTimestamp() == null || logEvent.getTimestamp().isAfter(until))) {
            allowed = false;
            numberOfExitEvents++;
        }

        if (appID != null && !appID.isEmpty() && !appID.equalsIgnoreCase(logEvent.getAppID())) {
            allowed = false;
        }

        if (env != null && !env.isEmpty() && !env.equalsIgnoreCase(logEvent.getEnv())) {
            allowed = false;
        }

        if (host != null && !host.isEmpty() && !host.equalsIgnoreCase(logEvent.getHost())) {
            allowed = false;
        }

        if (loglevel != null && (logEvent.getLevel() == null || logEvent.getLevel().toInt() < loglevel.toInt())) {
            allowed = false;
        }

        return allowed;
    }

    @Override
    public String toString() {
        return "LogEventFilter{" +
                "from=" + from +
                ", until=" + until +
                ", appID='" + appID + '\'' +
                ", host='" + host + '\'' +
                ", env='" + env + '\'' +
                ", loglevel=" + loglevel +
                '}';
    }
}
