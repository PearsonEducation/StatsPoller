# PostgreSQL Collector

The 'PostgreSQL' collector is a metric collector that connects to PostgreSQL through JDBC and queries PostgreSQL's various standard statistics views for metrics. StatsPoller can run up to 10,000 PostgreSQL collectors from a single StatsPoller deployment.

A single PostgreSQL collector's behavior is:

1. Connect to PostgreSQL 'postgres' database
1. Query for PostgreSQL Server Process specific metrics from:
  * pg_stat_activity
  * SHOW max_connections
  * pg_stat_bgwriter
  * pg_stat_replication
  * pg_last_xact_replay_timestamp()
  * pg_postmaster_start_time()
1. For each database found in pg_database (that is not Amazon's 'rdsadmin' database), make a new connection and query from:
  * pg_locks
  * pg_stat_database
  * pg_stat_all_tables
1. Disconnect all connections from PostgreSQL
1. Output the metrics
1. Repeat

### Metrics
Database-specific content will be repeated for each database present.

* bgwriter.MaxWrittenClean : Number of times the background writer stopped a cleaning scan because it had written too many buffers.
* bgwriter.CheckPointsTimed : Number of time scheduled checkpoints performed.
* bgwriter.CheckPointsReq : Number of requested checkpoints.
* bgwriter.CheckPointWriteTime : The total amount of time that has been spent in the portion of checkpoint processing where files are written to the disk (i.e. Buffered write() of dirty shared_buffers to the tables).
* bgwriter.CheckPointSyncTime : The amount of time spent on flushing to the disk during a checkpoint. (i.e. fsync(), ensures that the stuff in the OS buffers is written physically to the disk)
* bgwriter.BuffersBackend : Number of buffers written by backends, that is, buffers created not by the background writer. i.e. The client had to write a page in order to make space for the new allocation.
* bgwriter.BuffersBackendFsync : Time taken to ensure that updates are physically written to disk.
* bgwriter.BuffersClean : Buffers clean correspond to the number of buffers written to disk by the background writer. This only happens to pages that haven't been accessed recently.
* bgwriter.BuffersAlloc : The number of buffers allocated by bgwriter.
* bgwriter.BuffersCheckPoint : The number of buffers written to disk during a checkpoint.
* ConnectionsOpen-Count : The number of open connections to PostgreSQL.
* ConnectionsUsed-Pct : Open connections / Max connections
* SlaveRunning : Output whether the salve is running or not. (Master only)
* ReplicationLag : Replication lag from the master. (Slave only)
* Uptime-Seconds : Uptime of the PostgreSQL server process in seconds.
* DatabaseSpecific.postgres.LockedElements-Count : Count of locked elements in the database.
* DatabaseSpecific.postgres.CacheRate : Cache hit rate.
* DatabaseSpecific.postgres.CacheHit-Pct : Cache hit percentage.
* DatabaseSpecific.postgres.CountOfCommit-PerSecond : Number of commits averaged by second obtained by two samples counted between collection interval.
* DatabaseSpecific.postgres.CountOfInserted-PerSecond : Number of inserts averaged by second obtained by two samples counted between collection interval.
* DatabaseSpecific.postgres.CountOfUpdated-PerSecond : Number of updates averaged by second obtained by two samples counted between collection interval.
* DatabaseSpecific.postgres.CountOfDeleted-PerSecond : Number of deletes averaged by second obtained by two samples counted between collection interval.
* DatabaseSpecific.postgres.CreatedTmpTables-Count : Count of temp tables created.
* DatabaseSpecific.postgres.IdxScanRatio : Ratio of index usage to non index usage.
* DatabaseSpecific.postgres.DatabaseSize-kB : Size of database in KiloBytes.


### Example output (OpenTSDB formatted)

Available 1484684147834 1 Host=127.0.0.1 Port=5432  
bgwriter.BuffersAlloc 1484684147834 1431 Host=127.0.0.1 Port=5432  
bgwriter.BuffersBackend 1484684147834 16 Host=127.0.0.1 Port=5432  
bgwriter.BuffersBackendFsync 1484684147834 16 Host=127.0.0.1 Port=5432  
bgwriter.BuffersCheckPoint 1484684147834 4406 Host=127.0.0.1 Port=5432  
bgwriter.BuffersClean 1484684147834 0 Host=127.0.0.1 Port=5432  
bgwriter.CheckPointsReq 1484684147834 19 Host=127.0.0.1 Port=5432  
bgwriter.CheckPointsTimed 1484684147834 3735 Host=127.0.0.1 Port=5432  
bgwriter.CheckPointSyncTime 1484684147834 9714 Host=127.0.0.1 Port=5432  
bgwriter.CheckPointWriteTime 1484684147834 69265 Host=127.0.0.1 Port=5432  
bgwriter.MaxWrittenClean 1484684147834 0 Host=127.0.0.1 Port=5432  
ConnectionsOpen-Count 1484684147834 46 Host=127.0.0.1 Port=5432  
ConnectionsUsed-Pct 1484684147834 23.23232 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CacheHit-Pct 1484684147834 0.999884087481109907 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CacheRate 1484684147834 11581.455604075691 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CountOfCommit-PerSecond 1484684147834 1.5167 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CountOfDeleted-PerSec 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CountOfInserted-PerSecond 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CountOfUpdated-PerSecond 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.CreatedTmpTables-Count 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.DatabaseSize-kB 1484684147834 7336 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.IdxScanRatio 1484684147834 0.81082092429925223142 Host=127.0.0.1 Port=5432  
DatabaseSpecific.my-db.LockedElements-Count 1484684147834 2 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CacheHit-Pct 1484684147834 0.999919221588333129 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CacheRate 1484684147834 11580.927947598253 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CountOfCommit-PerSecond 1484684147834 1.5167 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CountOfDeleted-PerSecond 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CountOfInserted-PerSecond 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CountOfUpdated-PerSecond 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.CreatedTmpTables-Count 1484684147834 0 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.DatabaseSize-kB 1484684147834 7256 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.IdxScanRatio 1484684147834 0.82644358383794914826 Host=127.0.0.1 Port=5432  
DatabaseSpecific.postgres.LockedElements-Count 1484684147834 2 Host=127.0.0.1 Port=5432  
SlaveRunning 1484684147834 1 Host=127.0.0.1 Port=5432  
Uptime-Seconds 1484684147834 1124091.56740200007 Host=127.0.0.1 Port=5432  
