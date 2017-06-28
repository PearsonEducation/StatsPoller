
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Stats_ {

    @SerializedName("Async")
    @Expose
    private Long async;
    @SerializedName("Read")
    @Expose
    private Long read;
    @SerializedName("Sync")
    @Expose
    private Long sync;
    @SerializedName("Total")
    @Expose
    private Long total;
    @SerializedName("Write")
    @Expose
    private Long write;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Stats_() {
    }

    /**
     * 
     * @param total
     * @param sync
     * @param write
     * @param read
     * @param async
     */
    public Stats_(Long async, Long read, Long sync, Long total, Long write) {
        super();
        this.async = async;
        this.read = read;
        this.sync = sync;
        this.total = total;
        this.write = write;
    }

    public Long getAsync() {
        return async;
    }

    public void setAsync(Long async) {
        this.async = async;
    }

    public Long getRead() {
        return read;
    }

    public void setRead(Long read) {
        this.read = read;
    }

    public Long getSync() {
        return sync;
    }

    public void setSync(Long sync) {
        this.sync = sync;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getWrite() {
        return write;
    }

    public void setWrite(Long write) {
        this.write = write;
    }

}
