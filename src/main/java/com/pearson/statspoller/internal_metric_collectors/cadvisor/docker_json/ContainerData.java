
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ContainerData {

    @SerializedName("pgfault")
    @Expose
    private Long pgfault;
    @SerializedName("pgmajfault")
    @Expose
    private Long pgmajfault;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ContainerData() {
    }

    /**
     * 
     * @param pgmajfault
     * @param pgfault
     */
    public ContainerData(Long pgfault, Long pgmajfault) {
        super();
        this.pgfault = pgfault;
        this.pgmajfault = pgmajfault;
    }

    public Long getPgfault() {
        return pgfault;
    }

    public void setPgfault(Long pgfault) {
        this.pgfault = pgfault;
    }

    public Long getPgmajfault() {
        return pgmajfault;
    }

    public void setPgmajfault(Long pgmajfault) {
        this.pgmajfault = pgmajfault;
    }

}
