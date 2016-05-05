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
 * Based on raw data from /sys/block/(deviceName)/stat or /proc/diskstats
 */
public class DiskIoCollector extends InternalCollectorFramework implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(DiskIoCollector.class.getName());
    
    private static Map<String,String> previousRawDiskStats_ = null;
    private static long previousRawDiskStats_ReadTimestamp_ = -1;
     
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
            super.outputGraphiteMetrics(graphiteMetrics);

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
            
            Map<String,String> currentRawDiskStats = getDiskMetrics_GetRawDiskStats_ByDeviceName_FromSys();
            if ((currentRawDiskStats == null) || currentRawDiskStats.isEmpty()) currentRawDiskStats = getDiskMetrics_GetRawDiskStats_ByDeviceName_FromProc();
            
            if ((currentRawDiskStats == null) || currentRawDiskStats.keySet().isEmpty()) {
                logger.warn("Unabled to read disk io stats");
                previousRawDiskStats_ReadTimestamp_ = -1;
                previousRawDiskStats_ = null;
                return allGraphiteMetrics;
            }
            else if (previousRawDiskStats_ == null) {
                previousRawDiskStats_ReadTimestamp_ = currentTimestampInMilliseconds;
                previousRawDiskStats_ = currentRawDiskStats;
                return allGraphiteMetrics;
            }

            Map<String,String> previousRawDiskStats_Local = previousRawDiskStats_;
            long previousRawDiskStats_ReadTimestamp_Local = previousRawDiskStats_ReadTimestamp_;
            previousRawDiskStats_ = currentRawDiskStats;
            previousRawDiskStats_ReadTimestamp_ = currentTimestampInMilliseconds;

            if ((previousRawDiskStats_ReadTimestamp_Local <= 0) || (currentTimestampInMilliseconds <= 0)) return allGraphiteMetrics;
            if ((previousRawDiskStats_Local == null) || (currentRawDiskStats == null)) {
                logger.warn("Stats cannot be null");
                return allGraphiteMetrics;
            }
            if (previousRawDiskStats_Local.isEmpty() || currentRawDiskStats.isEmpty()) {
                logger.warn("Stats cannot be empty");
                return allGraphiteMetrics;
            }
            
            Map<String,String> rawDiskStats_DeltaBetweenPreviousAndCurrent = getDiskMetrics_GetDeltaBetweenRawDiskStatsSets(previousRawDiskStats_Local, currentRawDiskStats);
            long millisecondsBetweenPreviousAndCurrentDiskStats = currentTimestampInMilliseconds - previousRawDiskStats_ReadTimestamp_Local;
            
            for (String deviceName : rawDiskStats_DeltaBetweenPreviousAndCurrent.keySet()) {
                String rawDiskStatsDelta = rawDiskStats_DeltaBetweenPreviousAndCurrent.get(deviceName);
                allGraphiteMetrics.addAll(getDiskMetrics_GetGraphiteMetricsFromDiskStatsDelta(rawDiskStatsDelta, millisecondsBetweenPreviousAndCurrentDiskStats, currentTimestampInSeconds));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private Map<String,String> getDiskMetrics_GetRawDiskStats_ByDeviceName_FromSys() {
 
        Map<String,String> stats_ByDeviceName = new HashMap<>();
        
        try {   
            List<String> devices = FileIo.getListOfDirectoryNamesInADirectory(super.getLinuxSysFileSystemLocation() + "/block");
            
            for (String deviceName : devices) {
                String deviceStatFilePath = super.getLinuxSysFileSystemLocation() + "/block/" + deviceName + "/stat";
                String deviceStat = FileIo.readFileToString(deviceStatFilePath);
                
                if ((deviceStat == null) || deviceStat.isEmpty()) {
                    logger.debug(deviceStatFilePath + " cannot be null or empty");
                    continue;
                }
                
                deviceStat = deviceStat.trim();
                String[] deviceStatSplitFields = deviceStat.split("\\s+");
                
                if (deviceStatSplitFields.length < 11) {
                    logger.error("Unexpected number of fields in " + deviceStatFilePath + ". Expected 11 or more fields.");
                    break;
                }
                
                int countOfZeros = 0;
                for (String deviceStatSplitField : deviceStatSplitFields) if (deviceStatSplitField.equals("0")) countOfZeros++;
                boolean isAllZeros = (countOfZeros == deviceStatSplitFields.length);
                
                deviceStat = "0 0 " + deviceName + " " + deviceStat; // makes the format compatible with /proc/diskstats from a field-count perspective
                
                if (!isAllZeros && !deviceName.isEmpty()) stats_ByDeviceName.put(deviceName, deviceStat);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return stats_ByDeviceName;
    }
    
    private Map<String,String> getDiskMetrics_GetRawDiskStats_ByDeviceName_FromProc() {
        
        Map<String,String> diskStats_ByDeviceName = new HashMap<>();
        BufferedReader reader = null;

        try {
            String rawProcDiskStats = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/diskstats");
            
            reader = new BufferedReader(new StringReader(rawProcDiskStats));
            String currentLine = reader.readLine();
            
            while (currentLine != null) {
                String trimmedCurrentLine = currentLine.trim();                
                String[] deviceStatSplitFields = trimmedCurrentLine.split("\\s+");
                
                if (deviceStatSplitFields.length < 14) {
                    logger.error("Unexpected number of fields in " + super.getLinuxProcFileSystemLocation() + "/diskstat. Expected 14 or more fields.");
                    break;
                }
                
                String deviceName = deviceStatSplitFields[2].trim();

                int countOfZeros = 0;
                for (int i = 3; i < deviceStatSplitFields.length; i++) if (deviceStatSplitFields[i].equals("0")) countOfZeros++;
                boolean isAllZeros = (countOfZeros == (deviceStatSplitFields.length - 3));
                
                if (!isAllZeros && !deviceName.isEmpty()) diskStats_ByDeviceName.put(deviceName, trimmedCurrentLine);
                
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
        
        Map<String,String> rawDiskStatsDeltas_ByDeviceName = new HashMap<>();
        
        for (String deviceName : rawDiskStats_Current.keySet()) {
            try {
                String diskStats_Previous = rawDiskStats_Previous.get(deviceName);
                String diskStats_Current = rawDiskStats_Current.get(deviceName);
                if ((diskStats_Current == null) || diskStats_Current.isEmpty() || (diskStats_Previous == null) || diskStats_Previous.isEmpty()) continue;

                String[] rawDiskStats_DelimtedBySpace_Previous = diskStats_Previous.split("\\s+");
                String[] rawDiskStats_DelimtedBySpace_Current = diskStats_Current.split("\\s+");

                if ((rawDiskStats_DelimtedBySpace_Previous == null) || (rawDiskStats_DelimtedBySpace_Current == null)) return rawDiskStatsDeltas_ByDeviceName;
                if (rawDiskStats_DelimtedBySpace_Previous.length != rawDiskStats_DelimtedBySpace_Current.length) return rawDiskStatsDeltas_ByDeviceName;
                if (rawDiskStats_DelimtedBySpace_Previous.length < 14) return rawDiskStatsDeltas_ByDeviceName;

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

                rawDiskStatsDeltas_ByDeviceName.put(deviceName, rawDiskStats_Delta.toString().trim());
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return rawDiskStatsDeltas_ByDeviceName;
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
            if ((diskStat.getAverageRequestTimeInMilliseconds() != null) && (diskStat.getAverageRequestTimeInMilliseconds().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".AverageRequestTime|Millisecond", diskStat.getAverageRequestTimeInMilliseconds(), currentTimestampInSeconds));
            if ((diskStat.getAverageQueueLength() != null) && (diskStat.getAverageQueueLength().compareTo(BigDecimal.ZERO) != -1)) graphiteMetrics.add(new GraphiteMetric(deviceName + ".AverageQueueLength", diskStat.getAverageQueueLength(), currentTimestampInSeconds));
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
}