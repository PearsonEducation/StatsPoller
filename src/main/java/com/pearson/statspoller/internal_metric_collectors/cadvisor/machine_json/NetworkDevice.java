
package com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class NetworkDevice {

    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("mac_address")
    @Expose
    private String macAddress;
    @SerializedName("speed")
    @Expose
    private Long speed;
    @SerializedName("mtu")
    @Expose
    private Long mtu;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public Long getSpeed() {
        return speed;
    }

    public void setSpeed(Long speed) {
        this.speed = speed;
    }

    public Long getMtu() {
        return mtu;
    }

    public void setMtu(Long mtu) {
        this.mtu = mtu;
    }

}
