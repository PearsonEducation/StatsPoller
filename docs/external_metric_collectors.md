# External Metric collectors

StatsPoller can execute user-created scripts/programs to collect metrics. The way it works is:

1. A script/program is created to collect metrics. The script/program can collect metrics from anything/anywhere that can be accessed by the host operating system and can be developed in any (OS supported) language, but it *must* generate an output file that contains the output metrics (to be sent to Graphite/OpenTSDB). The metrics must be in either 'Graphite' format or 'OpenTSDB telnet' format. The runtime of the script/program should be short (enough to collect the metrics and exit). One should not create non-terminating 'daemon' metric collectors. The output file can be anywhere on the file system (as long as StatsPoller can access that file), but the preferred location is to output to the StatsPoller 'output' folder (commonly located at /opt/StatsPoller/output). 
1. StatsPoller must be configured to execute the script/program & collect the output. The details of how to configure it can be found here: [example_application.properties](./conf/example_application.properties). Note: the total runtime of the script/program should be less than the configured 'collection interval' time for the external metric collector. Otherwise multiple executions of the script/program may stack on top of each other.
1. StatsPoller will need to be restarted to pick up the configuration change.

### Example of what should be outputted to the output file (Graphite Formatted)

serverStatus.uptime 1129564 1468177825  
serverStatus.connections.current 10 1468177825  
serverStatus.connections.available 25590 1468177825  
