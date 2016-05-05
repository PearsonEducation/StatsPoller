package com.pearson.statspoller.internal_metric_collectors.linux.DiskIo;

import com.pearson.statspoller.utilities.MathUtilities;
import com.pearson.statspoller.utilities.StackTrace;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * Field 1 -- # of reads issued
 * Field 2 -- # of reads merged
 * Field 3 -- # of sectors read
 * Field 4 -- # of milliseconds spent reading
 * Field 5 -- # of writes completed
 * Field 6 -- # of writes merged
 * Field 7 -- # of sectors written
 * Field 8 -- # of milliseconds spent writing
 * Field 9 -- # of I/Os currently in progress
 * Field 10 -- # of milliseconds spent doing I/Os
 * Field 11 -- weighted # of milliseconds spent doing I/Os 
 * 
 * http://users.sosdg.org/~qiyong/lxr/diff/Documentation/block/stat.txt?diffvar=a;diffval=um
 */
public class DiskIoStat_Delta {
    
    private static final Logger logger = LoggerFactory.getLogger(DiskIoStat_Delta.class.getName());

    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal FIVE_HUNDRED_TWELVE = new BigDecimal("512");
    private static final BigDecimal BYTES_TO_MEGABYTES_DIVISOR = new BigDecimal("1048576");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    private final BigDecimal millisecondsBetweenSamples_;
    private final String deviceName_;
    private final BigDecimal numReadsCompleted_;
    private final BigDecimal numReadsMerged_;
    private final BigDecimal numSectorsRead_;
    private final BigDecimal numMillisecondsSpentReading_;
    private final BigDecimal numWritesCompleted_;
    private final BigDecimal numWritesMerged_;
    private final BigDecimal numSectorsWritten_;
    private final BigDecimal numMillisecondsSpentWriting_;
    private final BigDecimal numIosCurrentInProgress_;
    private final BigDecimal numMillisecondsSpentDoingIo_;
    private final BigDecimal weightedNumMillisecondsSpentDoingIo_;

    private BigDecimal readRequestsPerSecond_;
    private BigDecimal readBytesPerSecond_;
    private BigDecimal readMegabytesPerSecond_;
    private BigDecimal readRequestAverageTimeInMilliseconds_;
    private BigDecimal writeRequestsPerSecond_;
    private BigDecimal writeBytesPerSecond_;
    private BigDecimal writeMegabytesPerSecond_;
    private BigDecimal writeRequestAverageTimeInMilliseconds_;
    private BigDecimal averageRequestTimeInMilliseconds_;
    private BigDecimal averageQueueLength_;
    
    public DiskIoStat_Delta(long millisecondsBetweenSamples, String deviceName,
            String numReadsCompleted, String numReadsMerged, String numSectorsRead, String numMillisecondsSpentReading,
            String numWritesCompleted, String numWritesMerged, String numSectorsWritten, String numMillisecondsSpentWriting,
            String numIosCurrentInProgress, String numMillisecondsSpentDoingIo, String weightedNumMillisecondsSpentDoingIo) {

        this.millisecondsBetweenSamples_ = new BigDecimal(millisecondsBetweenSamples);
        
        this.deviceName_ = deviceName;
        this.numReadsCompleted_ = MathUtilities.safeGetBigDecimal(numReadsCompleted);
        this.numReadsMerged_ = MathUtilities.safeGetBigDecimal(numReadsMerged);
        this.numSectorsRead_ = MathUtilities.safeGetBigDecimal(numSectorsRead);
        this.numMillisecondsSpentReading_ = MathUtilities.safeGetBigDecimal(numMillisecondsSpentReading);
        this.numWritesCompleted_ = MathUtilities.safeGetBigDecimal(numWritesCompleted);
        this.numWritesMerged_ = MathUtilities.safeGetBigDecimal(numWritesMerged);
        this.numSectorsWritten_ = MathUtilities.safeGetBigDecimal(numSectorsWritten);
        this.numMillisecondsSpentWriting_ = MathUtilities.safeGetBigDecimal(numMillisecondsSpentWriting);
        this.numIosCurrentInProgress_ = MathUtilities.safeGetBigDecimal(numIosCurrentInProgress);
        this.numMillisecondsSpentDoingIo_ = MathUtilities.safeGetBigDecimal(numMillisecondsSpentDoingIo);
        this.weightedNumMillisecondsSpentDoingIo_ = MathUtilities.safeGetBigDecimal(numMillisecondsSpentDoingIo);
    }

    public String getDeviceName() {
        return deviceName_;
    }
    
