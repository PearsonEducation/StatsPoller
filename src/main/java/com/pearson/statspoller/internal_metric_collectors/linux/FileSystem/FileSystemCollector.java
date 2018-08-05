package com.pearson.statspoller.internal_metric_collectors.linux.FileSystem;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import com.pearson.statspoller.utilities.os_utils.ProcessUtils;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Collects file system metrics, such as disk-space-usage & inode-usage
 * 
 * This collector is based (and depends on) the 'df' program (part of gnu coreutils). 
 * This is not ideal as it creates a dependency on 'df', but it was necessary due to the complexity of acquiring the raw data.
 * 
 * In the event that this metric collector fails ('df' not available, 'df' output format is not supported, etc), then
 * the "StatsPoller Native" metric collector can also be used to acquire disk-space-usage metrics.
 */
public class FileSystemCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(FileSystemCollector.class.getName());
    
    public FileSystemCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the disk space stats in graphite format
            List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
            graphiteMetrics.addAll(getDiskSpaceMetrics());
            graphiteMetrics.addAll(getDiskInodeMetrics());
            
            // output graphite metrics
            super.outputGraphiteMetrics(graphiteMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-FileSystem metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }

    private List<GraphiteMetric> getDiskSpaceMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
        
        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            String dfOutput_DiskSpace = ProcessUtils.runProcessAndGetProcessOutput("df -P");
            allGraphiteMetrics = getMetricsFromDfOutput(dfOutput_DiskSpace, currentTimestampInSeconds);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private List<GraphiteMetric> getDiskInodeMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
        
        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            String dfOutput_Inodes = ProcessUtils.runProcessAndGetProcessOutput("df -P -i");
            allGraphiteMetrics = getMetricsFromDfOutput(dfOutput_Inodes, currentTimestampInSeconds);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    protected List<GraphiteMetric> getMetricsFromDfOutput(String dfOutput, int timestampInSeconds) {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();

        try {
            // find the columns that separate the fields. if every character in the column is a space character, then it is a field delimiter column
            HashMap<Integer,Set<Integer>> positionsOfSpaceCharacters_ByLine = findPositionsOfSpaceCharacters_ByLine(dfOutput);
            Set<Integer> positionsOfSpaceCharacters_SameColumnInAllLines = findPositionsOfSpaceCharacters_SameColumnInAllLines(positionsOfSpaceCharacters_ByLine);
            List<Integer> sortedPositionsOfSpaceCharacters_SameColumnInAllLines = new ArrayList<>(positionsOfSpaceCharacters_SameColumnInAllLines);
            Collections.sort(sortedPositionsOfSpaceCharacters_SameColumnInAllLines);

            // with the knowledge of the left & right boundary of each field, scan the df output for the values of each field
            List<Df_Fields> allDfFields = getDfFieldsForAllDevices(dfOutput, sortedPositionsOfSpaceCharacters_SameColumnInAllLines);
            if (allDfFields == null) return allGraphiteMetrics;
            
            // create graphite metrics
            for (Df_Fields dfFields : allDfFields) {
                if ((dfFields.getMountedOn() == null) || dfFields.getMountedOn().isEmpty()) continue;

                String graphiteFriendlyFileSystem = GraphiteMetric.getGraphiteSanitizedString(dfFields.getFileSystem(), true, true);
                if (graphiteFriendlyFileSystem != null) graphiteFriendlyFileSystem = graphiteFriendlyFileSystem.replace('.', '_');
                String graphiteFriendlyMountedOn = GraphiteMetric.getGraphiteSanitizedString(dfFields.getMountedOn(), true, true);
                if (graphiteFriendlyMountedOn != null) graphiteFriendlyMountedOn = graphiteFriendlyMountedOn.replace('.', '_');
                
                if (graphiteFriendlyFileSystem != null) allGraphiteMetrics.add(new GraphiteMetric(graphiteFriendlyMountedOn + ".FileSystem=" + graphiteFriendlyFileSystem, BigDecimal.ONE, timestampInSeconds));

                String mountNameDiskSpace = graphiteFriendlyMountedOn + ".DiskSpace-";
                if ((dfFields.getDiskSpaceTotalBytes() != null) && (dfFields.getDiskSpaceTotalBytes().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Total-Bytes", dfFields.getDiskSpaceTotalBytes(), timestampInSeconds));
                if ((dfFields.getDiskSpaceTotalGigabytes() != null) && (dfFields.getDiskSpaceTotalGigabytes().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Total-GB", dfFields.getDiskSpaceTotalGigabytes(), timestampInSeconds));
                if ((dfFields.getDiskSpaceFreeBytes() != null) && (dfFields.getDiskSpaceFreeBytes().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Free-Bytes", dfFields.getDiskSpaceFreeBytes(), timestampInSeconds));
                if ((dfFields.getDiskSpaceFreeGigabytes() != null) && (dfFields.getDiskSpaceFreeGigabytes().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Free-GB", dfFields.getDiskSpaceFreeGigabytes(), timestampInSeconds));
                if ((dfFields.getDiskSpaceUsedBytes() != null) && (dfFields.getDiskSpaceUsedBytes().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Used-Bytes", dfFields.getDiskSpaceUsedBytes(), timestampInSeconds));
                if ((dfFields.getDiskSpaceUsedGigabytes() != null) && (dfFields.getDiskSpaceUsedGigabytes().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Used-GB", dfFields.getDiskSpaceUsedGigabytes(), timestampInSeconds));
                if ((dfFields.getDiskSpaceUsedPercent() != null) && (dfFields.getDiskSpaceUsedPercent().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameDiskSpace + "Used-Pct", dfFields.getDiskSpaceUsedPercent(), timestampInSeconds));

                String mountNameInode = graphiteFriendlyMountedOn + ".Inodes-";
                if ((dfFields.getInodesTotal() != null) && (dfFields.getInodesTotal().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameInode + "Total", dfFields.getInodesTotal(), timestampInSeconds));
                if ((dfFields.getInodesFree() != null) && (dfFields.getInodesFree().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameInode + "Free", dfFields.getInodesFree(), timestampInSeconds));
                if ((dfFields.getInodesUsed() != null) && (dfFields.getInodesUsed().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameInode + "Used", dfFields.getInodesUsed(), timestampInSeconds));
                if ((dfFields.getInodesUsedPercent() != null) && (dfFields.getInodesUsedPercent().compareTo(BigDecimal.ZERO) != -1)) allGraphiteMetrics.add(new GraphiteMetric(mountNameInode + "Used-Pct", dfFields.getInodesUsedPercent(), timestampInSeconds));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private HashMap<Integer,Set<Integer>> findPositionsOfSpaceCharacters_ByLine(String dfOutput) {
        
        if ((dfOutput == null) || dfOutput.isEmpty()) {
            return new HashMap<>(); 
        }
        
        HashMap<Integer,Set<Integer>> positionsOfSpaceCharacters_ByLine = new HashMap<>();
        
        BufferedReader reader = null;
        
        try {
            int currentLineNumber = 0;
            reader = new BufferedReader(new StringReader(dfOutput));
            String currentLine = reader.readLine();
            
            while (currentLine != null) {
                Set<Integer> positionsOfSpaceCharacters = new HashSet<>();
                        
                for (int i = 0; i < currentLine.length(); i++) {
                    int currentCharacter = currentLine.charAt(i);
                    if (currentCharacter == ' ') positionsOfSpaceCharacters.add(i);
                }

                positionsOfSpaceCharacters_ByLine.put(currentLineNumber, positionsOfSpaceCharacters);
                currentLineNumber++;
                
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
        
        return positionsOfSpaceCharacters_ByLine;
    }

    private Set<Integer> findPositionsOfSpaceCharacters_SameColumnInAllLines(HashMap<Integer,Set<Integer>> positionsOfSpaceCharacters_ByLine) {
        
        if (positionsOfSpaceCharacters_ByLine == null) {
            return new HashSet<>();
        }
        
        Set<Integer> positionsOfSpaceCharacters_SameColumnInAllLines = new HashSet<>();
        
        try {
            Integer firstLine_IndexOfLastSpace = null;

            for (Integer lineNumber : positionsOfSpaceCharacters_ByLine.keySet()) {
                Set<Integer> positionsOfSpaceCharacters_SingleLine = positionsOfSpaceCharacters_ByLine.get(lineNumber);
                if (positionsOfSpaceCharacters_SingleLine == null) continue;

                if (lineNumber == 0) {
                    firstLine_IndexOfLastSpace = getLargestValue(positionsOfSpaceCharacters_SingleLine);
                    positionsOfSpaceCharacters_SameColumnInAllLines.addAll(positionsOfSpaceCharacters_SingleLine);
                    continue;
                }

                Integer columnWidth = getLargestValue(positionsOfSpaceCharacters_SingleLine);
                if ((columnWidth == null) || (columnWidth < 0)) continue;

                Integer highestPossibleIndexOfSpace = ((firstLine_IndexOfLastSpace != null) && (firstLine_IndexOfLastSpace > columnWidth)) ? (firstLine_IndexOfLastSpace + 1) : (columnWidth + 1);

                for (int i = 0; i < highestPossibleIndexOfSpace; i++) {
                    if (!positionsOfSpaceCharacters_SingleLine.contains(i)) {
                        positionsOfSpaceCharacters_SameColumnInAllLines.remove(i);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return positionsOfSpaceCharacters_SameColumnInAllLines;
    }
    
    private Integer getLargestValue(Set<Integer> numbers) {
        
        if ((numbers == null) || numbers.isEmpty()) {
            return null;
        }
        
        Integer maxValue = null;
        
        for (Integer number : numbers) {
            if (maxValue == null) maxValue = number;
            else if ((number != null) && (maxValue < number)) maxValue = number;
        }
        
        return maxValue;
    }
    
    private List<Df_Fields> getDfFieldsForAllDevices(String dfOutput, List<Integer> sortedPositionsOfSpaceCharacters_SameColumnInAllLines) {
        
        if ((dfOutput == null) || dfOutput.isEmpty()) {
            return new ArrayList<>();
        }
             
        List<Df_Fields> allDfFields = new ArrayList<>();
        BufferedReader reader = null;
        
        try {
            Map<Integer,String> fieldNames_ByPosition = new HashMap<>();
            int currentLineNumber = 0;
            reader = new BufferedReader(new StringReader(dfOutput));
            String currentLine = reader.readLine();
            
            while (currentLine != null) {
                Df_Fields dfFields = new Df_Fields();
                
                try {
                    for (int currentFieldPosition = 0; currentFieldPosition < sortedPositionsOfSpaceCharacters_SameColumnInAllLines.size(); currentFieldPosition++) {
                        Integer indexOfCurrentSpace = sortedPositionsOfSpaceCharacters_SameColumnInAllLines.get(currentFieldPosition);
                        String fieldValue, finalFieldValue = null;

                        if (currentFieldPosition == 0) {
                            fieldValue = currentLine.substring(0, indexOfCurrentSpace).trim();
                        }
                        else {
                            Integer indexOfPreviousSpace = sortedPositionsOfSpaceCharacters_SameColumnInAllLines.get(currentFieldPosition - 1);
                            fieldValue = currentLine.substring(indexOfPreviousSpace, indexOfCurrentSpace).trim();
                        }

                        if ((currentFieldPosition + 1) == sortedPositionsOfSpaceCharacters_SameColumnInAllLines.size()) {
                            finalFieldValue = currentLine.substring(indexOfCurrentSpace).trim();
                        }
                        
                        if (currentLineNumber == 0) {
                            if (fieldValue != null) fieldNames_ByPosition.put(currentFieldPosition, fieldValue);
                            if (finalFieldValue != null) fieldNames_ByPosition.put((currentFieldPosition + 1), finalFieldValue);
                        }
                        else {
                            HashMap<String,String> fieldsValues_ByFieldName = new HashMap<>();
                            String currentFieldName = fieldNames_ByPosition.get(currentFieldPosition);
                            String finalFieldName = fieldNames_ByPosition.get(currentFieldPosition + 1);
                            if ((currentFieldName != null) && (fieldValue != null)) fieldsValues_ByFieldName.put(currentFieldName, fieldValue);
                            if ((finalFieldName != null) && (finalFieldValue != null)) fieldsValues_ByFieldName.put(finalFieldName, finalFieldValue);
                            
                            for (String fieldName : fieldsValues_ByFieldName.keySet()) {
                                fieldValue = fieldsValues_ByFieldName.get(fieldName);
                                if (fieldName.equals("Filesystem")) dfFields.setFileSystem(fieldValue);
                                else if (fieldName.equals("Type")) dfFields.setType(fieldValue);
                                else if (fieldName.equals("1024-blocks")) dfFields.setNum1024kBlocks(fieldValue);
                                else if (fieldName.equals("1K-blocks")) dfFields.setNum1024kBlocks(fieldValue);
                                else if (fieldName.equals("Used")) dfFields.setNumUsedBlocks(fieldValue);
                                else if (fieldName.equals("Available")) dfFields.setNumAvailableBlocks(fieldValue);
                                else if (fieldName.equals("Avail")) dfFields.setNumAvailableBlocks(fieldValue);
                                else if (fieldName.equals("Capacity")) dfFields.setBlocksUsedPercent(fieldValue);
                                else if (fieldName.equals("Use%")) dfFields.setBlocksUsedPercent(fieldValue);
                                else if (fieldName.equals("Inodes")) dfFields.setNumInodes(fieldValue);
                                else if (fieldName.equals("IUsed")) dfFields.setNumInodesUsed(fieldValue);
                                else if (fieldName.equals("IFree")) dfFields.setNumInodesFree(fieldValue);
                                else if (fieldName.equals("IUse%")) dfFields.setInodesUsedPercent(fieldValue);
                                else if (fieldName.equals("Mounted on")) dfFields.setMountedOn(fieldValue);
                            }
                        }
                    }
                }
                catch (Exception e) {
                    logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                }

                allDfFields.add(dfFields);
                currentLineNumber++;
                
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
        
        return allDfFields;
    }

}
