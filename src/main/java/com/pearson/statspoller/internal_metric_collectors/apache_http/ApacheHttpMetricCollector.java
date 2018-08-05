package com.pearson.statspoller.internal_metric_collectors.apache_http;

import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.web_utils.NetIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class ApacheHttpMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpMetricCollector.class.getName());
    
    private final String protocol_;
    private final String host_;
    private final int port_;
    
    public ApacheHttpMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String protocol, String host, int port) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.protocol_ = protocol;
        this.host_ = host;
        this.port_ = port;
    }
    
    @Override
    public void run() {
        
        String url = protocol_ + "://" + host_ + ":" + port_ + "/server-status?auto";
 
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            String serverStatusRaw = NetIo.downloadUrl(url, 0, 1, true);
            long serverStatusDownloadTime = System.currentTimeMillis();

            List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
            boolean downloadSuccess;
            
            if (serverStatusRaw == null) {
                downloadSuccess = false;
                GraphiteMetric isAvailable = new GraphiteMetric("Available", BigDecimal.ZERO, ((int) (serverStatusDownloadTime / 1000)));
                graphiteMetrics.add(isAvailable);
            }
            else {
                downloadSuccess = true;
                GraphiteMetric isAvailable = new GraphiteMetric("Available", BigDecimal.ONE, ((int) (serverStatusDownloadTime / 1000)));
                graphiteMetrics.add(isAvailable);
                graphiteMetrics.addAll(parseApacheServerStatusMachine(serverStatusRaw, serverStatusDownloadTime));
            }
            
            super.outputGraphiteMetrics(graphiteMetrics);
                    
            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Apache HTTP metric collection routine. ApacheHttpServer=" + host_ + ":" + port_ + 
                    ", ConnectionSuccess=" + downloadSuccess +
                    ", ApacheHttpMetricsCollected=" + graphiteMetrics.size() +
                    ", ApacheHttpMetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) {
                Threads.sleepMilliseconds(sleepTimeInMs);
            }
        }

    }
    
    public List<GraphiteMetric> parseApacheServerStatusMachine(String apacheServerStatusMachineRaw, long serverStatusDownloadTime) {
        
        if (apacheServerStatusMachineRaw == null) {
            return new ArrayList<>();
        }
               
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        StringReader stringReader = new StringReader(apacheServerStatusMachineRaw);
        BufferedReader bufferedReader = new BufferedReader(stringReader);
        
        try {
            String currentLine = bufferedReader.readLine();
   
            while (currentLine != null) {
                int metricKeyIndexRange = currentLine.indexOf(':', 0);
                
                if (metricKeyIndexRange == -1) {
                    currentLine = bufferedReader.readLine();
                    continue;
                }
                
                String metricKey = currentLine.substring(0, metricKeyIndexRange);
               
                if (!metricKey.equalsIgnoreCase("Scoreboard")) {
                    try {
                        String metricValueString = currentLine.substring(metricKeyIndexRange + 2, currentLine.length());
                        BigDecimal metricValueBigDecimal = new BigDecimal(metricValueString);

                        String graphiteFriendlyMetricKey = GraphiteMetric.getGraphiteSanitizedString(metricKey, true, true);
                        GraphiteMetric graphiteMetric = new GraphiteMetric(graphiteFriendlyMetricKey, metricValueBigDecimal, ((int) (serverStatusDownloadTime / 1000)));
                        graphiteMetrics.add(graphiteMetric);
                    }
                    catch (Exception e) {}
                }
                
                currentLine = bufferedReader.readLine();
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
            
            try {
                if (stringReader != null) {
                    stringReader.close();
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return graphiteMetrics;
    }
    
}