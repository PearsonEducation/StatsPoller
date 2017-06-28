
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Interface {

    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("rx_bytes")
    @Expose
    private Long rxBytes;
    @SerializedName("rx_packets")
    @Expose
    private Long rxPackets;
    @SerializedName("rx_errors")
    @Expose
    private Long rxErrors;
    @SerializedName("rx_dropped")
    @Expose
    private Long rxDropped;
    @SerializedName("tx_bytes")
    @Expose
    private Long txBytes;
    @SerializedName("tx_packets")
    @Expose
    private Long txPackets;
    @SerializedName("tx_errors")
    @Expose
    private Long txErrors;
    @SerializedName("tx_dropped")
    @Expose
    private Long txDropped;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Interface() {
    }

    /**
     * 
     * @param rxDropped
     * @param txDropped
     * @param rxErrors
     * @param name
     * @param txPackets
     * @param txBytes
     * @param rxBytes
     * @param txErrors
     * @param rxPackets
     */
    public Interface(String name, Long rxBytes, Long rxPackets, Long rxErrors, Long rxDropped, Long txBytes, Long txPackets, Long txErrors, Long txDropped) {
        super();
        this.name = name;
        this.rxBytes = rxBytes;
        this.rxPackets = rxPackets;
        this.rxErrors = rxErrors;
        this.rxDropped = rxDropped;
        this.txBytes = txBytes;
        this.txPackets = txPackets;
        this.txErrors = txErrors;
        this.txDropped = txDropped;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getRxBytes() {
        return rxBytes;
    }

    public void setRxBytes(Long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public Long getRxPackets() {
        return rxPackets;
    }

    public void setRxPackets(Long rxPackets) {
        this.rxPackets = rxPackets;
    }

    public Long getRxErrors() {
        return rxErrors;
    }

    public void setRxErrors(Long rxErrors) {
        this.rxErrors = rxErrors;
    }

    public Long getRxDropped() {
        return rxDropped;
    }

    public void setRxDropped(Long rxDropped) {
        this.rxDropped = rxDropped;
    }

    public Long getTxBytes() {
        return txBytes;
    }

    public void setTxBytes(Long txBytes) {
        this.txBytes = txBytes;
    }

    public Long getTxPackets() {
        return txPackets;
    }

    public void setTxPackets(Long txPackets) {
        this.txPackets = txPackets;
    }

    public Long getTxErrors() {
        return txErrors;
    }

    public void setTxErrors(Long txErrors) {
        this.txErrors = txErrors;
    }

    public Long getTxDropped() {
        return txDropped;
    }

    public void setTxDropped(Long txDropped) {
        this.txDropped = txDropped;
    }

}
