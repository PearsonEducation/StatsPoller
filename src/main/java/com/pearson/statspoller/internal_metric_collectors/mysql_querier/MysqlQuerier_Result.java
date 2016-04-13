package com.pearson.statspoller.internal_metric_collectors.mysql_querier;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class MysqlQuerier_Result {
    
    private static final Logger logger = LoggerFactory.getLogger(MysqlQuerier_Result.class.getName());

    private final String statName_;
    private final BigDecimal statValue_;
    
    public MysqlQuerier_Result(String statName, BigDecimal statValue) {
        this.statName_ = statName;
        this.statValue_ = statValue;
    }

    public String getStatName() {
        return statName_;
    }

    public BigDecimal getStatValue() {
        return statValue_;
    }
    
}
