package com.seuapp.domain.policy;

import com.seuapp.config.PolicyProperties;
import java.util.Comparator;
import java.util.List;

public final class PolicySnapshot {

    private final long[] timeoutMin;
    private final long[] timeoutMax;
    private final long[] timeoutMs;

    private final long[] fallbackMin;
    private final long[] fallbackMax;
    private final String[] fallbackValue;

    private PolicySnapshot(
            long[] timeoutMin, long[] timeoutMax, long[] timeoutMs,
            long[] fallbackMin, long[] fallbackMax, String[] fallbackValue
    ) {
        this.timeoutMin = timeoutMin;
        this.timeoutMax = timeoutMax;
        this.timeoutMs = timeoutMs;
        this.fallbackMin = fallbackMin;
        this.fallbackMax = fallbackMax;
        this.fallbackValue = fallbackValue;
    }

    public long resolveTimeoutMs(long value) {
        int idx = findRange(timeoutMin, timeoutMax, value);
        if (idx < 0) throw new IllegalStateException("No timeout range for value=" + value);
        return timeoutMs[idx];
    }

    public String resolveFallback(long value) {
        int idx = findRange(fallbackMin, fallbackMax, value);
        if (idx < 0) throw new IllegalStateException("No fallback range for value=" + value);
        return fallbackValue[idx];
    }

    private int findRange(long[] mins, long[] maxs, long v) {
        for (int i = 0; i < mins.length; i++) {
            if (v >= mins[i] && v <= maxs[i]) return i;
        }
        return -1;
    }

    public static PolicySnapshot compile(
            List<PolicyProperties.TimeoutRange> tRanges,
            List<PolicyProperties.FallbackRange> fRanges
    ) {
        var t = tRanges.stream().sorted(Comparator.comparingLong(PolicyProperties.TimeoutRange::min)).toList();
        var f = fRanges.stream().sorted(Comparator.comparingLong(PolicyProperties.FallbackRange::min)).toList();

        long[] tMin = new long[t.size()];
        long[] tMax = new long[t.size()];
        long[] tMs  = new long[t.size()];

        for (int i = 0; i < t.size(); i++) {
            var r = t.get(i);
            tMin[i] = r.min();
            tMax[i] = r.max();
            tMs[i]  = r.timeoutMs();
        }

        long[] fMin = new long[f.size()];
        long[] fMax = new long[f.size()];
        String[] fVal = new String[f.size()];

        for (int i = 0; i < f.size(); i++) {
            var r = f.get(i);
            fMin[i] = r.min();
            fMax[i] = r.max();
            fVal[i] = r.defaultResult();
        }

        return new PolicySnapshot(tMin, tMax, tMs, fMin, fMax, fVal);
    }
}
