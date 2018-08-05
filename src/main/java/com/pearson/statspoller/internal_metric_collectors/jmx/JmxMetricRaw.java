package com.pearson.statspoller.internal_metric_collectors.jmx;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
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

        String objectInstanceName_Reordered = moveTypeToBeginningOfObjectInstanceName(objectInstanceName_);
        unformattedGraphiteMetricPath_ = objectInstanceName_Reordered + "." + attributePath_;

        return unformattedGraphiteMetricPath_;
    }
    
    private String moveTypeToBeginningOfObjectInstanceName(String objectInstanceName) {
        
        if (objectInstanceName == null) {
            return objectInstanceName;   
        }
        
        try {
            int indexOfFirstColon = objectInstanceName.indexOf(":");
            if (indexOfFirstColon == -1) return objectInstanceName;
            if ((indexOfFirstColon + 1) >= objectInstanceName.length()) return objectInstanceName;
            
            String rightOfFirstColon = objectInstanceName.substring(indexOfFirstColon + 1);
            String[] csvOfObjectInstanceNameValues = rightOfFirstColon.split(",");
            if (csvOfObjectInstanceNameValues.length <= 1) return objectInstanceName;
            List<String> objectInstanceNameFields = new ArrayList(Arrays.asList(csvOfObjectInstanceNameValues));
            
            int indexOfTypeField = 0;
            String objectInstanceNameField_Type = null;
            for (String objectInstanceNameField : objectInstanceNameFields) {
                if ((objectInstanceNameField != null) && !objectInstanceNameField.isEmpty() && objectInstanceNameField.startsWith("type=")) {
                    objectInstanceNameField_Type = objectInstanceNameField;
                    break;
                }
                
                indexOfTypeField++;
            }
            
            if ((objectInstanceNameField_Type != null) && (indexOfTypeField > 0)) {
                objectInstanceNameFields.remove(indexOfTypeField);
                objectInstanceNameFields.add(0, objectInstanceNameField_Type);
                
                StringBuilder objectInstanceName_Reordered_StringBuilder = new StringBuilder();
                for (int j = 0; j < objectInstanceNameFields.size(); j++) {
                    String objectInstanceNameField = objectInstanceNameFields.get(j);
                    objectInstanceName_Reordered_StringBuilder.append(objectInstanceNameField);
                    if ((j + 1) < objectInstanceNameFields.size()) objectInstanceName_Reordered_StringBuilder.append(",");
                }
                
                String objectInstanceName_Reordered_ReturnString = objectInstanceName_Reordered_StringBuilder.toString();
                if (!objectInstanceName_Reordered_ReturnString.isEmpty()) return objectInstanceName_Reordered_ReturnString;
            }
            
            return objectInstanceName;
        }
        catch (Exception e) {
            return objectInstanceName;
        }
        
    }
    
    public String createAndGetGraphiteFormattedMetricPath() {
        
        if (formattedGraphiteMetricPath_ != null) {
            return formattedGraphiteMetricPath_;   
        }

        String unformattedGraphiteMetricPath = createAndGetUnformattedGraphiteMetricPath();
        if (unformattedGraphiteMetricPath == null) return null;
        //unformattedGraphiteMetricPath = moveTypeToBeginningOfPath(unformattedGraphiteMetricPath);
 
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
