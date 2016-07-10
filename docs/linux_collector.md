# Linux Metric Collector

The Linux Metric Collector package are built-in collectors in StatsPoller. Their function is to collect OS-level metrics from Linux-based operating systems. To properly function, the Linux collectors require the /proc and /sys filesystems to be mounted.

<br>

## Connections

The Connections collector collects TCP & UDP connection count information.

### Metrics

* TcpIPv4 - Connection Count : The number of currently 'in use' TCP-IP (version 4) connections
* UdpIPv4 - Connection Count : The number of currently 'in use' UDP-IP (version 4) connections
* TcpIPv6 - Connection Count : The number of currently 'in use' TCP-IP (version 6) connections
* UdpIPv6 - Connection Count : The number of currently 'in use' UDP-IP (version 6) connections

### Example output (Graphite Formatted)

TcpIPv4-ConnectionCount 3 1463267223  
UdpIPv4-ConnectionCount 5 1463267223  
TcpIPv6-ConnectionCount 3 1463267223  
UdpIPv6-ConnectionCount 2 1463267223  

<br>

## CPU

The CPU collector provides output that is similar to systat's mpstat package. Metrics are output on a per-core basis, and include a rollup of the overall CPU utilization. Credit to the mpstat man pages for some of the metric descriptions.

### Metrics

* CPU User % : The percentage of CPU utilization that occurred while executing at the user level (application).
* CPU Nice % : The percentage of CPU utilization that occurred while executing at the user level with nice priority.
* CPU System % : The percentage of CPU utilization that occurred while executing at the system level (kernel). This does not include time spent servicing hardware and software interrupts.
* CPU Idle % : The percentage of time that the CPU was idle and the system did not have an outstanding disk I/O request.
* CPU Iowait % : The percentage of time that the CPU was idle during which the system had an outstanding disk I/O request.
* CPU Irq % : The percentage of time spent by the CPU or CPUs to service hardware interrupts.
* CPU SoftIrq % : The percentage of time spent by the CPU or CPUs to service software interrupts.
* CPU Steal % : The percentage of time spent in involuntary wait by the virtual CPU while the hypervisor was servicing another virtual processor.
* CPU Guest % : The percentage of time spent by the CPU to run a virtual processor.
* CPU GuestNice % : The percentage of time spent by the CPU to run a virtual processor with nice priority.
* CPU Extra % : The percentage of time spent spent servicing anything CPU related that isn't covered by the other metrics.
* CPU Used % : A derived metric for CPU usage percentage. The formula is: (100 - "CPU Idle %")

### Example output (Graphite Formatted)

CPU-All.User-Pct 8.85497 1463264463  
CPU-All.Nice-Pct 0.0000000 1463264463  
CPU-All.System-Pct 2.26763 1463264463  
CPU-All.Idle-Pct 88.83428 1463264463  
CPU-All.Iowait-Pct 0.00862 1463264463  
CPU-All.Irq-Pct 0.0000000 1463264463  
CPU-All.SoftIrq-Pct 0.03449 1463264463  
CPU-All.Steal-Pct 0.0000000 1463264463  
CPU-All.Guest-Pct 0.0000000 1463264463  
CPU-All.GuestNice-Pct 0.0000000 1463264463  
CPU-All.Extra-Pct 0.0000000 1463264463  
CPU-All.Used-Pct 11.16572 1463264463  

<br>

## Disk IO

The Disk IO collector provides output that is similar to systat's iostat package. Metrics are output on a per-disk basis. Credit to the iostat man pages for some of the metric descriptions.

### Metrics

* Device - Read-Requests / Second : The number of read requests that were issued to the device per second.
* Device - Read-Bytes / Second : The number of bytes read from the device per second.
* Device - Read-Megabytes / Second : The number of megabytes read from the device per second.
* Device - Read-AvgRequestTime / Millisecond : The average time (in milliseconds) for read I/O requests issued to the device to be served. This includes the time spent by the requests in queue and the time spent servicing them.
* Device - Write-Requests / Second : The number of write requests that were issued to the device per second.
* Device - Write-Bytes / Second : The number of bytes written to the device per second.
* Device - Write-Megabytes / Second : The number of megabytes written to the device per second.
* Device - Write-AvgRequestTime / Millisecond : The average time (in milliseconds) for write I/O requests issued to the device to be served. This includes the time spent by the requests in queue and the time spent servicing them.
* Device - Average Request Time (ms) : The average time (in milliseconds) for I/O requests issued to the device to be served. This includes the time spent by the requests in queue and the time spent servicing them.
* Device - Average Queue Length : The average queue length of the requests that were issued to the device.

### Example output (Graphite Formatted)

sda.Read-Requests|Second 0.0333 1463264523  
sda.Read-Bytes|Second 136.5504 1463264523  
sda.Read-Megabytes|Second 0.0001302 1463264523  
sda.Read-AvgRequestTime|Millisecond 0.0000000 1463264523  
sda.Write-Requests|Second 2.6666 1463264523  
sda.Write-Bytes|Second 24370.3808 1463264523  
sda.Write-Megabytes|Second 0.0232414 1463264523  
sda.Write-AvgRequestTime|Millisecond 0.15 1463264523  
sda.AverageRequestTime|Millisecond 0.1481481 1463264523  
sda.AverageQueueLength 0.0004 1463264523  