    public BigDecimal getReadRequestsPerSecond() {
        if (readRequestsPerSecond_ != null) return readRequestsPerSecond_;
        
        try {
            if (millisecondsBetweenSamples_.compareTo(BigDecimal.ZERO) <= 0) readRequestsPerSecond_ = BigDecimal.ZERO;
            else readRequestsPerSecond_ = numReadsCompleted_.divide(millisecondsBetweenSamples_, SCALE, ROUNDING_MODE).multiply(ONE_THOUSAND);
            return readRequestsPerSecond_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getBytesReadPerSecond() {
        if (readBytesPerSecond_ != null) return readBytesPerSecond_;
        
        try {
            if (millisecondsBetweenSamples_.compareTo(BigDecimal.ZERO) <= 0) readBytesPerSecond_ = BigDecimal.ZERO;
            else readBytesPerSecond_ = numSectorsRead_.divide(millisecondsBetweenSamples_, SCALE, ROUNDING_MODE).multiply(ONE_THOUSAND).multiply(FIVE_HUNDRED_TWELVE);
            return readBytesPerSecond_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getMegabytesReadPerSecond() {
        if (readMegabytesPerSecond_ != null) return readMegabytesPerSecond_;
        
        try {
            readMegabytesPerSecond_ = getBytesReadPerSecond().divide(BYTES_TO_MEGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
            return readMegabytesPerSecond_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getReadRequestAverageTimeInMilliseconds() {
        if (readRequestAverageTimeInMilliseconds_ != null) return readRequestAverageTimeInMilliseconds_;
        
        try {
            if (numReadsCompleted_.compareTo(BigDecimal.ZERO) <= 0) readRequestAverageTimeInMilliseconds_ = BigDecimal.ZERO;
            else readRequestAverageTimeInMilliseconds_ = numMillisecondsSpentReading_.divide(numReadsCompleted_, SCALE, ROUNDING_MODE);
            return readRequestAverageTimeInMilliseconds_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getWriteRequestsPerSecond() {
        if (writeRequestsPerSecond_ != null) return writeRequestsPerSecond_;
        
        try {
            writeRequestsPerSecond_ = numWritesCompleted_.divide(millisecondsBetweenSamples_, SCALE, ROUNDING_MODE).multiply(ONE_THOUSAND);
            return writeRequestsPerSecond_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getBytesWrittenPerSecond() {
        if (writeBytesPerSecond_ != null) return writeBytesPerSecond_;
        
        try {
            if (millisecondsBetweenSamples_.compareTo(BigDecimal.ZERO) <= 0) writeBytesPerSecond_ = BigDecimal.ZERO;
            else writeBytesPerSecond_ = numSectorsWritten_.divide(millisecondsBetweenSamples_, SCALE, ROUNDING_MODE).multiply(ONE_THOUSAND).multiply(FIVE_HUNDRED_TWELVE);
            return writeBytesPerSecond_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getMegabytesWrittenPerSecond() {
        if (writeMegabytesPerSecond_ != null) return writeMegabytesPerSecond_;
        
        try {
            writeMegabytesPerSecond_ = getBytesWrittenPerSecond().divide(BYTES_TO_MEGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
            return writeMegabytesPerSecond_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getWriteRequestAverageTimeInMilliseconds() {
        if (writeRequestAverageTimeInMilliseconds_ != null) return writeRequestAverageTimeInMilliseconds_;
        
        try {
            if (numWritesCompleted_.compareTo(BigDecimal.ZERO) <= 0) writeRequestAverageTimeInMilliseconds_ = BigDecimal.ZERO;
            else writeRequestAverageTimeInMilliseconds_ = numMillisecondsSpentWriting_.divide(numWritesCompleted_, SCALE, ROUNDING_MODE);
            
            return writeRequestAverageTimeInMilliseconds_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    public BigDecimal getAverageRequestTimeInMilliseconds() {
        if (averageRequestTimeInMilliseconds_ != null) return averageRequestTimeInMilliseconds_;
        
        try {
            BigDecimal sumMillisecondsActive = numMillisecondsSpentReading_.add(numMillisecondsSpentWriting_);
            BigDecimal sumIosCompleted = numReadsCompleted_.add(numWritesCompleted_);
            
            if (sumIosCompleted.compareTo(BigDecimal.ZERO) <= 0) averageRequestTimeInMilliseconds_ = BigDecimal.ZERO;
            else averageRequestTimeInMilliseconds_ = sumMillisecondsActive.divide(sumIosCompleted, SCALE, ROUNDING_MODE);
            
            return averageRequestTimeInMilliseconds_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
                
    public BigDecimal getAverageQueueLength() {
        if (averageQueueLength_ != null) return averageQueueLength_;
        
        try {
            BigDecimal sumMillisecondsActive = numMillisecondsSpentReading_.add(numMillisecondsSpentWriting_);
            BigDecimal averageRequestTimeInMilliseconds = getAverageRequestTimeInMilliseconds();

            if (averageRequestTimeInMilliseconds.compareTo(BigDecimal.ZERO) <= 0) averageQueueLength_ = BigDecimal.ZERO;
            else averageQueueLength_ = sumMillisecondsActive.divide(averageRequestTimeInMilliseconds, SCALE, ROUNDING_MODE);
            
            return averageQueueLength_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
}
