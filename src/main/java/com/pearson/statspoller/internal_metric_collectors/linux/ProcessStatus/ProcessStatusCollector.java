package com.pearson.statspoller.internal_metric_collectors.linux.ProcessStatus;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class ProcessStatusCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessStatusCollector.class.getName());
    
    public ProcessStatusCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the update stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getZombieProcessMetrics();

            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-ZombieProcess metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private List<GraphiteMetric> getZombieProcessMetrics() {
        
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

        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    
    private Map<String,String> getStateOfEveryPid(List<String> listOfProcessDirectoriesInProc) {
        
        // k=pid#, v=status
        Map<String,String> stateByPid= new HashMap<>();
        BufferedReader reader = null;

        for (String pid : listOfProcessDirectoriesInProc) {
            try {
                String rawPidStatus = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/" + pid + "/status");
                if ((rawPidStatus == null) || rawPidStatus.isEmpty()) continue;

                reader = new BufferedReader(new StringReader(rawPidStatus));
                String currentLine = reader.readLine();

                while (currentLine != null) {
                    String currentLineTrimmed = currentLine.trim();
                    if (currentLineTrimmed.startsWith("State:")) {
                        currentLineTrimmed.indexOf('(');
                        
                    }
                    
                    currentLine = reader.readLine();
                } 
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
            finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                }
                catch (Exception e) {
                    logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                }
            }
  
        }
        
        return stateByPid;
    }
    
}
