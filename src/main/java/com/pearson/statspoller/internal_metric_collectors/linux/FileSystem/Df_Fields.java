package com.pearson.statspoller.internal_metric_collectors.linux.FileSystem;

import com.pearson.statspoller.utilities.MathUtilities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class Df_Fields {
    
    private static final Logger logger = LoggerFactory.getLogger(Df_Fields.class.getName());
    
    private static final BigDecimal MULTIPLIER_1024 = new BigDecimal("1024");
    private static final BigDecimal BYTES_TO_GIGABYTES_DIVISOR = new BigDecimal("1073741824");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    private String fileSystem_;
    private String type_;
    private String num1024kBlocks_;
    private String numUsedBlocks_;
    private String numAvailableBlocks_;
    private String blocksUsedPercent_;
    private String numInodes_;
    private String numInodesUsed_;
    private String numInodesFree_;
    private String inodesUsedPercent_;
    private String mountedOn_;
    
    private BigDecimal diskSpaceTotalBytes_;
    private BigDecimal diskSpaceTotalGigabytes_;
    private BigDecimal diskSpaceUsedBytes_;
    private BigDecimal diskSpaceUsedGigabytes_;
    private BigDecimal diskSpaceFreeBytes_;
    private BigDecimal diskSpaceFreeGigabytes_;
    private BigDecimal diskSpaceUsedPercent_;
    private BigDecimal diskInodesUsedPercent_;
    
    public Df_Fields() {}
    
    public String getFileSystem() {
        return fileSystem_;
    }
    
    public String getType() {
        return type_;
    }
    
    public BigDecimal getDiskSpaceTotalBytes() {
        if (diskSpaceTotalBytes_ != null) return diskSpaceTotalBytes_;
        if (num1024kBlocks_ == null) return null;
        
        BigDecimal num1024kBlocks_BigDecimal = MathUtilities.safeGetBigDecimal(num1024kBlocks_);
        if (num1024kBlocks_BigDecimal == null) return null;
        
        diskSpaceTotalBytes_ = num1024kBlocks_BigDecimal.multiply(MULTIPLIER_1024);
        return diskSpaceTotalBytes_;
    }
    
    public BigDecimal getDiskSpaceTotalGigabytes() {
        if (diskSpaceTotalGigabytes_ != null) return diskSpaceTotalGigabytes_;

        BigDecimal diskSpaceTotalBytes = getDiskSpaceTotalBytes();
        if (diskSpaceTotalBytes != null) diskSpaceTotalGigabytes_ = diskSpaceTotalBytes.divide(BYTES_TO_GIGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
        else diskSpaceTotalGigabytes_ = null;
        
        return diskSpaceTotalGigabytes_;
    }
    
    public BigDecimal getDiskSpaceUsedBytes() {
        if (diskSpaceUsedBytes_ != null) return diskSpaceUsedBytes_;
        if (numUsedBlocks_ == null) return null;
        
        BigDecimal numUsedBlocks_BigDecimal = MathUtilities.safeGetBigDecimal(numUsedBlocks_);
        if (numUsedBlocks_BigDecimal == null) return null;
        
        diskSpaceUsedBytes_ = numUsedBlocks_BigDecimal.multiply(MULTIPLIER_1024);
        return diskSpaceUsedBytes_;
    }
    
    public BigDecimal getDiskSpaceUsedGigabytes() {
        if (diskSpaceUsedGigabytes_ != null) return diskSpaceUsedGigabytes_;
        
        BigDecimal diskSpaceUsedBytes = getDiskSpaceUsedBytes();
        if (diskSpaceUsedBytes != null) diskSpaceUsedGigabytes_ = diskSpaceUsedBytes.divide(BYTES_TO_GIGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
        else diskSpaceUsedGigabytes_ =  null;
        
        return diskSpaceUsedGigabytes_;
    }
    
    public BigDecimal getDiskSpaceFreeBytes() {
        if (diskSpaceFreeBytes_ != null) return diskSpaceFreeBytes_;
        if (numAvailableBlocks_ == null) return null;
        
        BigDecimal numAvailableBlocks_BigDecimal = MathUtilities.safeGetBigDecimal(numAvailableBlocks_);
        if (numAvailableBlocks_BigDecimal == null) return null;
        
        diskSpaceFreeBytes_ = numAvailableBlocks_BigDecimal.multiply(MULTIPLIER_1024);
        return diskSpaceFreeBytes_;
    }
    
    public BigDecimal getDiskSpaceFreeGigabytes() {
        if (diskSpaceFreeGigabytes_ != null) return diskSpaceFreeGigabytes_;
        
        BigDecimal diskSpaceFreeBytes = getDiskSpaceFreeBytes();
        if (diskSpaceFreeBytes != null) diskSpaceFreeGigabytes_ = diskSpaceFreeBytes.divide(BYTES_TO_GIGABYTES_DIVISOR, SCALE, ROUNDING_MODE);
        else diskSpaceFreeGigabytes_ = null;
        
        return diskSpaceFreeGigabytes_;
    }
    
    public BigDecimal getDiskSpaceUsedPercent() {
        if (diskSpaceUsedPercent_ != null) return diskSpaceUsedPercent_;
        if (blocksUsedPercent_ == null) return null;
        
        String blocksUsedPercent_Numeric = StringUtils.remove(blocksUsedPercent_, "%");
        if (blocksUsedPercent_Numeric == null) return null;
        if (!StringUtils.isNumeric(blocksUsedPercent_Numeric)) return null;
        
        diskSpaceUsedPercent_ = MathUtilities.safeGetBigDecimal(blocksUsedPercent_Numeric);
        return diskSpaceUsedPercent_;
    }
    
    public BigDecimal getInodesTotal() {
        return MathUtilities.safeGetBigDecimal(numInodes_);
    }
    
    public BigDecimal getInodesUsed() {
        return MathUtilities.safeGetBigDecimal(numInodesUsed_);
    }
    
    public BigDecimal getInodesFree() {
        return MathUtilities.safeGetBigDecimal(numInodesFree_);
    }
    
    public BigDecimal getInodesUsedPercent() {
        if (diskInodesUsedPercent_ != null) return diskInodesUsedPercent_;
        if (inodesUsedPercent_ == null) return null;
        
        String inodesUsedPercent_Numeric = StringUtils.remove(inodesUsedPercent_, "%");
        if (inodesUsedPercent_Numeric == null) return null;
        if (!StringUtils.isNumeric(inodesUsedPercent_Numeric)) return null;
        
        diskInodesUsedPercent_ = MathUtilities.safeGetBigDecimal(inodesUsedPercent_Numeric);
        return diskInodesUsedPercent_;
    }

    public void setFileSystem(String fileSystem) {
        this.fileSystem_ = fileSystem;
    }

    public void setType(String type) {
        this.type_ = type;
    }

    public void setNum1024kBlocks(String num1024kBlocks) {
        this.diskSpaceTotalBytes_ = null;
        this.diskSpaceTotalGigabytes_ = null;
        this.num1024kBlocks_ = num1024kBlocks;
    }

    public void setNumUsedBlocks(String numUsedBlocks) {
        this.diskSpaceUsedBytes_ = null;
        this.diskSpaceUsedGigabytes_ = null;
        this.numUsedBlocks_ = numUsedBlocks;
    }

    public void setNumAvailableBlocks(String numAvailableBlocks) {
        this.diskSpaceFreeBytes_ = null;
        this.diskSpaceFreeGigabytes_ = null;
        this.numAvailableBlocks_ = numAvailableBlocks;
    }

    public void setBlocksUsedPercent(String blocksUsedPercent) {
        this.diskSpaceUsedPercent_ = null;
        this.blocksUsedPercent_ = blocksUsedPercent;
    }

    public void setNumInodes(String numInodes) {
        this.numInodes_ = numInodes;
    }

    public void setNumInodesUsed(String numInodesUsed) {
        this.numInodesUsed_ = numInodesUsed;
    }

    public void setNumInodesFree(String numInodesFree) {
        this.numInodesFree_ = numInodesFree;
    }

    public void setInodesUsedPercent(String inodeUsedPercent) {
        this.diskInodesUsedPercent_ = null;
        this.inodesUsedPercent_ = inodeUsedPercent;
    }

    public String getMountedOn() {
        return mountedOn_;
    }

    public void setMountedOn(String mountedOn) {
        this.mountedOn_ = mountedOn;
    }

}
