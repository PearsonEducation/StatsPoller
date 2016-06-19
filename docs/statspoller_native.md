# StatsPoller Native Collector

The StatsPoller Native Collector package collects operating system utilization metrics, but from the perspective of the JVM. This makes the 'StatsPoller Native Collector package' OS independent, but it also means that its output is only as good as what the JVM makes available.

<br>

## Agent

The 'Agent' metrics are StatsPoller metrics that always run, and output StatsPoller-specific data. They are not tied to any OS-specific data.

### Metrics

* Agent - Available : When StatsPoller is running, this metric will output a '1'. This allows monitoring tools to detect when StatsPoller is not running (or is unable to send metrics).
* Agent - Version : The version of StatsPoller that is running. The version itself in the key, and the value of this metric will always be '1'.

### Example output

Agent.Available 1 1463373915  
Agent.Version=2-0-beta3 1 1463373915  

<br>

## CPU

The CPU collector collects information about CPU utilization on a system. These metrics are viewed through the lens of the JVM, but are generally an accurate representation of CPU utilization. OS-specific CPU collectors may present more accurate/granular information (ex -- 'Linux CPU Collector').

### Metrics

* CPU - CPU % : The overall CPU utilization percentage of all CPUs.
* CPU - Core Count : The number of CPUs detected on the system.

### Example output

Cpu.Cpu-Pct 3.675 1463373915  
Cpu.CoreCount 4 1463373915  

<br>

## Physical Memory

The swap space collector collects information about the amount of physical memory used on a system. Since these metrics are viewed through the lens of the JVM, the values presented will have OS-specific meanings, and will oftentimes not take factors like cache/buffers/etc into account. OS-specific memory collectors will generally present more accurate information (ex -- 'Linux Memory Collector').

### Metrics

* Physical Memory - Total - Bytes : The total number of bytes of swap space used on the operating system.
* Physical Memory - Free - Bytes : The number of bytes of free swap space on the operating system.
* Physical Memory - Used - Bytes : The number of bytes of used swap space on the operating system.
* Physical Memory - Total - GB : The total number of gigabytes of swap space used on the operating system.
* Physical Memory - Free - GB : The number of gigabytes of free swap space on the operating system.
* Physical Memory - Used - GB : The number of gigabytes of used swap space on the operating system.
* Physical Memory - Used % : The percentage of used swap space on the operating system.

### Example output

PhysicalMemory.Free-Bytes 30896128 1463373915  
PhysicalMemory.Free-GB 0.029 1463373915  
PhysicalMemory.Total-Bytes 4143558656 1463373915  
PhysicalMemory.Total-GB 3.859 1463373915  
PhysicalMemory.Used-Bytes 4112662528 1463373915  
PhysicalMemory.Used-GB 3.83 1463373915  
PhysicalMemory.Used-Pct 99.254 1463373915  

<br>

## Disk Space

The disk space collector collector collects file-system disk space utilization metrics. These metrics are viewed through the lens of the JVM, but are generally accurate & align with other mechanisms of collecting disk space utilization.

### Metrics

* DiskSpace - Mount - Disk Space Total - Bytes : The total number of bytes of disk space on the file system.
* DiskSpace - Mount - Disk Space Free - Bytes : The number of free bytes of disk space on the file system.
* DiskSpace - Mount - Disk Space Used - Bytes : The number of used bytes of disk space on the file system.
* DiskSpace - Mount - Disk Space Total - GB : The total number of gigabytes of disk space on the file system.
* DiskSpace - Mount - Disk Space Free - GB : The number of free gigabytes of disk space on the file system.
* DiskSpace - Mount - Disk Space Used - GB : The number of used gigabytes of disk space on the file system.
* DiskSpace - Mount - Disk Space Used % : The percentage of used disk space on the file system.

### Example output

DiskSpace.|dev|shm.Free-Bytes 2071515136 1463373915  
DiskSpace.|dev|shm.Free-GB 1.929 1463373915  
DiskSpace.|dev|shm.Total-Bytes 2071777280 1463373915  
DiskSpace.|dev|shm.Total-GB 1.929 1463373915  
DiskSpace.|dev|shm.Used-Bytes 262144 1463373915  
DiskSpace.|dev|shm.Used-GB 0.000 1463373915  
DiskSpace.|dev|shm.Used-Pct 0.013 1463373915  

<br>

## Swap Space

The swap space collector collects information about the amount of swap space used on a system. Since these metrics are viewed through the lens of the JVM, the values presented may have OS-specific meanings.

### Metrics

* Swap Space - Total - Bytes : The total number of bytes of swap space used on the operating system.
* Swap Space - Free - Bytes : The number of bytes of free swap space on the operating system.
* Swap Space - Used - Bytes : The number of bytes of used swap space on the operating system.
* Swap Space - Total - GB : The total number of gigabytes of swap space used on the operating system.
* Swap Space - Free - GB : The number of gigabytes of free swap space on the operating system.
* Swap Space - Used - GB : The number of gigabytes of used swap space on the operating system.
* Swap Space - Used % : The percentage of used swap space on the operating system.

### Example output

SwapSpace.Free-Bytes 30896128 1463373915  
SwapSpace.Free-GB 0.029 1463373915  
SwapSpace.Total-Bytes 4143558656 1463373915  
SwapSpace.Total-GB 3.859 1463373915  
SwapSpace.Used-Bytes 4112662528 1463373915  
SwapSpace.Used-GB 3.83 1463373915  
SwapSpace.Used-Pct 99.254 1463373915  
