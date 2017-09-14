package com.pearson.statspoller.globals;

import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class GlobalVariables {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalVariables.class.getName());
    
    public final static ConcurrentHashMap<Long,GraphiteMetric> graphiteMetrics = new ConcurrentHashMap<>();
    public final static ConcurrentHashMap<Long,OpenTsdbMetric> openTsdbMetrics = new ConcurrentHashMap<>();
    public final static AtomicLong metricHashKeyGenerator = new AtomicLong(Long.MIN_VALUE);
    public final static AtomicLong metricTransmitErrorCount = new AtomicLong(0l);
      
}

