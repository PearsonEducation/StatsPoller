# cAdvisor Collector

The 'cAdvisor' collector is a metric collector that connects to cAdvisor through cAdvisor's HTTP API & retrieves docker metrics. For this functionality to work, cAdvisor's HTTP interface (usually running on port 8080) must be accessible by the StatsPoller agent. StatsPoller can run up to 10,000 cAdvisor collectors from a single StatsPoller deployment. The StatsPoller 'cAdvisor' collector was developed against cAdvisor HTTP API version 1.3. Other HTTP API versions may be compatible, but compatibility is not guaranteed.

A single cAdvisor collector's behavior is:

1. Download cAdvisor docker metrics via HTTP from host:port//api/{api-version}/docker/  
1. Download cAdvisor machine metrics via HTTP from host:port//api/{api-version}/machine/  
1. Process/output the metrics  
1. Repeat  

Metric paths will follow the format of: {global_metric_prefix}.cAdvisor.{container_prefix}.<br>
{container_prefix} is auto-generated, and follows the convention of...<br>

1. If the container is running on Amazon Web Services (AWS) via their ECS platform, then {container_prefix} = {ecs_taskdefinition_family}.{ecs_container_name}.{short_container_id}
1. If the container is not on ECS, but has a name, then {container_prefix} = {container_name}
1. If the container does not meet any other criteria, then {container_prefix} = {short_container_id}

### Metrics

* Available : Was StatsPoller able to connect to cAdvisor? Outputs 1 if true, 0 if false.
* Memory - Working Set - Bytes : The amount of memory currently needed by run the container by the kernel. This is "hot" memory (in use).
* Memory - RSS (Resident set size) - Bytes : The amount main memory (RAM) used by the container.
* Memory - Cache - Bytes : The amount of memory being used by the container for disk cache.
* Memory - Swap - Bytes : The amount of memory being used by the container for swap (on disk).
* Memory - Usage - Bytes : The total amount of memory being used by the container. This is cumulative of all the memory that has been allocated to the container; not just the currently active parts. This can be thought of as "hot + cold" memory, and is what is used to determine how close the container is to any container memory limits.
* Memory - Container Page Fault - Count : The total number of page faults that have occurred on the container since it was launched. A page fault happens when a process accesses a part of its virtual memory space which is nonexistent or protected.
* Memory - Container Page Fault Major - Count : The total number of major page faults that have occurred on the container since it was launched. "Major" faults happen when the kernel actually has to interact with the disk (ex- swap).
* Memory - Hierarchy Page Fault - Count : The total number of page faults that have occurred on the container's cgroup process hierarchy since it was launched. A page fault happens when a process accesses a part of its virtual memory space which is nonexistent or protected.
* Memory - Hierarchy Page Fault Major - Count : The total number of major page faults that have occurred on the container's cgroup process hierarchy since it was launched. "Major" faults happen when the kernel actually has to interact with the disk (ex- swap).
* Memory - Usage Relative To Soft Limit - % : If a memory reservation is put on the container (a 'soft' memory limit), then this metric gives how close the container is to that limit (memory_usage/memory_reservation). If no memory reservation is specified, then the equation used is (memory_usage/overall_system_memory).
* Memory - Usage Relative To Hard Limit - % : If a memory limit is put on the container (a 'hard' memory limit), then this metric gives how close the container is to that limit (memory_usage/memory_limit). If no memory limit is specified, then the equation used is (memory_usage/overall_system_memory).
* Network - Tcp Connections - Count : The total number of TCP connections to this container. Note that some versions of cAdvisor disable the collection of these metrics by default.
* Network - TcpV6 Connections - Count : The total number of TCP (version 6) connections to this container. Note that some versions of cAdvisor disable the collection of these metrics by default.
* Network - Received-Bytes / Second : The number of bytes received, per second, on this container.
* Network - Transmitted-Bytes / Second : The number of bytes transmitted, per second, on this container.
* Network - Overall-Bytes / Second : The overall number of bytes received & transmitted, per second, on this container.
* Network - Received-Megabits / Second : The number of megabits received, per second, on this container.
* Network - Transmitted-Megabits / Second : The number of megabits transmitted, per second, on this container.
* Network - Overall-Megabits / Second : The overall number of megabits received & transmitted, per second, on this container.
* Cpu - Overall Usage Relative To Host - % : The amount of cpu that this container is using, relative to the host OS. For example, if this metric reads 20%, then 20% of the total cpu of the *server* is being consumed by this container.
* Cpu - User Usage Relative To Host - % : The overall amount of 'user' cpu that this container is using, relative to the host OS. This is the amount of time being spent running your code/program (as opposed to doing OS stuff, waiting on disk/network I/O, etc).
* Cpu - System Usage Relative To Host - % : The overall amount of 'system' cpu that this container is using, relative to the host OS. This is the amount of time being spent by the container making system calls (ex- spawning threads, etc).
* Cpu - Other Usage Relative To Host - % : The overall amount of 'other' cpu that this container is using, relative to the host OS. This is the amount of time being spent by the container being spent on anything other than 'user' or 'system' (ex - disk I/O).
* Cpu - Overall Usage Relative To Cpu Shares - % : The amount of cpu that this container is using, relative to the number of cpu shares that have been reserved for this container. Since this only reserves a *minimum* cpu capacity (which this container can go over if the server has available cpu capacity), this value rise over 100%.
* Uptime - Seconds : The amount of time, in seconds, that this container has been running.

