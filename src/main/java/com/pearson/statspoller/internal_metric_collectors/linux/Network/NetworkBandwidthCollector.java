package com.pearson.statspoller.internal_metric_collectors.linux.Network;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import com.pearson.statspoller.utilities.file_utils.FileIo;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Collects network i/o metrics (ex - bandwidth)
 * Gets the raw data from /sys/class/net/(interface)/statistics/ or /proc/net/dev
 */
public class NetworkBandwidthCollector extends InternalCollectorFramework implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(NetworkBandwidthCollector.class.getName());
    
    private static final BigDecimal BYTES_TO_MEGABITS_DIVISOR = new BigDecimal("125000");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    private static Map<String,NetworkBandwidthStat> previousNetworkStats_ = null;
    
    public NetworkBandwidthCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
        resetVariables();
        
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the network stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getNetworkMetrics();
            
            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-Network-Bandwidth metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private void resetVariables() {
        previousNetworkStats_ = null;
    }
    
    private List<GraphiteMetric> getNetworkMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
        
        try {
            Map<String,NetworkBandwidthStat> currentNetworkStats = getCurrentNetworkStats_FromSys(super.getLinuxSysFileSystemLocation());
            if ((currentNetworkStats == null) || currentNetworkStats.isEmpty()) currentNetworkStats = getCurrentNetworkStats_FromProc(super.getLinuxProcFileSystemLocation());
            
            if ((currentNetworkStats == null) || currentNetworkStats.keySet().isEmpty()) {
                logger.warn("Unabled to read network stats");
                previousNetworkStats_ = null;
                return allGraphiteMetrics;
            }
            else if (previousNetworkStats_ == null) {
                previousNetworkStats_ = currentNetworkStats;
                return allGraphiteMetrics;
            }
            
            Map<String,NetworkBandwidthStat> previousNetworkStats_Local = previousNetworkStats_;
            previousNetworkStats_ = currentNetworkStats;
            
            for (String networkInterface : currentNetworkStats.keySet()) {
                NetworkBandwidthStat networkStat_Previous = previousNetworkStats_Local.get(networkInterface);
                NetworkBandwidthStat networkStat_Current = currentNetworkStats.get(networkInterface);
                allGraphiteMetrics.addAll(getBandwidthGraphiteMetricsForInterface(networkStat_Previous, networkStat_Current));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private static Map<String,NetworkBandwidthStat> getCurrentNetworkStats_FromSys(String sysFileSystemLocation) {
        
        Map<String,NetworkBandwidthStat> networkStats = new HashMap<>();

        try {
            List<String> networkInterfaces = FileIo.getListOfDirectoryNamesInADirectory(sysFileSystemLocation + "/class/net");

            if (networkInterfaces == null) return networkStats;

            for (String networkInterface : networkInterfaces) {
                if (networkInterface.equals("lo")) continue;
                
                long currentTimestamp = System.currentTimeMillis();
                String rxBytes = FileIo.readFileToString(sysFileSystemLocation + "/class/net/" + networkInterface + "/statistics/rx_bytes");
                String txBytes = FileIo.readFileToString(sysFileSystemLocation + "/class/net/" + networkInterface + "/statistics/tx_bytes");

                if ((rxBytes == null) || (txBytes == null)) continue;

                NetworkBandwidthStat networkStat = new NetworkBandwidthStat(networkInterface, currentTimestamp, rxBytes, txBytes);
                networkStats.put(networkInterface, networkStat);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return networkStats;
    }
    
    private static Map<String,NetworkBandwidthStat> getCurrentNetworkStats_FromProc(String procFileSystemLocation) {
        
        Map<String,NetworkBandwidthStat> networkStats = new HashMap<>();

        try {
            long currentTimestampInMilliseconds = System.currentTimeMillis();
            String procNetDev= FileIo.readFileToString(procFileSystemLocation + "/net/dev");
            if (procNetDev == null) return networkStats;

            BufferedReader reader = null;
            
            try {
                reader = new BufferedReader(new StringReader(procNetDev));
                String currentLine = reader.readLine();
                int lineCounter = 0;
                
                while (currentLine != null) {
                    if (lineCounter <= 1) {
                        currentLine = reader.readLine();
                        lineCounter++;
                        continue;
                    }
                    
                    String trimmedCurrentLine = currentLine.trim();                
                    String[] splitFields = trimmedCurrentLine.split("\\s+");
                    if (splitFields.length != 17) {
                        logger.error("Unexpected number of fields in " + procFileSystemLocation + "/net/dev. Expected 17 fields.");
                        break;
                    }
                    
                    String interfaceName = StringUtils.remove(splitFields[0], ":");
                    String rxBytes = splitFields[1].trim();
                    String txBytes = splitFields[9].trim();
                    if (!interfaceName.isEmpty() && !rxBytes.isEmpty() && !txBytes.isEmpty() && !interfaceName.equals("lo")) {
                        NetworkBandwidthStat networkBandwidthStat = new NetworkBandwidthStat(interfaceName, currentTimestampInMilliseconds, rxBytes, txBytes);
                        networkStats.put(interfaceName, networkBandwidthStat);
                    }
                    
                    currentLine = reader.readLine();
                    lineCounter++;
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
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return networkStats;
    }
    
    private List<GraphiteMetric> getBandwidthGraphiteMetricsForInterface(NetworkBandwidthStat networkStat_Previous, NetworkBandwidthStat networkStat_Current) {
        
        if ((networkStat_Previous == null) || (networkStat_Current == null)) {
            return new ArrayList<>();
        }
         
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
         
        try {
            int currentTimestampInSeconds = (int) (networkStat_Current.getTimestampMs() / 1000);
            long timeElapsedBetweenSamplesInSeconds = ((networkStat_Current.getTimestampMs() - networkStat_Previous.getTimestampMs()) / 1000);
            BigDecimal timeElapsedBetweenSamplesInSeconds_BigDecimal = new BigDecimal(timeElapsedBetweenSamplesInSeconds);

            if (timeElapsedBetweenSamplesInSeconds_BigDecimal.compareTo(BigDecimal.ZERO) <= 0) return graphiteMetrics;
            
            BigDecimal rxBytesPerSecond = (networkStat_Current.getRxBytes().subtract(networkStat_Previous.getRxBytes())).divide(timeElapsedBetweenSamplesInSeconds_BigDecimal, SCALE, ROUNDING_MODE);
            BigDecimal txBytesPerSecond = (networkStat_Current.getTxBytes().subtract(networkStat_Previous.getTxBytes())).divide(timeElapsedBetweenSamplesInSeconds_BigDecimal, SCALE, ROUNDING_MODE);
            BigDecimal overallBytesPerSecond = rxBytesPerSecond.add(txBytesPerSecond);

            BigDecimal rxMegabitsPerSecond = rxBytesPerSecond.divide(BYTES_TO_MEGABITS_DIVISOR, SCALE, ROUNDING_MODE);
            BigDecimal txMegabitsPerSecond = txBytesPerSecond.divide(BYTES_TO_MEGABITS_DIVISOR, SCALE, ROUNDING_MODE);
            BigDecimal overallMegabitsPerSecond = rxMegabitsPerSecond.add(txMegabitsPerSecond);

            if (rxBytesPerSecond.compareTo(BigDecimal.ZERO) == -1) return graphiteMetrics;
            if (txBytesPerSecond.compareTo(BigDecimal.ZERO) == -1) return graphiteMetrics;
            if (overallBytesPerSecond.compareTo(BigDecimal.ZERO) == -1) return graphiteMetrics;
            if (rxMegabitsPerSecond.compareTo(BigDecimal.ZERO) == -1) return graphiteMetrics;
            if (txMegabitsPerSecond.compareTo(BigDecimal.ZERO) == -1) return graphiteMetrics;
            if (overallMegabitsPerSecond.compareTo(BigDecimal.ZERO) == -1) return graphiteMetrics;
        
            String interfaceName = networkStat_Current.getFormattedInterfaceName();
            graphiteMetrics.add(new GraphiteMetric(interfaceName + ".Received-Bytes-Second", rxBytesPerSecond, currentTimestampInSeconds)); 
            graphiteMetrics.add(new GraphiteMetric(interfaceName + ".Transmitted-Bytes-Second", txBytesPerSecond, currentTimestampInSeconds));
            graphiteMetrics.add(new GraphiteMetric(interfaceName + ".Overall-Bytes-Second", overallBytesPerSecond, currentTimestampInSeconds));
            graphiteMetrics.add(new GraphiteMetric(interfaceName + ".Received-Megabits-Second", rxMegabitsPerSecond, currentTimestampInSeconds));
            graphiteMetrics.add(new GraphiteMetric(interfaceName + ".Transmitted-Megabits-Second", txMegabitsPerSecond, currentTimestampInSeconds));
            graphiteMetrics.add(new GraphiteMetric(interfaceName + ".Overall-Megabits-Second", overallMegabitsPerSecond, currentTimestampInSeconds));
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
}