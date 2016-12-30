package com.pearson.statspoller.internal_metric_collectors.db_querier;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class DbQuerier_Result {
    
    private static final Logger logger = LoggerFactory.getLogger(DbQuerier_Result.class.getName());

    private final String statName_;
    private final BigDecimal statValue_;
    
    public DbQuerier_Result(String statName, BigDecimal statValue) {
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
