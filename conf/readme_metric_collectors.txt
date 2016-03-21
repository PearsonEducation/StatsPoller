########## Linux ##########
# A good set of OS metric collectors for CentOS/RHEL (assuming prereqs are installed)
metric_collector_1=./bin/vmstat_poller.sh,30000,./output/vmstat_poller.out,vmstat
metric_collector_2=python ./bin/df_poller.py ./output/df_poller.out,30000,./output/df_poller.out,df
metric_collector_3=perl ./bin/iostat_poller.pl ./output/iostat_poller.out,30000,./output/iostat_poller.out,iostat
metric_collector_4=./bin/ifconfig_poller.sh,30000,./output/ifconfig_poller.out,ifconfig
metric_collector_5=python ./bin/mpstat_poller.py -w 20 ./output/mpstat_poller.out,30000,./output/mpstat_poller.out,mpstat
metric_collector_6=python ./bin/meminfo_poller.py,30000,./output/meminfo_poller.out,meminfo
metric_collector_7=python ./bin/uptime_poller.py,30000,./output/uptime_poller.out,uptime
metric_collector_8=./bin/ss_poller.sh,30000,./output/ss_poller.out,ss



########## Windows ##########
# A good default set of OS metric collectors for Windows
metric_collector_200=cscript ./bin/diskstat_poller.vbs,30000,./output/diskstat_poller_output.out,diskstat
metric_collector_201=cscript ./bin/iis_poller.vbs,30000,./output/iis_poller_output.out,iis
metric_collector_202=cscript ./bin/memoryinfo_poller.vbs,30000,./output/memoryinfo_poller_output.out,memoryinfo
#metric_collector_203=cscript ./bin/networkinfo_poller.vbs,30000,./output/networkinfo_poller_output.out,networkinfo
metric_collector_204=cscript ./bin/osstat_poller.vbs,30000,./output/osstat_poller_output.out,osstat
metric_collector_205=cscript ./bin/processorstat_poller.vbs,30000,./output/processorstat_poller_output.out,processorstat
metric_collector_206=cscript ./bin/tcpnetinfo_poller.vbs,30000,./output/tcpnetinfo_poller_output.out,tcpnetinfo

# SQL Server collectors
metric_collector_225=cscript ./bin/sqlserver-access_poller.vbs,30000,./output/sqlserver-access_poller_output.out,sqlserver.access
metric_collector_226=cscript ./bin/sqlserver-buffer_poller.vbs,30000,./output/sqlserver-buffer_poller_output.out,sqlserver.buffer
metric_collector_227=cscript ./bin/sqlserver-database_poller.vbs,30000,./output/sqlserver-database_poller_output.out,sqlserver.database
metric_collector_228=cscript ./bin/sqlserver-memory_poller.vbs,30000,./output/sqlserver-memory_poller_output.out,sqlserver.memory
metric_collector_229=cscript ./bin/sqlserver-statistics_poller.vbs,30000,./output/sqlserver-statistics_poller_output.out,sqlserver.statistics
metric_collector_230=cscript ./bin/sqlserver-userslocks_poller.vbs,30000,./output/sqlserver-userslocks_poller_output.out,sqlserver.userslocks



########## Application specific collectors ##########
#metric_collector_100=./bin/redis_poller.sh,180000,./output/redis_poller_output.out,redis
metric_collector_101=python ./bin/mongo_poller.py -host 127.0.0.1 -port 27017 -username user -password pw -file ./output/mongo_poller_output.out,30000,./output/mongo_poller_output.out,mongo



########## Domain specific collectors ##########
metric_collector_300=python ./bin/file_counter.py -path /path/to/monitored/folder -output ./output/file_counter.out,30000,./output/file_counter.out,file_counter
metric_collector_301=python ./bin/process_monitor.py -config ./bin/config/process_monitor.conf -output ./output/process_monitor.out,30000,./output/process_monitor.out,process_monitor
