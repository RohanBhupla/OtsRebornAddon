package net.rebornaddon.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PerformanceMonitorTest {
    @Test
    public void parsesCompoundSamplingDurations() {
        assertEquals(1000L, PerformanceMonitor.parseDuration("1s"));
        assertEquals(150000L, PerformanceMonitor.parseDuration("2m30s"));
        assertEquals(3599000L, PerformanceMonitor.parseDuration("59m59s"));
    }

    @Test
    public void rejectsInvalidSamplingDurations() {
        assertEquals(PerformanceMonitor.DEFAULT_PROFILE_MILLIS, PerformanceMonitor.parseDuration(""));
        assertEquals(-1L, PerformanceMonitor.parseDuration("2minutes"));
        assertEquals(-1L, PerformanceMonitor.parseDuration("1m-2s"));
    }

    @Test
    public void formatsOnlyRequiredUnits() {
        assertEquals("5s", PerformanceMonitor.formatDuration(5000L));
        assertEquals("2m 5s", PerformanceMonitor.formatDuration(125000L));
        assertEquals("1h 2m 3s", PerformanceMonitor.formatDuration(3723000L));
    }
}
