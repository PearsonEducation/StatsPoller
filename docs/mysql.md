# MySQL Collector

The 'MySQL' collector is a metric collector that connects to MySQL through JDBC and queries MySQL's metadata tables for metrics. The complete list of metrics collected is listed below.

### Metrics

* Available : Was StatsPoller able to connect to MySQL? Outputs 1 if true, 0 if false.
* Innodb Deadlocks Since Reset : The number of deadlocks that have occurred since MySQL last reset this metric. This metric may only be found in Percona and/or MariaDB. This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_DEADLOCKS
* Innodb Current Row Locks : The number of deadlocks that have occurred since MySQL last reset this metric. This metric may only be found in Percona and/or MariaDB. This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_CURRENT_ROW_LOCKS
* Open Files - Count : The current number of files that are open (MySQL data files). This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.OPEN_FILES
* Connections Busy - Count : The number of connections that are not idle. This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.THREADS_RUNNING
* Connections Open - Count : The number of connections that currently open (including idle). This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.THREADS_CONNECTED
* Connections Used % : The percentage of used connections. This metric is derived from (INFORMATION_SCHEMA.GLOBAL_STATUS.THREADS_CONNECTED / INFORMATION_SCHEMA.GLOBAL_VARIABLES.MAX_CONNECTIONS).
* Uptime - Sec : How long has MySQL been running (in seconds)? This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.UPTIME
* Bytes Received / Second : The current rate that MySQL is receiving data (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.BYTES_RECEIVED
* Bytes Sent / Second : The current rate that MySQL is transmitting data (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.BYTES_SENT
* TX Commits / Second : The current rate of transaction commits (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.COM_COMMIT
* TX Rollbacks / Second : The current rate of transaction rollbacks (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.COM_ROLLBACK
* DML Deletes / Second : The current rate of deletes (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.COM_DELETE
* DML Inserts / Second : The current rate of inserts (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.COM_INSERT
* DML Selects / Second : The current rate of selects (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.COM_SELECT
* DML Updates / Second : The current rate of updates (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.COM_UPDATE
* Created Tmp Tables On Disk / Interval : The number of temporary tables that were created on disk since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.CREATED_TMP_DISK_TABLES
* Created Tmp Tables / Interval : The number of temporary tables that were created since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.CREATED_TMP_TABLES
* Created Tmp Tables In Memory / Interval : The number of in-memory temporary tables that were created since the last time StatsPoller queried for this information (StatsPoller collection inverval). This derived by taking (CREATED_TMP_TABLES - CREATED_TMP_DISK_TABLES).
* Innodb Data Read - Bytes / Sec : The current rate that MySQL-Innodb is reading data (bytes per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_DATA_READ
* Innodb Data Written - Bytes / Sec : The current rate that MySQL-Innodb is writing data (bytes per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_DATA_WRITTEN
* Innodb Row Lock Time - Milliseconds / Interval : The amount of time that MySQL-Innodb  waited to acquire row locks. This is the number of milliseconds since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROW_LOCK_TIME
* Innodb Rows Deleted / Sec : The current rate that MySQL-Innodb is deleting rows (rows per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_DELETED
* Innodb Rows Deleted / Interval : The total number of rows that MySQL-Innodb has deleted since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_DELETED
* Innodb Rows Inserted / Sec : The current rate that MySQL-Innodb is inserting rows (rows per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_INSERTED
* Innodb Rows Inserted / Interval : The total number of rows that MySQL-Innodb has inserted since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_INSERTED
* Innodb Rows Read / Sec : The current rate that MySQL-Innodb is reading rows (rows per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_READ
* Innodb Rows Read / Interval : The total number of rows that MySQL-Innodb has read since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_UPDATED
* Innodb Rows Updated / Sec : The current rate that MySQL-Innodb is updating rows (rows per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_READ
* Innodb Rows Updated / Interval : The total number of rows that MySQL-Innodb has updated since the last time StatsPoller queried for this information (StatsPoller collection inverval). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.INNODB_ROWS_UPDATED
* Queries / Second : The current rate of statements (queries) being executed by the server (per second). This metric is derived from INFORMATION_SCHEMA.GLOBAL_STATUS.QUERIES
* Slave Running : This is '1' if this server is a replication slave that is connected to a replication master, and both the I/O and SQL threads are running; otherwise, it is '0'. This is a direct reading of INFORMATION_SCHEMA.GLOBAL_STATUS.SLAVE_RUNNING
* Slave Behind Master - Seconds: The time difference (in seconds) between the slave SQL thread and the slave I/O thread. This can be used to gauge how much MySQL replication is lagging (higher value = more lag). This is a direct reading of SLAVE_STATUS -> Seconds_Behind_Master
* Slave Sql Running? : Is the slave SQL thread is started? Outputs 1 if true, 0 if false. This is a direct reading of SLAVE_STATUS -> Slave_SQL_Running
* Slave Io Running? : Is the slave I/O thread is started and successfully connected to the master? Outputs 1 if true, 0 if false. This is a direct reading of SLAVE_STATUS -> Slave_IO_Running

### Example output (OpenTSDB formatted)

Available 1468113383111 1 Host=192.168.1.5 Port=3306  
InnodbDeadlocksSinceReset-Count 1468113383111 75 Host=192.168.1.5 Port=3306  
InnodbCurrentRowLocks-Count 1468113383111 17 Host=192.168.1.5 Port=3306  
OpenFiles-Count 1468113383111 64 Host=192.168.1.5 Port=3306  
ConnectionsBusy-Count 1468113383111 9 Host=192.168.1.5 Port=3306  
ConnectionsOpen-Count 1468113383111 935 Host=192.168.1.5 Port=3306  
ConnectionsUsed-Pct 1468113383111 46.75 Host=192.168.1.5 Port=3306  
Uptime-Secs 1468113383111 772198 Host=192.168.1.5 Port=3306  
BytesReceived-PerSec 1468113383111 72220.3851 Host=192.168.1.5 Port=3306  
BytesSent-PerSec 1468113383111 585571.533 Host=192.168.1.5 Port=3306  
TX-Commits-PerSec 1468113383111 2.0052 Host=192.168.1.5 Port=3306  
TX-Rollbacks-PerSec 1468113383111 0.034 Host=192.168.1.5 Port=3306  
DML-Deletes-PerSec 1468113383111 1.7333 Host=192.168.1.5 Port=3306  
DML-Inserts-PerSec 1468113383111 1.4274 Host=192.168.1.5 Port=3306  
DML-Selects-PerSec 1468113383111 249.3585 Host=192.168.1.5 Port=3306  
DML-Updates-PerSec 1468113383111 2.2771 Host=192.168.1.5 Port=3306  
CreatedTmpTablesOnDisk-PerInterval 1468113383111 60 Host=192.168.1.5 Port=3306  
CreatedTmpTables-PerInterval 1468113383111 682 Host=192.168.1.5 Port=3306  
CreatedTmpTablesInMem-PerInterval 1468113383111 622 Host=192.168.1.5 Port=3306  
InnodbDataRead-BytesPerSec 1468113383111 29790.6095 Host=192.168.1.5 Port=3306  
InnodbDataWritten-BytesPerSec 1468113383111 672011.4194 Host=192.168.1.5 Port=3306  
InnodbRowLockTime-MsPerInterval 1468113383111 0 Host=192.168.1.5 Port=3306  
InnodbRowsDeleted-PerSec 1468113383111 11.7253 Host=192.168.1.5 Port=3306  
InnodbRowsDeleted-PerInterval 1468113383111 690 Host=192.168.1.5 Port=3306  
InnodbRowsInserted-PerSec 1468113383111 1.4274 Host=192.168.1.5 Port=3306  
InnodbRowsInserted-PerInterval 1468113383111 84 Host=192.168.1.5 Port=3306  
InnodbRowsRead-PerSec 1468113383111 9309098.7306 Host=192.168.1.5 Port=3306  
InnodbRowsRead-PerInterval 1468113383111 547812533 Host=192.168.1.5 Port=3306  
InnodbRowsUpdated-PerSec 1468113383111 442.0446 Host=192.168.1.5 Port=3306  
InnodbRowsUpdated-PerInterval 1468113383111 26013 Host=192.168.1.5 Port=3306  
Queries-PerSec 1468113383111 329.1757 Host=192.168.1.5 Port=3306  
SlaveRunning 1468113383111 1 Host=192.168.1.5 Port=3306  
SlaveBehindMaster-Secs 1468113383111 0 Host=192.168.1.5 MasterHost=192.168.1.4 MasterPort=3306   MasterServerId=12 Port=3306  
SlaveSqlRunning 1468113383111 1 Host=192.168.1.5 MasterHost=192.168.1.4 MasterPort=3306   MasterServerId=12 Port=3306  
SlaveIoRunning 1468113383111 1 Host=192.168.1.5 MasterHost=192.168.1.4 MasterPort=3306   MasterServerId=12 Port=3306  
