package com.pearson.statspoller.internal_metric_collectors.mysql_querier;

import com.pearson.statspoller.utilities.DatabaseUtils;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbTag;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class MysqlQuerier extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MysqlQuerier.class.getName());
    
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    private final String host_;
    private final int port_;
    private final String username_;
    private final String password_;
    private final String jdbcString_;
    private final boolean isUserSpecifiedJdbcString_;
    private final List<OpenTsdbTag> openTsdbTags_;

    public MysqlQuerier(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String username, String password, String jdbcString, 
            List<OpenTsdbTag> openTsdbTags) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
        this.jdbcString_ = ((jdbcString == null) || jdbcString.isEmpty()) ? "jdbc:mysql://" + host_ + ":" + port_ + "?connectTimeout=7500&socketTimeout=7500&autoReconnect=false" : jdbcString;
        this.isUserSpecifiedJdbcString_ = ((jdbcString != null) && !jdbcString.isEmpty());
        this.openTsdbTags_ = openTsdbTags;
    }

    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // connect to the db
            Connection connection = !isUserSpecifiedJdbcString_ ? DatabaseUtils.connect(jdbcString_, username_, password_) : DatabaseUtils.connect(jdbcString_);
            
            List<OpenTsdbMetric> openTsdbMetrics = getMysqlMetrics(connection);
            
            // disconnect from the db
            DatabaseUtils.disconnect(connection);
            
            // output opentsdb metrics
            super.outputOpenTsdbMetrics(openTsdbMetrics);
            
            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            // lay on anything worth logging here
            logger.info("Finished MySQL Querier metric collection routine. MyqlServer=" + host_ + ":" + port_ + 
                    ", MetricsCollected=" + openTsdbMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    public void singleRun() {
         
        if (super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // connect to the db
            Connection connection = !isUserSpecifiedJdbcString_ ? DatabaseUtils.connect(jdbcString_, username_, password_) : DatabaseUtils.connect(jdbcString_);
            
            getMysqlMetrics(connection); // first iteration, don't store metrics because we dont have rate data

            long sleepTimeInMs = getCollectionInterval();
            Threads.sleepMilliseconds(sleepTimeInMs);

            List<OpenTsdbMetric> openTsdbMetrics = getMysqlMetrics(connection); // second iteration, get graphite metrics

            // disconnect from the db
            DatabaseUtils.disconnect(connection);
            
            // output opentsdb metrics
            super.outputOpenTsdbMetrics(openTsdbMetrics);
            
            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            logger.info("Finished MySQL Querier metric collection routine." + " MyqlServer=" + host_ + ":" + port_ + 
                    ", MetricsCollected=" + openTsdbMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed +
                    ", MetricCollectionTimeExcludingSleep=" + (routineTimeElapsed - sleepTimeInMs));
        }

    }
    
    private List<OpenTsdbMetric> getMysqlMetrics(Connection connection) {

        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
        OpenTsdbMetric openTsdbMetric;
        BigDecimal metric;
        
        try {
            boolean isConnectionValid = DatabaseUtils.isConnectionValid(connection, 5);

            if (!isConnectionValid || connection.isClosed()) {
                openTsdbMetric = new OpenTsdbMetric("Available",  System.currentTimeMillis(), BigDecimal.ZERO, openTsdbTags_);
                openTsdbMetrics.add(openTsdbMetric);
                logger.warn("MyqlServer=" + host_ + ":" + port_ + " is unavailable");
                return openTsdbMetrics;
            }
            else connection.setReadOnly(true);
            
            long currentTimestampMilliseconds = System.currentTimeMillis();

            openTsdbMetric = new OpenTsdbMetric("Available",  currentTimestampMilliseconds, BigDecimal.ONE, openTsdbTags_);
            openTsdbMetrics.add(openTsdbMetric);
            


            Map<String,BigDecimal> queryResults = runQuery(connection, "");
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }
  
    private Map<String,BigDecimal> runQuery(Connection connection, String sql) {
        
        if (connection == null) {
            return null;
        }
        
        Statement statement = null;
        ResultSet resultSet = null;
         
        try {
            if (!DatabaseUtils.isConnectionValid(connection)) return null;

            String query = "select * from information_schema.GLOBAL_STATUS";
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
            if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
            
            Map<String,BigDecimal> globalStatus = new HashMap<>();
            
            while (resultSet.next()) {
                String variableName = resultSet.getString("STAT_NAME");
                if (resultSet.wasNull()) variableName = null;
                
                Object variableValue_Object = resultSet.getObject("STAT_VALUE");
                BigDecimal variableValue_BigDecimal = new BigDecimal(variableValue_Object.toString());
                if (resultSet.wasNull()) variableValue_BigDecimal = null;
                
                if ((variableName != null) && (variableValue_BigDecimal != null)) globalStatus.put(variableName, variableValue_BigDecimal);
            }

            return globalStatus;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
        finally {
            DatabaseUtils.cleanup(statement, resultSet);
        } 
        
    }
 
}
