package com.pearson.statspoller.internal_metric_collectors.jmx;

import com.pearson.statspoller.globals.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class JmxJvmShutdownHook extends Thread {
    
    private static final Logger logger = LoggerFactory.getLogger(JmxJvmShutdownHook.class.getName());
    
    @Override
    public void run() {
        for (JmxMetricCollector jmxMetricCollector : ApplicationConfiguration.getJmxMetricCollectors()) {
            jmxMetricCollector.close();
        }
    }
    
}
