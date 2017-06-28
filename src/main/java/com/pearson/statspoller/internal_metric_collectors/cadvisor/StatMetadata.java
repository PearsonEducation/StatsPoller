package com.pearson.statspoller.internal_metric_collectors.cadvisor;

import com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json.Docker;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json.Stat;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json.Machine;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class StatMetadata {
    
    private static final Logger logger = LoggerFactory.getLogger(StatMetadata.class.getName());

    private final Docker docker_;
    private final Stat stat_;
    private final Date timestamp_;
    private final Machine machine_;
    
    public StatMetadata(Docker docker, Stat stat, Date timestamp, Machine machine) {
        this.docker_ = docker;
        this.stat_ = stat;
        this.timestamp_ = timestamp;
        this.machine_ = machine;
    }

    public boolean areAnyFieldsNull() {
        if (this.docker_ == null) return true;
        if (this.stat_ == null) return true;
        if (this.timestamp_ == null) return true;
        if (this.machine_ == null ) return true;
        return false;
    }
    
    public Docker getDocker() {
        return docker_;
    }

    public Stat getStat() {
        return stat_;
    }

    public Date getTimestamp() {
        return timestamp_;
    }

    public Machine getMachine() {
        return machine_;
    }
    
}
