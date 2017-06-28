
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Tcp6 {

    @SerializedName("Established")
    @Expose
    private Long established;
    @SerializedName("SynSent")
    @Expose
    private Long synSent;
    @SerializedName("SynRecv")
    @Expose
    private Long synRecv;
    @SerializedName("FinWait1")
    @Expose
    private Long finWait1;
    @SerializedName("FinWait2")
    @Expose
    private Long finWait2;
    @SerializedName("TimeWait")
    @Expose
    private Long timeWait;
    @SerializedName("Close")
    @Expose
    private Long close;
    @SerializedName("CloseWait")
    @Expose
    private Long closeWait;
    @SerializedName("LastAck")
    @Expose
    private Long lastAck;
    @SerializedName("Listen")
    @Expose
    private Long listen;
    @SerializedName("Closing")
    @Expose
    private Long closing;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Tcp6() {
    }

    /**
     * 
     * @param timeWait
     * @param finWait2
     * @param synSent
     * @param finWait1
     * @param established
     * @param closeWait
     * @param listen
     * @param lastAck
     * @param closing
     * @param close
     * @param synRecv
     */
    public Tcp6(Long established, Long synSent, Long synRecv, Long finWait1, Long finWait2, Long timeWait, Long close, Long closeWait, Long lastAck, Long listen, Long closing) {
        super();
        this.established = established;
        this.synSent = synSent;
        this.synRecv = synRecv;
        this.finWait1 = finWait1;
        this.finWait2 = finWait2;
        this.timeWait = timeWait;
        this.close = close;
        this.closeWait = closeWait;
        this.lastAck = lastAck;
        this.listen = listen;
        this.closing = closing;
    }

    public Long getEstablished() {
        return established;
    }

    public void setEstablished(Long established) {
        this.established = established;
    }

    public Long getSynSent() {
        return synSent;
    }

    public void setSynSent(Long synSent) {
        this.synSent = synSent;
    }

    public Long getSynRecv() {
        return synRecv;
    }

    public void setSynRecv(Long synRecv) {
        this.synRecv = synRecv;
    }

    public Long getFinWait1() {
        return finWait1;
    }

    public void setFinWait1(Long finWait1) {
        this.finWait1 = finWait1;
    }

    public Long getFinWait2() {
        return finWait2;
    }

    public void setFinWait2(Long finWait2) {
        this.finWait2 = finWait2;
    }

    public Long getTimeWait() {
        return timeWait;
    }

    public void setTimeWait(Long timeWait) {
        this.timeWait = timeWait;
    }

    public Long getClose() {
        return close;
    }

    public void setClose(Long close) {
        this.close = close;
    }

    public Long getCloseWait() {
        return closeWait;
    }

    public void setCloseWait(Long closeWait) {
        this.closeWait = closeWait;
    }

    public Long getLastAck() {
        return lastAck;
    }

    public void setLastAck(Long lastAck) {
        this.lastAck = lastAck;
    }

    public Long getListen() {
        return listen;
    }

    public void setListen(Long listen) {
        this.listen = listen;
    }

    public Long getClosing() {
        return closing;
    }

    public void setClosing(Long closing) {
        this.closing = closing;
    }

}
