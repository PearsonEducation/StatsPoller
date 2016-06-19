package com.pearson.statspoller.external_metric_collectors;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.pearson.statspoller.globals.ApplicationConfiguration;
import com.pearson.statspoller.globals.GlobalVariables;
import com.pearson.statspoller.metric_formats.GenericMetricFormat;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.io.BufferedReader;
import java.io.StringReader;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class ReadMetricsFromFileThread implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ReadMetricsFromFileThread.class.getName());
    
    private final int NUM_FILE_READ_RETRIES = 3;
    private final int DELAY_BETWEEN_READ_RETRIES_IN_MS = 100;
    
    private final long applicationStartTimeInMs_;
    private final Map<String,Long> previousMetricTimestamps_ = new HashMap<>();
    private Long previousFileLastModifiedTimestamp_ = null;
    private long oldestValidMetricTimestamp_Seconds_ = -1;

    private final File fileToMonitor_;
    private final long checkFilesIntervalInMilliseconds_;
    private final String metricCollectorPrefix_;
    
    public ReadMetricsFromFileThread(File fileToMonitor, long checkFilesIntervalInMilliseconds, String metricCollectorPrefix) {
        this.fileToMonitor_ = fileToMonitor;
        this.checkFilesIntervalInMilliseconds_ = checkFilesIntervalInMilliseconds;
        this.metricCollectorPrefix_ = metricCollectorPrefix;
        
        this.applicationStartTimeInMs_ = ApplicationConfiguration.getApplicationStartTimeInMs();
    }
    
    @Override
    public void run() {
        
        String metricPrefix = "";
        if (ApplicationConfiguration.isGlobalMetricNamePrefixEnabled() && (ApplicationConfiguration.getGlobalMetricNamePrefix() != null)) metricPrefix += ApplicationConfiguration.getGlobalMetricNamePrefix();
        if (!metricPrefix.isEmpty() && !metricPrefix.endsWith(".")) metricPrefix += ".";
        if (metricCollectorPrefix_ != null) metricPrefix += metricCollectorPrefix_;
        if (!metricPrefix.isEmpty() && !metricPrefix.endsWith(".")) metricPrefix += ".";
        
        while (true) {
            long readMetricsTimeStart = System.currentTimeMillis();
            
            oldestValidMetricTimestamp_Seconds_ = Math.round((double) (((double) System.currentTimeMillis() - (double) ApplicationConfiguration.getMaxMetricAge()) / 1000));
            
            List<GenericMetricFormat> metrics = getMetricsFromFile(ApplicationConfiguration.isAlwaysCheckOutputFiles(), metricPrefix);
            List<GenericMetricFormat> newMetrics = getNewMetrics(metrics);
            
            for (GenericMetricFormat metric : newMetrics) {
                metric.setMetricHashKey(GlobalVariables.metricHashKeyGenerator.incrementAndGet());
                if (metric instanceof GraphiteMetric) GlobalVariables.graphiteMetrics.put(metric.getMetricHashKey(), (GraphiteMetric) metric);
                else if (metric instanceof OpenTsdbMetric) GlobalVariables.openTsdbMetrics.put(metric.getMetricHashKey(), (OpenTsdbMetric) metric);
            }
            
            cleanupPreviousMetrics();

            String filename = (fileToMonitor_ == null) ? null : fileToMonitor_.getName();
            String outputStatusString = "Finished reading metrics from file. File=\"" + filename + "\", NewMetricCount=" + newMetrics.size() +
                    ", FileTimestamp=" + previousFileLastModifiedTimestamp_;
            
            if (newMetrics.size() > 0) logger.info(outputStatusString);
            else logger.debug(outputStatusString);

            long readMetricsTimeElapsed = System.currentTimeMillis() - readMetricsTimeStart;
            
            long sleepTime = checkFilesIntervalInMilliseconds_ - readMetricsTimeElapsed;
            
            Threads.sleepMilliseconds(sleepTime);          
        }
        
    }
    
    protected List<GenericMetricFormat> getMetricsFromFile(boolean alwaysCheckOutputFiles, String metricPrefix) {
        
        if (fileToMonitor_ == null) {
            return new ArrayList<>();
        }
        
        List<GenericMetricFormat> metrics = new ArrayList<>();

        if (FileIo.doesFileExist(fileToMonitor_)) {
            Long currentFileLastModifiedTimestamp = null;

            try {
                currentFileLastModifiedTimestamp = fileToMonitor_.lastModified();
            }
            catch (Exception e) {
                logger.warn(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }

            if ((currentFileLastModifiedTimestamp != null) && (currentFileLastModifiedTimestamp >= applicationStartTimeInMs_)) {
                boolean doesFileHaveNewerLastModifiedTimestamp = (previousFileLastModifiedTimestamp_ != null) && (previousFileLastModifiedTimestamp_ < currentFileLastModifiedTimestamp);
                
                if ((previousFileLastModifiedTimestamp_ == null) || ((previousFileLastModifiedTimestamp_ != null) && alwaysCheckOutputFiles) || doesFileHaveNewerLastModifiedTimestamp) {
                    String fileContents = FileIo.readFileToString(fileToMonitor_, NUM_FILE_READ_RETRIES, DELAY_BETWEEN_READ_RETRIES_IN_MS);
                    List<GenericMetricFormat> metricsFromFile = getMetricsFromString(fileContents, metricPrefix, applicationStartTimeInMs_);
                    metrics.addAll(metricsFromFile);
                    previousFileLastModifiedTimestamp_ = currentFileLastModifiedTimestamp;
                }
            }
        }
        
        return metrics;
    }
    
    protected List<GenericMetricFormat> getNewMetrics(List<GenericMetricFormat> metrics) {
        
        if ((metrics == null) || metrics.isEmpty()) {
            return new ArrayList<>();
        }

        List<GenericMetricFormat> newMetrics = new ArrayList<>();
        
        for (GenericMetricFormat metric : metrics) {

            if (metric.getMetricTimestampInMilliseconds() >= oldestValidMetricTimestamp_Seconds_) {
                Long previousGraphiteMetricTimestamp = previousMetricTimestamps_.get(metric.getMetricKey());

                if ((previousGraphiteMetricTimestamp == null) || (previousGraphiteMetricTimestamp < metric.getMetricTimestampInMilliseconds())) {
                    newMetrics.add(metric);
                    previousMetricTimestamps_.put(metric.getMetricKey(), metric.getMetricTimestampInMilliseconds());
                }
            }
            
        }
        
        return newMetrics;
    }

    protected void cleanupPreviousMetrics() {
    
        if ((previousMetricTimestamps_ == null) || previousMetricTimestamps_.isEmpty()) {
            return;
        }
        
        List<String> metricPathsToForget = new ArrayList<>();
        
        for (String metricPath : previousMetricTimestamps_.keySet()) {
            Long metricTimestamp = previousMetricTimestamps_.get(metricPath);
            
            if (metricTimestamp < oldestValidMetricTimestamp_Seconds_) {
                metricPathsToForget.add(metricPath);
            }
        } 
        
        for (String metricPath : metricPathsToForget) {
            previousMetricTimestamps_.remove(metricPath);
        }
        
    }
    
    protected static List<GenericMetricFormat> getMetricsFromString(String unparsedMetrics, String metricPrefix, long oldestAllowedMetricTimestamp) {
        
        if ((unparsedMetrics == null) || unparsedMetrics.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<GenericMetricFormat> metrics = new ArrayList();
        long currentTimestamp = System.currentTimeMillis();
        
        StringReader stringReader = null;
        BufferedReader bufferedReader = null;
            
        try {
            stringReader = new StringReader(unparsedMetrics);
            bufferedReader = new BufferedReader(stringReader);
        
            String unparsedMetric = bufferedReader.readLine();
            while(unparsedMetric != null) {
                String unparsedMetricTrimmed = unparsedMetric.trim();
                boolean isGraphiteMetric = isGraphiteMetric(unparsedMetricTrimmed);
                
                GraphiteMetric graphiteMetric = null;
                OpenTsdbMetric openTsdbMetric = null;
                if (isGraphiteMetric) graphiteMetric = GraphiteMetric.parseGraphiteMetric(unparsedMetric, metricPrefix, currentTimestamp);
                else {
                    boolean isOpenTsdbMetric = isOpenTsdbMetric(unparsedMetricTrimmed);
                    if (isOpenTsdbMetric) openTsdbMetric = OpenTsdbMetric.parseOpenTsdbMetric(unparsedMetric, metricPrefix, currentTimestamp);
                    else logger.error("Invalid metric -- unrecognized format. Metric=\"" + unparsedMetric + "\"");
                }

                if ((graphiteMetric != null) && (graphiteMetric.getMetricTimestampInMilliseconds() > oldestAllowedMetricTimestamp)) metrics.add(graphiteMetric);
                if ((openTsdbMetric != null) && (openTsdbMetric.getMetricTimestampInMilliseconds() > oldestAllowedMetricTimestamp)) metrics.add(openTsdbMetric);

                unparsedMetric = bufferedReader.readLine();
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
            
            try {
                if (stringReader != null) {
                    stringReader.close();
                }
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }

        return metrics;
    }
    
    private static boolean isGraphiteMetric(String unparsedMetricTrimmed) {
        
        if (unparsedMetricTrimmed == null) {
            return false;
        }
        
        String[] unparsedMetric_SpaceDelimited = StringUtils.split(unparsedMetricTrimmed, ' ');
        
        if ((unparsedMetric_SpaceDelimited == null) || (unparsedMetric_SpaceDelimited.length == 0)) {
            return false;
        }
        
        int blocksOfOfTextCount = 0;
        
        for (String unparsedMetric_SpaceDelimited_Block : unparsedMetric_SpaceDelimited) {
            if (unparsedMetric_SpaceDelimited_Block.length() > 0) blocksOfOfTextCount++;
        }
        
        return blocksOfOfTextCount == 3;
    }
    
    private static boolean isOpenTsdbMetric(String unparsedMetricTrimmed) {
        
        if (unparsedMetricTrimmed == null) {
            return false;
        }
        
        String[] unparsedMetric_SpaceDelimited = StringUtils.split(unparsedMetricTrimmed, ' ');
        
        if ((unparsedMetric_SpaceDelimited == null) || (unparsedMetric_SpaceDelimited.length == 0)) {
            return false;
        }
        
        int blocksOfOfTextCount = 0;
        
        for (String unparsedMetric_SpaceDelimited_Block : unparsedMetric_SpaceDelimited) {
            if (unparsedMetric_SpaceDelimited_Block.length() > 0) blocksOfOfTextCount++;
        }
        
        return blocksOfOfTextCount > 3;
    }
    
    public File getFileToMonitor() {
        return fileToMonitor_;
    }

    public long getCheckFilesIntervalInMilliseconds() {
        return checkFilesIntervalInMilliseconds_;
    }

    public String getMetricCollectorPrefix_() {
        return metricCollectorPrefix_;
    }

}
