package com.pearson.statspoller.internal_metric_collectors.linux.ProcessCounter;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Counts the number of processes that are running that match a user-specified regex (matched against process cmdline)
 * Based on raw data from /proc/(pid)/cmdline
 */
public class ProcessCounterMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessCounterMetricCollector.class.getName());
    
    private final List<String[]> processCounterPrefixesAndRegexes_;
    
    private final Map<String,Pattern> regexPatterns_ = new HashMap<>();
    private final List<String[]> processCounterPrefixesAndRegexes_Trimmed_;

    public ProcessCounterMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles, List<String[]> processCounterPrefixesAndRegexes) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        
        this.processCounterPrefixesAndRegexes_ = processCounterPrefixesAndRegexes;
        this.processCounterPrefixesAndRegexes_Trimmed_ = createProcessCounterPrefixesAndRegexes_Trimmed();
        
        createPatternsFromRegexStrings();
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // read the stats from disk
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            Map<String,AtomicLong> processCountsByProcessIdentifer = getProcessCountsByProcessIdentifer();
            
            // get the stats in graphite format
            List<GraphiteMetric> graphiteMetrics = createGraphiteMetrics(processCountsByProcessIdentifer, currentTimestampInSeconds);

            // output graphite metrics
            super.outputMetrics(graphiteMetrics, true);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-ProcessCounter metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private void createPatternsFromRegexStrings() {

        if ((processCounterPrefixesAndRegexes_ == null) || processCounterPrefixesAndRegexes_.isEmpty()) {
            return;
        }
        
        for (String[] processCounterPrefixAndRegex : processCounterPrefixesAndRegexes_) {
            try {
                Pattern pattern = Pattern.compile(processCounterPrefixAndRegex[1]);
                regexPatterns_.put(processCounterPrefixAndRegex[1], pattern);
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
    }
    
    private List<String[]> createProcessCounterPrefixesAndRegexes_Trimmed() {
        
        if ((processCounterPrefixesAndRegexes_ == null) || processCounterPrefixesAndRegexes_.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String[]> processCounterPrefixesAndRegexes_Trimmed = new ArrayList<>();
        
        for (String[] processCounterPrefixAndRegex : processCounterPrefixesAndRegexes_) {
            if ((processCounterPrefixAndRegex[0] == null) || processCounterPrefixAndRegex[0].isEmpty()) continue;
            
            String trimmedProcessIdentifier = processCounterPrefixAndRegex[0].trim();
            if (trimmedProcessIdentifier.isEmpty()) continue;
            
            String[] processCounterPrefixAndRegex_Trimmed = new String[2];
            processCounterPrefixAndRegex_Trimmed[0] = trimmedProcessIdentifier;
            processCounterPrefixAndRegex_Trimmed[1] = processCounterPrefixAndRegex[1];
            processCounterPrefixesAndRegexes_Trimmed.add(processCounterPrefixAndRegex_Trimmed);
        }
        
        return processCounterPrefixesAndRegexes_Trimmed;
    }
    
    private Map<String,AtomicLong> getProcessCountsByProcessIdentifer() {
        
        if ((processCounterPrefixesAndRegexes_Trimmed_ == null) || processCounterPrefixesAndRegexes_Trimmed_.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String,AtomicLong> processCountsByProcessIdentifer = new HashMap<>();
        
        try {
            for (String[] processCounterPrefixAndRegex : processCounterPrefixesAndRegexes_Trimmed_) {
                processCountsByProcessIdentifer.put(processCounterPrefixAndRegex[0], new AtomicLong(0));
            }

            List<String> directories = FileIo.getListOfDirectoryNamesInADirectory(super.getLinuxProcFileSystemLocation());
            if (directories == null) return processCountsByProcessIdentifer;

            for (String directory : directories) {
                if (directory.isEmpty() || !StringUtils.isNumeric(directory)) continue;
                
                String pidCmdLine = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/" + directory + "/cmdline");
                if ((pidCmdLine == null) || pidCmdLine.isEmpty()) continue;
                
                String pidCmdLine_SpaceDelimiters = pidCmdLine.replace('\0', ' ').trim();
                if (pidCmdLine_SpaceDelimiters.isEmpty()) continue;
                
                for (String[] processCounterPrefixAndRegex : processCounterPrefixesAndRegexes_Trimmed_) {
                    try {
                        Pattern pattern = regexPatterns_.get(processCounterPrefixAndRegex[1]);
                        if (pattern == null) continue;
                        
                        boolean isMatch = pattern.matcher(pidCmdLine_SpaceDelimiters).find();
                        if (isMatch) {
                            AtomicLong processCount = processCountsByProcessIdentifer.get(processCounterPrefixAndRegex[0]);
                            if (processCount != null) processCount.getAndIncrement();
                        }
                    }
                    catch (Exception e) {
                        logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return processCountsByProcessIdentifer;
    }
    
    private List<GraphiteMetric> createGraphiteMetrics(Map<String,AtomicLong> processCountsByProcessIdentifer, int currentTimestampInSeconds) {
        
        if ((processCountsByProcessIdentifer == null) || processCountsByProcessIdentifer.isEmpty()) {
            return new ArrayList();
        }
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList();
        
        try {
            for (String processIdentifier : processCountsByProcessIdentifer.keySet()) {
                AtomicLong processCount = processCountsByProcessIdentifer.get(processIdentifier);
                if ((processCount == null) || (processCount.get() < 0)) continue;
                
                String graphiteFriendlyProcessIdentifier = GraphiteMetric.getGraphiteSanitizedString(processIdentifier, true, true);
                GraphiteMetric graphiteMetric = new GraphiteMetric(graphiteFriendlyProcessIdentifier, new BigDecimal(processCount.get()), currentTimestampInSeconds);
                graphiteMetrics.add(graphiteMetric);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
}
