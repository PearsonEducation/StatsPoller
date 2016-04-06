package com.pearson.statspoller.internal_metric_collectors;

import com.pearson.statspoller.globals.ApplicationConfiguration;
import com.pearson.statspoller.globals.GlobalVariables;
import java.util.List;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
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
    private final String fullInternalCollectorMetricPrefix_;
    
    private final String linuxProcFileSystemLocation_ = removeTrailingSlash(ApplicationConfiguration.getLinuxProcLocation());
    private final String linuxSysFileSystemLocation_ = removeTrailingSlash(ApplicationConfiguration.getLinuxSysLocation());

    public InternalCollectorFramework(boolean isEnabled, long collectionInterval, String internalCollectorMetricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles) {
        this.isEnabled_ = isEnabled;
        this.collectionInterval_ = collectionInterval;
        this.internalCollectorMetricPrefix_ = internalCollectorMetricPrefix;
        this.outputFilePathAndFilename_ = outputFilePathAndFilename;
        this.writeOutputFiles_ = writeOutputFiles;
        this.fullInternalCollectorMetricPrefix_ = createFullInternalCollectorMetricPrefix();
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
                isSuccessfulWrite = FileIo.saveStringToFile(outputFilePathAndFilename_, output);

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
    private String createFullInternalCollectorMetricPrefix() {
        String metricPrefix = "";
        
        if (ApplicationConfiguration.isGlobalMetricNamePrefixEnabled() && (ApplicationConfiguration.getGlobalMetricNamePrefixValue() != null)) metricPrefix += ApplicationConfiguration.getGlobalMetricNamePrefixValue();
        if (!metricPrefix.isEmpty() && !metricPrefix.endsWith(".")) metricPrefix += ".";
        if (internalCollectorMetricPrefix_ != null) metricPrefix += internalCollectorMetricPrefix_;
        if (!metricPrefix.isEmpty() && !metricPrefix.endsWith(".")) metricPrefix += ".";
        
        return metricPrefix;
    }
    
    private static String removeTrailingSlash(String input) {
        if ((input == null) || input.isEmpty()) return input;
        
        while(input.endsWith("/")) input = StringUtils.removeEnd(input, "/");
        return input;
    }
    
    protected boolean isEnabled() {
        return isEnabled_;
    }

    protected long getCollectionInterval() {
        return collectionInterval_;
    }

    protected boolean isWriteOutputFiles() {
        return writeOutputFiles_;
    }

    protected String getLinuxProcFileSystemLocation() {
        return linuxProcFileSystemLocation_;
    }

    protected String getLinuxSysFileSystemLocation() {
        return linuxSysFileSystemLocation_;
    }
    
}
