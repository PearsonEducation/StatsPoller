package com.pearson.statspoller.internal_metric_collectors.linux.DiskIo;

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
 * Collects Disk IO metrics -- similar to the metrics collected by iostat
 * Based on raw data from /proc/diskstats
 */
public class DiskIoCollector extends InternalCollectorFramework implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(DiskIoCollector.class.getName());
    
    private static String previousRawProcDiskStats_ = null;
    private static long previousRawProcDiskStats_ReadTimestamp_ = -1;
     
    public DiskIoCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the io usage stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getDiskMetrics();

            // output graphite metrics
            super.outputMetrics(graphiteMetrics, true);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-DiskIO metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private List<GraphiteMetric> getDiskMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
        
        try {
            long currentTimestampInMilliseconds = System.currentTimeMillis();
            int currentTimestampInSeconds = (int) (currentTimestampInMilliseconds / 1000);
            String currentRawProcDiskStats = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/diskstats");

            if ((currentRawProcDiskStats == null) || currentRawProcDiskStats.isEmpty()) {
                return allGraphiteMetrics;
            }
            else if (previousRawProcDiskStats_ == null) {
                previousRawProcDiskStats_ReadTimestamp_ = currentTimestampInMilliseconds;
                previousRawProcDiskStats_ = currentRawProcDiskStats;
                return allGraphiteMetrics;
            }
            
            String previousRawProcDiskStats_Local = previousRawProcDiskStats_;
            long previousRawProcDiskStats_ReadTimestamp_Local = previousRawProcDiskStats_ReadTimestamp_;
            previousRawProcDiskStats_ = currentRawProcDiskStats;
            previousRawProcDiskStats_ReadTimestamp_ = currentTimestampInMilliseconds;

            if ((previousRawProcDiskStats_ReadTimestamp_ <= 0) || (currentTimestampInMilliseconds <= 0)) return allGraphiteMetrics;
            
            Map<String,String> rawDiskStats_Previous = getDiskMetrics_GetRawDiskStats_ByDeviceName(previousRawProcDiskStats_Local);
            Map<String,String> rawDiskStats_Current = getDiskMetrics_GetRawDiskStats_ByDeviceName(currentRawProcDiskStats);
            if (rawDiskStats_Previous.isEmpty() || rawDiskStats_Current.isEmpty()) return allGraphiteMetrics;
            
            Map<String,String> rawCpuStats_DeltaBetweenPreviousAndCurrent = getDiskMetrics_GetDeltaBetweenRawDiskStatsSets(rawDiskStats_Previous, rawDiskStats_Current);
            long millisecondsBetweenPreviousAndCurrentDiskstats = currentTimestampInMilliseconds - previousRawProcDiskStats_ReadTimestamp_Local;
            
            for (String deviceName : rawCpuStats_DeltaBetweenPreviousAndCurrent.keySet()) {
                String rawDiskStatsDelta = rawCpuStats_DeltaBetweenPreviousAndCurrent.get(deviceName);
                allGraphiteMetrics.addAll(getDiskMetrics_GetGraphiteMetricsFromDiskStatsDelta(rawDiskStatsDelta, millisecondsBetweenPreviousAndCurrentDiskstats, currentTimestampInSeconds));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private Map<String,String> getDiskMetrics_GetRawDiskStats_ByDeviceName(String rawProcStats) {
        
        if (rawProcStats == null) {
            return new HashMap<>();
        }
        
        Map<String,String> diskStats_ByDeviceName = new HashMap<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new StringReader(rawProcStats));
            String currentLine = reader.readLine();
            
            while (currentLine != null) {
                String trimmedCurrentLine = currentLine.trim();                
                String[] splitFields = trimmedCurrentLine.split("\\s+");
                
                if (splitFields.length < 14) {
                    logger.error("Unexpected number of fields in " + super.getLinuxProcFileSystemLocation() + "/diskstat. Expected 14 or more fields.");
                    break;
                }
                
                String deviceName = splitFields[2].trim();
                boolean isMainDevice = splitFields[1].equals("0");

                int countOfZeros = 0;
                for (int i = 3; i < splitFields.length; i++) if (splitFields[i].equals("0")) countOfZeros++;
                boolean isAllZeros = (countOfZeros == (splitFields.length - 3));
                
                if (isMainDevice && !isAllZeros && !deviceName.isEmpty()) diskStats_ByDeviceName.put(deviceName, trimmedCurrentLine);
                
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
        
        return diskStats_ByDeviceName;
    }
    
    private Map<String,String> getDiskMetrics_GetDeltaBetweenRawDiskStatsSets(Map<String,String> rawDiskStats_Previous, Map<String,String> rawDiskStats_Current) {
        
        if ((rawDiskStats_Previous == null) || (rawDiskStats_Current == null)) {
            return new HashMap<>();
        }
        
        Map<String,String> rawDiskStatsDeltas_ByDiskName = new HashMap<>();
        
        for (String diskIdAndName : rawDiskStats_Current.keySet()) {
            try {
                String diskStats_Previous = rawDiskStats_Previous.get(diskIdAndName);
                String diskStats_Current = rawDiskStats_Current.get(diskIdAndName);
                if ((diskStats_Current == null) || diskStats_Current.isEmpty() || (diskStats_Previous == null) || diskStats_Previous.isEmpty()) continue;

                String[] rawDiskStats_DelimtedBySpace_Previous = diskStats_Previous.split("\\s+");
                String[] rawDiskStats_DelimtedBySpace_Current = diskStats_Current.split("\\s+");

                if ((rawDiskStats_DelimtedBySpace_Previous == null) || (rawDiskStats_DelimtedBySpace_Current == null)) return rawDiskStatsDeltas_ByDiskName;
                if (rawDiskStats_DelimtedBySpace_Previous.length != rawDiskStats_DelimtedBySpace_Current.length) return rawDiskStatsDeltas_ByDiskName;
                if (rawDiskStats_DelimtedBySpace_Previous.length < 14) return rawDiskStatsDeltas_ByDiskName;

                StringBuilder rawDiskStats_Delta = new StringBuilder();
                
                for (int i = 0; i < 3; i++) rawDiskStats_Delta.append(rawDiskStats_DelimtedBySpace_Current[i]).append(" ");

                for (int i = 3; i < rawDiskStats_DelimtedBySpace_Current.length; i++) {
                    try {
                        BigDecimal previousValue = new BigDecimal(rawDiskStats_DelimtedBySpace_Previous[i]);
                        BigDecimal currentValue = new BigDecimal(rawDiskStats_DelimtedBySpace_Current[i]);
                        BigDecimal deltaValue = currentValue.subtract(previousValue);
                        if (deltaValue.compareTo(BigDecimal.ZERO) == -1) return new HashMap<>();
                        rawDiskStats_Delta.append(deltaValue.stripTrailingZeros().toPlainString()).append(" ");
                    }
                    catch (Exception e) {
                        logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                    }
                }

                rawDiskStatsDeltas_ByDiskName.put(diskIdAndName, rawDiskStats_Delta.toString().trim());
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return rawDiskStatsDeltas_ByDiskName;
    }
    
    private List<GraphiteMetric> getDiskMetrics_GetGraphiteMetricsFromDiskStatsDelta(String rawDiskStats_Delta, 
            long millisecondsBetweenPreviousAndCurrentDiskstats, int currentTimestampInSeconds) {
        
        if (rawDiskStats_Delta == null) {
            return new ArrayList<>();
        }

        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();

        try {
            String[] statsDelimitedBySpace = rawDiskStats_Delta.split("\\s+");
            if ((statsDelimitedBySpace == null) || (statsDelimitedBySpace.length == 0)) return graphiteMetrics;
            
            DiskIoStat_Delta diskStat = new DiskIoStat_Delta(millisecondsBetweenPreviousAndCurrentDiskstats, statsDelimitedBySpace[2], statsDelimitedBySpace[3],
                    statsDelimitedBySpace[4], statsDelimitedBySpace[5], statsDelimitedBySpace[6], statsDelimitedBySpace[7], statsDelimitedBySpace[8],
                    statsDelimitedBySpace[9], statsDelimitedBySpace[10], statsDelimitedBySpace[11], statsDelimitedBySpace[12], statsDelimitedBySpace[13]);

            if (diskStat.getDeviceName() == null) return graphiteMetrics;
            
            String deviceName = diskStat.getDeviceName();
            if ((diskStat.getReadRequestsPerSecond() != null) && (diskStat.getReadRequestsPerSecond().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Read-Requests|Second", diskStat.getReadRequestsPerSecond(), currentTimestampInSeconds));
            if ((diskStat.getBytesReadPerSecond() != null) && (diskStat.getBytesReadPerSecond().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Read-Bytes|Second", diskStat.getBytesReadPerSecond(), currentTimestampInSeconds));
            if ((diskStat.getMegabytesReadPerSecond() != null) && (diskStat.getMegabytesReadPerSecond().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Read-Megabytes|Second", diskStat.getMegabytesReadPerSecond(), currentTimestampInSeconds));
            if ((diskStat.getReadRequestAverageTimeInMilliseconds() != null) && (diskStat.getReadRequestAverageTimeInMilliseconds().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Read-AvgRequestTime|Millisecond", diskStat.getReadRequestAverageTimeInMilliseconds(), currentTimestampInSeconds));
            if ((diskStat.getWriteRequestsPerSecond() != null) && (diskStat.getWriteRequestsPerSecond().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Write-Requests|Second", diskStat.getWriteRequestsPerSecond(), currentTimestampInSeconds));
            if ((diskStat.getBytesWrittenPerSecond() != null) && (diskStat.getBytesWrittenPerSecond().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Write-Bytes|Second", diskStat.getBytesWrittenPerSecond(), currentTimestampInSeconds));
            if ((diskStat.getMegabytesWrittenPerSecond() != null) && (diskStat.getMegabytesWrittenPerSecond().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Write-Megabytes|Second", diskStat.getMegabytesWrittenPerSecond(), currentTimestampInSeconds));
            if ((diskStat.getWriteRequestAverageTimeInMilliseconds() != null) && (diskStat.getWriteRequestAverageTimeInMilliseconds().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".Write-AvgRequestTime|Millisecond", diskStat.getWriteRequestAverageTimeInMilliseconds(), currentTimestampInSeconds));
            if ((diskStat.getRequestAverageTimeInMilliseconds() != null) && (diskStat.getRequestAverageTimeInMilliseconds().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".AverageRequestTime|Millisecond", diskStat.getRequestAverageTimeInMilliseconds(), currentTimestampInSeconds));
            if ((diskStat.getAverageQueueLength() != null) && (diskStat.getAverageQueueLength().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".AverageQueueLength", diskStat.getAverageQueueLength(), currentTimestampInSeconds));
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
}