package com.pearson.statspoller.internal_metric_collectors.mysql;

import com.pearson.statspoller.utilities.DatabaseUtils;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.math.BigDecimal;
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
public class MysqlMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MysqlMetricCollector.class.getName());
    
    private final String host_;
    private final int port_;
    private final String username_;
    private final String password_;
    
    private final String jdbcString_;
    
    private static Map<String,String> previousGlobalStatus_ = new HashMap<>();
    private static Integer previousTimestampInSeconds_Status_ = null;
    
    public MysqlMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String username, String password, String jdbcString) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
  
        jdbcString_ = ((jdbcString == null) || jdbcString.isEmpty()) ? "jdbc:mysql://" + host_ + ":" + port_ + "/?user=" + username_ + "&password=" + password_ : jdbcString;
    }

    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // connect to the db
            Connection connection = DatabaseUtils.connect(jdbcString_);
            
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
    
    private List<GraphiteMetric> getMysqlMetrics(Connection connection) {
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        GraphiteMetric graphiteMetric;
        
        try {
            Map<String,String> globalVariables = getGlobalVariables(connection);
            
            int currentTimestampInSeconds_Status = (int) (System.currentTimeMillis() / 1000);
            int previousTimestampInSeconds_Status = previousTimestampInSeconds_Status_;
            Map<String,String> currentGlobalStatus = getGlobalStatus(connection);
            Map<String,String> previousGlobalStatus = previousGlobalStatus_;
           
            if ((currentGlobalStatus != null) && !currentGlobalStatus.isEmpty()) {
                previousGlobalStatus_ = currentGlobalStatus;
                previousTimestampInSeconds_Status_ = currentTimestampInSeconds_Status;
                
                graphiteMetric = new GraphiteMetric("Available", BigDecimal.ONE, currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "OPEN_FILES", "OpenFiles", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "THREADS_RUNNING", "ThreadsRunning", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "THREADS_CONNECTED", "ThreadsConnected", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);

                graphiteMetric = getSimpleGlobalStatusMetric(currentGlobalStatus, "UPTIME", "Uptime-Secs", currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
            }
            else {
                graphiteMetric = new GraphiteMetric("Available", BigDecimal.ZERO, currentTimestampInSeconds_Status);
                if (graphiteMetric != null) graphiteMetrics.add(graphiteMetric);
                
                previousGlobalStatus_ = new HashMap<>();
                previousTimestampInSeconds_Status_ = null;
            }
            
            // innodb mem usage
            // connection usage %
            // bandwidth
            // sql rates (select, update, insert, delete)
            // tx rates (commits, rollbacks, etc)
            // replication
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private GraphiteMetric getSimpleGlobalStatusMetric(Map<String,String> globalStatus, String statusKey, String metricName, int timestampInSeconds) {
        try {
            String threadsRunning = globalStatus.get(statusKey);
            if (threadsRunning == null) return null;
            return new GraphiteMetric(metricName, new BigDecimal(threadsRunning), timestampInSeconds);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }
    
    private Map<String,String> getGlobalStatus(Connection connection) {
        
        Statement statement = null;
        
        try {
            if (!DatabaseUtils.isConnectionValid(connection)) return null;

            String query = "select * from information_schema.GLOBAL_STATUS";
            statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
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
            DatabaseUtils.cleanup(statement);
        } 
        
    }
    
    private Map<String,String> getGlobalVariables(Connection connection) {
        
        Statement statement = null;
        
        try {
            if (!DatabaseUtils.isConnectionValid(connection)) return null;

            String query = "select * from information_schema.GLOBAL_VARIABLES";
            statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
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
            DatabaseUtils.cleanup(statement);
        } 
        
    }
    
}
