package com.pearson.statspoller.internal_metric_collectors.mysql;

import com.pearson.statspoller.utilities.DatabaseUtils;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
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
    private final List<OpenTsdbTag> opentsdbTags_;
    
    private Map<String,String> previousGlobalStatus_ = new HashMap<>();
    private Long previousTimestampInMilliseconds_ = null;
    
    public MysqlMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String username, String password, String jdbcString, 
            List<OpenTsdbTag> tags) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
        this.jdbcString_ = ((jdbcString == null) || jdbcString.isEmpty()) ? "jdbc:mysql://" + host_ + ":" + port_ + "?connectTimeout=7500&socketTimeout=7500&autoReconnect=false" : jdbcString;
        this.isUserSpecifiedJdbcString_ = ((jdbcString != null) && !jdbcString.isEmpty());
        this.opentsdbTags_ = tags;
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
            logger.info("Finished MySQL metric collection routine. MyqlServer=" + host_ + ":" + port_ + 
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
            
            logger.info("Finished MySQL metric collection routine." + " MyqlServer=" + host_ + ":" + port_ + 
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
                openTsdbMetric = new OpenTsdbMetric("Available",  System.currentTimeMillis(), BigDecimal.ZERO, opentsdbTags_);
                openTsdbMetrics.add(openTsdbMetric);
                logger.warn("MyqlServer=" + host_ + ":" + port_ + " is unavailable");
                return openTsdbMetrics;
            }
            
            long currentTimestampMilliseconds_Status = System.currentTimeMillis();
            
            Map<String,String> currentGlobalStatus = getGlobalStatus(connection);
            Map<String,String> globalVariables = getGlobalVariables(connection);
            
            if ((previousGlobalStatus_ == null) || (previousTimestampInMilliseconds_ == null)) {
                previousGlobalStatus_ = currentGlobalStatus;
                previousTimestampInMilliseconds_ = currentTimestampMilliseconds_Status;
                return openTsdbMetrics;
            }
            
            if ((currentGlobalStatus != null) && !currentGlobalStatus.isEmpty()) {
                Map<String,String> previousGlobalStatus = previousGlobalStatus_;
                long previousTimestampMilliseconds = previousTimestampInMilliseconds_;
                previousGlobalStatus_ = currentGlobalStatus; // sets up previousGlobalStatus_ for the next iteration
                previousTimestampInMilliseconds_ = currentTimestampMilliseconds_Status; // sets up previousTimestampInMilliseconds_ for the next iteration
                
                long millisecondsBetweenSamples = currentTimestampMilliseconds_Status - previousTimestampMilliseconds;
                BigDecimal millisecondsBetweenSamples_BigDecimal = new BigDecimal(millisecondsBetweenSamples);
                Map<String,BigDecimal> globalStatusDelta_ByVariable = getGlobalStatusDelta(currentGlobalStatus, previousGlobalStatus, GLOBAL_STATUS_VARIABLES_TO_DELTA);
                
                openTsdbMetric = new OpenTsdbMetric("Available", currentTimestampMilliseconds_Status, BigDecimal.ONE, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "INNODB_DEADLOCKS", "InnodbDeadlocksSinceReset-Count", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "INNODB_CURRENT_ROW_LOCKS", "InnodbCurrentRowLocks-Count", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "OPEN_FILES", "OpenFiles-Count", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "SLAVE_RUNNING", "SlaveRunning", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "THREADS_RUNNING", "ConnectionsBusy-Count", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "THREADS_CONNECTED", "ConnectionsOpen-Count", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "UPTIME", "Uptime-Secs", currentTimestampMilliseconds_Status, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("BYTES_RECEIVED"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("BytesReceived-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("BYTES_SENT"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("BytesSent-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_COMMIT"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("TX-Commits-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_DELETE"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DML-Deletes-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_INSERT"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DML-Inserts-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_ROLLBACK"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("TX-Rollbacks-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_SELECT"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DML-Selects-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("COM_UPDATE"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DML-Updates-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = globalStatusDelta_ByVariable.get("CREATED_TMP_DISK_TABLES");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("CreatedTmpTablesOnDisk-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = globalStatusDelta_ByVariable.get("CREATED_TMP_TABLES");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("CreatedTmpTables-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getDifference(globalStatusDelta_ByVariable.get("CREATED_TMP_TABLES"), globalStatusDelta_ByVariable.get("CREATED_TMP_DISK_TABLES"));
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("CreatedTmpTablesInMem-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_DATA_READ"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbDataRead-BytesPerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_DATA_WRITTEN"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbDataWritten-BytesPerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROW_LOCK_TIME");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowLockTime-MsPerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_DELETED"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsDeleted-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_DELETED");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsDeleted-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_INSERTED"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsInserted-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_INSERTED");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsInserted-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_READ"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsRead-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_READ");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsRead-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("INNODB_ROWS_UPDATED"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsUpdated-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = globalStatusDelta_ByVariable.get("INNODB_ROWS_UPDATED");
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("InnodbRowsUpdated-PerInterval", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getRatePerSecond(globalStatusDelta_ByVariable.get("QUERIES"), millisecondsBetweenSamples_BigDecimal);
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("Queries-PerSec", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                metric = getUsagePercent(currentGlobalStatus.get("THREADS_CONNECTED"), globalVariables.get("MAX_CONNECTIONS"));
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("ConnectionUsed-Pct", currentTimestampMilliseconds_Status, metric, opentsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
            }
            else {
                previousGlobalStatus_ = new HashMap<>();
                previousTimestampInMilliseconds_ = null;
                
                openTsdbMetric = new OpenTsdbMetric("Available",  currentTimestampMilliseconds_Status, BigDecimal.ZERO, opentsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
            }
            
            // innodb mem usage
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }
    
    private OpenTsdbMetric getSimpleGlobalStatusMetric(Map<String,String> globalStatus, String statusKey, String metricName, long timestampInMilliseconds, List<OpenTsdbTag> opentsdbTags) {
        try {
            String statusValue = globalStatus.get(statusKey);
            if (statusValue == null) return null;
            else if (statusValue.equalsIgnoreCase("ON")) return new OpenTsdbMetric(metricName, timestampInMilliseconds, new BigDecimal(1), opentsdbTags);
            else if (statusValue.equalsIgnoreCase("OFF")) return new OpenTsdbMetric(metricName, timestampInMilliseconds, new BigDecimal(0), opentsdbTags);
            return new OpenTsdbMetric(metricName, timestampInMilliseconds, new BigDecimal(statusValue), opentsdbTags);
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
        if ((sumValue == null) || (millisecondsBetweenSamples == null) || (BigDecimal.ZERO.compareTo(millisecondsBetweenSamples) == 0)) return null;
        
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
            
            if (BigDecimal.ZERO.compareTo(maxConnectionsCount_BigDecimal) == 0) return null;
            
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