<br>

## File System

The File-System collector collectors disk space & disk inode information. Unline other collectors, the File-System collector uses a 3rd party program to retrieve file-system information. Specifically, the 'df' application must be installed & callable on the operating system.

### Metrics

* Mount - FileSystem : an informational metric about what type of file system is used.
* Mount - Disk Space Total - Bytes : The total number of bytes of disk space on the file system.
* Mount - Disk Space Free - Bytes : The number of free bytes of disk space on the file system.
* Mount - Disk Space Used - Bytes : The number of used bytes of disk space on the file system.
* Mount - Disk Space Total - GB : The total number of gigabytes of disk space on the file system.
* Mount - Disk Space Free - GB : The number of free gigabytes of disk space on the file system.
* Mount - Disk Space Used - GB : The number of used gigabytes of disk space on the file system.
* Mount - Disk Space Used % : The percentage of used disk space on the file system.
* Mount - Inodes Total : The total number of inodes on the file system.
* Mount - Inodes Free : The number of free inodes on the file system.
* Mount - Inodes Used : The number of used inodes on the file system.
* Mount - Inodes Used % : The percentage of used inodes on the file system.

### Example output (Graphite Formatted)

|dev|shm.FileSystem=tmpfs 1 1463266473  
|dev|shm.DiskSpace-Total-Bytes 2071777280 1463266473  
|dev|shm.DiskSpace-Total-GB 1.929493 1463266473  
|dev|shm.DiskSpace-Free-Bytes 2071515136 1463266473  
|dev|shm.DiskSpace-Free-GB 1.9292488 1463266473  
|dev|shm.DiskSpace-Used-Bytes 262144 1463266473  
|dev|shm.DiskSpace-Used-GB 0.0002441 1463266473  
|dev|shm.DiskSpace-Used-Pct 1 1463266473  
|dev|shm.Inodes-Total 505805 1463266473  
|dev|shm.Inodes-Free 505795 1463266473  
|dev|shm.Inodes-Used 10 1463266473  
|dev|shm.Inodes-Used-Pct 1 1463266473  

<br>

## Memory

The Memory collect provides two primary outputs: 'raw' and 'derived'. 'Raw' outputs are directly lifted from /proc/meminfo with no alterations. 'Derived' outputs provide more practical metrics by accounting for memory fields that can be reclaimed from the OS. Specifically, derived memory metrics are based around this formula: Memory Used = (MemTotal - (SwapCached + Cached + Buffers + MemFree + SReclaimable))

### Metrics

* Raw - (memory-metric in bytes) : The current amount of memory devoted to the metric in question, in bytes. These metrics are scraped directly from /proc/meminfo.
* Derived - Total Bytes :  The total number of bytes of physical memory on the system.
* Derived - Free Bytes : The number of free bytes of physical memory on the system.
* Derived - Used Bytes : The number of used bytes of physical memory on the system.
* Derived - Total Megabytes :  The total number of megabytes of physical memory on the system.
* Derived - Free Megabytes : The number of free megabytes of physical memory on the system.
* Derived - Used Megabytes : The number of used megabytes of physical memory on the system.
* Derived - Used % : The percentage of used physical memory on the system.

### Example output (Graphite Formatted)

Derived.Total-Bytes 4143558656 1463267553  
Derived.Free-Bytes 1204305920 1463267553  
Derived.Used-Bytes 2939252736 1463267553  
Derived.Total-Megabytes 3951.6054688 1463267553  
Derived.Free-Megabytes 1148.515625 1463267553  
Derived.Used-Megabytes 2803.0898438 1463267553  
Derived.Used-Pct 70.93547 1463267553  

<br>

## Network Bandwidth

The Network Bandwidth collector provides network bandwidth utilization metrics for all network adapters (excluding 'lo', the loopback device).

### Metrics

* Device - Received-Bytes / Second : The number of bytes received, per second, on this device.
* Device - Transmitted-Bytes / Second : The number of bytes transmitted, per second, on this device.
* Device - Overall-Bytes / Second : The overall number of bytes received & transmitted, per second, on this device.
* Device - Received-Megabits / Second : The number of megabits received, per second, on this device.
* Device - Transmitted-Megabits / Second : The number of megabits transmitted, per second, on this device.
* Device - Overall-Megabits / Second : The overall number of megabits received & transmitted, per second, on this device.

### Example output (Graphite Formatted)

enp0s3.Received-Bytes-Second 13491.9666667 1463371627  
enp0s3.Transmitted-Bytes-Second 5399.4 1463371627  
enp0s3.Overall-Bytes-Second 18891.3666667 1463371627  
enp0s3.Received-Megabits-Second 0.1079357 1463371627  
enp0s3.Transmitted-Megabits-Second 0.0431952 1463371627  
enp0s3.Overall-Megabits-Second 0.1511309 1463371627  

<br>

## Uptime

The uptime collector reports the number of seconds since the last operating system reboot.

### Metrics

OS_Uptime / Seconds : The number of seconds since the last operating system reboot.

### Example output

OS_Uptime-Seconds 109675.75 1463371897  
