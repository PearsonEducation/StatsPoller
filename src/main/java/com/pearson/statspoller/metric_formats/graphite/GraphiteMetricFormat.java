package com.pearson.statspoller.metric_formats.graphite;

/**
 * @author Jeffrey Schmidt
 */
public interface GraphiteMetricFormat {
        
    public String getGraphiteFormatString(boolean sanitizeMetric, boolean substituteCharacters); 
    
}
