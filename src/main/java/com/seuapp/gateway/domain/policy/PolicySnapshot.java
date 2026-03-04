package com.seuapp.gateway.domain.policy;

import com.seuapp.gateway.config.PolicyProperties;
import java.util.Comparator;
import java.util.List;

public final class PolicySnapshot {
    private final long[] timeoutMin, timeoutMax, timeoutMs;
    private final long[] fallbackMin, fallbackMax;
    private final String[] fallbackValue;

    private PolicySnapshot(long[] tMin,long[] tMax,long[] tMs,long[] fMin,long[] fMax,String[] fVal){
        this.timeoutMin=tMin; this.timeoutMax=tMax; this.timeoutMs=tMs;
        this.fallbackMin=fMin; this.fallbackMax=fMax; this.fallbackValue=fVal;
    }

    public long resolveTimeoutMs(long value){
        int idx=find(timeoutMin, timeoutMax, value);
        if(idx<0) throw new IllegalStateException("No timeout range for value="+value);
        return timeoutMs[idx];
    }

    public String resolveFallback(long value){
        int idx=find(fallbackMin, fallbackMax, value);
        if(idx<0) throw new IllegalStateException("No fallback range for value="+value);
        return fallbackValue[idx];
    }

    private int find(long[] mins,long[] maxs,long v){
        for(int i=0;i<mins.length;i++){
            if(v>=mins[i] && v<=maxs[i]) return i;
        }
        return -1;
    }

    public static PolicySnapshot compile(List<PolicyProperties.TimeoutRange> tRanges,
                                         List<PolicyProperties.FallbackRange> fRanges){
        var t=tRanges.stream().sorted(Comparator.comparingLong(PolicyProperties.TimeoutRange::min)).toList();
        var f=fRanges.stream().sorted(Comparator.comparingLong(PolicyProperties.FallbackRange::min)).toList();

        long[] tMin=new long[t.size()], tMax=new long[t.size()], tMs=new long[t.size()];
        for(int i=0;i<t.size();i++){ var r=t.get(i); tMin[i]=r.min(); tMax[i]=r.max(); tMs[i]=r.timeoutMs(); }

        long[] fMin=new long[f.size()], fMax=new long[f.size()]; String[] fVal=new String[f.size()];
        for(int i=0;i<f.size();i++){ var r=f.get(i); fMin[i]=r.min(); fMax[i]=r.max(); fVal[i]=r.defaultResult(); }

        return new PolicySnapshot(tMin,tMax,tMs,fMin,fMax,fVal);
    }
}
