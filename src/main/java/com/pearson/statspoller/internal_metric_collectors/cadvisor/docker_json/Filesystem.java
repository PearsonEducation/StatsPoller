
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Filesystem {

    @SerializedName("device")
    @Expose
    private String device;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("capacity")
    @Expose
    private Long capacity;
    @SerializedName("usage")
    @Expose
    private Long usage;
    @SerializedName("base_usage")
    @Expose
    private Long baseUsage;
    @SerializedName("available")
    @Expose
    private Long available;
    @SerializedName("has_inodes")
    @Expose
    private Boolean hasInodes;
    @SerializedName("inodes")
    @Expose
    private Long inodes;
    @SerializedName("inodes_free")
    @Expose
    private Long inodesFree;
    @SerializedName("reads_completed")
    @Expose
    private Long readsCompleted;
    @SerializedName("reads_merged")
    @Expose
    private Long readsMerged;
    @SerializedName("sectors_read")
    @Expose
    private Long sectorsRead;
    @SerializedName("read_time")
    @Expose
    private Long readTime;
    @SerializedName("writes_completed")
    @Expose
    private Long writesCompleted;
    @SerializedName("writes_merged")
    @Expose
    private Long writesMerged;
    @SerializedName("sectors_written")
    @Expose
    private Long sectorsWritten;
    @SerializedName("write_time")
    @Expose
    private Long writeTime;
    @SerializedName("io_in_progress")
    @Expose
    private Long ioInProgress;
    @SerializedName("io_time")
    @Expose
    private Long ioTime;
    @SerializedName("weighted_io_time")
    @Expose
    private Long weightedIoTime;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Filesystem() {
    }

    /**
     * 
     * @param writesMerged
     * @param hasInodes
     * @param writesCompleted
     * @param readTime
     * @param sectorsRead
     * @param ioTime
     * @param readsMerged
     * @param available
     * @param type
     * @param inodesFree
     * @param weightedIoTime
     * @param readsCompleted
     * @param writeTime
     * @param inodes
     * @param device
     * @param capacity
     * @param usage
     * @param sectorsWritten
     * @param ioInProgress
     * @param baseUsage
     */
    public Filesystem(String device, String type, Long capacity, Long usage, Long baseUsage, Long available, Boolean hasInodes, Long inodes, Long inodesFree, Long readsCompleted, Long readsMerged, Long sectorsRead, Long readTime, Long writesCompleted, Long writesMerged, Long sectorsWritten, Long writeTime, Long ioInProgress, Long ioTime, Long weightedIoTime) {
        super();
        this.device = device;
        this.type = type;
        this.capacity = capacity;
        this.usage = usage;
        this.baseUsage = baseUsage;
        this.available = available;
        this.hasInodes = hasInodes;
        this.inodes = inodes;
        this.inodesFree = inodesFree;
        this.readsCompleted = readsCompleted;
        this.readsMerged = readsMerged;
        this.sectorsRead = sectorsRead;
        this.readTime = readTime;
        this.writesCompleted = writesCompleted;
        this.writesMerged = writesMerged;
        this.sectorsWritten = sectorsWritten;
        this.writeTime = writeTime;
        this.ioInProgress = ioInProgress;
        this.ioTime = ioTime;
        this.weightedIoTime = weightedIoTime;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getCapacity() {
        return capacity;
    }

    public void setCapacity(Long capacity) {
        this.capacity = capacity;
    }

    public Long getUsage() {
        return usage;
    }

    public void setUsage(Long usage) {
        this.usage = usage;
    }

    public Long getBaseUsage() {
        return baseUsage;
    }

    public void setBaseUsage(Long baseUsage) {
        this.baseUsage = baseUsage;
    }

    public Long getAvailable() {
        return available;
    }

    public void setAvailable(Long available) {
        this.available = available;
    }

    public Boolean getHasInodes() {
        return hasInodes;
    }

    public void setHasInodes(Boolean hasInodes) {
        this.hasInodes = hasInodes;
    }

    public Long getInodes() {
        return inodes;
    }

    public void setInodes(Long inodes) {
        this.inodes = inodes;
    }

    public Long getInodesFree() {
        return inodesFree;
    }

    public void setInodesFree(Long inodesFree) {
        this.inodesFree = inodesFree;
    }

    public Long getReadsCompleted() {
        return readsCompleted;
    }

    public void setReadsCompleted(Long readsCompleted) {
        this.readsCompleted = readsCompleted;
    }

    public Long getReadsMerged() {
        return readsMerged;
    }

    public void setReadsMerged(Long readsMerged) {
        this.readsMerged = readsMerged;
    }

    public Long getSectorsRead() {
        return sectorsRead;
    }

    public void setSectorsRead(Long sectorsRead) {
        this.sectorsRead = sectorsRead;
    }

    public Long getReadTime() {
        return readTime;
    }

    public void setReadTime(Long readTime) {
        this.readTime = readTime;
    }

    public Long getWritesCompleted() {
        return writesCompleted;
    }

    public void setWritesCompleted(Long writesCompleted) {
        this.writesCompleted = writesCompleted;
    }

    public Long getWritesMerged() {
        return writesMerged;
    }

    public void setWritesMerged(Long writesMerged) {
        this.writesMerged = writesMerged;
    }

    public Long getSectorsWritten() {
        return sectorsWritten;
    }

    public void setSectorsWritten(Long sectorsWritten) {
        this.sectorsWritten = sectorsWritten;
    }

    public Long getWriteTime() {
        return writeTime;
    }

    public void setWriteTime(Long writeTime) {
        this.writeTime = writeTime;
    }

    public Long getIoInProgress() {
        return ioInProgress;
    }

    public void setIoInProgress(Long ioInProgress) {
        this.ioInProgress = ioInProgress;
    }

    public Long getIoTime() {
        return ioTime;
    }

    public void setIoTime(Long ioTime) {
        this.ioTime = ioTime;
    }

    public Long getWeightedIoTime() {
        return weightedIoTime;
    }

    public void setWeightedIoTime(Long weightedIoTime) {
        this.weightedIoTime = weightedIoTime;
    }

}
