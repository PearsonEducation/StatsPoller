# StatsPoller

## Overview

StatsPoller is an agent-based metric collection & reporting platform. It currently outputs Graphite-formatted metrics & OpenTSDB-formatted metrics. It functions in a similar way to other metric collection agents, such as TCollector, sCollector, collectd, etc. StatsPoller is ideally paired with StatsAgg for alerting, and Graphite or OpenTSDB for metric storage, and Grafana for alerting.

## What are StatsPoller's core features?

* Metric collectors that retrieve metrics on a user-specified intervals.
* Built-in metric collectors have few dependencies.
    * Most metric collectors are built into the main program & rely only on Java 1.7+ being installed to run StatsPoller. 
    * For Linux metric collectors, most metrics are retrieved from Linux's proc and/or sys filesystems. As a result, dependencies on apps like vmstat, ifconfig, etc are virtually non-existant in StatsPoller.
* Advanced Java JMX metric collection is a primary focus in StatsPoller. Its JMX metric retrieval capabilities exceed that of most comparable tools.
* Built-in metric collectors include....
	* Linux OS (network, filesystem, disk i/o, cpu, uptime, memory)
	* Cassandra (via JMX)
	* Tomcat (via JMX)
	* JBoss/Wildfly (via JMX)
	* Almost any Java-based technology (via JMX)
	* Apache HTTP server
	* MongoDB
	* MySQL
	* File-counter (counts/outputs the number of files in a specified folder)
	* Process-counter (counts/outputs the number of processes running that match a particular regex pattern)
* Support for user-created metric collectors (plugins).
    * StatsPoller supports running user-created metric collectors. These plugins are executed by StatsPoller & allow StatsPoller to output the metrics that they collect.
* Bundled 'user-created metric collectors' include...
    * Windows OS (cpu, disk, iis, memory, network)
	* SQL Server 
* Advanced/flexible configuration capabilities
	* Most metric collectors that collect metrics from external services (Mongo, MySQL, Java JMX, etc) can be configured to collect from an infinite number of external services. 
	* StatsPoller can be configured to monitor dozens of JVMs (via JMX), or hundreds of MySQL servers

<br>

## Why release another metrics poller when there are several good ones already out there?

StatsPoller was originally written at a time when there were few peers on the market. Other tools have emerged then, and many of them are very good at what they do. StatsPoller has served Pearson well & is particularly well suited to monitor Java-based applications, so we figured that we'd just give the market another choice. 

<br>

## Configuration



