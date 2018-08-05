package com.pearson.statspoller.internal_metric_collectors;

import com.pearson.statspoller.globals.ApplicationConfiguration;
import com.pearson.statspoller.globals.GlobalVariables;
import java.util.List;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import com.pearson.statspoller.utilities.file_utils.FileIo;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public abstract class InternalCollectorFramework {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalCollectorFramework.class.getName());
    
    protected final int NUM_FILE_WRITE_RETRIES = 3;
    protected final int DELAY_BETWEEN_WRITE_RETRIES_IN_MS = 100;
    
    private final boolean isEnabled_;
    private final long collectionInterval_;
    private final String internalCollectorMetricPrefix_;
    private final String outputFilePathAndFilename_;
    private final boolean writeOutputFiles_;
    
    private final String linuxProcFileSystemLocation_ = removeTrailingSlash(ApplicationConfiguration.getLinuxProcLocation());
    private final String linuxSysFileSystemLocation_ = removeTrailingSlash(ApplicationConfiguration.getLinuxSysLocation());

    private String fullInternalCollectorMetricPrefix_ = null;
    private String finalOutputFilePathAndFilename_ = null;
    
    public InternalCollectorFramework(boolean isEnabled, long collectionInterval, String internalCollectorMetricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles) {
        this.isEnabled_ = isEnabled;
        this.collectionInterval_ = collectionInterval;
        this.internalCollectorMetricPrefix_ = internalCollectorMetricPrefix;
        this.outputFilePathAndFilename_ = outputFilePathAndFilename;
        this.writeOutputFiles_ = writeOutputFiles;
        
        createFullInternalCollectorMetricPrefix();
        this.finalOutputFilePathAndFilename_ = this.outputFilePathAndFilename_;
    }
    
    public void outputGraphiteMetrics(List<GraphiteMetric> graphiteMetrics) {
        
        if (graphiteMetrics == null) return;
        
        for (GraphiteMetric graphiteMetric : graphiteMetrics) {
            try {
                if (graphiteMetric == null) continue;

                String graphiteMetricPathWithPrefix = fullInternalCollectorMetricPrefix_ + graphiteMetric.getMetricPath();
                GraphiteMetric outputGraphiteMetric = new GraphiteMetric(graphiteMetricPathWithPrefix, graphiteMetric.getMetricValue(), graphiteMetric.getMetricTimestampInSeconds());

                outputGraphiteMetric.setHashKey(GlobalVariables.metricHashKeyGenerator.incrementAndGet());
                GlobalVariables.graphiteMetrics.put(outputGraphiteMetric.getHashKey(), outputGraphiteMetric);
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        if (writeOutputFiles_) {
            String outputString = buildGraphiteMetricsFile(graphiteMetrics, true, fullInternalCollectorMetricPrefix_);
            writeGraphiteMetricsToFile(outputString);
        }
        
    }
    
    public void outputOpentsdbMetricsAsGraphiteMetrics(List<OpenTsdbMetric> openTsdbMetrics) {

        if (openTsdbMetrics == null) return;
        
        for (OpenTsdbMetric openTsdbMetric : openTsdbMetrics) {
            try {
                if (openTsdbMetric == null) continue;

                String metricNameWithPrefix = fullInternalCollectorMetricPrefix_ + openTsdbMetric.getMetric();
                GraphiteMetric outputGraphiteMetric = new GraphiteMetric(metricNameWithPrefix, openTsdbMetric.getMetricValue(), openTsdbMetric.getMetricTimestampInSeconds());

                outputGraphiteMetric.setHashKey(GlobalVariables.metricHashKeyGenerator.incrementAndGet());
                GlobalVariables.graphiteMetrics.put(outputGraphiteMetric.getHashKey(), outputGraphiteMetric);
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        if (writeOutputFiles_) {
            List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
            
            for (OpenTsdbMetric openTsdbMetric : openTsdbMetrics) {
                try {
                    if (openTsdbMetric == null) continue;
                    GraphiteMetric graphiteMetric = new GraphiteMetric(openTsdbMetric.getMetric(), openTsdbMetric.getMetricValue(), openTsdbMetric.getMetricTimestampInSeconds());
                    graphiteMetrics.add(graphiteMetric);
                } 
                catch (Exception e) {
                    logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                }
            }
            
            String outputString = buildGraphiteMetricsFile(graphiteMetrics, true, fullInternalCollectorMetricPrefix_);
            writeGraphiteMetricsToFile(outputString);
        }
        
    }
    
    public void outputOpenTsdbMetrics(List<OpenTsdbMetric> openTsdbMetrics) {
        
        if (openTsdbMetrics == null) return;
        
        for (OpenTsdbMetric openTsdbMetric : openTsdbMetrics) {
            try {
                if (openTsdbMetric == null) continue;

                String metricNameWithPrefix = fullInternalCollectorMetricPrefix_ + openTsdbMetric.getMetric();
                OpenTsdbMetric outputOpenTsdbMetric = new OpenTsdbMetric(metricNameWithPrefix, openTsdbMetric.getMetricTimestampInMilliseconds(), 
                        openTsdbMetric.getMetricValue(), openTsdbMetric.getTags());

                outputOpenTsdbMetric.setHashKey(GlobalVariables.metricHashKeyGenerator.incrementAndGet());
                GlobalVariables.openTsdbMetrics.put(outputOpenTsdbMetric.getHashKey(), outputOpenTsdbMetric);
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        if (writeOutputFiles_) {
            String outputString = buildOpenTsdbMetricsFile(openTsdbMetrics, true, fullInternalCollectorMetricPrefix_);
            writeGraphiteMetricsToFile(outputString);
        }
        
    }
    
    private void writeGraphiteMetricsToFile(String output) {
        
        if ((output == null) || output.isEmpty()) {
            return;
        }
        
        boolean isSuccessfulWrite;
        
        try {
            for (int i = 0; i <= NUM_FILE_WRITE_RETRIES; i++) {
                isSuccessfulWrite = FileIo.saveStringToFile(finalOutputFilePathAndFilename_, output);

                if (isSuccessfulWrite) break;
                else Threads.sleepMilliseconds(DELAY_BETWEEN_WRITE_RETRIES_IN_MS);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
    }
    
    private String buildGraphiteMetricsFile(List<GraphiteMetric> graphiteMetrics, boolean stripPrefix, String metricPrefix) {
        
        if ((graphiteMetrics == null) || graphiteMetrics.isEmpty()) {
            return null;
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        
        for (GraphiteMetric graphiteMetric : graphiteMetrics) {
            try {
                if (graphiteMetric == null) continue;

                GraphiteMetric outputGraphiteMetric = graphiteMetric;

                if (stripPrefix && (metricPrefix != null)) {
                    String graphiteMetricPathNoPrefix = StringUtils.removeStart(graphiteMetric.getMetricPath(), metricPrefix);
                    outputGraphiteMetric = new GraphiteMetric(graphiteMetricPathNoPrefix, graphiteMetric.getMetricValue(), graphiteMetric.getMetricTimestampInSeconds());
                }

                stringBuilder.append(outputGraphiteMetric.getGraphiteFormatString(true, true)).append("\n");
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return stringBuilder.toString();
    }
    
    private String buildOpenTsdbMetricsFile(List<OpenTsdbMetric> openTsdbMetrics, boolean stripPrefix, String metricPrefix) {
        
        if ((openTsdbMetrics == null) || openTsdbMetrics.isEmpty()) {
            return null;
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        
        for (OpenTsdbMetric openTsdbMetric : openTsdbMetrics) {
            try {
                if (openTsdbMetric == null) continue;

                OpenTsdbMetric outputOpenTsdbMetric = openTsdbMetric;

                if (stripPrefix && (metricPrefix != null)) {
                    String openTsdbMetricNameNoPrefix = StringUtils.removeStart(openTsdbMetric.getMetric(), metricPrefix);
                    outputOpenTsdbMetric = new OpenTsdbMetric(openTsdbMetricNameNoPrefix, openTsdbMetric.getMetricTimestampInMilliseconds(), 
                            openTsdbMetric.getMetricValue(), openTsdbMetric.getTags());
                }

                stringBuilder.append(outputOpenTsdbMetric.getOpenTsdbTelnetFormatString(true)).append("\n");
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return stringBuilder.toString();
    }
    
    /*
    returns GlobalMetricPrefix.CollectorMetricPrefix.
    */
    protected final void createFullInternalCollectorMetricPrefix() {
        createAndUpdateFullInternalCollectorMetricPrefix(null, null);
    }
    
    protected void createAndUpdateFullInternalCollectorMetricPrefix(String textToReplace, String replacement) {
        
        String metricPrefix = "";
        
        if (ApplicationConfiguration.isGlobalMetricNamePrefixEnabled() && (ApplicationConfiguration.getGlobalMetricNamePrefix() != null)) metricPrefix += ApplicationConfiguration.getGlobalMetricNamePrefix();
        
        if (!metricPrefix.isEmpty() && !metricPrefix.endsWith(".")) metricPrefix += ".";
        
        if (internalCollectorMetricPrefix_ != null) {
            String localInternalCollectorMetricPrefix = internalCollectorMetricPrefix_;
            if ((textToReplace != null) && localInternalCollectorMetricPrefix.contains(textToReplace)) {
                if (replacement == null) localInternalCollectorMetricPrefix = localInternalCollectorMetricPrefix.replace(textToReplace, "");
                else localInternalCollectorMetricPrefix = localInternalCollectorMetricPrefix.replace(textToReplace, replacement);
                metricPrefix += localInternalCollectorMetricPrefix;
            }
            else metricPrefix += internalCollectorMetricPrefix_;
        }
        
        if (!metricPrefix.isEmpty() && !metricPrefix.endsWith(".")) metricPrefix += ".";
        
        fullInternalCollectorMetricPrefix_ = metricPrefix;
    }
    
    protected void updateOutputFilePathAndFilename(String textToReplace, String replacement) {
        String localOutputFilePathAndFilename = outputFilePathAndFilename_;

        try {
            if ((localOutputFilePathAndFilename != null) && (textToReplace != null) && localOutputFilePathAndFilename.contains(textToReplace)) {
                if (replacement == null) {
                    localOutputFilePathAndFilename = localOutputFilePathAndFilename.replace(textToReplace, "");
                }
                else {
                    localOutputFilePathAndFilename = localOutputFilePathAndFilename.replace(textToReplace, replacement);
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
      
        finalOutputFilePathAndFilename_ = localOutputFilePathAndFilename;
    }
    
    private static String removeTrailingSlash(String input) {
        if ((input == null) || input.isEmpty()) return input;
        
        while(input.endsWith("/")) input = StringUtils.removeEnd(input, "/");
        return input;
    }
    
    public boolean isEnabled() {
        return isEnabled_;
    }

    public long getCollectionInterval() {
        return collectionInterval_;
    }
    
    public String getInternalCollectorMetricPrefix() {
        return internalCollectorMetricPrefix_;
    }
    
    public boolean isWriteOutputFiles() {
        return writeOutputFiles_;
    }

    protected String getLinuxProcFileSystemLocation() {
        return linuxProcFileSystemLocation_;
    }

    protected String getLinuxSysFileSystemLocation() {
        return linuxSysFileSystemLocation_;
    }
    
}
