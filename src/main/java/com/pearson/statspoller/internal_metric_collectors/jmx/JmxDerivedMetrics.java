package com.pearson.statspoller.internal_metric_collectors.jmx;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.pearson.statspoller.utilities.MathUtilities;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class JmxDerivedMetrics {

    private static final Logger logger = LoggerFactory.getLogger(JmxDerivedMetrics.class.getName());
        
    private static final int SCALE = 3;
    private static final int PRECISION = 31;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MATH_CONTEXT = new MathContext(PRECISION, ROUNDING_MODE);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    
    private final List<String> numericDerivedMetricRegexs_;
    private final List<String> stringDerivedMetricRegexs_;

    private Long previousProcessCpuTimeTimestampInMs_ = null;
    private BigDecimal previousProcessCpuTimeInNs_ = null;
    
    private HashMap<String,BigDecimal> previousCollectionTimeCountersForGcTypes_ = new HashMap<>();
    private HashMap<String,BigDecimal> previousCollectionTimestampsForGcTypes_ = new HashMap<>();
    
    public JmxDerivedMetrics() {
        this.numericDerivedMetricRegexs_ = createNumericDerivedMetricRegexs();
        this.stringDerivedMetricRegexs_  = createStringDerivedMetricRegexs();
    }
    
    public void reset() {
        previousProcessCpuTimeTimestampInMs_ = null;
        previousProcessCpuTimeInNs_ = null;
        
        previousCollectionTimeCountersForGcTypes_.clear();
        previousCollectionTimeCountersForGcTypes_ = new HashMap<>();
        previousCollectionTimestampsForGcTypes_.clear();
        previousCollectionTimestampsForGcTypes_ = new HashMap<>();
    }
            
    private List<String> createNumericDerivedMetricRegexs() {
        List<String> derivedMetricRegexs = new ArrayList<>();
        
        derivedMetricRegexs.add("java-lang\\.Memory\\.HeapMemoryUsage.*");
        derivedMetricRegexs.add("java-lang\\.Memory\\.NonHeapMemoryUsage.*");
        
        derivedMetricRegexs.add("java-lang\\.MemoryPool\\..+Usage.*");

        derivedMetricRegexs.add("java-lang\\.GarbageCollector\\..+CollectionTime");
        
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.AvailableProcessors");
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.ProcessCpuTime"); 
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.FreePhysicalMemorySize");
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.FreeSwapSpaceSize");
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.ProcessCpuLoad");
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.SystemCpuLoad");
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.TotalPhysicalMemorySize");
        derivedMetricRegexs.add("java-lang\\.OperatingSystem\\.TotalSwapSpaceSize");

        return derivedMetricRegexs;
    }
    
    private List<String> createStringDerivedMetricRegexs() {
        List<String> derivedMetricRegexs = new ArrayList<>();
        
        derivedMetricRegexs.add("java-lang\\.MemoryPool\\..+Type");
        
        return derivedMetricRegexs;
    }
    
    public List<JmxMetricRaw> createDerivedMetrics(List<JmxMetricRaw> jmxMetricsRaw) {
      
        if ((jmxMetricsRaw == null) || jmxMetricsRaw.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<JmxMetricRaw> derivedJmxMetrics = new ArrayList<>();
        
        Map<String,JmxMetricRaw> jmxMetricsRawByUnformattedGraphiteMetricPath = new HashMap<>();
        
        for (JmxMetricRaw jmxMetricRaw : jmxMetricsRaw) {
            String unformattedGraphiteMetricPath = jmxMetricRaw.createAndGetUnformattedGraphiteMetricPath();
            jmxMetricsRawByUnformattedGraphiteMetricPath.put(unformattedGraphiteMetricPath, jmxMetricRaw);
        }
        
        BigDecimal numberOfAvailableProcessors = getNumberAvailableProcessors(jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.AvailableProcessors"));
        
        Set<String> heapTypes = getHeapTypesFromMetricPath(jmxMetricsRawByUnformattedGraphiteMetricPath.keySet());
        List<JmxMetricRaw> heapPercentsByType = createHeapPercentsForEachHeapType(jmxMetricsRawByUnformattedGraphiteMetricPath, heapTypes);
        derivedJmxMetrics.addAll(heapPercentsByType);
        
        Set<String> nonHeapTypes = getNonHeapTypesFromMetricPath(jmxMetricsRawByUnformattedGraphiteMetricPath.keySet());
        List<JmxMetricRaw> nonHeapPercentsByType = createNonHeapPercentsForEachNonHeapType(jmxMetricsRawByUnformattedGraphiteMetricPath, nonHeapTypes);
        derivedJmxMetrics.addAll(nonHeapPercentsByType);
        
        JmxMetricRaw heapUsagePercent = createHeapUsagePercent(
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=Memory.HeapMemoryUsage.used"), 
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=Memory.HeapMemoryUsage.max"));
        if (heapUsagePercent != null) derivedJmxMetrics.add(heapUsagePercent);
        
        JmxMetricRaw nonHeapUsagePercent = createNonHeapUsagePercent(
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=Memory.NonHeapMemoryUsage.used"), 
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=Memory.NonHeapMemoryUsage.max"));
        if (nonHeapUsagePercent != null) derivedJmxMetrics.add(nonHeapUsagePercent);
        
        Set<String> gcTypes = getGcTypesFromMetricPath(jmxMetricsRawByUnformattedGraphiteMetricPath.keySet());
        List<JmxMetricRaw> gcActivityPercentsForEachGcType = createGcActivityPercentsForEachGcType(jmxMetricsRawByUnformattedGraphiteMetricPath, 
                gcTypes, numberOfAvailableProcessors, jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.ProcessCpuTime"));
        derivedJmxMetrics.addAll(gcActivityPercentsForEachGcType);
        
        JmxMetricRaw gcActivityPercentOverall = createGcActivityPercentOverall(gcActivityPercentsForEachGcType);
        if (gcActivityPercentOverall != null) derivedJmxMetrics.add(gcActivityPercentOverall);        
                
        JmxMetricRaw jvmCpuUsagePercent = createJvmCpuUsagePercent(jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.ProcessCpuTime"), numberOfAvailableProcessors);
        if (jvmCpuUsagePercent != null) derivedJmxMetrics.add(jvmCpuUsagePercent);
        
        JmxMetricRaw jvmRecentCpuUsagePercent = createJvmRecentCpuUsagePercent(jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.ProcessCpuLoad"));
        if (jvmRecentCpuUsagePercent != null) derivedJmxMetrics.add(jvmRecentCpuUsagePercent);
                
        JmxMetricRaw systemRecentCpuUsagePercent = createSystemRecentCpuUsagePercent(jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.SystemCpuLoad"));
        if (systemRecentCpuUsagePercent != null) derivedJmxMetrics.add(systemRecentCpuUsagePercent);
        
        JmxMetricRaw physicalMemoryUsagePercent = createPhysicalMemoryUsagePercent(
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.FreePhysicalMemorySize"),
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.TotalPhysicalMemorySize"));
        if (physicalMemoryUsagePercent != null) derivedJmxMetrics.add(physicalMemoryUsagePercent);
        
        JmxMetricRaw swapSpaceUsagePercent = createSwapSpaceUsagePercent(
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.FreeSwapSpaceSize"),
                jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.TotalSwapSpaceSize"));
        if (swapSpaceUsagePercent != null) derivedJmxMetrics.add(swapSpaceUsagePercent);
        
        if (jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.ProcessCpuTime") != null) {
            previousProcessCpuTimeTimestampInMs_ = jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.ProcessCpuTime").getMetricRetrievalTimestampInMs();
            previousProcessCpuTimeInNs_ = jmxMetricsRawByUnformattedGraphiteMetricPath.get("java.lang:type=OperatingSystem.ProcessCpuTime").getMetricValue();
        }
        
        // uncomment for debugging
//        for (JmxMetricRaw jmxMetricRaw : derivedJmxMetrics) {
//            System.out.println(jmxMetricRaw.createAndGetUnformattedGraphiteMetricPath() + " : " + 
//                    jmxMetricRaw.getMetricValue().toString() + " : " + jmxMetricRaw.getMetricRetrievalTimestampInMs());
//        }

        return derivedJmxMetrics;
    }
    
    private BigDecimal getNumberAvailableProcessors(JmxMetricRaw availableProcessors) {
        
        if ((availableProcessors == null) || (availableProcessors.getMetricValue() == null) || 
                availableProcessors.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        return availableProcessors.getMetricValue();
    }
    
    /* Gets the set of heap memory pool names that the JVM is using. 
     * Ex- 'Par Survivor Space'
     */
    private Set<String> getHeapTypesFromMetricPath(Set<String> metricPaths) {
        
        if ((metricPaths == null) || metricPaths.isEmpty()) {
            return new HashSet<>();
        }
        
        Set<String> heapTypes = new HashSet<>();
        
        for (String metricPath : metricPaths) {
            if (metricPath.contains("java.lang:type=MemoryPool,name=") && metricPath.contains(".Type-HEAP")) {
                String heapType = StringUtils.remove(metricPath, "java.lang:type=MemoryPool,name=");
                heapType = StringUtils.remove(heapType, ".Type-HEAP");
                heapTypes.add(heapType);
            }
        }
        
        return heapTypes;
    }
    
    /* Gets the set of non-heap memory pool names that the JVM is using. 
     * Ex- 'CMS Perm Gen'
     */
    private Set<String> getNonHeapTypesFromMetricPath(Set<String> metricPaths) {
        
        if ((metricPaths == null) || metricPaths.isEmpty()) {
            return new HashSet<>();
        }
        
        Set<String> nonHeapTypes = new HashSet<>();
        
        for (String metricPath : metricPaths) {
            if (metricPath.contains("java.lang:type=MemoryPool,name=")  && metricPath.contains(".Type-NON_HEAP")) {
                String nonHeapType = StringUtils.remove(metricPath, "java.lang:type=MemoryPool,name=");
                nonHeapType = StringUtils.remove(nonHeapType, ".Type-NON_HEAP");
                nonHeapTypes.add(nonHeapType);
            }
        }
        
        return nonHeapTypes;
    }
    
    private List<JmxMetricRaw> createHeapPercentsForEachHeapType(Map<String,JmxMetricRaw> jmxMetricsRawByUnformattedGraphiteMetricPath, Set<String> heapTypes) {
        
        if ((jmxMetricsRawByUnformattedGraphiteMetricPath == null) || jmxMetricsRawByUnformattedGraphiteMetricPath.isEmpty() || 
                (heapTypes == null) || heapTypes.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<JmxMetricRaw> heapPercentsByType = new ArrayList<>();
                
        for (String heapType : heapTypes) {
            String heapUsedMetricPath = "java.lang:type=MemoryPool,name=" + heapType + ".Usage.used";
            String heapMaxMetricPath = "java.lang:type=MemoryPool,name=" + heapType + ".Usage.max";
            
            if (jmxMetricsRawByUnformattedGraphiteMetricPath.containsKey(heapUsedMetricPath) && 
                    jmxMetricsRawByUnformattedGraphiteMetricPath.containsKey(heapMaxMetricPath)) {
                
                JmxMetricRaw heapUsedTypeJmxMetric = jmxMetricsRawByUnformattedGraphiteMetricPath.get(heapUsedMetricPath);
                JmxMetricRaw heapMaxTypeJmxMetric = jmxMetricsRawByUnformattedGraphiteMetricPath.get(heapMaxMetricPath);
                
                if ((heapUsedTypeJmxMetric == null) || (heapUsedTypeJmxMetric.getMetricValue() == null) || 
                        (heapMaxTypeJmxMetric == null) || (heapMaxTypeJmxMetric.getMetricValue() == null) ||
                        (heapUsedTypeJmxMetric.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) ||
                        (heapMaxTypeJmxMetric.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
                    logger.debug("Invalid heap usage values for HeapType=" + heapType);
                }
                else {
                    BigDecimal percentUsedHeapType = MathUtilities.smartBigDecimalScaleChange(
                            heapUsedTypeJmxMetric.getMetricValue()
                            .divide(heapMaxTypeJmxMetric.getMetricValue(), MATH_CONTEXT)
                            .multiply(ONE_HUNDRED, MATH_CONTEXT),
                            SCALE, ROUNDING_MODE);
                    
                    percentUsedHeapType = MathUtilities.correctOutOfRangePercentage(percentUsedHeapType);
                    
                    long averageMetricRetrievalTimestampInMs = (heapUsedTypeJmxMetric.getMetricRetrievalTimestampInMs() + heapMaxTypeJmxMetric.getMetricRetrievalTimestampInMs()) / 2;
                    JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "Memory.Heap." + heapType + "-UsedPct", percentUsedHeapType, averageMetricRetrievalTimestampInMs);
                    heapPercentsByType.add(jmxMetricRaw);
                } 
            }  
        }
        
        return heapPercentsByType;
    }
    
    private List<JmxMetricRaw> createNonHeapPercentsForEachNonHeapType(Map<String,JmxMetricRaw> jmxMetricsRawByUnformattedGraphiteMetricPath, Set<String> nonHeapTypes) {

        if ((jmxMetricsRawByUnformattedGraphiteMetricPath == null) || jmxMetricsRawByUnformattedGraphiteMetricPath.isEmpty() || 
                (nonHeapTypes == null) || nonHeapTypes.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<JmxMetricRaw> nonHeapPercentsByType = new ArrayList<>();
                
        for (String nonHeapType : nonHeapTypes) {
            String nonHeapUsedMetricPath = "java.lang:type=MemoryPool,name=" + nonHeapType + ".Usage.used";
            String nonHeapMaxMetricPath = "java.lang:type=MemoryPool,name=" + nonHeapType + ".Usage.max";
            
            if (jmxMetricsRawByUnformattedGraphiteMetricPath.containsKey(nonHeapUsedMetricPath) && 
                    jmxMetricsRawByUnformattedGraphiteMetricPath.containsKey(nonHeapMaxMetricPath)) {
                
                JmxMetricRaw nonHeapUsedTypeJmxMetric = jmxMetricsRawByUnformattedGraphiteMetricPath.get(nonHeapUsedMetricPath);
                JmxMetricRaw nonHeapMaxTypeJmxMetric = jmxMetricsRawByUnformattedGraphiteMetricPath.get(nonHeapMaxMetricPath);
                
                if ((nonHeapUsedTypeJmxMetric == null) || (nonHeapUsedTypeJmxMetric.getMetricValue() == null) || 
                        (nonHeapMaxTypeJmxMetric == null) || (nonHeapMaxTypeJmxMetric.getMetricValue() == null) ||
                        (nonHeapUsedTypeJmxMetric.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) ||
                        (nonHeapMaxTypeJmxMetric.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
                    logger.debug("Invalid non-heap usage values for NonHeapType=" + nonHeapType);
                }
                else {
                    BigDecimal percentUsedNonHeapType = MathUtilities.smartBigDecimalScaleChange(
                            nonHeapUsedTypeJmxMetric.getMetricValue()
                            .divide(nonHeapMaxTypeJmxMetric.getMetricValue(), MATH_CONTEXT)
                            .multiply(ONE_HUNDRED, MATH_CONTEXT),
                            SCALE, ROUNDING_MODE);
                    
                    percentUsedNonHeapType = MathUtilities.correctOutOfRangePercentage(percentUsedNonHeapType);
                    
                    long averageMetricRetrievalTimestampInMs = (nonHeapUsedTypeJmxMetric.getMetricRetrievalTimestampInMs() + nonHeapMaxTypeJmxMetric.getMetricRetrievalTimestampInMs()) / 2;
                    JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "Memory.NonHeap." + nonHeapType + "-UsedPct", percentUsedNonHeapType, averageMetricRetrievalTimestampInMs);
                    nonHeapPercentsByType.add(jmxMetricRaw);
                } 
            }  
        }
        
        return nonHeapPercentsByType;
    }
    
    private JmxMetricRaw createHeapUsagePercent(JmxMetricRaw heapUsed, JmxMetricRaw heapMax) {
        
        if ((heapUsed == null) || (heapUsed.getMetricValue() == null) || 
                (heapMax == null) || (heapMax.getMetricValue() == null) || 
                (heapUsed.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) || 
                (heapMax.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        BigDecimal percentUsedHeap = MathUtilities.smartBigDecimalScaleChange(
                heapUsed.getMetricValue()
                .divide(heapMax.getMetricValue(), MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT),
                SCALE, ROUNDING_MODE);
            
        percentUsedHeap = MathUtilities.correctOutOfRangePercentage(percentUsedHeap);
        
        long averageMetricRetrievalTimestampInMs = (heapUsed.getMetricRetrievalTimestampInMs() + heapMax.getMetricRetrievalTimestampInMs()) / 2;
        JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "Memory.Heap.Overall-UsedPct", percentUsedHeap, averageMetricRetrievalTimestampInMs);
        
        return jmxMetricRaw;
    }
    
    
    private JmxMetricRaw createNonHeapUsagePercent(JmxMetricRaw nonHeapUsed, JmxMetricRaw nonHeapMax) {
        
        if ((nonHeapUsed == null) || (nonHeapUsed.getMetricValue() == null) || 
                (nonHeapMax == null) || (nonHeapMax.getMetricValue() == null) || 
                (nonHeapUsed.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) || 
                (nonHeapMax.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        BigDecimal percentUsedNonHeap = MathUtilities.smartBigDecimalScaleChange(
                nonHeapUsed.getMetricValue()
                .divide(nonHeapMax.getMetricValue(), MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT),
                SCALE, ROUNDING_MODE);
            
        percentUsedNonHeap = MathUtilities.correctOutOfRangePercentage(percentUsedNonHeap);
        
        long averageMetricRetrievalTimestampInMs = (nonHeapUsed.getMetricRetrievalTimestampInMs() + nonHeapMax.getMetricRetrievalTimestampInMs()) / 2;
        JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "Memory.NonHeap.Overall-UsedPct", percentUsedNonHeap, averageMetricRetrievalTimestampInMs);
        
        return jmxMetricRaw;
    }
    
    /* Gets the set of garbage collector names that the JVM is using. 
     * Ex- 'ParNew', 'ConcurrentMarkAndSweep' 
     */
    private Set<String> getGcTypesFromMetricPath(Set<String> metricPaths) {
        
        if ((metricPaths == null) || metricPaths.isEmpty()) {
            return new HashSet<>();
        }
        
        Set<String> gcTypes = new HashSet<>();
        
        for (String metricPath : metricPaths) {
            if (metricPath.contains("java.lang:type=GarbageCollector,name=") && metricPath.contains(".CollectionTime")) {
                String gcType = StringUtils.remove(metricPath, "java.lang:type=GarbageCollector,name=");
                gcType = StringUtils.remove(gcType, ".CollectionTime");
                gcTypes.add(gcType);
            }
        }
        
        return gcTypes;
    }

    /*
     * The average garbage collection activity percent over an interval of time for each garbage collector. 
     * 'Activity' is defined as GcTime-For-The-Collector / numProcessors.
     * 'Interval of time' the Jmx collection interval of this program.
     */
    private List<JmxMetricRaw> createGcActivityPercentsForEachGcType(Map<String,JmxMetricRaw> jmxMetricsRawByUnformattedGraphiteMetricPath, 
            Set<String> gcTypes, BigDecimal numberOfAvailableProcessors, JmxMetricRaw processCpuTime) {
        
        if ((jmxMetricsRawByUnformattedGraphiteMetricPath == null) || jmxMetricsRawByUnformattedGraphiteMetricPath.isEmpty() || (gcTypes == null) || 
                gcTypes.isEmpty() || (numberOfAvailableProcessors == null) || (numberOfAvailableProcessors.compareTo(BigDecimal.ZERO) <= 0) ||
                (processCpuTime == null) || (processCpuTime.getMetricValue() == null) || 
                (processCpuTime.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return new ArrayList<>();
        }
                
        List<JmxMetricRaw> gcActivityPercents = new ArrayList<>();
                
        if ((previousProcessCpuTimeTimestampInMs_ == null) || (previousProcessCpuTimeInNs_ == null)) {  
            return gcActivityPercents;
        }
        
        BigDecimal processCpuTimeSinceLastIterationInNs = processCpuTime.getMetricValue().subtract(previousProcessCpuTimeInNs_);
        BigDecimal divisor_NsToMs = new BigDecimal(1000000);
        BigDecimal processCpuTimeSinceLastIterationInMs = MathUtilities.smartBigDecimalScaleChange(
                processCpuTimeSinceLastIterationInNs.divide(divisor_NsToMs, MATH_CONTEXT), 
                SCALE, ROUNDING_MODE);
        
        for (String gcType : gcTypes) {
            String collectionTimeMetricPathForGcType = "java.lang:type=GarbageCollector,name=" + gcType + ".CollectionTime";
            
            if (jmxMetricsRawByUnformattedGraphiteMetricPath.containsKey(collectionTimeMetricPathForGcType)) {
                JmxMetricRaw collectionTimeCounter = jmxMetricsRawByUnformattedGraphiteMetricPath.get(collectionTimeMetricPathForGcType);
                
                if ((collectionTimeCounter == null) || (collectionTimeCounter.getMetricValue() == null) || 
                        (collectionTimeCounter.getMetricValue().compareTo(BigDecimal.ZERO) < 0)) {
                    logger.debug("Invalid GC time for GcType=" + gcType);
                }
                else {
                    BigDecimal collectionTimestamp = new BigDecimal(Long.toString(collectionTimeCounter.getMetricRetrievalTimestampInMs()));
                                    
                    if (previousCollectionTimeCountersForGcTypes_.containsKey(gcType) && previousCollectionTimestampsForGcTypes_.containsKey(gcType)) {
                        BigDecimal previousCollectionTimeCounter = previousCollectionTimeCountersForGcTypes_.get(gcType);
                        BigDecimal previousCollectionTimestamp = previousCollectionTimestampsForGcTypes_.get(gcType);
                        BigDecimal timeSpentOnGcActivitySinceLastIterationInMs = collectionTimeCounter.getMetricValue().subtract(previousCollectionTimeCounter);
                        BigDecimal timeElaspsedSinceLastIterationInMs = collectionTimestamp.subtract(previousCollectionTimestamp);

                        BigDecimal percentGcActivityForGcType;
                        
                        if ((timeElaspsedSinceLastIterationInMs.compareTo(BigDecimal.ZERO) <= 0) || (processCpuTimeSinceLastIterationInMs.compareTo(BigDecimal.ZERO) <= 0)) {
                            percentGcActivityForGcType = BigDecimal.ZERO;
                        }
                        else {
                            percentGcActivityForGcType = MathUtilities.smartBigDecimalScaleChange(
                                    timeSpentOnGcActivitySinceLastIterationInMs
                                    .divide(processCpuTimeSinceLastIterationInMs, MATH_CONTEXT)
                                    .multiply(ONE_HUNDRED, MATH_CONTEXT),
                                    SCALE, ROUNDING_MODE);
                        }

                        percentGcActivityForGcType = MathUtilities.correctOutOfRangePercentage(percentGcActivityForGcType);
                        
                        JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "GC." + gcType + "-Pct",  
                                percentGcActivityForGcType, collectionTimeCounter.getMetricRetrievalTimestampInMs());
                        
                        gcActivityPercents.add(jmxMetricRaw);
                    }

                    previousCollectionTimeCountersForGcTypes_.put(gcType, collectionTimeCounter.getMetricValue());
                    previousCollectionTimestampsForGcTypes_.put(gcType, collectionTimestamp);
                }
            }
            
        }
        
        return gcActivityPercents;
    }
    
    /*
     * The overall/cumulative garbage collection activity percent over an interval of time. 
     * 'Activity' is defined as 'the cumulative amount % of the JVM's CPU activity that is attributable to GC' 
     * 'Interval of time' the Jmx collection interval of this program.
     */
    private JmxMetricRaw createGcActivityPercentOverall(List<JmxMetricRaw> derviedJmxMetrics_PercentGcActivityForGcType) {

        if ((derviedJmxMetrics_PercentGcActivityForGcType == null) || derviedJmxMetrics_PercentGcActivityForGcType.isEmpty()) {
            return null;
        }

        BigDecimal gcActivityPercentSum = BigDecimal.ZERO;
        int counter = 0;
        long timestampSum = 0;

        for (JmxMetricRaw gcTypePercentActivtyJmxMetric : derviedJmxMetrics_PercentGcActivityForGcType) {
            if ((gcTypePercentActivtyJmxMetric.getMetricValue() != null) && (gcTypePercentActivtyJmxMetric.getMetricValue().compareTo(BigDecimal.ZERO) >= 0)) {
                gcActivityPercentSum = gcActivityPercentSum.add(gcTypePercentActivtyJmxMetric.getMetricValue());
                timestampSum += gcTypePercentActivtyJmxMetric.getMetricRetrievalTimestampInMs();
                counter++;
            }
        }

        if (counter > 0) {
            long averagedMetricRetrievalTimestampInMs = timestampSum / counter;
            gcActivityPercentSum = MathUtilities.correctOutOfRangePercentage(gcActivityPercentSum);
            
            JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "GC.Overall-Pct", gcActivityPercentSum, averagedMetricRetrievalTimestampInMs);
            return jmxMetricRaw;
        }
        else {
            return null;
        }
    }
    
    /*
     * The average JVM CPU usage percent over an interval of time. 
     * 'CPU load' is defined as CpuTime / numProcessors.
     * 'Interval of time' the Jmx collection interval of this program.
     */
    private JmxMetricRaw createJvmCpuUsagePercent(JmxMetricRaw processCpuTime, BigDecimal numberOfAvailableProcessors) {
        
        if ((processCpuTime == null) || (processCpuTime.getMetricValue() == null) || (processCpuTime.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) ||
                (numberOfAvailableProcessors == null) || (numberOfAvailableProcessors.compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        JmxMetricRaw jvmCpuUsagePercent  = null;
        
        if ((previousProcessCpuTimeTimestampInMs_ != null) && (previousProcessCpuTimeInNs_ != null)) {   
            BigDecimal processCpuTimeSinceLastIterationInNs = processCpuTime.getMetricValue().subtract(previousProcessCpuTimeInNs_);
            long timeElaspedSinceLastIterationInMs = (long) (processCpuTime.getMetricRetrievalTimestampInMs() - previousProcessCpuTimeTimestampInMs_);
            long timeElaspedSinceLastIterationInNs = (long) (1000000 * timeElaspedSinceLastIterationInMs);
            BigDecimal timeElaspedSinceLastIterationInNs_BigDecimal = new BigDecimal(timeElaspedSinceLastIterationInNs);

            BigDecimal jvmCpuUsagePercentValue = MathUtilities.smartBigDecimalScaleChange(
                    processCpuTimeSinceLastIterationInNs
                    .divide(timeElaspedSinceLastIterationInNs_BigDecimal, MATH_CONTEXT)
                    .divide(numberOfAvailableProcessors, MATH_CONTEXT)
                    .multiply(ONE_HUNDRED, MATH_CONTEXT),
                    SCALE, ROUNDING_MODE);

            jvmCpuUsagePercentValue = MathUtilities.correctOutOfRangePercentage(jvmCpuUsagePercentValue);
            
            jvmCpuUsagePercent = new JmxMetricRaw("Derived", "CPU.JvmCpu-UsagePct", jvmCpuUsagePercentValue, processCpuTime.getMetricRetrievalTimestampInMs());
        }
        
        return jvmCpuUsagePercent;
    }
    
    /*
     * The average JVM CPU usage percent over a recent interval of time. 
     * Note - The 'processCpuLoad' metric has been observed to be buggy in Java 1.7.0.40 on Windows 2008 (inappropriately reporting 0 or -1).
     */
    private JmxMetricRaw createJvmRecentCpuUsagePercent(JmxMetricRaw processCpuLoad) {
        
        if ((processCpuLoad == null) || (processCpuLoad.getMetricValue() == null) || (processCpuLoad.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        BigDecimal jvmRecentCpuUsagePercentValue = MathUtilities.smartBigDecimalScaleChange(
                processCpuLoad.getMetricValue().multiply(ONE_HUNDRED, MATH_CONTEXT),
                SCALE, ROUNDING_MODE);
            
        jvmRecentCpuUsagePercentValue = MathUtilities.correctOutOfRangePercentage(jvmRecentCpuUsagePercentValue);
            
        JmxMetricRaw jvmRecentCpuUsagePercent = new JmxMetricRaw("Derived", "CPU.JvmRecentCpu-UsedPct", jvmRecentCpuUsagePercentValue, processCpuLoad.getMetricRetrievalTimestampInMs());
        
        return jvmRecentCpuUsagePercent;
    }
    
    /*
     * The average system (entire OS) CPU usage percent over a recent interval of time. 
     */
    private JmxMetricRaw createSystemRecentCpuUsagePercent(JmxMetricRaw systemCpuLoad) {
        
        if ((systemCpuLoad == null) || (systemCpuLoad.getMetricValue() == null) || (systemCpuLoad.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        BigDecimal systemRecentCpuUsagePercentValue = MathUtilities.smartBigDecimalScaleChange(
                systemCpuLoad.getMetricValue().multiply(ONE_HUNDRED, MATH_CONTEXT),
                SCALE, ROUNDING_MODE);
        
        systemRecentCpuUsagePercentValue = MathUtilities.correctOutOfRangePercentage(systemRecentCpuUsagePercentValue);
            
        JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "CPU.SystemRecentCpu-UsedPct", systemRecentCpuUsagePercentValue, systemCpuLoad.getMetricRetrievalTimestampInMs());
        
        return jmxMetricRaw;
    }
    
    private JmxMetricRaw createPhysicalMemoryUsagePercent(JmxMetricRaw freePhysicalMemorySize, JmxMetricRaw totalPhysicalMemorySize) {
        
        if ((freePhysicalMemorySize == null) || (freePhysicalMemorySize.getMetricValue() == null) || 
                (totalPhysicalMemorySize == null) || (totalPhysicalMemorySize.getMetricValue() == null) || 
                (freePhysicalMemorySize.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) || 
                (totalPhysicalMemorySize.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        BigDecimal percentUsedPhysicalMemory = MathUtilities.smartBigDecimalScaleChange(
                freePhysicalMemorySize.getMetricValue()
                .divide(totalPhysicalMemorySize.getMetricValue(), MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT),
                SCALE, ROUNDING_MODE);
        
        percentUsedPhysicalMemory = MathUtilities.correctOutOfRangePercentage(percentUsedPhysicalMemory);

        long averageMetricRetrievalTimestampInMs = (freePhysicalMemorySize.getMetricRetrievalTimestampInMs() + totalPhysicalMemorySize.getMetricRetrievalTimestampInMs()) / 2;
        JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "Memory.System.PhysicalMemory-UsedPct", percentUsedPhysicalMemory, averageMetricRetrievalTimestampInMs);
        
        return jmxMetricRaw;
    }

    private JmxMetricRaw createSwapSpaceUsagePercent(JmxMetricRaw freeSwapSpaceSize, JmxMetricRaw totalSwapSpaceSize) {
        
        if ((freeSwapSpaceSize == null) || (freeSwapSpaceSize.getMetricValue() == null) || 
                (totalSwapSpaceSize == null) || (totalSwapSpaceSize.getMetricValue() == null) || 
                (freeSwapSpaceSize.getMetricValue().compareTo(BigDecimal.ZERO) <= 0) || 
                (totalSwapSpaceSize.getMetricValue().compareTo(BigDecimal.ZERO) <= 0)) {
            return null;
        }
       
        BigDecimal percentUsedSwapSpace = MathUtilities.smartBigDecimalScaleChange(
                freeSwapSpaceSize.getMetricValue()
                .divide(totalSwapSpaceSize.getMetricValue(), MATH_CONTEXT)
                .multiply(ONE_HUNDRED, MATH_CONTEXT),
                SCALE, ROUNDING_MODE);
            
        percentUsedSwapSpace = MathUtilities.correctOutOfRangePercentage(percentUsedSwapSpace);
        
        long averageMetricRetrievalTimestampInMs = (freeSwapSpaceSize.getMetricRetrievalTimestampInMs() + totalSwapSpaceSize.getMetricRetrievalTimestampInMs()) / 2;
        JmxMetricRaw jmxMetricRaw = new JmxMetricRaw("Derived", "Memory.System.SystemSwapSize-UsedPct", percentUsedSwapSpace, averageMetricRetrievalTimestampInMs);
        
        return jmxMetricRaw;
    }
    
    private JmxMetricRaw getJmxMetricRawWithLargerMetricValue(JmxMetricRaw jmxMetricRaw1, JmxMetricRaw jmxMetricRaw2) {
        
        if ((jmxMetricRaw1 == null) && (jmxMetricRaw2 == null)) {
            return null;
        }
        
        if ((jmxMetricRaw1 == null) && (jmxMetricRaw2 != null)) {
            if (jmxMetricRaw2.getMetricValue() != null) {
                return jmxMetricRaw2;
            }
        }
        
        if ((jmxMetricRaw1 != null) && (jmxMetricRaw2 == null)) {
            if (jmxMetricRaw1.getMetricValue() != null) {
                return jmxMetricRaw1;
            }
        }
        
        if ((jmxMetricRaw1 == null) || (jmxMetricRaw2 == null) || (jmxMetricRaw1.getMetricValue() == null) || (jmxMetricRaw2.getMetricValue() == null)) {
            return null;
        }
        else {
            int compareResult = jmxMetricRaw1.getMetricValue().compareTo(jmxMetricRaw2.getMetricValue());
            
            if (compareResult >= 0) {
                return jmxMetricRaw1;
            }
            else {
                return jmxMetricRaw2;
            }
        }
        
    }
    
    public List<String> getNumericDerivedMetricRegexs() {
        return numericDerivedMetricRegexs_;
    }
    
    public List<String> getStringDerivedMetricRegexs() {
        return stringDerivedMetricRegexs_;
    }
    
}
