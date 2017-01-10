package com.pearson.statspoller.internal_metric_collectors.postgres;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Judah Walker
 */
public class PostgresMetricCollector extends InternalCollectorFramework implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(PostgresMetricCollector.class.getName());

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

    private Map<String, String> previousStatistics_ = new HashMap<>();
    private Long previousTimestampInMilliseconds_ = null;
    private final List<String> databases_ = new ArrayList();
    
    public PostgresMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix,
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String username, String password, String jdbcString,
            List<OpenTsdbTag> openTsdbTags) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
        this.jdbcString_ = ((jdbcString == null) || jdbcString.isEmpty()) ? "jdbc:postgresql://" + host_ + ":" + port_ + "/postgres" : jdbcString;
        this.isUserSpecifiedJdbcString_ = ((jdbcString != null) && !jdbcString.isEmpty());
        this.openTsdbTags_ = openTsdbTags;
    }

    @Override
    public void run() {

        while (super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();

            // connect to the db
            Connection connection = !isUserSpecifiedJdbcString_ ? DatabaseUtils.connect(jdbcString_, username_, password_) : DatabaseUtils.connect(jdbcString_);

            List<String> statisticsVariablesToDelta = getStatisticsVariablesToDelta(connection);

            List<OpenTsdbMetric> openTsdbMetrics = getPostgresMetrics(connection, statisticsVariablesToDelta);

            // disconnect from the db
            DatabaseUtils.disconnect(connection);

            // output opentsdb metrics
            super.outputOpenTsdbMetrics(openTsdbMetrics);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;

            logger.info("Finished PostgreSQL metric collection routine. PostgreSQL Server=" + host_ + ":" + port_
                    + ", MetricsCollected=" + openTsdbMetrics.size()
                    + ", MetricCollectionTime=" + routineTimeElapsed);

            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) {
                Threads.sleepMilliseconds(sleepTimeInMs);
            }
        }

    }

    private List<OpenTsdbMetric> getPostgresMetrics(Connection connection, List<String> statisticsVariablesToDelta) {

        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
        OpenTsdbMetric openTsdbMetric;
        BigDecimal metric;

        try {
            boolean isConnectionValid = DatabaseUtils.isConnectionValid(connection, 5);

            if (!isConnectionValid || connection.isClosed()) {
                openTsdbMetric = new OpenTsdbMetric("Available", System.currentTimeMillis(), BigDecimal.ZERO, openTsdbTags_);
                openTsdbMetrics.add(openTsdbMetric);
                logger.warn("PostgresServer=" + host_ + ":" + port_ + " is unavailable");
                return openTsdbMetrics;
            } 
            else {
                connection.setReadOnly(true);
            }

            long currentTimestampMilliseconds_Status = System.currentTimeMillis();

            Map<String, String> currentStatistics = getStatistics(connection, "");

            try {
                for (String variableValue : databases_) {
                    if (variableValue != null) {
                        Connection tempConnection = !isUserSpecifiedJdbcString_
                                ? DatabaseUtils.connect("jdbc:postgresql://" + host_ + ":" + port_ + "/" + variableValue, username_, password_)
                                : DatabaseUtils.connect("jdbc:postgresql://" + host_ + ":" + port_ + "/" + variableValue);
                        Map<String, String> tempStatistics = getStatistics(tempConnection, variableValue);
                        currentStatistics.putAll(tempStatistics);
                    }
                }
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }

            if ((previousStatistics_ == null) || (previousTimestampInMilliseconds_ == null)) {
                previousStatistics_ = currentStatistics;
                previousTimestampInMilliseconds_ = currentTimestampMilliseconds_Status;
                return openTsdbMetrics;
            }

            if ((currentStatistics != null) && !currentStatistics.isEmpty()) {
                Map<String, String> previousStatistics = previousStatistics_;
                previousStatistics_ = currentStatistics; // sets up previousStatistics_ for the next iteration
                previousTimestampInMilliseconds_ = currentTimestampMilliseconds_Status; // sets up previousTimestampInMilliseconds_ for the next iteration

                Map<String, BigDecimal> statisticsDelta_ByVariable = getStatisticsDelta(currentStatistics, previousStatistics, statisticsVariablesToDelta);

                //List of all metrics gathered from Postgres
                //------------------------------------------
                //Global level Metrics
                //the number of times that the bgwriter has overwritten the maximum number of buffers allowed in that round, higher is badder - maxwritten_clean
                //checkpoints timed - checkpoints_timed
                //checkpoints_req - checkpoints_req
                //what external processes are forced to ask for more space many times in the buffers - buffers_backend
                //extra bg stats - checkpoint_write_time, checkpoint_sync_time, buffers_clean, buffers_alloc, buffers_checkpoint
                //open connections - open connections
                //max connections - max_connections %of connections
                //slave running - slave running
                //replication lag - replication_lag
                //uptime - uptime
                //-------------------------------------------
                //Per Database level Metrics
                //Number of locked elements - lock_count
                //rate of cache hit, the higher the number is the better - cache_rate
                //ratio of cache hit, the closer to 1 the better - cache_hit_ratio
                //number of commits - number_of_commit delta
                //number of rollbacks - number_of_rollback delta
                //number of insertions - number_of_inserted delta
                //number of updates - number_of_updated delta
                //number of deletes - number_of_deleted delta
                //number of temp files - number_of_temp_files
                //idx scan ratio - idx_scan_ratio
                //Size
                
                openTsdbMetric = new OpenTsdbMetric("Available", currentTimestampMilliseconds_Status, BigDecimal.ONE, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "maxwritten_clean", "bgwriter.MaxWrittenClean", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "checkpoints_timed", "bgwriter.CheckPointsTimed", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "checkpoints_req", "bgwriter.CheckPointsReq", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "checkpoint_write_time", "bgwriter.CheckPointWriteTime", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "checkpoint_sync_time", "bgwriter.CheckPointSyncTime", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "buffers_backend", "bgwriter.BuffersBackend", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "buffers_backend", "bgwriter.BuffersBackendFsync", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "buffers_clean", "bgwriter.BuffersClean", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "buffers_alloc", "bgwriter.BuffersAlloc", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "buffers_checkpoint", "bgwriter.BuffersCheckPoint", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
                
                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "open_connections", "ConnectionsOpen-Count", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                metric = getUsagePercent(currentStatistics.get("open_connections"), currentStatistics.get("max_connections"));
                openTsdbMetric = (metric != null) ? new OpenTsdbMetric("ConnectionsUsed-Pct", currentTimestampMilliseconds_Status, metric, openTsdbTags_) : null;
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "slave_running", "SlaveRunning", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "replication_lag", "ReplicationLag", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, "uptime", "Uptime-Secs", currentTimestampMilliseconds_Status, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);


                try {
                    for (String variableValue : databases_) {
                        if (variableValue != null) {
                            openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, variableValue + "." + "lock_count", "DatabaseSpecific." + variableValue + ".LockedElements-Count", currentTimestampMilliseconds_Status, openTsdbTags_);
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);


                            openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, variableValue + "." + "cache_rate", "DatabaseSpecific." + variableValue + ".CacheRate", currentTimestampMilliseconds_Status, openTsdbTags_);
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, variableValue + "." + "cache_hit_ratio", "DatabaseSpecific." + variableValue + ".CacheHit-Pct", currentTimestampMilliseconds_Status, openTsdbTags_);
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            metric = getDifference(statisticsDelta_ByVariable.get(variableValue + "." + "number_of_commit"), statisticsDelta_ByVariable.get(variableValue + "." + "number_of_commit"));
                            openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DatabaseSpecific." + variableValue + ".CountOfCommit-PerInterval", currentTimestampMilliseconds_Status, metric, openTsdbTags_) : null;
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            metric = getDifference(statisticsDelta_ByVariable.get(variableValue + "." + "number_of_rollbacks"), statisticsDelta_ByVariable.get(variableValue + "." + "number_of_rollbacks"));
                            openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DatabaseSpecific." + variableValue + ".CountOfRollbacks-PerInterval", currentTimestampMilliseconds_Status, metric, openTsdbTags_) : null;
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            metric = getDifference(statisticsDelta_ByVariable.get(variableValue + "." + "number_of_inserted"), statisticsDelta_ByVariable.get(variableValue + "." + "number_of_inserted"));
                            openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DatabaseSpecific." + variableValue + ".CountOfInserted-PerInterval", currentTimestampMilliseconds_Status, metric, openTsdbTags_) : null;
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            metric = getDifference(statisticsDelta_ByVariable.get(variableValue + "." + "number_of_updated"), statisticsDelta_ByVariable.get(variableValue + "." + "number_of_updated"));
                            openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DatabaseSpecific." + variableValue + ".CountOfUpdated-PerInterval", currentTimestampMilliseconds_Status, metric, openTsdbTags_) : null;
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            metric = getDifference(statisticsDelta_ByVariable.get(variableValue + "." + "number_of_deleted"), statisticsDelta_ByVariable.get(variableValue + "." + "number_of_deleted"));
                            openTsdbMetric = (metric != null) ? new OpenTsdbMetric("DatabaseSpecific." + variableValue + ".CountOfDeleted-PerInterval", currentTimestampMilliseconds_Status, metric, openTsdbTags_) : null;
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, variableValue + "." + "number_of_temp_files", "DatabaseSpecific." + variableValue + ".CreatedTmpTables-Count", currentTimestampMilliseconds_Status, openTsdbTags_);
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            openTsdbMetric = getSimpleStatisticsMetric(currentStatistics, variableValue + "." + "idx_scan_ratio", "DatabaseSpecific." + variableValue + ".IdxScanRatio", currentTimestampMilliseconds_Status, openTsdbTags_);
                            if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);

                            String querySize = "SELECT pg_size_pretty(pg_database_size('" + variableValue + "'))";
                            Statement statementSize = connection.createStatement();
                            ResultSet resultSetSize = statementSize.executeQuery(querySize);
                            resultSetSize.next();
                            String size = resultSetSize.getString("pg_size_pretty").split(" ")[0];
                            openTsdbMetric = new OpenTsdbMetric("DatabaseSpecific." + variableValue + ".DatabaseSize-kB", currentTimestampMilliseconds_Status, new BigDecimal(size), openTsdbTags_);
                            openTsdbMetrics.add(openTsdbMetric);
                        }
                    }

                } 
                catch (Exception e) {
                    logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                }

                if (openTsdbMetrics.size() > 0) {
                    Collections.sort(openTsdbMetrics, new Comparator<OpenTsdbMetric>() {
                        @Override
                        public int compare(final OpenTsdbMetric object1, final OpenTsdbMetric object2) {
                            return object1.getMetricKey().compareToIgnoreCase(object2.getMetricKey());
                        }
                    });
                }

            } 
            else {
                previousStatistics_ = new HashMap<>();
                previousTimestampInMilliseconds_ = null;

                openTsdbMetric = new OpenTsdbMetric("Available", currentTimestampMilliseconds_Status, BigDecimal.ZERO, openTsdbTags_);
                if (openTsdbMetric != null) openTsdbMetrics.add(openTsdbMetric);
            }
        } 
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }

        return openTsdbMetrics;
    }

    private OpenTsdbMetric getSimpleStatisticsMetric(Map<String, String> statistics, String statusKey, String metricName, long timestampInMilliseconds, List<OpenTsdbTag> opentsdbTags) {
        try {
            String statusValue = statistics.get(statusKey);
            
            if (statusValue == null) {
                return null;
            } 
            else if (statusValue.equalsIgnoreCase("ON")) {
                return new OpenTsdbMetric(metricName, timestampInMilliseconds, new BigDecimal(1), opentsdbTags);
            } 
            else if (statusValue.equalsIgnoreCase("OFF")) {
                return new OpenTsdbMetric(metricName, timestampInMilliseconds, new BigDecimal(0), opentsdbTags);
            }
            
            return new OpenTsdbMetric(metricName, timestampInMilliseconds, new BigDecimal(statusValue), opentsdbTags);
        } 
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    private Map<String, BigDecimal> getStatisticsDelta(Map<String, String> currentStatistics,
            Map<String, String> previousStatistics, List<String> statisticsVariablesToDelta) {

        if ((currentStatistics == null) || (previousStatistics == null) || (statisticsVariablesToDelta == null)) {
            return new HashMap<>();
        }

        Map<String, BigDecimal> statisticsDelta_ByVariable = new HashMap<>();

        for (String statisticsVariable : statisticsVariablesToDelta) {
            try {
                String previousStatisticsValue = previousStatistics.get(statisticsVariable);
                String currentStatisticsValue = currentStatistics.get(statisticsVariable);
                
                if ((previousStatisticsValue == null) || (currentStatisticsValue == null)) continue;
                if (!StringUtils.isNumeric(previousStatisticsValue) || !StringUtils.isNumeric(currentStatisticsValue)) continue;

                BigDecimal previousStatisticsValue_BigDecimal = new BigDecimal(previousStatisticsValue);
                BigDecimal currentStatisticsValue_BigDecimal = new BigDecimal(currentStatisticsValue);
                BigDecimal deltaStatisticsValue_BigDecimal = currentStatisticsValue_BigDecimal.subtract(previousStatisticsValue_BigDecimal);

                statisticsDelta_ByVariable.put(statisticsVariable, deltaStatisticsValue_BigDecimal);
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }

        return statisticsDelta_ByVariable;
    }

    private BigDecimal getRatePerSecond(BigDecimal sumValue, BigDecimal millisecondsBetweenSamples) {
        if ((sumValue == null) || (millisecondsBetweenSamples == null) || (BigDecimal.ZERO.compareTo(millisecondsBetweenSamples) == 0)) {
            return null;
        }

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
        if ((minuend == null) || (subtrahend == null)) {
            return null;
        }

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
        if ((numerator == null) || (denominator == null)) {
            return null;
        }

        try {
            BigDecimal currentConnectionsCount_BigDecimal = new BigDecimal(numerator);
            BigDecimal maxConnectionsCount_BigDecimal = new BigDecimal(denominator);

            if (BigDecimal.ZERO.compareTo(maxConnectionsCount_BigDecimal) == 0) {
                return null;
            }

            BigDecimal value = currentConnectionsCount_BigDecimal.divide(maxConnectionsCount_BigDecimal, SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
            
            return value;
        } 
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        }
    }

    private Map<String, String> getStatistics(Connection connection, String database) {

        if (connection == null) {
            return null;
        }

        Statement statement = null;
        ResultSet resultSet = null;
        String query;
        Map<String, String> statistics = new HashMap<>();
        try {
            //List of all metrics gathered from Postgres
            //------------------------------------------
            //Global level Metrics
            //the number of times that the bgwriter has overwritten the maximum number of buffers allowed in that round, higher is badder - maxwritten_clean
            //checkpoints timed - checkpoints_timed
            //checkpoints_req - checkpoints_req
            //what external processes are forced to ask for more space many times in the buffers - buffers_backend
            //extra bg stats - checkpoint_write_time, checkpoint_sync_time, buffers_clean, buffers_alloc, buffers_checkpoint
            //open connections - open connections
            //max connections - max_connections %of connections
            //slave running - slave running
            //replication lag - replication_lag
            //uptime - uptime
            //-------------------------------------------
            //Per Database level Metrics
            //Number of locked elements - lock_count
            //rate of cache hit, the higher the number is the better - cache_rate
            //ratio of cache hit, the closer to 1 the better - cache_hit_ratio
            //number of commits - number_of_commit delta
            //number of rollbacks - number_of_rollback delta
            //number of insertions - number_of_inserted delta
            //number of updates - number_of_updated delta
            //number of deletes - number_of_deleted delta
            //number of temp files - number_of_temp_files
            //idx scan ratio - idx_scan_ratio
            
            if (!database.equals("")) {
                //Number of locked elements
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT COUNT(*) FROM pg_locks";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("count");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "lock_count", variableValue);
                }

                //rate of cache hit, the higher the number is the better
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT SUM(blks_hit) / SUM(blks_read) AS div FROM pg_stat_database";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("div");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "cache_rate", variableValue);
                }

                //ratio of cache hit, the closer to 1 the better
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT blks_hit::float/(blks_read + blks_hit) as cache_hit_ratio FROM pg_stat_database WHERE datname=current_database()";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("cache_hit_ratio");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "cache_hit_ratio", variableValue);
                }
                
                //total number of transactions
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT SUM(xact_commit) AS \"sum_commit\", SUM(xact_rollback) AS \"sum_rollback\", SUM(tup_inserted) AS \"sum_inserted\", SUM(tup_updated) AS \"sum_updated\", SUM(tup_deleted) AS \"sum_deleted\", SUM(temp_files) AS \"sum_temp_files\" FROM pg_stat_database";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("sum_commit");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "number_of_commit", variableValue);

                    variableValue = resultSet.getString("sum_rollback");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "number_of_rollback", variableValue);

                    variableValue = resultSet.getString("sum_inserted");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "number_of_inserted", variableValue);

                    variableValue = resultSet.getString("sum_updated");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "number_of_updated", variableValue);

                    variableValue = resultSet.getString("sum_deleted");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "number_of_deleted", variableValue);

                    variableValue = resultSet.getString("sum_temp_files");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "number_of_temp_files", variableValue);
                }

                //index scan ratio
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT sum(idx_scan)/(sum(idx_scan) + sum(seq_scan)) as idx_scan_ratio FROM pg_stat_all_tables";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("idx_scan_ratio");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put(database + "." + "idx_scan_ratio", variableValue);
                }

            } 
            else {
                //open connections
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT COUNT(*) FROM pg_stat_activity";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("count");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("open_connections", variableValue);
                }

                //max connections
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SHOW max_connections";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("max_connections");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("max_connections", variableValue);
                }

                //bg elements
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT maxwritten_clean, checkpoints_timed, checkpoints_req, buffers_backend, checkpoint_write_time, checkpoint_sync_time, buffers_clean, buffers_alloc, buffers_checkpoint, buffers_backend_fsync FROM pg_stat_bgwriter";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("maxwritten_clean");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("maxwritten_clean", variableValue);

                    variableValue = resultSet.getString("checkpoints_timed");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("checkpoints_timed", variableValue);

                    variableValue = resultSet.getString("checkpoints_req");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("checkpoints_req", variableValue);

                    variableValue = resultSet.getString("buffers_backend");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("buffers_backend", variableValue);
                    
                    variableValue = resultSet.getString("checkpoint_write_time");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("checkpoint_write_time", variableValue);

                    variableValue = resultSet.getString("checkpoint_sync_time");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("checkpoint_sync_time", variableValue);

                    variableValue = resultSet.getString("buffers_clean");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("buffers_clean", variableValue);

                    variableValue = resultSet.getString("buffers_alloc");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("buffers_alloc", variableValue);

                    variableValue = resultSet.getString("buffers_checkpoint");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("buffers_checkpoint", variableValue);

                    variableValue = resultSet.getString("buffers_backend_fsync");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("buffers_backend_fsync", variableValue);
                }

                //slave running
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "select count(*) from pg_stat_replication";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("count");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) {
                        statistics.put("slave_running", variableValue);
                    }
                }
                
                //replication lag
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))::INT";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("date_part");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("replication_lag", variableValue);
                }

                //uptime
                if (!DatabaseUtils.isConnectionValid(connection)) return null;
                query = "SELECT EXTRACT(epoch FROM now() - pg_postmaster_start_time())";
                statement = connection.createStatement();
                resultSet = statement.executeQuery(query);                
                if (!DatabaseUtils.isResultSetValid(resultSet)) return null;
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("date_part");
                    if (resultSet.wasNull()) variableValue = null;
                    if (variableValue != null) statistics.put("uptime", variableValue);
                }
            }

            return statistics;
        } 
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return null;
        } 
        finally {
            DatabaseUtils.cleanup(statement, resultSet);
        }

    }

    private List<String> getStatisticsVariablesToDelta(Connection connection) {

        List<String> statisticsVariablesToDelta = new ArrayList<>();

        databases_.clear();

        try {
            Statement statement;
            ResultSet resultSet;
            String query = "SELECT datname FROM pg_database WHERE datistemplate = false AND datname <> 'rdsadmin'";
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
            
            if (DatabaseUtils.isResultSetValid(resultSet)) {
                while (resultSet.next()) {
                    String variableValue = resultSet.getString("datname");
                    if (resultSet.wasNull()) variableValue = null;
                    
                    if (variableValue != null) {
                        databases_.add(variableValue);
                        statisticsVariablesToDelta.add(variableValue + "." + "number_of_commit");
                        statisticsVariablesToDelta.add(variableValue + "." + "number_of_rollback");
                        statisticsVariablesToDelta.add(variableValue + "." + "number_of_inserted");
                        statisticsVariablesToDelta.add(variableValue + "." + "number_of_updated");
                        statisticsVariablesToDelta.add(variableValue + "." + "number_of_deleted");
                    }
                }
            }
        } 
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }

        return statisticsVariablesToDelta;
    }

}
