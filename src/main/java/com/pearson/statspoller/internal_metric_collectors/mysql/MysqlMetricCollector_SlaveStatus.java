package com.pearson.statspoller.internal_metric_collectors.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class MysqlMetricCollector_SlaveStatus {
    
    private static final Logger logger = LoggerFactory.getLogger(MysqlMetricCollector_SlaveStatus.class.getName());

    private String masterHost_ = null;
    private Integer masterPort_ = null;
    private Boolean slaveIoRunning_ = null;
    private Boolean slaveSqlRunning_ = null;
    private Long secondsBehindMaster_ = null;
    private Integer masterServerId_ = null;
    
    public MysqlMetricCollector_SlaveStatus() {}

    public String getMasterHost() {
        return masterHost_;
    }

    public void setMasterHost(String masterHost) {
        this.masterHost_ = masterHost;
    }

    public Integer getMasterPort() {
        return masterPort_;
    }

    public void setMasterPort(Integer masterPort) {
        this.masterPort_ = masterPort;
    }

    public Boolean getSlaveIoRunning() {
        return slaveIoRunning_;
    }

    public void setSlaveIoRunning(Boolean slaveIoRunning) {
        this.slaveIoRunning_ = slaveIoRunning;
    }

    public Boolean getSlaveSqlRunning() {
        return slaveSqlRunning_;
    }

    public void setSlaveSqlRunning(Boolean slaveSqlRunning) {
        this.slaveSqlRunning_ = slaveSqlRunning;
    }

    public Long getSecondsBehindMaster() {
        return secondsBehindMaster_;
    }

    public void setSecondsBehindMaster(Long secondsBehindMaster) {
        this.secondsBehindMaster_ = secondsBehindMaster;
    }

    public Integer getMasterServerId() {
        return masterServerId_;
    }

    public void setMasterServerId(Integer masterServerId) {
        this.masterServerId_ = masterServerId;
    }

}
