package com.pearson.statspoller.internal_metric_collectors.linux.Uptime;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import com.pearson.statspoller.utilities.file_utils.FileIo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class UptimeCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(UptimeCollector.class.getName());
    
    public UptimeCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the update stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getUptimeMetrics();

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-Uptime metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private List<GraphiteMetric> getUptimeMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
        
        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);

            String rawUptime = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/uptime");
            if ((rawUptime == null) || rawUptime.isEmpty()) return allGraphiteMetrics;
            
            int indexOfFirstSpace = rawUptime.indexOf(' ');
            if (indexOfFirstSpace == -1) return allGraphiteMetrics;
            
            String uptimeSinceOsBoot = rawUptime.substring(0, indexOfFirstSpace).trim();
            if ((uptimeSinceOsBoot == null) || uptimeSinceOsBoot.isEmpty()) return allGraphiteMetrics;
            
            BigDecimal uptimeSinceOsBoot_BigDecimal = new BigDecimal(uptimeSinceOsBoot);

            if (uptimeSinceOsBoot_BigDecimal.compareTo(BigDecimal.ZERO) != -1) {
                allGraphiteMetrics.add(new GraphiteMetric("OS_Uptime-Seconds", uptimeSinceOsBoot_BigDecimal, currentTimestampInSeconds));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
}
