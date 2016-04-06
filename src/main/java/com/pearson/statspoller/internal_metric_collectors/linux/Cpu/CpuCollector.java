package com.pearson.statspoller.internal_metric_collectors.linux.Cpu;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Collects CPU % metrics -- similar to the metrics collected by mpstat
 * Based on raw data from /proc/stat
 */
public class CpuCollector extends InternalCollectorFramework implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CpuCollector.class.getName());
    
    private static String previousRawProcStats_ = null;
    
    public CpuCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the cpu usage percentages in graphite format
            List<GraphiteMetric> graphiteMetrics = getCpuMetrics();

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-Cpu metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private List<GraphiteMetric> getCpuMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();

        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            String currentRawProcStats = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/stat");

            if ((currentRawProcStats == null) || currentRawProcStats.isEmpty()) {
                return allGraphiteMetrics;
            }
            else if (previousRawProcStats_ == null) {
                previousRawProcStats_ = currentRawProcStats;
                return allGraphiteMetrics;
            }
            
            String previousRawProcStats_Local = previousRawProcStats_;
            previousRawProcStats_ = currentRawProcStats;
            
            Map<String,String> rawCpuStats_ByCpu_Previous = getCpuMetrics_GetRawCpuStats_ByCpu(previousRawProcStats_Local);
            Map<String,String> rawCpuStats_ByCpu_Current = getCpuMetrics_GetRawCpuStats_ByCpu(currentRawProcStats);
            if (rawCpuStats_ByCpu_Previous.isEmpty() || rawCpuStats_ByCpu_Current.isEmpty()) return allGraphiteMetrics;

            Map<String,String> rawCpuStats_DeltaBetweenPreviousAndCurrent = getCpuMetrics_GetDeltaBetweenRawCpuStatsSets(rawCpuStats_ByCpu_Previous, rawCpuStats_ByCpu_Current);
            if (rawCpuStats_DeltaBetweenPreviousAndCurrent == null) return allGraphiteMetrics;
            
            for (String cpuName : rawCpuStats_DeltaBetweenPreviousAndCurrent.keySet()) {
                String rawCpuStatsDelta = rawCpuStats_DeltaBetweenPreviousAndCurrent.get(cpuName);
                allGraphiteMetrics.addAll(getCpuMetrics_GetGraphiteMetricsFromCpuStatsDelta(rawCpuStatsDelta, currentTimestampInSeconds));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private Map<String,String> getCpuMetrics_GetRawCpuStats_ByCpu(String rawProcStats) {
        
        if (rawProcStats == null) {
            return new HashMap<>();
        }
        
        Map<String,String> cpuStats_ByCpuName = new HashMap<>();
        
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new StringReader(rawProcStats));
            String currentLine = reader.readLine();
            
            while (currentLine != null) {
                String trimmedCurrentLine = currentLine.trim();
                
                if (trimmedCurrentLine.startsWith("cpu")) {
                    int indexOfFirstSpace = trimmedCurrentLine.indexOf(' ');

                    if (indexOfFirstSpace > 0) {
                        String cpuName = trimmedCurrentLine.substring(0, indexOfFirstSpace);
                        cpuStats_ByCpuName.put(cpuName, trimmedCurrentLine);
                    }
                }
                
                currentLine = reader.readLine();
            } 
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return cpuStats_ByCpuName;
    }
    
    /*
    Returns the delta for each cpu's stats (from two different samples of /proc/stat)
    In the event of an error and/or an abnormal data, a blank hashmap is returned
    */
    private Map<String,String> getCpuMetrics_GetDeltaBetweenRawCpuStatsSets(Map<String,String> rawCpuStats_Previous, Map<String,String> rawCpuStats_Current) {
        
        if ((rawCpuStats_Previous == null) || (rawCpuStats_Current == null)) {
            return new HashMap<>();
        }
        
        Map<String,String> rawCpuStatsDeltas_ByCpuName = new HashMap<>();
        
        for (String cpuName : rawCpuStats_Current.keySet()) {
            try {
                String cpuStats_Previous = rawCpuStats_Previous.get(cpuName);
                String cpuStats_Current = rawCpuStats_Current.get(cpuName);
                if ((cpuStats_Current == null) || cpuStats_Current.isEmpty() || (cpuStats_Previous == null) || cpuStats_Previous.isEmpty()) continue;

                String[] rawCpuStats_DelimtedBySpace_Previous = cpuStats_Previous.split("\\s+");
                String[] rawCpuStats_DelimtedBySpace_Current = cpuStats_Current.split("\\s+");

                if ((rawCpuStats_DelimtedBySpace_Previous == null) || (rawCpuStats_DelimtedBySpace_Current == null)) return rawCpuStatsDeltas_ByCpuName;
                if (rawCpuStats_DelimtedBySpace_Previous.length != rawCpuStats_DelimtedBySpace_Current.length) return rawCpuStatsDeltas_ByCpuName;
                if (rawCpuStats_DelimtedBySpace_Previous.length < 5) return rawCpuStatsDeltas_ByCpuName;

                StringBuilder rawCpuStats_Delta = new StringBuilder();
                rawCpuStats_Delta.append(cpuName).append(" ");

                for (int i = 1; i < rawCpuStats_DelimtedBySpace_Current.length; i++) {
                    try {
                        BigDecimal previousValue = new BigDecimal(rawCpuStats_DelimtedBySpace_Previous[i]);
                        BigDecimal currentValue = new BigDecimal(rawCpuStats_DelimtedBySpace_Current[i]);
                        BigDecimal deltaValue = currentValue.subtract(previousValue);
                        if (deltaValue.compareTo(BigDecimal.ZERO) == -1) return new HashMap<>();
                        rawCpuStats_Delta.append(deltaValue.stripTrailingZeros().toPlainString()).append(" ");
                    }
                    catch (Exception e) {
                        logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                    }
                }

                rawCpuStatsDeltas_ByCpuName.put(cpuName, rawCpuStats_Delta.toString().trim());
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return rawCpuStatsDeltas_ByCpuName;
    }
    
    private List<GraphiteMetric> getCpuMetrics_GetGraphiteMetricsFromCpuStatsDelta(String rawCpuStats_Delta, int currentTimestampInSeconds) {
        
        if (rawCpuStats_Delta == null) {
            return new ArrayList<>();
        }

        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();

        try {
            String[] statsDelimitedBySpace = rawCpuStats_Delta.split("\\s+");
            if ((statsDelimitedBySpace == null) || (statsDelimitedBySpace.length == 0)) return graphiteMetrics;
            
            boolean isCpuMetric = rawCpuStats_Delta.startsWith("cpu");
            if (!isCpuMetric) return graphiteMetrics;
                
            // handles extra fields that may have been added after this version of statspoller was compiled
            List<String> extraFields = new ArrayList<>();
            if (statsDelimitedBySpace.length > 11) {
                for (int i = 11; i < statsDelimitedBySpace.length; i++) {
                    extraFields.add(statsDelimitedBySpace[i]);
                }
            }
            
            // handles old versions of linux that didn't have 11 fields for cpu stats
            if (statsDelimitedBySpace.length < 11) {
                String[] statsDelimitedBySpace_OldKernel = new String[11];
                for (int i = 0; i < statsDelimitedBySpace_OldKernel.length; i++) statsDelimitedBySpace_OldKernel[i] = null;
                for (int i = 0; i < statsDelimitedBySpace.length; i++) statsDelimitedBySpace_OldKernel[i] = statsDelimitedBySpace[i];
                statsDelimitedBySpace = statsDelimitedBySpace_OldKernel;
            }
            
            CpuStat_Delta cpuStat = new CpuStat_Delta(statsDelimitedBySpace[0], statsDelimitedBySpace[1], statsDelimitedBySpace[2], statsDelimitedBySpace[3],
                    statsDelimitedBySpace[4], statsDelimitedBySpace[5], statsDelimitedBySpace[6], statsDelimitedBySpace[7], statsDelimitedBySpace[8],
                    statsDelimitedBySpace[9], statsDelimitedBySpace[10], extraFields.toArray(new String[extraFields.size()]));

            if (cpuStat.getFormattedCpuName() == null) return graphiteMetrics;
            
            String cpuName = cpuStat.getFormattedCpuName();
            if ((cpuStat.getUserPercent() != null) && (cpuStat.getUserPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".User-Pct", cpuStat.getUserPercent(), currentTimestampInSeconds));
            if ((cpuStat.getNicePercent() != null) && (cpuStat.getNicePercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Nice-Pct", cpuStat.getNicePercent(), currentTimestampInSeconds));
            if ((cpuStat.getSystemPercent() != null) && (cpuStat.getSystemPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".System-Pct", cpuStat.getSystemPercent(), currentTimestampInSeconds));
            if ((cpuStat.getIdlePercent() != null) && (cpuStat.getIdlePercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Idle-Pct", cpuStat.getIdlePercent(), currentTimestampInSeconds));
            if ((cpuStat.getIowaitPercent() != null) && (cpuStat.getIowaitPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Iowait-Pct", cpuStat.getIowaitPercent(), currentTimestampInSeconds));
            if ((cpuStat.getIrqPercent() != null) && (cpuStat.getIrqPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Irq-Pct", cpuStat.getIrqPercent(), currentTimestampInSeconds));
            if ((cpuStat.getSoftIrqPercent() != null) && (cpuStat.getSoftIrqPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".SoftIrq-Pct", cpuStat.getSoftIrqPercent(), currentTimestampInSeconds));
            if ((cpuStat.getStealPercent() != null) && (cpuStat.getStealPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Steal-Pct", cpuStat.getStealPercent(), currentTimestampInSeconds));
            if ((cpuStat.getGuestPercent() != null) && (cpuStat.getGuestPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Guest-Pct", cpuStat.getGuestPercent(), currentTimestampInSeconds));
            if ((cpuStat.getGuestNicePercent() != null) && (cpuStat.getGuestNicePercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".GuestNice-Pct", cpuStat.getGuestNicePercent(), currentTimestampInSeconds));
            if ((cpuStat.getExtraPercent() != null) && (cpuStat.getExtraPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Extra-Pct", cpuStat.getExtraPercent(), currentTimestampInSeconds));
            if ((cpuStat.getUsedPercent() != null) && (cpuStat.getUsedPercent().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(cpuName + ".Used-Pct", cpuStat.getUsedPercent(), currentTimestampInSeconds));
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
}
