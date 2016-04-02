package com.pearson.statspoller.internal_metric_collectors.mysql;

import com.pearson.statspoller.utilities.DatabaseUtils;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
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
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class MysqlMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MysqlMetricCollector.class.getName());
    
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final List<String> GLOBAL_STATUS_VARIABLES_TO_DELTA = getGlobalStatusVariablesToDelta();
    
    private final String host_;
    private final int port_;
    private final String username_;
    private final String password_;
    
    private final String jdbcString_;
    private final boolean isUserSpecifiedJdbcString_;
    
    private static Map<String,String> previousGlobalStatus_ = new HashMap<>();
    private static Long previousTimestampInMilliseconds_Status_ = null;
    
    public MysqlMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String username, String password, String jdbcString) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
  
        jdbcString_ = ((jdbcString == null) || jdbcString.isEmpty()) ? "jdbc:mysql://" + host_ + ":" + port_ : jdbcString;
        isUserSpecifiedJdbcString_ = ((jdbcString != null) && !jdbcString.isEmpty());
    }

    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // connect to the db
            Connection connection = !isUserSpecifiedJdbcString_ ? DatabaseUtils.connect(jdbcString_, username_, password_) : DatabaseUtils.connect(jdbcString_);
            
            // put graphite metrics in here
            List<GraphiteMetric> graphiteMetrics = getMysqlMetrics(connection);
            
            // disconnect from the db
            DatabaseUtils.disconnect(connection);
            
            // output graphite metrics
            super.outputMetrics(graphiteMetrics, true);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            // lay on anything worth logging here
            logger.info("Finished MySQL metric collection routine. MyqlServer=" + host_ + ":" + port_ + 
                    ", MetricsCollected=" + graphiteMetrics.size() +
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
            
            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;
            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
            
            List<GraphiteMetric> graphiteMetrics = getMysqlMetrics(connection); // second iteration, get graphite metrics
            
            // disconnect from the db
            DatabaseUtils.disconnect(connection);
            
            // output graphite metrics
            super.outputMetrics(graphiteMetrics, true);
            
            logger.info("Finished MySQL metric collection routine. MyqlServer=" + host_ + ":" + port_ + 
                    ", MetricsCollected=" + graphiteMetrics.size());
        }

    }
    
    
    private List<GraphiteMetric> getMysqlMetrics(Connection connection) {
        
        if (connection == null) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        GraphiteMetric graphiteMetric;
        BigDecimal metric;
        
        try {
            if (connection.isClosed()) return graphiteMetrics;
            
            long currentTimestampMilliseconds_Status = System.currentTimeMillis();
            Map<String,String> globalVariables = getGlobalVariables(connection);
            
            if (previousTimestampInMilliseconds_Status_ == null) {
                previousTimestampInMilliseconds_Status_ = currentTimestampMilliseconds_Status;
                return graphiteMetrics;
            }
            
            int currentTimestampInSeconds_Status = (int) (currentTimestampMilliseconds_Status / 1000);
            long previousTimestampMilliseconds_Status = previousTimestampInMilliseconds_Status_;
            Map<String,String> currentGlobalStatus = getGlobalStatus(connection);
            Map<String,String> previousGlobalStatus = previousGlobalStatus_;
           
            if ((currentGlobalStatus != null) && !currentGlobalStatus.isEmpty()) {
                previousGlobalStatus_ = currentGlobalStatus;
                previousTimestampInMilliseconds_Status_ = currentTimestampMilliseconds_Status;
                
                long millisecondsBetweenSamples = currentTimestampMilliseconds_Status - previousTimestampMilliseconds_Status;
                BigDecimal millisecondsBetweenSamples_BigDecimal = new BigDecimal(millisecondsBetweenSamples);
                Map<String,BigDecimal> globalStatusDelta_ByVariable = getGlobalStatusDelta(currentGlobalStatus, previousGlobalStatus, GLOBAL_STATUS_VARIABLES_TO_DELTA);
                
                graphiteMetric = new GraphiteMetric("Available", BigDecimal.ONE, currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "INNODB_DEADLOCKS", "InnodbDeadlocksSinceReset-Count", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "INNODB_CURRENT_ROW_LOCKS", "InnodbCurrentRowLocks-Count", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "OPEN_FILES", "OpenFiles-Count", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "SLAVE_RUNNING", "SlaveRunning", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "THREADS_RUNNING", "ConnectionsBusy-Count", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "THREADS_CONNECTED", "ConnectionsOpen-Count", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "UPTIME", "Uptime-Secs", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("BYTES_RECEIVED"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("BytesReceived-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("BYTES_SENT"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("BytesSent-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_COMMIT"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("TX-Commits-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_DELETE"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("DML-Deletes-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_INSERT"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("DML-Inserts-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_ROLLBACK"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("TX-Rollbacks-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_SELECT"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("DML-Selects-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_UPDATE"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("DML-Updates-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = globalStatusDelta_ByVariable.get("CREATED_TMP_DISK_TABLES");
                graphiteMetric = (metric != null) ? new GraphiteMetric("CreatedTmpTablesOnDisk-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = globalStatusDelta_ByVariable.get("CREATED_TMP_TABLES");
                graphiteMetric = (metric != null) ? new GraphiteMetric("CreatedTmpTables-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getDifference(globalStatusDelta_ByVariable.get("CREATED_TMP_TABLES"), globalStatusDelta_ByVariable.get("CREATED_TMP_DISK_TABLES"));
                graphiteMetric = (metric != null) ? new GraphiteMetric("CreatedTmpTablesInMem-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_DATA_READ"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbDataRead-BytesPerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_DATA_WRITTEN"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbDataWritten-BytesPerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROW_LOCK_TIME");
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowLockTime-MsPerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_DELETED"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsDeleted-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_DELETED");
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsDeleted-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_INSERTED"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsInserted-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_INSERTED");
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsInserted-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_READ"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsRead-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_READ");
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsRead-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_UPDATED"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsUpdated-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_UPDATED");
                graphiteMetric = (metric != null) ? new GraphiteMetric("InnodbRowsUpdated-PerInterval", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("QUERIES"), millisecondsBetweenSamples_BigDecimal);
                graphiteMetric = (metric != null) ? new GraphiteMetric("Queries-PerSec", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                metric = getUsagePercent(currentGlobalStatus.get("THREADS_CONNECTED"), globalVariables.get("MAX_CONNECTIONS"));
                graphiteMetric = (metric != null) ? new GraphiteMetric("ConnectionUsed-Pct", metric, currentTimestampInSeconds_Status) : null;
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
            }
            else {
                previousGlobalStatus_ = new HashMap<>();
                previousTimestampInMilliseconds_Status_ = null;
                
                graphiteMetric = new GraphiteMetric("Available", BigDecimal.ZERO, currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
            }
            
            // innodb mem usage
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private GraphiteMetric getSimpleGlobalStatusMetric(Map<String,String> globalStatus, String statusKey, String metricName, int timestampInSeconds) {
        try {
            String statusValue = globalStatus.get(statusKey);
            if (statusValue == null) return null;
            else if (statusValue.equalsIgnoreCase("ON")) return new GraphiteMetric(metricName, new BigDecimal(1), timestampInSeconds);
            else if (statusValue.equalsIgnoreCase("OFF")) return new GraphiteMetric(metricName, new BigDecimal(0), timestampInSeconds);
            return new GraphiteMetric(metricName, new BigDecimal(statusValue), timestampInSeconds);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    private Map<String,BigDecimal> getGlobalStatusDelta(Map<String,String> currentGlobalStatus, 
            Map<String,String> previousGlobalStatus, List<String> globalStatusVariablesToDelta) {
        
        if ((currentGlobalStatus == null) || (previousGlobalStatus == null) || (globalStatusVariablesToDelta == null)) {
            return new HashMap<>();
        }
        
        Map<String,BigDecimal> globalStatusDelta_ByVariable = new HashMap<>();
        
        for (String globalStatusVariable : globalStatusVariablesToDelta) {
            try {
                String previousGlobalStatusValue = previousGlobalStatus.get(globalStatusVariable);
                String currentGlobalStatusValue = currentGlobalStatus.get(globalStatusVariable);
                if ((previousGlobalStatusValue == null) || (currentGlobalStatusValue == null)) continue;
                if (!StringUtils.isNumeric(previousGlobalStatusValue) || !StringUtils.isNumeric(currentGlobalStatusValue)) continue;

                BigDecimal previousGlobalStatusValue_BigDecimal = new BigDecimal(previousGlobalStatusValue);
                BigDecimal currentGlobalStatusValue_BigDecimal = new BigDecimal(currentGlobalStatusValue);
                BigDecimal deltaGlobalStatusValue_BigDecimal = currentGlobalStatusValue_BigDecimal.subtract(previousGlobalStatusValue_BigDecimal);
                
                globalStatusDelta_ByVariable.put(globalStatusVariable, deltaGlobalStatusValue_BigDecimal);
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return globalStatusDelta_ByVariable;
    }
    
    private BigDecimal getRatePerSecond(BigDecimal sumValue, BigDecimal millisecondsBetweenSamples) {
        if ((sumValue == null) || (millisecondsBetweenSamples == null)) return null;
        
        try {
            BigDecimal rateValue = sumValue.divide(millisecondsBetweenSamples, SCALE, ROUNDING_MODE).multiply(ONE_THOUSAND);
            return rateValue;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    private BigDecimal getDifference(BigDecimal minuend, BigDecimal subtrahend) {
        if ((minuend == null) || (subtrahend == null)) return null;
        
        try {
            BigDecimal difference = minuend.subtract(subtrahend);
            return difference;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    private BigDecimal getUsagePercent(String numerator, String denominator) {
        if ((numerator == null) || (denominator == null)) return null;
        
        try {
            BigDecimal currentConnectionsCount_BigDecimal = new BigDecimal(numerator);
            BigDecimal maxConnectionsCount_BigDecimal = new BigDecimal(denominator);
            BigDecimal value = currentConnectionsCount_BigDecimal.divide(maxConnectionsCount_BigDecimal, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            return value;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    private Map<String,String> getGlobalStatus(Connection connection) {
        
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
            
            Map<String,String> globalStatus = new HashMap<>();
            
            while (resultSet.next()) {
                String variableName = resultSet.getString("VARIABLE_NAME");
                if (resultSet.wasNull()) variableName = null;
                
                String variableValue = resultSet.getString("VARIABLE_VALUE");
                if (resultSet.wasNull()) variableValue = null;
                
                if ((variableName != null) && (variableValue != null)) globalStatus.put(variableName, variableValue);
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
    
    private Map<String,String> getGlobalVariables(Connection connection) {
        
        if (connection == null) {
            return null;
        }
        
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            if (!DatabaseUtils.isConnectionValid(connection)) return null;

            String query = "select * from information_schema.GLOBAL_VARIABLES";
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
            if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
            
            Map<String,String> globalStatuses = new HashMap<>();
            
            while (resultSet.next()) {
                String variableName = resultSet.getString("VARIABLE_NAME");
                if (resultSet.wasNull()) variableName = null;
                
                String variableValue = resultSet.getString("VARIABLE_VALUE");
                if (resultSet.wasNull()) variableValue = null;
                
                if ((variableName != null) && (variableValue != null)) globalStatuses.put(variableName, variableValue);
            }

            return globalStatuses;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
        finally {
            DatabaseUtils.cleanup(statement, resultSet);
        } 
        
    }
    
    private static List<String> getGlobalStatusVariablesToDelta() {
     
        List<String> globalStatusVariablesToDelta = new ArrayList<>();
        
        globalStatusVariablesToDelta.add("BYTES_RECEIVED");
        globalStatusVariablesToDelta.add("BYTES_SENT");
        globalStatusVariablesToDelta.add("COM_COMMIT");
        globalStatusVariablesToDelta.add("COM_DELETE");
        globalStatusVariablesToDelta.add("COM_INSERT");
        globalStatusVariablesToDelta.add("COM_ROLLBACK");
        globalStatusVariablesToDelta.add("COM_SELECT");
        globalStatusVariablesToDelta.add("COM_UPDATE");
        globalStatusVariablesToDelta.add("CREATED_TMP_DISK_TABLES");
        globalStatusVariablesToDelta.add("CREATED_TMP_TABLES");
        globalStatusVariablesToDelta.add("INNODB_DATA_READ");
        globalStatusVariablesToDelta.add("INNODB_DATA_WRITTEN");
        globalStatusVariablesToDelta.add("INNODB_ROW_LOCK_TIME");
        globalStatusVariablesToDelta.add("INNODB_ROWS_DELETED");
        globalStatusVariablesToDelta.add("INNODB_ROWS_INSERTED");
        globalStatusVariablesToDelta.add("INNODB_ROWS_READ");
        globalStatusVariablesToDelta.add("INNODB_ROWS_UPDATED");
        globalStatusVariablesToDelta.add("QUERIES");
        
        return globalStatusVariablesToDelta;
    }
    
}
