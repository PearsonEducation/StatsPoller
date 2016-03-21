package com.pearson.statspoller.internal_metric_collectors.linux.Memory;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Reads /proc/meminfo
 * Outputs the raw values from /proc/meminfo, as well as some metrics derived from /proc/meminfo
 */
public class MemoryCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryCollector.class.getName());
    
    private static final BigDecimal KILOBYTES_TO_BYTES_MULTIPLIER = new BigDecimal("1024");
    private static final BigDecimal BYTES_TO_MEGABYTES_DIVISOR = new BigDecimal("1048576");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    public MemoryCollector(boolean isEnabled, long collectionInterval, String metricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // get the memory stats in graphite format
            List<GraphiteMetric> graphiteMetrics = getMemoryMetrics();
            
            // output graphite metrics
            super.outputMetrics(graphiteMetrics, true);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished Linux-Memory metric collection routine. " +
                    "MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    private List<GraphiteMetric> getMemoryMetrics() {
        
        List<GraphiteMetric> allGraphiteMetrics = new ArrayList<>();
         
        try {
            int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);
            String rawProcMeminfo = FileIo.readFileToString(super.getLinuxProcFileSystemLocation() + "/meminfo");
            if ((rawProcMeminfo == null) || rawProcMeminfo.isEmpty()) return allGraphiteMetrics;
            
            Map<String,BigDecimal> memMetrics_ByMemFieldName = parseRawMeminfo(rawProcMeminfo);
            if (memMetrics_ByMemFieldName == null) return allGraphiteMetrics;
            
            for (String memFieldName : memMetrics_ByMemFieldName.keySet()) {
                BigDecimal memFieldValue_Bytes = memMetrics_ByMemFieldName.get(memFieldName);
                if (memFieldValue_Bytes.compareTo(BigDecimal.ZERO) != -1) allGraphiteMetrics.add(new GraphiteMetric("Raw." + memFieldName + "-Bytes", memFieldValue_Bytes, currentTimestampInSeconds));
            }
            
            Map<String,BigDecimal> derivedMemMetrics_ByMemFieldName = getDerivedMemoryMetrics(memMetrics_ByMemFieldName);
            if (derivedMemMetrics_ByMemFieldName == null) return allGraphiteMetrics;

            for (String memFieldName : derivedMemMetrics_ByMemFieldName.keySet()) {
                BigDecimal memFieldValue_Bytes = derivedMemMetrics_ByMemFieldName.get(memFieldName);
                if (memFieldValue_Bytes.compareTo(BigDecimal.ZERO) != -1) allGraphiteMetrics.add(new GraphiteMetric("Derived." + memFieldName, memFieldValue_Bytes, currentTimestampInSeconds));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return allGraphiteMetrics;
    }
    
    private Map<String,BigDecimal> parseRawMeminfo(String rawProcMeminfo) {
        
        if (rawProcMeminfo == null) {
            return new HashMap<>();
        }
        
        Map<String,BigDecimal> memMetrics_ByMemFieldName = new HashMap<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new StringReader(rawProcMeminfo));
            String currentLine = reader.readLine();
            
            while (currentLine != null) {
                try {
                    String trimmedCurrentLine = currentLine.trim();
                    int indexOfFirstColon = trimmedCurrentLine.indexOf(':');

                    if (indexOfFirstColon > 0) {
                        String memFieldName = trimmedCurrentLine.substring(0, indexOfFirstColon).trim();
                        String[] rawMemInfoStats_SpaceDelimited = trimmedCurrentLine.split("\\s+");
                        String memFieldValue = rawMemInfoStats_SpaceDelimited[1].trim();
                        BigDecimal memFieldValue_Bytes = new BigDecimal(memFieldValue).multiply(KILOBYTES_TO_BYTES_MULTIPLIER);
                        memMetrics_ByMemFieldName.put(memFieldName, memFieldValue_Bytes);
                    }
                }
                catch (Exception e) {
                    logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
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
        
        return memMetrics_ByMemFieldName;
    }

    private Map<String,BigDecimal> getDerivedMemoryMetrics(Map<String,BigDecimal> memMetrics_ByMemFieldName) {
        
        if (memMetrics_ByMemFieldName == null) {
            return new HashMap<>();
        }
        
        Map<String,BigDecimal> derivedMemMetrics_ByMemFieldName = new HashMap<>();
        
        try {
            List<BigDecimal> freeableMemoryFields = new ArrayList<>();
            
            BigDecimal totalMemory = memMetrics_ByMemFieldName.get("MemTotal");
            if (totalMemory == null) return derivedMemMetrics_ByMemFieldName;
            
            BigDecimal swapCached = memMetrics_ByMemFieldName.get("SwapCached");
            if (swapCached != null) freeableMemoryFields.add(swapCached);
            
            BigDecimal cached = memMetrics_ByMemFieldName.get("Cached");
            if (cached != null) freeableMemoryFields.add(cached);
            
            BigDecimal buffers = memMetrics_ByMemFieldName.get("Buffers");
            if (buffers != null) freeableMemoryFields.add(buffers);  
            
            BigDecimal memFree = memMetrics_ByMemFieldName.get("MemFree");
            if (memFree != null) freeableMemoryFields.add(memFree);  
            
            BigDecimal sReclaimable = memMetrics_ByMemFieldName.get("SReclaimable");
            if (sReclaimable != null) freeableMemoryFields.add(sReclaimable);  
            
            BigDecimal sumFreeableMemoryFields = BigDecimal.ZERO;
            for (BigDecimal freeableMemoryField : freeableMemoryFields) sumFreeableMemoryFields = sumFreeableMemoryFields.add(freeableMemoryField);
            
            BigDecimal totalMemoryBytes = totalMemory;
            BigDecimal freeMemoryBytes = sumFreeableMemoryFields;
            BigDecimal usedMemoryBytes = totalMemory.subtract(sumFreeableMemoryFields);
            BigDecimal totalMemoryMegabytes = totalMemoryBytes.divide(BYTES_TO_MEGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
            BigDecimal freeMemoryMegabytes = sumFreeableMemoryFields.divide(BYTES_TO_MEGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
            BigDecimal usedMemoryMegabytes = usedMemoryBytes.divide(BYTES_TO_MEGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
            BigDecimal usedMemoryPercent = (totalMemoryBytes.compareTo(BigDecimal.ZERO) == 1) ? ONE_HUNDRED.subtract(freeMemoryBytes.divide(totalMemoryBytes, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED)) : null;
            
            derivedMemMetrics_ByMemFieldName.put("Total-Bytes", totalMemoryBytes);
            derivedMemMetrics_ByMemFieldName.put("Free-Bytes", freeMemoryBytes);
            derivedMemMetrics_ByMemFieldName.put("Used-Bytes", usedMemoryBytes);
            derivedMemMetrics_ByMemFieldName.put("Total-Megabytes", totalMemoryMegabytes);
            derivedMemMetrics_ByMemFieldName.put("Free-Megabytes", freeMemoryMegabytes);
            derivedMemMetrics_ByMemFieldName.put("Used-Megabytes", usedMemoryMegabytes);
            if (usedMemoryPercent != null) derivedMemMetrics_ByMemFieldName.put("Used-Pct", usedMemoryPercent);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return derivedMemMetrics_ByMemFieldName;
    }
    
}