### Key Metrics

* Cpu: "Overall Usage Relative To Host" and "Overall Usage Relative To Cpu Shares" are the two main metrics to care about for cpu.
* Memory: "Usage - Bytes" and "Usage Relative To Hard Limit" (if set) are the two main metrics to care about for memory.
* Network: "Bandwidth Overall" is the most important network metric.

### Example output (OpenTSDB formatted)
MyContainer.Memory.WorkingSet-Bytes 1498661366624 242987008 ContainerId=4d82acc4d671  
MyContainer.Memory.RSS-Bytes 1498661366624 215134208 ContainerId=4d82acc4d671  
MyContainer.Memory.Cache-Bytes 1498661366624 73871360 ContainerId=4d82acc4d671  
MyContainer.Memory.Swap-Bytes 1498661366624 0 ContainerId=4d82acc4d671  
MyContainer.Memory.Usage-Bytes 1498661366624 291803136 ContainerId=4d82acc4d671  
MyContainer.Memory.PageFault-Container-Count 1498661366624 436579 ContainerId=4d82acc4d671  
MyContainer.Memory.PageFaultMajor-Container-Count 1498661366624 196 ContainerId=4d82acc4d671  
MyContainer.Memory.PageFault-Hierarchical-Count 1498661366624 436579 ContainerId=4d82acc4d671  
MyContainer.Memory.PageFaultMajor-Hierarchical-Count 1498661366624 196 ContainerId=4d82acc4d671  
MyContainer.Memory.UsageRelativeToSoftLimit-Pct 1498661366624 56.10588 ContainerId=4d82acc4d671  
MyContainer.Memory.UsageRelativeToHardLimit-Pct 1498661366624 7.39035 ContainerId=4d82acc4d671  
MyContainer.Network.Connections.TcpV4-Count 1498661366624 0 ContainerId=4d82acc4d671  
MyContainer.Network.Connections.TcpV6-Count 1498661366624 0 ContainerId=4d82acc4d671  
MyContainer.Network.Bandwidth.Received-Bytes-Second 1498661366624 36952.9879431 ContainerId=4d82acc4d671  
MyContainer.Network.Bandwidth.Transmitted-Bytes-Second 1498661366624 23650.6455534 ContainerId=4d82acc4d671  
MyContainer.Network.Bandwidth.Overall-Bytes-Second 1498661366624 60603.6334965 ContainerId=4d82acc4d671  
MyContainer.Network.Bandwidth.Received-Megabits-Second 1498661366624 0.2956239 ContainerId=4d82acc4d671  
MyContainer.Network.Bandwidth.Transmitted-Megabits-Second 1498661366624 0.1892052 ContainerId=4d82acc4d671  
MyContainer.Network.Bandwidth.Overall-Megabits-Second 1498661366624 0.4848291 ContainerId=4d82acc4d671  
MyContainer.Uptime.Uptime-Seconds 1498661459770 64995 ContainerId=4d82acc4d671  
MyContainer.Cpu.CpuOverallUsage-RelativeToHost-Pct 1498661366624 1.0935452 ContainerId=4d82acc4d671 CpuShares=2  
MyContainer.Cpu.CpuUserUsage-RelativeToHost-Pct 1498661366624 0.1314104 ContainerId=4d82acc4d671 CpuShares=2  
MyContainer.Cpu.CpuSystemUsage-RelativeToHost-Pct 1498661366624 0.4927889 ContainerId=4d82acc4d671 CpuShares=2  
MyContainer.Cpu.CpuOtherUsage-RelativeToHost-Pct 1498661366624 0.469346 ContainerId=4d82acc4d671 CpuShares=2  
MyContainer.Cpu.CpuOverallUsage-RelativeToCpuShares-Pct 1498661366624 559.8951434 ContainerId=4d82acc4d671 CpuShares=2
