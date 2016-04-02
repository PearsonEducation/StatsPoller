package com.pearson.statspoller.internal_metric_collectors.linux.Network;

import com.pearson.statspoller.utilities.MathUtilities;
import java.math.BigDecimal;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class NetworkBandwidthStat {

    private static final Logger logger = LoggerFactory.getLogger(NetworkBandwidthStat.class.getName());

    private final String interfaceName_;
    private final long timestampMs_;
    private final BigDecimal rxBytes_;
    private final BigDecimal txBytes_;
    
    public NetworkBandwidthStat(String interfaceName, long timestampMs, String rxBytes, String txBytes) {
        this.interfaceName_ = interfaceName;
        this.timestampMs_ = timestampMs;
        this.rxBytes_ = MathUtilities.safeGetBigDecimal(rxBytes);
        this.txBytes_ = MathUtilities.safeGetBigDecimal(txBytes);
    }
    
    public String getFormattedInterfaceName() {
        if (interfaceName_ == null) return null;
        else return StringUtils.remove(interfaceName_, ':').trim();
    }
    
    public String getInterfaceName() {
        return interfaceName_;
    }

    public long getTimestampMs() {
        return timestampMs_;
    }
    
    public BigDecimal getRxBytes() {
        return rxBytes_;
    }

    public BigDecimal getTxBytes() {
        return txBytes_;
    }

}
