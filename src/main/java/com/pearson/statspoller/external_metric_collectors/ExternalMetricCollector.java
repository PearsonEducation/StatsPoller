package com.pearson.statspoller.external_metric_collectors;

import com.pearson.statspoller.utilities.core_utils.StackTrace;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class ExternalMetricCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalMetricCollector.class.getName());
    
    private final String programPathAndFilename_;
    private final long collectionIntervalInMs_;
    private final String outputPathAndFilename_;
    private final String metricPrefix_;
    
    public ExternalMetricCollector(String programPathAndFilename, long collectionIntervalInMs, String outputPathAndFilename, String metricPrefix) {
        this.programPathAndFilename_ = programPathAndFilename;
        this.collectionIntervalInMs_ = collectionIntervalInMs;
        this.outputPathAndFilename_ = outputPathAndFilename;
        this.metricPrefix_ = metricPrefix;
    }
    
    public File getFileFromProgramPathAndFilename() {
        try {
            File file = new File(programPathAndFilename_);
            return file;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public File getFileFromOutputPathAndFilename() {
        try {
            File file = new File(outputPathAndFilename_);
            return file;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public String getProgramPathAndFilename() {
        return programPathAndFilename_;
    }

    public long getCollectionIntervalInMs() {
        return collectionIntervalInMs_;
    }

    public String getOutputPathAndFilename() {
        return outputPathAndFilename_;
    }

    public String getMetricPrefix() {
        return metricPrefix_;
    }
    
}
