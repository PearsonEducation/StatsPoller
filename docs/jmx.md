# JMX Collector

The JMX metric collector is a metric collector that plugs into Java JVMs & collects JMX metrics. StatsPoller can run up to 10,000 JMX collectors from a single StatsPoller deployment.

A single JMX collector's behavior is:

1. Connect to the JVM
1. Ask the JVM what all of its available JMX metrics are
1. Filter out the objects on the 'Object Blacklist' (jmx_blacklist_objectname_regex in the conf file)
1. Fetch the key/value pairs of JMX attributes that were not filtered by the 'Object Blacklist'. Note that only JMX key/value *attributes* are collected; JMX *operations* are not touched/invoked.
1. Format the metrics into a Graphite/OpenTSDB friendly Format
1. Filter out attributes based on the the 'blacklist' and 'whitelist' rules (jmx_blacklist_regex & jmx_whitelist_regex in the conf file). Any metrics filtered out here will not be requested on subsequent 'Fetch the key/value pairs of JMX attributes' operations.
1. Retrieve, compute, and format 'derived' JMX metrics.
1. Output the metrics
1. Repeat (with the following notes)
   * Connecting to the JVM only happens once (unless the connection is severed)
   * The JVM will be asked what all of its available JMX metrics are on a user-specified time interval (jmx_query_metric_tree in the conf file)

<br>

## Standard Metrics

Without blacklists and/or whitelists filtering out metrics, the JMX collector will grab every numeric attribute offered by the JVM. A plain JVM will often output 100+ metrics. A JVM that uses lots of fancy libraries and/or exposes lots of JMX metrics will sometimes offer thousands of metrics. For example, a single Apache Cassandra JVM will offer over 3000 metrics. As a result, a description of each metric will not be provided in this documentation. To preview the output of what StatsPoller might fetch/output, connect to your JVM with a tool like VisualVM & use the mBean viewer to see all the available JMX metrcs. Note that StatsPoller always outputs a JVM availability metric, 'Availability.Available'. Whenever StatsPoller is connected to the target JVM, 'Availability.Available' will output '1', otherwise it outputs '0'.

### Example Output (Sample, Graphite Formatted)

Availability.Available 1 1466371333  
java-lang.OperatingSystem.TotalSwapSpaceSize 131608461312 1466370213  
java-lang.OperatingSystem.ProcessCpuTime 4250340845600 1466370213  
java-lang.OperatingSystem.SystemCpuLoad 0.05514683146571775 1466370213  
java-lang.OperatingSystem.Version 6.2 1466370213  
java-lang.OperatingSystem.CommittedVirtualMemorySize 269664256 1466370213  
java-lang.OperatingSystem.AvailableProcessors 16 1466370213  
java-lang.ClassLoading.TotalLoadedClassCount 17205 1466370213  
java-lang.Runtime.Uptime 390586460 1466370213  
java-lang.Runtime.SpecVersion 1.8 1466370213  
java-lang.Runtime.StartTime 1465979621429 1466370213  
java-lang.Threading.ThreadCount 47 1466370213  
java-lang.Memory.HeapMemoryUsage.committed 42991616 1466370213  
java-lang.Memory.HeapMemoryUsage.init 167772160 1466370213  
java-lang.Memory.HeapMemoryUsage.max 167772160 1466370213  
java-lang.Memory.HeapMemoryUsage.used 31962856 1466370213  
java-lang.MemoryPool.G1_Old_Gen.Usage.committed 30408704 1466370213  
java-lang.MemoryPool.G1_Old_Gen.Usage.init 157286400 1466370213  
java-lang.MemoryPool.G1_Old_Gen.Usage.max 167772160 1466370213  
java-lang.MemoryPool.G1_Old_Gen.Usage.used 24622824 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.LastGcInfo.GcThreadCount 29 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.LastGcInfo.duration 135 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.LastGcInfo.endTime 267823462 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.LastGcInfo.id 3 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.LastGcInfo.startTime 267823327 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.CollectionTime 327 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.Valid 1 1466370213  
java-lang.GarbageCollector.G1_Old_Generation.CollectionCount 3 1466370213  

<br>

## Derived Metrics

Derived JMX metrics are metrics that are oftentimes useful for those that are operationally responsible for maintaining a JVM. These fill a gap that 'standard' JVM metrics don't provide. For example, going the usage percentage of a JVM's heap is very useful for an operations team, but that information is not output by a JVM's standard metrics. Derived metrics are always prefixed by the word 'Derived'.

### Metrics

* CPU - JVM CPU Usage % : The % of CPU being used by the JVM (relative to the what CPU resources are  available on the server). This metric represents server CPU % relative to the collection interval of the JMX collector. This metric's output will generally be more accurate than 'JVM Recent CPU Usage %'.
* CPU - JVM Recent CPU Usage % : The % of CPU being used by the JVM (relative to the what CPU resources are  available on the server). This metric represents server CPU % relative to a JVM-selected time period. Note that this metric is the JVM's view of it's own CPU usage %, and it has been found to be less reliable than 'CPU - JVM CPU Usage %'.
* CPU - System Recent CPU Usage % : The % of CPU being used by all processes on the server that is running the target JVM. This metric represents server  CPU % relative to a JVM-selected time period.
* GC - % (by GC collector) : The percentage of garbage collection activity being done by a specific garbage collector. Note that this is the percentage of the JVM CPU usage, and not the percentage of the overall OS CPU usage.
* GC - Overall % : The overall percentage of garbage collection activity being done in the JVM (includes all garbage collectors). Note that this is the percentage of the JVM CPU usage, and not the percentage of the overall OS CPU usage.
* Memory - Heap - Overall Usage % : The overall heap memory utilization % of the JVM. Includes/covers all generations of heap memory.
* Memory - NonHeap - Overall Usage % : The overall non-heap memory utilization % of the JVM. This includes memory spaces such as 'perm gen', 'code cache', etc.
* Memory - System - Physical Memory Usage % : The overall physical memory utilization % of the operating system that the JVM is running on. Since this metric is viewed through the lens of the target JVM, the value presented will have an OS-specific meaning, and will oftentimes not take factors like cache/buffers/etc into account.
* Memory - System - Swap Usage % : The overall swap utilization % of the operating system that the JVM is running on. Since this metric is viewed through the lens of the target JVM, the value presented has an OS-specific meanings.

### Example Output (Graphite Formatted)

Derived.CPU.JvmCpu-UsagePct 0.633 1468181023  
Derived.CPU.JvmRecentCpu-UsedPct 0.65 1468181023  
Derived.CPU.SystemRecentCpu-UsedPct 52.174 1468181023  
Derived.GC.PS_MarkSweep-Pct 0 1468181023  
Derived.GC.PS_Scavenge-Pct 2.632 1468181023  
Derived.GC.Overall-Pct 2.632 1468181023  
Derived.Memory.Heap.Overall-UsedPct 17.472 1468181023  
Derived.Memory.NonHeap.Overall-UsedPct 16.202 1468181023  
Derived.Memory.System.PhysicalMemory-UsedPct 3.644 1468181023  
Derived.Memory.System.SystemSwapSize-UsedPct 94.719 1468181023  
