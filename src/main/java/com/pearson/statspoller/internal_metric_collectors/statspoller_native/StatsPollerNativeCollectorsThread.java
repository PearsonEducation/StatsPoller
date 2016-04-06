package com.pearson.statspoller.internal_metric_collectors.statspoller_native;

import com.pearson.statspoller.globals.GlobalVariables;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.MathUtilities;
import com.pearson.statspoller.utilities.Threads;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class StatsPollerNativeCollectorsThread extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(StatsPollerNativeCollectorsThread.class.getName());

    private static final int SCALE = 3;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    private final String version_;
    
    private boolean isUnix_;
    private com.sun.management.UnixOperatingSystemMXBean unixOperatingSystemMXBean_;
    private com.sun.management.OperatingSystemMXBean operatingSystemMXBean_;
    
    public StatsPollerNativeCollectorsThread(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String version) {
        
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.version_ = version;
        
        try {
            operatingSystemMXBean_ = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        }
        catch(Exception e) {}
        
        try {
            unixOperatingSystemMXBean_ = (com.sun.management.UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            isUnix_ = true;
        }
        catch(Exception e) {
            isUnix_ = false;
        }
    }
    
    @Override
    public void run() {
        
        while (true) {
            long readMetricsTimeStart = System.currentTimeMillis();

            List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
            
            if (isEnabled()) {
                List<GraphiteMetric> systemGraphiteMetrics = getSystemMetrics();            
                graphiteMetrics.addAll(systemGraphiteMetrics);
            }

            graphiteMetrics.add(getStatsPollerAvailabilityMetric());
            graphiteMetrics.add(getStatsPollerVersionMetric());

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);
            
            String outputStatusString = "Finished StatsPoller Native metrics collection routine. NewMetricCount=" + graphiteMetrics.size();
            
            if (graphiteMetrics.size() > 0) logger.info(outputStatusString);
            else logger.debug(outputStatusString);
            
            long readMetricsTimeElapsed = System.currentTimeMillis() - readMetricsTimeStart;
            
            long sleepTime = getCollectionInterval() - readMetricsTimeElapsed;
            
            Threads.sleepMilliseconds(sleepTime);          
        }
        
    }
    
    private GraphiteMetric getStatsPollerAvailabilityMetric() {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        GraphiteMetric graphiteMetric = new GraphiteMetric("Agent.Available", BigDecimal.ONE, timestamp);
        return graphiteMetric;
    }
    
    private GraphiteMetric getStatsPollerVersionMetric() {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        
        String versionLocal = version_;
        if (versionLocal == null || versionLocal.isEmpty()) versionLocal = "unknown";
        versionLocal = versionLocal.replace('.', '-');
        
        GraphiteMetric graphiteMetric = new GraphiteMetric("Agent.Version=" + versionLocal, BigDecimal.ONE, timestamp);
        return graphiteMetric;
    }
    
    private GraphiteMetric getStatsPollerTransmitErrorCountMetric() {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        GraphiteMetric graphiteMetric = new GraphiteMetric("Agent.Transmit-Errors", new BigDecimal(GlobalVariables.metricTransmitErrorCount.get()), timestamp);
        return graphiteMetric;
    }
    
    private List<GraphiteMetric> getSystemMetrics() {
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
            
        try {
            int timestamp = (int) (System.currentTimeMillis() / 1000);

            List<GraphiteMetric> cpuStats = getCpuStats(timestamp);
            graphiteMetrics.addAll(cpuStats);

            List<GraphiteMetric> physicalMemoryStats = getPhysicalMemoryStats(timestamp);
            graphiteMetrics.addAll(physicalMemoryStats);

            List<GraphiteMetric> swapSpaceStats = getSwapSpaceStats(timestamp);
            graphiteMetrics.addAll(swapSpaceStats);

            if (isUnix_) {
                List<GraphiteMetric> fileDescriptorStats = getFileDescriptorStats(timestamp);
                graphiteMetrics.addAll(fileDescriptorStats);
            }

            List<GraphiteMetric> diskUsageStats = getDiskSpaceStats(timestamp);
            graphiteMetrics.addAll(diskUsageStats);
        }
        catch (Exception e) {}

        return graphiteMetrics;
    }
    
    private List<GraphiteMetric> getCpuStats(Integer timestamp) {
        if (timestamp == null) timestamp = (int) (System.currentTimeMillis() / 1000);
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        try {
            double systemLoadPercent = operatingSystemMXBean_.getSystemCpuLoad() * 100;
            BigDecimal cpuPct_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(systemLoadPercent), SCALE, ROUNDING_MODE);
            cpuPct_BigDecimal = MathUtilities.correctOutOfRangePercentage(cpuPct_BigDecimal);
            GraphiteMetric cpuPct_Graphite = new GraphiteMetric("Cpu" + "." + "Cpu-%", cpuPct_BigDecimal, timestamp);
            graphiteMetrics.add(cpuPct_Graphite);

            GraphiteMetric coreCount_Graphite = new GraphiteMetric("Cpu" + "." + "CoreCount", new BigDecimal(operatingSystemMXBean_.getAvailableProcessors()), timestamp);
            graphiteMetrics.add(coreCount_Graphite);
        }
        catch (Exception e) {}
        
        return graphiteMetrics;
    }
    
    private List<GraphiteMetric> getPhysicalMemoryStats(Integer timestamp) {
        if (timestamp == null) timestamp = (int) (System.currentTimeMillis() / 1000);
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();

        try {
            double freePhysicalMemorySizeBytes_Double = operatingSystemMXBean_.getFreePhysicalMemorySize();
            if (freePhysicalMemorySizeBytes_Double > 0) {
                BigDecimal freePhysicalMemorySizeBytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(freePhysicalMemorySizeBytes_Double), SCALE, ROUNDING_MODE);
                BigDecimal freePhysicalMemorySizeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(freePhysicalMemorySizeBytes_Double / 1073741824), SCALE, ROUNDING_MODE);
                GraphiteMetric freePhysicalMemorySizeBytes_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Free-Bytes", freePhysicalMemorySizeBytes_BigDecimal, timestamp);
                GraphiteMetric freePhysicalMemorySizeGigabytes_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Free-GB", freePhysicalMemorySizeGigabytes_BigDecimal, timestamp);
                graphiteMetrics.add(freePhysicalMemorySizeBytes_Graphite);
                graphiteMetrics.add(freePhysicalMemorySizeGigabytes_Graphite);
            }

            double totalPhysicalMemorySizeBytes_Double = operatingSystemMXBean_.getTotalPhysicalMemorySize();
            if (totalPhysicalMemorySizeBytes_Double > 0) {
                BigDecimal totalPhysicalMemorySizeBytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(totalPhysicalMemorySizeBytes_Double), SCALE, ROUNDING_MODE);
                BigDecimal totalPhysicalMemorySizeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(totalPhysicalMemorySizeBytes_Double / 1073741824), SCALE, ROUNDING_MODE);
                GraphiteMetric totalPhysicalMemorySizeBytes_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Total-Bytes", totalPhysicalMemorySizeBytes_BigDecimal, timestamp);
                GraphiteMetric totalPhysicalMemorySizeGigabytes_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Total-GB", totalPhysicalMemorySizeGigabytes_BigDecimal, timestamp);
                graphiteMetrics.add(totalPhysicalMemorySizeBytes_Graphite);
                graphiteMetrics.add(totalPhysicalMemorySizeGigabytes_Graphite);
            }

            double usedPhysicalMemorySizeBytes_Double = totalPhysicalMemorySizeBytes_Double - freePhysicalMemorySizeBytes_Double;
            if (usedPhysicalMemorySizeBytes_Double > 0) {
                BigDecimal usedPhysicalMemorySizeBytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedPhysicalMemorySizeBytes_Double), SCALE, ROUNDING_MODE);
                BigDecimal usedPhysicalMemorySizeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedPhysicalMemorySizeBytes_Double / 1073741824), SCALE, ROUNDING_MODE);
                GraphiteMetric usedPhysicalMemorySizeBytes_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Used-Bytes", usedPhysicalMemorySizeBytes_BigDecimal, timestamp);
                GraphiteMetric usedPhysicalMemorySizeGigabytes_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Used-GB", usedPhysicalMemorySizeGigabytes_BigDecimal, timestamp);
                graphiteMetrics.add(usedPhysicalMemorySizeBytes_Graphite);
                graphiteMetrics.add(usedPhysicalMemorySizeGigabytes_Graphite);
            }

            if ((totalPhysicalMemorySizeBytes_Double > 0) && (usedPhysicalMemorySizeBytes_Double > 0)) {
                double usedPhysicalMemoryPct_Double = (usedPhysicalMemorySizeBytes_Double / totalPhysicalMemorySizeBytes_Double) * 100;
                BigDecimal usedPhysicalMemoryPct_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedPhysicalMemoryPct_Double), SCALE, ROUNDING_MODE);
                GraphiteMetric usedPhysicalMemoryPct_Graphite = new GraphiteMetric("PhysicalMemory" + "." + "Used-%", usedPhysicalMemoryPct_BigDecimal, timestamp);
                graphiteMetrics.add(usedPhysicalMemoryPct_Graphite);
            }
        }
        catch (Exception e) {}
        
        return graphiteMetrics;
    }
    
    private List<GraphiteMetric> getSwapSpaceStats(Integer timestamp) {
        if (timestamp == null) timestamp = (int) (System.currentTimeMillis() / 1000);
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();

        try {            
            double freeSwapSpaceSizeBytes_Double = operatingSystemMXBean_.getFreeSwapSpaceSize();
            if (freeSwapSpaceSizeBytes_Double > 0) {
                BigDecimal freeSwapSpaceSizeBytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(freeSwapSpaceSizeBytes_Double), SCALE, ROUNDING_MODE);
                BigDecimal freeSwapSpaceSizeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(freeSwapSpaceSizeBytes_Double / 1073741824), SCALE, ROUNDING_MODE);
                GraphiteMetric freeSwapSpaceSizeBytes_Graphite = new GraphiteMetric("SwapSpace" + "." + "Free-Bytes", freeSwapSpaceSizeBytes_BigDecimal, timestamp);
                GraphiteMetric freeSwapSpaceSizeGigabytes_Graphite = new GraphiteMetric("SwapSpace" + "." + "Free-GB", freeSwapSpaceSizeGigabytes_BigDecimal, timestamp);
                graphiteMetrics.add(freeSwapSpaceSizeBytes_Graphite);
                graphiteMetrics.add(freeSwapSpaceSizeGigabytes_Graphite);
            }

            double totalSwapSpaceSizeBytes_Double = operatingSystemMXBean_.getTotalSwapSpaceSize();
            if (totalSwapSpaceSizeBytes_Double > 0) {
                BigDecimal totalSwapSpaceSizeBytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(totalSwapSpaceSizeBytes_Double), SCALE, ROUNDING_MODE);
                BigDecimal totalSwapSpaceSizeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(totalSwapSpaceSizeBytes_Double / 1073741824), SCALE, ROUNDING_MODE);
                GraphiteMetric totalSwapSpaceSizeBytes_Graphite = new GraphiteMetric("SwapSpace" + "." + "Total-Bytes", totalSwapSpaceSizeBytes_BigDecimal, timestamp);
                GraphiteMetric totalSwapSpaceSizeGigabytes_Graphite = new GraphiteMetric("SwapSpace" + "." + "Total-GB", totalSwapSpaceSizeGigabytes_BigDecimal, timestamp);
                graphiteMetrics.add(totalSwapSpaceSizeBytes_Graphite);
                graphiteMetrics.add(totalSwapSpaceSizeGigabytes_Graphite);
            }

            double usedSwapSpaceSizeBytes_Double = totalSwapSpaceSizeBytes_Double - freeSwapSpaceSizeBytes_Double;
            if (usedSwapSpaceSizeBytes_Double > 0) {
                BigDecimal usedSwapSpaceSizeBytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedSwapSpaceSizeBytes_Double), SCALE, ROUNDING_MODE);
                BigDecimal usedSwapSpaceSizeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedSwapSpaceSizeBytes_Double / 1073741824), SCALE, ROUNDING_MODE);
                GraphiteMetric usedSwapSpaceSizeBytes_Graphite = new GraphiteMetric("SwapSpace" + "." + "Used-Bytes", usedSwapSpaceSizeBytes_BigDecimal, timestamp);
                GraphiteMetric usedSwapSpaceSizeGigabytes_Graphite = new GraphiteMetric("SwapSpace" + "." + "Used-GB", usedSwapSpaceSizeGigabytes_BigDecimal, timestamp);
                graphiteMetrics.add(usedSwapSpaceSizeBytes_Graphite);
                graphiteMetrics.add(usedSwapSpaceSizeGigabytes_Graphite);
            }

            if ((totalSwapSpaceSizeBytes_Double > 0) && (usedSwapSpaceSizeBytes_Double > 0)) {
                double usedSwapSpacePct_Double = (usedSwapSpaceSizeBytes_Double / totalSwapSpaceSizeBytes_Double) * 100;
                BigDecimal usedSwapSpacePct_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedSwapSpacePct_Double), SCALE, ROUNDING_MODE);
                GraphiteMetric usedSwapSpacePct_Graphite = new GraphiteMetric("SwapSpace" + "." + "Used-%", usedSwapSpacePct_BigDecimal, timestamp);
                graphiteMetrics.add(usedSwapSpacePct_Graphite);
            }
        }
        catch (Exception e) {}
        
        return graphiteMetrics;
    }
    
    private List<GraphiteMetric> getFileDescriptorStats(Integer timestamp) {
        if (timestamp == null) timestamp = (int) (System.currentTimeMillis() / 1000);

        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        try {        
            long openFileDescriptorCount_Long = unixOperatingSystemMXBean_.getOpenFileDescriptorCount();
            if (openFileDescriptorCount_Long > 0) {
                GraphiteMetric openFileDescriptorCount_Graphite = new GraphiteMetric("FileDescriptors" + "." + "Open", new BigDecimal(openFileDescriptorCount_Long), timestamp);
                graphiteMetrics.add(openFileDescriptorCount_Graphite);
            }

            long maxFileDescriptorCount_Long = unixOperatingSystemMXBean_.getMaxFileDescriptorCount();
            if (maxFileDescriptorCount_Long > 0) {
                GraphiteMetric maxFileDescriptorCount_Graphite = new GraphiteMetric("FileDescriptors" + "." + "Max", new BigDecimal(maxFileDescriptorCount_Long), timestamp);
                graphiteMetrics.add(maxFileDescriptorCount_Graphite);
            }

            if ((maxFileDescriptorCount_Long > 0) && (openFileDescriptorCount_Long > 0)) {
                long availableFileDescriptorCount_Long = maxFileDescriptorCount_Long - openFileDescriptorCount_Long;
                GraphiteMetric availableFileDescriptorCount_Graphite = new GraphiteMetric("FileDescriptors" + "." + "Available", new BigDecimal(availableFileDescriptorCount_Long), timestamp);
                graphiteMetrics.add(availableFileDescriptorCount_Graphite);
            }
            
            if ((maxFileDescriptorCount_Long > 0) && (openFileDescriptorCount_Long > 0)) {
                double usedFileDescriptorsPct_Double = (openFileDescriptorCount_Long / maxFileDescriptorCount_Long) * 100;
                GraphiteMetric usedFileDescriptorsPct_Graphite = new GraphiteMetric("FileDescriptors" + "." + "Used-%", new BigDecimal(usedFileDescriptorsPct_Double), timestamp);
                graphiteMetrics.add(usedFileDescriptorsPct_Graphite);
            }
        }
        catch (Exception e) {}
        
        return graphiteMetrics;
    }
    
    private List<GraphiteMetric> getDiskSpaceStats(Integer timestamp) {
        if (timestamp == null) timestamp = (int) (System.currentTimeMillis() / 1000);
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        try {
            for (FileStore fileStore : FileSystems.getDefault().getFileStores()) {

                try {
                    String fileStoreString = fileStore.toString();
                    String fileStoreName = fileStore.name();
                    String adjustedFileStoreName = StringUtils.removeEnd(fileStoreString, "(" + fileStoreName + ")").replace('.', '_').trim();
                    
                    long freeBytes_Long = fileStore.getUnallocatedSpace();
                    if (freeBytes_Long > 0) {
                        GraphiteMetric freeBytes_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Free-Bytes", new BigDecimal(freeBytes_Long), timestamp);
                        BigDecimal freeGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal((double) freeBytes_Long / (double) 1073741824), SCALE, ROUNDING_MODE);
                        GraphiteMetric freeGigabytes_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Free-GB", freeGigabytes_BigDecimal, timestamp);
                        graphiteMetrics.add(freeBytes_Graphite);
                        graphiteMetrics.add(freeGigabytes_Graphite);
                    }

                    long totalBytes_Long = fileStore.getTotalSpace();
                    if (totalBytes_Long > 0) {
                        GraphiteMetric totalBytes_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Total-Bytes", new BigDecimal(totalBytes_Long), timestamp);
                        BigDecimal totalGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal((double) totalBytes_Long / (double) 1073741824), SCALE, ROUNDING_MODE);
                        GraphiteMetric totalGigabytes_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Total-GB", totalGigabytes_BigDecimal, timestamp);
                        graphiteMetrics.add(totalBytes_Graphite);
                        graphiteMetrics.add(totalGigabytes_Graphite);
                    }

                    long usedBytes_Long = totalBytes_Long - freeBytes_Long;
                    if (usedBytes_Long > 0) {
                        GraphiteMetric usedBytes_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Used-Bytes", new BigDecimal(usedBytes_Long), timestamp);
                        BigDecimal usedGigabytes_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal((double) usedBytes_Long / (double) 1073741824), SCALE, ROUNDING_MODE);
                        GraphiteMetric usedGigabytes_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Used-GB", usedGigabytes_BigDecimal, timestamp);
                        graphiteMetrics.add(usedBytes_Graphite);
                        graphiteMetrics.add(usedGigabytes_Graphite);
                    }
                    
                    if ((totalBytes_Long > 0) && (usedBytes_Long > 0)) {
                        double usedPct_Double = ((double) ((double) usedBytes_Long / (double) totalBytes_Long) * 100);
                        BigDecimal usedPct_BigDecimal = MathUtilities.smartBigDecimalScaleChange(new BigDecimal(usedPct_Double), SCALE, ROUNDING_MODE);
                        GraphiteMetric usedPct_Graphite = new GraphiteMetric("DiskSpace" + "." + adjustedFileStoreName + "." + "Used-Pct", usedPct_BigDecimal, timestamp);
                        graphiteMetrics.add(usedPct_Graphite);   
                    }
                }
                catch (Exception e) {}
            }
        }
        catch (Exception e) {}

        return graphiteMetrics;
    }

}
