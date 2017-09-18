
package com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Machine {

    @SerializedName("num_cores")
    @Expose
    private Long numCores;
    @SerializedName("cpu_frequency_khz")
    @Expose
    private Long cpuFrequencyKhz;
    @SerializedName("memory_capacity")
    @Expose
    private Long memoryCapacity;
    @SerializedName("machine_id")
    @Expose
    private String machineId;
    @SerializedName("system_uuid")
    @Expose
    private String systemUuid;
    @SerializedName("boot_id")
    @Expose
    private String bootId;
//    @SerializedName("filesystems")
//    @Expose
//    private List<Filesystem> filesystems = new ArrayList<Filesystem>();
//    @SerializedName("network_devices")
//    @Expose
//    private List<NetworkDevice> networkDevices = new ArrayList<NetworkDevice>();
//    @SerializedName("topology")
//    @Expose
//    private List<Topology> topology = new ArrayList<Topology>();
    @SerializedName("cloud_provider")
    @Expose
    private String cloudProvider;
    @SerializedName("instance_type")
    @Expose
    private String instanceType;
    @SerializedName("instance_id")
    @Expose
    private String instanceId;

    public Long getNumCores() {
        return numCores;
    }

    public void setNumCores(Long numCores) {
        this.numCores = numCores;
    }

    public Long getCpuFrequencyKhz() {
        return cpuFrequencyKhz;
    }

    public void setCpuFrequencyKhz(Long cpuFrequencyKhz) {
        this.cpuFrequencyKhz = cpuFrequencyKhz;
    }

    public Long getMemoryCapacity() {
        return memoryCapacity;
    }

    public void setMemoryCapacity(Long memoryCapacity) {
        this.memoryCapacity = memoryCapacity;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getSystemUuid() {
        return systemUuid;
    }

    public void setSystemUuid(String systemUuid) {
        this.systemUuid = systemUuid;
    }

    public String getBootId() {
        return bootId;
    }

    public void setBootId(String bootId) {
        this.bootId = bootId;
    }

//    public List<Filesystem> getFilesystems() {
//        return filesystems;
//    }
//
//    public void setFilesystems(List<Filesystem> filesystems) {
//        this.filesystems = filesystems;
//    }
//
//    public List<NetworkDevice> getNetworkDevices() {
//        return networkDevices;
//    }
//
//    public void setNetworkDevices(List<NetworkDevice> networkDevices) {
//        this.networkDevices = networkDevices;
//    }
//
//    public List<Topology> getTopology() {
//        return topology;
//    }
//
//    public void setTopology(List<Topology> topology) {
//        this.topology = topology;
//    }

    public String getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

}
