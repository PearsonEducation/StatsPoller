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
public class PreviousStatMetadata {
    
    private static final Logger logger = LoggerFactory.getLogger(PreviousStatMetadata.class.getName());

    private final Stat stat_;
    private final Date timestamp_;
    private final Machine machine_;
    
    public PreviousStatMetadata(Stat stat, Date timestamp, Machine machine) {
        this.stat_ = stat;
        this.timestamp_ = timestamp;
        this.machine_ = machine;
    }

    public boolean areCoreFieldsNull() {
        if (this.stat_ == null) return true;
        if (this.timestamp_ == null) return true;
        if (this.machine_ == null ) return true;
        return false;
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
