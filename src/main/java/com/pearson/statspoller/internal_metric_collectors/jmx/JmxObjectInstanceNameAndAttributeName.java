package com.pearson.statspoller.internal_metric_collectors.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class JmxObjectInstanceNameAndAttributeName {
    
    private static final Logger logger = LoggerFactory.getLogger(JmxObjectInstanceNameAndAttributeName.class.getName());

    private final String objectInstanceName_;
    private final String attributeName_;
    
    public JmxObjectInstanceNameAndAttributeName(String objectInstanceName, String attributeName) {
        this.objectInstanceName_ = objectInstanceName;
        this.attributeName_= attributeName;
    }

    public String getObjectInstanceName() {
        return objectInstanceName_;
    }

    public String getAttributeName() {
        return attributeName_;
    }
    
}
