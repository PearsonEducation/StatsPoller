# Database Querier

The 'Database Querier' collector is a metric collector that connects to supported databases through JDBC and executes arbitrary queries to collect metrics from databases. The custom queries are expected to be lightweight (execute quickly) & are generally expected to query for business metrics. If multiple queries are specified, they will be executed sequentially (serially). Supported databases include MySQL, MariaDB, and PostgreSQL.

The query results are expected to have 2 columns: STAT_NAME & STAT_VALUE.
The queries can return multiple rows, but the 'STAT_NAME' value must be unique for each row returned. The expected formats are:

 * Column 1: STAT_NAME (a string -- an meaningful name for the metric. The character-set should stick to letters, numbers, dashes, and underscores)  
 * Column 2: STAT_VALUE (a numeric value of any type)

### Metrics

* Available : Was StatsPoller able to connect to the database? Outputs 1 if true, 0 if false.  
* STAT_NAME.Result : STAT_VALUE (the value of the query result).  
* STAT_NAME.QueryTime_Ms : The amount of time, in milliseconds, that it took to run the query.  

### Example Input

select 'MyMetric' as STAT_NAME, count(*) as STAT_VALUE from MyTable  

### Example Output (Graphite Formatted)

Available 1 1492491882  
MyMetric.Result 3981 1492491882  
MyMetric.QueryTime_Ms 20 1492491882  
