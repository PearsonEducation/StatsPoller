# Mongo Collector

The 'Mongo' collector is a metric collector that connects to Mongo through Mongo's Java driver and queries Mongo for metrics. The admin database, as well as user-created databases, are queried. Retrieved metrics include 'replSetGetStatus' (replication set) metrics, 'serverStatus' (mongo server state/status) metrics, 'collStats' (collection stats) metrics, and 'dbStats' (database-specific) metrics. StatsPoller can run up to 10,000 Mongo collectors from a single StatsPoller deployment. Mongo 2.4 through 3.4 & are explicitly supported, though other versions of Mongo may be compatible.

A single Mongo collector's behavior is:

1. Connect to Mongo
1. Collect 'replSetStatus' metrics from the admin database
1. Collect 'serverStatus' metrics from the admin database
1. Collect 'dbStats' metrics from user-created databases
1. Collect 'collStats' metrics from user-created databases
1. Disconnect from Mongo
1. Output the metrics
1. Repeat

### Metrics

Since hundreds to thousands of metrics can be collected from this collector, a description of each metric will not be provided. Instead, Mongo's documentation for 'serverStatus', 'dbStats', and 'collStats' should be consulted @ [Mongo Documentation](https://docs.mongodb.com/manual/reference/command/nav-diagnostic/)

### Example output (Sample, Graphite Formatted)

replSetStatus.myState 7 1468177825  
replSetStatus.term 0 1468177825  
serverStatus.uptime 1129564 1468177825  
serverStatus.connections.current 10 1468177825  
serverStatus.connections.available 25590 1468177825  
serverStatus.connections.totalCreated 178 1468177825  
serverStatus.globalLock.totalTime 1129563369000 1468177825  
serverStatus.globalLock.currentQueue.total 0 1468177825  
serverStatus.globalLock.currentQueue.readers 0 1468177825  
serverStatus.globalLock.currentQueue.writers 0 1468177825  
serverStatus.globalLock.activeClients.total 40 1468177825  
serverStatus.globalLock.activeClients.readers 0 1468177825  
serverStatus.globalLock.activeClients.writers 0 1468177825  
replSetStatus.myState 2 1468177825  
replSetStatus.term 2 1468177825  
replSetStatus.heartbeatIntervalMillis 2000 1468177825  
dbStats.local.collections 5 1468177825  
dbStats.local.objects 65021 1468177825  
dbStats.local.avgObjSize 82392.29656572492 1468177825  
dbStats.local.dataSize 5357229515 1468177825  
dbStats.local.storageSize 1238908928 1468177825  
dbStats.local.numExtents 0 1468177825  
dbStats.local.indexes 4 1468177825  
dbStats.local.indexSize 65536 1468177825  
collectionStats.local.system.replset.count 1 1468177825  
collectionStats.local.system.replset.size 791 1468177825  
collectionStats.local.system.replset.avgObjSize 791 1468177825  
collectionStats.local.system.replset.storageSize 16384 1468177825  
collectionStats.local.system.replset.nindexes 1 1468177825  
collectionStats.local.system.replset.totalIndexSize 16384 1468177825  
collectionStats.local.system.replset.indexSizes._id_ 16384 1468177825  
