package com.pearson.statspoller.internal_metric_collectors.linux.ProcessStatus;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Collects aggregated process status metrics
 * 
 * Based on raw data from /proc/(pid)/status
 * http://man7.org/linux/man-pages/man5/proc.5.html
 */
public class ProcessStatusCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessStatusCollector.class.getName());
    
    private static final HashSet<String> CORE_STATES = getSetOfCoreProcessStates();
    
    public ProcessStatusCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the update stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getProcessStatusMetrics();

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-ProcessState metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private List<GraphiteMetric> getProcessStatusMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
        
        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            List<String> listOfDirectoriesInProc = FileIo.getListOfDirectoryNamesInADirectory(super.getLinuxProcFileSystemLocation());
            List<String> listOfProcessDirectoriesInProc = new ArrayList<>();
            
            for (String directoryInProc : listOfDirectoriesInProc) {
                if ((directoryInProc == null) || directoryInProc.isEmpty()) continue;
                
                boolean isDirectoryNumeric = StringUtils.isNumeric(directoryInProc);
                boolean doesStatusExist = FileIo.doesFileExist(super.getLinuxProcFileSystemLocation() + "/" + directoryInProc + "/status" );
                if (isDirectoryNumeric && doesStatusExist) listOfProcessDirectoriesInProc.add(directoryInProc);
            }

            Map<String,String> stateOfEveryPid = new HashMap<>(); // k=pid#, v=status
            AtomicInteger countOfThreads = new AtomicInteger(0);
            getProcessStatusMetrics(listOfProcessDirectoriesInProc, stateOfEveryPid, countOfThreads);
            
            allGraphiteMetrics.addAll(createMetrics(listOfProcessDirectoriesInProc, stateOfEveryPid, countOfThreads, currentTimestampInSeconds));
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    
    private void getProcessStatusMetrics(List<String> listOfProcessDirectoriesInProc, Map<String,String> stateOfEveryPid, AtomicInteger countOfThreads) {
        
        if ((listOfProcessDirectoriesInProc == null) || (stateOfEveryPid == null) || (countOfThreads == null)) {
            return;
        }

        BufferedReader reader = null;

        for (String pid : listOfProcessDirectoriesInProc) {
            try {
                String rawPidStatus = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/" + pid + "/status");
                if ((rawPidStatus == null) || rawPidStatus.isEmpty()) continue;

                reader = new BufferedReader(new StringReader(rawPidStatus));
                String currentLine = reader.readLine();

                while (currentLine != null) {
                    String currentLineTrimmed = currentLine.trim();
                    
                    // get the process state
                    if (currentLineTrimmed.startsWith("State:")) {
                        int leftBoundary = currentLineTrimmed.indexOf(':') + 1;
                        int rightBoundary = currentLineTrimmed.indexOf('(');
                        if ((leftBoundary > 0) && (rightBoundary > 0)) stateOfEveryPid.put(pid, currentLineTrimmed.substring(leftBoundary, rightBoundary).trim());
                    }
                    
                    // get number of threads
                    if (currentLineTrimmed.startsWith("Threads:")) {
                        int leftBoundary = currentLineTrimmed.indexOf(':') + 1;
                        if (leftBoundary > 0) {
                            String threadsString = currentLineTrimmed.substring(leftBoundary, currentLineTrimmed.length()).trim();
                            int threadsInt = Integer.parseInt(threadsString);
                            countOfThreads.addAndGet(threadsInt);
                        }
                    }
                    
                    currentLine = reader.readLine();
                } 
            }
            catch (Exception e) {
                logger.debug(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
            finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                }
                catch (Exception e) {
                    logger.debug(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                }
            }
  
        }
    }
    
    private List<GraphiteMetric> createMetrics(List<String> listOfProcessDirectoriesInProc, Map<String,String> stateOfEveryPid, AtomicInteger countOfThreads, int currentTimestampInSeconds) {
        
        if ((listOfProcessDirectoriesInProc == null) || (listOfProcessDirectoriesInProc.size() <= 0) || (stateOfEveryPid == null) || (countOfThreads == null)) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        try {
            // create process state metrics
            HashMap<String,AtomicInteger> countOfStates = new HashMap<>();
            for (String pid : stateOfEveryPid.keySet()) {
                String state = stateOfEveryPid.get(pid);
                if (countOfStates.containsKey(state)) countOfStates.get(state).incrementAndGet();
                else countOfStates.put(state.trim(), new AtomicInteger(1));
            }

            for (String coreState : CORE_STATES) {
                if (!countOfStates.containsKey(coreState)) {
                    countOfStates.put(coreState.trim(), new AtomicInteger(0));
                }
            }
            
            for (String state : countOfStates.keySet()) {
                AtomicInteger stateCount = countOfStates.get(state);
                graphiteMetrics.add(new GraphiteMetric(("States.CountOfProcessesInState-" + state), new BigDecimal(stateCount.get()), currentTimestampInSeconds));
            }
            
            // create process other process status metrics
            graphiteMetrics.add(new GraphiteMetric(("TotalProcessCount"), new BigDecimal(listOfProcessDirectoriesInProc.size()), currentTimestampInSeconds));
            graphiteMetrics.add(new GraphiteMetric(("TotalThreadCount"), new BigDecimal(countOfThreads.get()), currentTimestampInSeconds));
        }
        catch (Exception e) {
            logger.debug(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private static HashSet<String> getSetOfCoreProcessStates() {
        HashSet<String> coreProcessStates = new HashSet<>();
        coreProcessStates.add("R");
        coreProcessStates.add("S");
        coreProcessStates.add("D");
        coreProcessStates.add("Z");
        coreProcessStates.add("T");
        return coreProcessStates;
    }

}
