package com.pearson.statspoller.internal_metric_collectors.linux.Cpu;

import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.math_utils.MathUtilities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 *
 * Derives CPU % values by reading /proc/stat 
 * /proc/stat docs: http://man7.org/linux/man-pages/man5/proc.5.html
 * 
 * This class is intended to hold the delta of the values from two reads of /proc/stat
 */
public class CpuStat_Delta {

    private static final Logger logger = LoggerFactory.getLogger(CpuStat_Delta.class.getName());

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final String cpuName_;
    private final BigDecimal rawUser_;
    private final BigDecimal rawNice_;
    private final BigDecimal rawSystem_;
    private final BigDecimal rawIdle_;
    private final BigDecimal rawIowait_;
    private final BigDecimal rawIrq_;
    private final BigDecimal rawSoftIrq_;
    private final BigDecimal rawSteal_;
    private final BigDecimal rawGuest_;
    private final BigDecimal rawGuestNice_;
    private final BigDecimal rawExtra_;

    private final BigDecimal sumCpuStats_;

    private BigDecimal userPercent_;
    private BigDecimal nicePercent_;
    private BigDecimal systemPercent_;
    private BigDecimal idlePercent_;
    private BigDecimal iowaitPercent_;
    private BigDecimal irqPercent_;
    private BigDecimal softIrqPercent_;
    private BigDecimal stealPercent_;
    private BigDecimal guestPercent_;
    private BigDecimal guestNicePercent_;
    private BigDecimal extraPercent_;
    private BigDecimal usedPercent_;

    public CpuStat_Delta(String cpuName,
            String rawUser, String rawNice, String rawSystem, String rawIdle,
            String rawIowait, String rawIrq, String rawSoftIrq, String rawSteal, String rawGuest, String rawGuestNice,
            String... rawExtraFields) {

        this.cpuName_ = cpuName;

        this.rawUser_ = MathUtilities.safeGetBigDecimal(rawUser);
        this.rawNice_ = MathUtilities.safeGetBigDecimal(rawNice);
        this.rawSystem_ = MathUtilities.safeGetBigDecimal(rawSystem);
        this.rawIdle_ = MathUtilities.safeGetBigDecimal(rawIdle);
        this.rawIowait_ = MathUtilities.safeGetBigDecimal(rawIowait);
        this.rawIrq_ = MathUtilities.safeGetBigDecimal(rawIrq);
        this.rawSoftIrq_ = MathUtilities.safeGetBigDecimal(rawSoftIrq);
        this.rawSteal_ = MathUtilities.safeGetBigDecimal(rawSteal);
        this.rawGuest_ = MathUtilities.safeGetBigDecimal(rawGuest);
        this.rawGuestNice_ = MathUtilities.safeGetBigDecimal(rawGuestNice);

        if (rawExtraFields != null) {
            BigDecimal rawExtra = BigDecimal.ZERO;

            for (String rawExtraField : rawExtraFields) {
                if (rawExtraField != null) {
                    BigDecimal rawExtraField_BigDecimal = MathUtilities.safeGetBigDecimal(rawExtraField);
                    if (rawExtraField_BigDecimal != null) {
                        rawExtra = rawExtra.add(rawExtraField_BigDecimal);
                    }
                }
            }

            rawExtra_ = rawExtra;
        }
        else {
            rawExtra_ = null;
        }

        sumCpuStats_ = sumCpuStats();
    }

    private BigDecimal sumCpuStats() {

        BigDecimal sumCpuStats = new BigDecimal(0);

        if (rawUser_ != null) sumCpuStats = sumCpuStats.add(rawUser_);
        if (rawNice_ != null) sumCpuStats = sumCpuStats.add(rawNice_);
        if (rawSystem_ != null) sumCpuStats = sumCpuStats.add(rawSystem_);
        if (rawIdle_ != null) sumCpuStats = sumCpuStats.add(rawIdle_);
        if (rawIowait_ != null) sumCpuStats = sumCpuStats.add(rawIowait_);
        if (rawIrq_ != null) sumCpuStats = sumCpuStats.add(rawIrq_);
        if (rawSoftIrq_ != null) sumCpuStats = sumCpuStats.add(rawSoftIrq_);
        if (rawSteal_ != null) sumCpuStats = sumCpuStats.add(rawSteal_);
        if (rawGuest_ != null) sumCpuStats = sumCpuStats.add(rawGuest_);
        if (rawGuestNice_ != null) sumCpuStats = sumCpuStats.add(rawGuestNice_);
        if (rawExtra_ != null) sumCpuStats = sumCpuStats.add(rawExtra_);

        return sumCpuStats;
    }

    public BigDecimal getUserPercent() {
        
        if (userPercent_ != null) return userPercent_;
        if ((rawUser_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            userPercent_ = rawUser_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return userPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getNicePercent() {

        if (nicePercent_ != null) return nicePercent_;
        if ((rawNice_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            nicePercent_ = rawNice_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return nicePercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getSystemPercent() {
        
        if (systemPercent_ != null) return systemPercent_;
        if ((rawSystem_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            systemPercent_ = rawSystem_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return systemPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getIdlePercent() {

        if (idlePercent_ != null) return idlePercent_;
        if ((rawIdle_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            idlePercent_ = rawIdle_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return idlePercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getIowaitPercent() {

        if (iowaitPercent_ != null) return iowaitPercent_;
        if ((rawIowait_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            iowaitPercent_ = rawIowait_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return iowaitPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getIrqPercent() {

        if (irqPercent_ != null) return irqPercent_;
        if ((rawIrq_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            irqPercent_ = rawIrq_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return irqPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getSoftIrqPercent() {

        if (softIrqPercent_ != null) return softIrqPercent_;
        if ((rawSoftIrq_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            softIrqPercent_ = rawSoftIrq_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return softIrqPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getStealPercent() {

        if (stealPercent_ != null) return stealPercent_;
        if ((rawSteal_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            stealPercent_ = rawSteal_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return stealPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getGuestPercent() {
        
        if (guestPercent_ != null) return guestPercent_;
        if ((rawGuest_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            guestPercent_ = rawGuest_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return guestPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getGuestNicePercent() {

        if (guestNicePercent_ != null) return guestNicePercent_;
        if ((rawGuestNice_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            guestNicePercent_ = rawGuestNice_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return guestNicePercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getExtraPercent() {

        if (extraPercent_ != null) return extraPercent_;
        if ((rawExtra_ == null) || (sumCpuStats_ == null)) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            extraPercent_ = rawExtra_.divide(sumCpuStats_, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return extraPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public BigDecimal getUsedPercent() {

        if (usedPercent_ != null) return usedPercent_;
        if (getIdlePercent() == null) return null;

        boolean isSumCpuStatsEqualToZero = MathUtilities.areBigDecimalsNumericallyEqual(sumCpuStats_, BigDecimal.ZERO);
        if (isSumCpuStatsEqualToZero) return null;

        try {
            usedPercent_ = ONE_HUNDRED.subtract(getIdlePercent());
            return usedPercent_;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    public String getFormattedCpuName() {
        if (cpuName_ == null) return null;
        if (cpuName_.trim().equals("cpu")) return "CPU-All";
        else return cpuName_.trim().replace("cpu", "CPU-");
    }

}
