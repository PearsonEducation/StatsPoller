package com.pearson.statspoller.internal_metric_collectors.jmx;

import java.math.BigDecimal;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class JmxMetricRaw {
    
    private static final Logger logger = LoggerFactory.getLogger(JmxMetricRaw.class.getName());
    
    private final String objectInstanceName_;
    private final String attributePath_;
    private final BigDecimal metricValue_;
    private final long metricRetrievalTimestampInMs_;
    
    private String unformattedGraphiteMetricPath_ = null;
    private String formattedGraphiteMetricPath_ = null;
    
    public JmxMetricRaw(String objectInstanceName, String attributePath, BigDecimal metricValue, long metricRetrievalTimestampInMs) {
        this.objectInstanceName_ = objectInstanceName;
        this.attributePath_ = attributePath;
        this.metricValue_ = metricValue;
        this.metricRetrievalTimestampInMs_ = metricRetrievalTimestampInMs;
    }
    
    public String createAndGetUnformattedGraphiteMetricPath() {
        
        if (unformattedGraphiteMetricPath_ != null) {
            return unformattedGraphiteMetricPath_;
        }
        
        if ((objectInstanceName_ == null) || (attributePath_ == null)) {
            return null;
        }

        unformattedGraphiteMetricPath_ = objectInstanceName_ + "." + attributePath_;

        return unformattedGraphiteMetricPath_;
    }
    
    public String createAndGetGraphiteFormattedMetricPath() {
        
        if (formattedGraphiteMetricPath_ != null) {
            return formattedGraphiteMetricPath_;   
        }

        String unformattedGraphiteMetricPath = createAndGetUnformattedGraphiteMetricPath();

        if (unformattedGraphiteMetricPath == null) {
            return null;
        }

        String formattedGraphiteMetricPath = unformattedGraphiteMetricPath;
 
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, objectInstanceName_, objectInstanceName_.replace('.', '-'), 1);
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "%", "Pct");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, " ", "_");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ",", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ":j2eeType=", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ":type=", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ":name=", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ".name=", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "\"", "");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ":", "");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "[", "|");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "]", "|");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "{", "|");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "}", "|");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "=//", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, ".//", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "=/", ".");
        formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "/", ".");

        while (formattedGraphiteMetricPath.contains("..")) {
            formattedGraphiteMetricPath = StringUtils.replace(formattedGraphiteMetricPath, "..", ".");
        }
        
        formattedGraphiteMetricPath_ = formattedGraphiteMetricPath;
        
        return formattedGraphiteMetricPath_;
    }

    public String getObjectInstanceName() {
        return objectInstanceName_;
    }

    public String getAttributePath() {
        return attributePath_;
    }

    public BigDecimal getMetricValue() {
        return metricValue_;
    }

    public long getMetricRetrievalTimestampInMs() {
        return metricRetrievalTimestampInMs_;
    }
    
}
