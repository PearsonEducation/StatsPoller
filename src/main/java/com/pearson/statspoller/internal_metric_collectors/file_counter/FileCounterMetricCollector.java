package com.pearson.statspoller.internal_metric_collectors.file_counter;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class FileCounterMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(FileCounterMetricCollector.class.getName());
    
    private final String rootDirectory_;
    private final boolean countFilesInSubdirectories_;
    
    public FileCounterMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String rootDirectory, boolean countFilesInSubdirectories) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        
        rootDirectory_ = removeTrailingSlashes(rootDirectory);
        countFilesInSubdirectories_ = countFilesInSubdirectories;
    }
    
    @Override
    public void run() {
        
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the update stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getFileCountMetrics();

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-Uptime metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }
        
    }
    
    private List<GraphiteMetric> getFileCountMetrics() {
        
        if (rootDirectory_ == null) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        try {
            Collection<File> directories = new ArrayList<>();
            File rootDirectory_File = new File(rootDirectory_);
            
            if (countFilesInSubdirectories_) {
                Collection<File> subdirectories = FileUtils.listFilesAndDirs(rootDirectory_File, DirectoryFileFilter.DIRECTORY, TrueFileFilter.TRUE);
                if (subdirectories != null) directories.addAll(subdirectories);
            }
            else if (rootDirectory_File.isDirectory()) {
                directories.add(rootDirectory_File);
            }
            
            for (File directory : directories) {
                int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
                Collection<File> files = FileUtils.listFiles(directory, null, false);
                if (files == null) continue;
                
                String metricPath = StringUtils.removeStart(directory.getCanonicalPath(), rootDirectory_);
                if (metricPath == null) metricPath = "";
                metricPath = removeLeadingSlashes(metricPath);
                if (SystemUtils.IS_OS_WINDOWS) metricPath = metricPath.replace('\\', '.');
                else metricPath = metricPath.replace('/', '.');
                
                String graphiteFriendlyMetricPath = GraphiteMetric.getGraphiteSanitizedString(metricPath, true, true);
                String finalMetricPath = (graphiteFriendlyMetricPath.isEmpty()) ? "filecount" : (graphiteFriendlyMetricPath + ".filecount");
                
                GraphiteMetric graphiteMetric = new GraphiteMetric(finalMetricPath, new BigDecimal(files.size()), currentTimestampInSeconds);
                graphiteMetrics.add(graphiteMetric);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private static String removeLeadingSlashes(String input) {
        if ((input == null) || input.isEmpty()) return input;
        
        while(input.startsWith("/")) input = org.apache.commons.lang3.StringUtils.removeStart(input, "/");
        while(input.startsWith("\\")) input = org.apache.commons.lang3.StringUtils.removeStart(input, "\\");
        
        return input;
    }
    
    private static String removeTrailingSlashes(String input) {
        if ((input == null) || input.isEmpty()) return input;
        
        while(input.endsWith("/")) input = org.apache.commons.lang3.StringUtils.removeEnd(input, "/");
        while(input.endsWith("\\")) input = org.apache.commons.lang3.StringUtils.removeEnd(input, "\\");
        
        return input;
    }
    
}
