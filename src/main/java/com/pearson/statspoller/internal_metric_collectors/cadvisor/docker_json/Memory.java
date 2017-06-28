
package com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Memory {

    @SerializedName("limit")
    @Expose
    private Long limit;
    @SerializedName("reservation")
    @Expose
    private Long reservation;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Memory() {
    }

    /**
     * 
     * @param limit
     * @param reservation
     */
    public Memory(Long limit, Long reservation) {
        super();
        this.limit = limit;
        this.reservation = reservation;
    }

    public Long getLimit() {
        return limit;
    }

    public void setLimit(Long limit) {
        this.limit = limit;
    }

    public Long getReservation() {
        return reservation;
    }

    public void setReservation(Long reservation) {
        this.reservation = reservation;
    }

}
