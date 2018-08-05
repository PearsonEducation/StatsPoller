package com.pearson.statspoller.internal_metric_collectors.db_querier;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import com.pearson.statspoller.utilities.db_utils.DatabaseUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class DbQuerier extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(DbQuerier.class.getName());
    
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    private final String username_;
    private final String password_;
    private final String jdbcString_;
    private final List<String> queries_;

    public DbQuerier(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String username, String password, String jdbcString, List<String> queries) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.username_ = username;
        this.password_ = password;
        this.jdbcString_ = jdbcString;
        this.queries_ = queries;
    }

    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // connect to the db
            Connection connection = DatabaseUtils.connect(jdbcString_, username_, password_);
            
            List<GraphiteMetric> graphiteMetrics = getMetrics(connection);
            
            // disconnect from the db
            DatabaseUtils.disconnect(connection);
            
            // output opentsdb metrics
            super.outputGraphiteMetrics(graphiteMetrics);
            
            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;

            logger.info("Finished Database Querier metric collection routine. Server=\"" + jdbcString_ + "\"" +
                    ", MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }

    private List<GraphiteMetric> getMetrics(Connection connection) {
        
        if (queries_ == null || queries_.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        GraphiteMetric graphiteMetric;
        
        try {
            boolean isConnectionValid = DatabaseUtils.isConnectionValid(connection, 5);

            if (!isConnectionValid || connection.isClosed()) {
                graphiteMetric = new GraphiteMetric("Available",  BigDecimal.ZERO, (int) (System.currentTimeMillis() / 1000));
                graphiteMetrics.add(graphiteMetric);
                logger.warn("Server=\"" + jdbcString_ + "\" is unavailable");
                return graphiteMetrics;
            }
            else {
                connection.setReadOnly(true);
                graphiteMetric = new GraphiteMetric("Available",  BigDecimal.ONE, (int) (System.currentTimeMillis() / 1000));
                graphiteMetrics.add(graphiteMetric);
            }
  
            for (String query : queries_) {
                long startQuery_TimestampMilliseconds = System.currentTimeMillis();
                int startQuery_TimestampMillisecond_Int = (int) (startQuery_TimestampMilliseconds / 1000);
                List<DbQuerier_Result> dbQuerier_Results = runQuery(connection, query);
                long elapsedTime_TimestampMilliseconds = System.currentTimeMillis() - startQuery_TimestampMilliseconds;
                
                if (dbQuerier_Results != null) {
                    for (DbQuerier_Result dbQuerier_Result : dbQuerier_Results) {
                        if (dbQuerier_Result == null) continue; 
                        
                        graphiteMetric = new GraphiteMetric(dbQuerier_Result.getStatName() + "." + "Result", dbQuerier_Result.getStatValue(), startQuery_TimestampMillisecond_Int);
                        graphiteMetrics.add(graphiteMetric);

                        graphiteMetric = new GraphiteMetric(dbQuerier_Result.getStatName() + "." + "QueryTime_Ms", new BigDecimal(elapsedTime_TimestampMilliseconds), startQuery_TimestampMillisecond_Int);
                        graphiteMetrics.add(graphiteMetric);
                    }
                }
            }
            
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
  
    private List<DbQuerier_Result> runQuery(Connection connection, String sql) {
        
        if (connection == null) {
            return new ArrayList<>();
        }
        
        List<DbQuerier_Result> dbQuerier_Results = new ArrayList<>();
        
        Statement statement = null;
        ResultSet resultSet = null;
         
        try {
            if (!DatabaseUtils.isConnectionValid(connection)) return dbQuerier_Results;

            statement = connection.createStatement();
            resultSet = statement.executeQuery(sql);
            if (!DatabaseUtils.isResultSetValid(resultSet)) return dbQuerier_Results;
                        
            while (resultSet.next()) {
                String variableName = resultSet.getString("STAT_NAME");
                if (resultSet.wasNull()) variableName = null;
                
                Object variableValue_Object = resultSet.getObject("STAT_VALUE");
                BigDecimal variableValue_BigDecimal = new BigDecimal(variableValue_Object.toString());
                if (resultSet.wasNull()) variableValue_BigDecimal = null;
                
                if ((variableName != null) && (variableValue_BigDecimal != null)) {
                    DbQuerier_Result dbQuerier_Result = new DbQuerier_Result(variableName, variableValue_BigDecimal);
                    dbQuerier_Results.add(dbQuerier_Result);
                }
            }

            return dbQuerier_Results;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return dbQuerier_Results;
        }
        finally {
            DatabaseUtils.cleanup(statement, resultSet);
        } 
        
    }
 
}
