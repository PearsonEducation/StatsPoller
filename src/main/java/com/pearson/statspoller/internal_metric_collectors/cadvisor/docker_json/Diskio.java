
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Diskio {

    @SerializedName("io_service_bytes")
    @Expose
    private List<IoServiceByte> ioServiceBytes = new ArrayList<IoServiceByte>();
    @SerializedName("io_serviced")
    @Expose
    private List<IoServiced> ioServiced = new ArrayList<IoServiced>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public Diskio() {
    }

    /**
     * 
     * @param ioServiced
     * @param ioServiceBytes
     */
    public Diskio(List<IoServiceByte> ioServiceBytes, List<IoServiced> ioServiced) {
        super();
        this.ioServiceBytes = ioServiceBytes;
        this.ioServiced = ioServiced;
    }

    public List<IoServiceByte> getIoServiceBytes() {
        return ioServiceBytes;
    }

    public void setIoServiceBytes(List<IoServiceByte> ioServiceBytes) {
        this.ioServiceBytes = ioServiceBytes;
    }

    public List<IoServiced> getIoServiced() {
        return ioServiced;
    }

    public void setIoServiced(List<IoServiced> ioServiced) {
        this.ioServiced = ioServiced;
    }

}
