package com.pearson.statspoller.external_metric_collectors;

import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class ExternalMetricCollectorExecuterThread implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalMetricCollectorExecuterThread.class.getName());
    
    private final ExternalMetricCollector externalMetricCollector_;
    
    public ExternalMetricCollectorExecuterThread(ExternalMetricCollector externalMetricCollector) {
        this.externalMetricCollector_ = externalMetricCollector;
    }
    
    @Override
    public void run() {
        
        while (true) {
            long startTime = System.currentTimeMillis();
            
            try {
                if ((externalMetricCollector_.getProgramPathAndFilename() != null) && !externalMetricCollector_.getProgramPathAndFilename().isEmpty()) {
                    String[] commands = externalMetricCollector_.getProgramPathAndFilename().split(" ");
                    ProcessBuilder processBuilder = new ProcessBuilder(commands);
                    processBuilder.start();
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
            
            long elaspedTime = System.currentTimeMillis() - startTime;
            
            long sleepTime = externalMetricCollector_.getCollectionIntervalInMs() - elaspedTime;
            
            logger.debug("FinishedExecuting=\"" + externalMetricCollector_.getProgramPathAndFilename() + "\", SleepingFor=" + sleepTime + "ms");
            
            Threads.sleepMilliseconds(sleepTime);         
        }
        
    }

    public ExternalMetricCollector getExternalMetricCollector() {
        return externalMetricCollector_;
    }
    
}