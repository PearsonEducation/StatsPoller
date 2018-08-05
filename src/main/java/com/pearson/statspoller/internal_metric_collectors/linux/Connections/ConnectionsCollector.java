package com.pearson.statspoller.internal_metric_collectors.linux.Connections;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import com.pearson.statspoller.utilities.file_utils.FileIo;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Collects TCP & UDP connection count metrics
 * Reads from /proc/net/sockstat & /proc/net/sockstat6
 */
public class ConnectionsCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionsCollector.class.getName());
    
    public ConnectionsCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the connection stats in graphite format
            List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
            graphiteMetrics.addAll(getSockstatMetrics(super.getLinuxProcFileSystemLocation()));
            graphiteMetrics.addAll(getSockstat6Metrics(super.getLinuxProcFileSystemLocation()));

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-Connections metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private static List<GraphiteMetric> getSockstatMetrics(String procFileSystemLocation) {
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();

        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            String procNetSockstat = FileIo.readFileToString(procFileSystemLocation + "/net/sockstat");
            if (procNetSockstat == null) return graphiteMetrics;

            BufferedReader reader = null;
            
            try {
                reader = new BufferedReader(new StringReader(procNetSockstat));
                String currentLine = reader.readLine();

                while (currentLine != null) {
                    try {
                        if (currentLine.startsWith("TCP:")) {
                            GraphiteMetric graphiteMetric = getInuseValueFromSockstatLine(currentLine, "TcpIPv4-ConnectionCount", currentTimestampInSeconds);
                            if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                        }
                        
                        if (currentLine.startsWith("UDP:")) {
                            GraphiteMetric graphiteMetric = getInuseValueFromSockstatLine(currentLine, "UdpIPv4-ConnectionCount", currentTimestampInSeconds);
                            if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                        }
                    } 
                    catch (Exception e) {
                        logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
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
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }

    private static List<GraphiteMetric> getSockstat6Metrics(String procFileSystemLocation) {
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();

        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            String procNetSockstat6 = FileIo.readFileToString(procFileSystemLocation + "/net/sockstat6");
            if (procNetSockstat6 == null) return graphiteMetrics;

            BufferedReader reader = null;
            
            try {
                reader = new BufferedReader(new StringReader(procNetSockstat6));
                String currentLine = reader.readLine();

                while (currentLine != null) {
                    try {
                        if (currentLine.startsWith("TCP6:")) {
                            GraphiteMetric graphiteMetric = getInuseValueFromSockstatLine(currentLine, "TcpIPv6-ConnectionCount", currentTimestampInSeconds);
                            if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                        }
                        
                        if (currentLine.startsWith("UDP6:")) {
                            GraphiteMetric graphiteMetric = getInuseValueFromSockstatLine(currentLine, "UdpIPv6-ConnectionCount", currentTimestampInSeconds);
                            if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                        }
                    } 
                    catch (Exception e) {
                        logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
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
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private static GraphiteMetric getInuseValueFromSockstatLine(String currentLineInSockStat, String graphiteMetricPath, int timestampInSeconds) {
        
        if ((currentLineInSockStat == null) || (graphiteMetricPath == null) || (timestampInSeconds < 0)) {
            return null;
        }
        
        GraphiteMetric graphiteMetric = null;
        
        try {
            String inuse = "inuse";
            int indexOfInuse = currentLineInSockStat.indexOf(inuse);

            if (indexOfInuse > 0) {
                int indexOfInuseValue = indexOfInuse + inuse.length() + 1;
                int indexOfSpaceAfterInuseValue = currentLineInSockStat.indexOf(" ", indexOfInuseValue);

                if (indexOfSpaceAfterInuseValue > 0) {
                    String inuseValue = currentLineInSockStat.substring(indexOfInuseValue, indexOfSpaceAfterInuseValue);
                    if (StringUtils.isNumeric(inuseValue)) graphiteMetric = new GraphiteMetric(graphiteMetricPath, new BigDecimal(inuseValue), timestampInSeconds);
                }
                else { // this covers the case of 'inuse' being the last metric on the line
                    String inuseValue = currentLineInSockStat.substring(indexOfInuseValue, currentLineInSockStat.length());
                    if (StringUtils.isNumeric(inuseValue)) graphiteMetric = new GraphiteMetric(graphiteMetricPath, new BigDecimal(inuseValue), timestampInSeconds);
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetric;
    }
    
}
