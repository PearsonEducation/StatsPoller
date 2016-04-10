# StatsPoller

## Overview

StatsPoller is an agent-based metric collection & reporting platform. It currently outputs Graphite-formatted metrics & OpenTSDB-formatted metrics. It functions in a similar way to other metric collection agents, such as  [TCollector](https://github.com/OpenTSDB/tcollector), [scollector](http://bosun.org/scollector/), [collectd](https://collectd.org/), etc. StatsPoller is ideally paired with [StatsAgg](https://github.com/PearsonEducation/StatsAgg) for alerting, and [Graphite](https://github.com/graphite-project/) or [OpenTSDB](http://opentsdb.net/) for metric storage, and [Grafana](http://grafana.org/) for dashboarding.

<br>

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
	* A single deployment of StatsPoller can be configured to monitor many of JVMs (via JMX), MySQL servers, etc

<br>

## Why release another metrics poller when there are several good ones already out there?

StatsPoller was originally written at a time when there were few peers on the open-source market. Other tools have emerged then, and many of them are very good at what they do. StatsPoller has served Pearson well & is particularly well suited to monitor Java-based applications, so we figured that we'd just give the market another choice. 

<br>

## Installation

StatsPoller currently supports installation via rpm or via manual installation.

### Installation prerequisites

* Java 1.7 (or newer). Oracle Java is preferred. OpenJDK may also work, but some functionality may be disabled.
* A valid version of Java must be configured as an environment variable for the user that is running StatsPoller.
* (Linux) Kernel 2.6 or newer
* (Linux) Several metric collectors depend on the 'proc' filesystem being mounted somewhere (StatsPoller assumes /proc by default)

### RPM installation on Linux (for RedHat, CentOS, etc)

* Instructions coming shortly...

### Manual installation on Linux (may vary on your distro)

* sudo unzip statspoller.zip -d /opt
* sudo cp /opt/StatsPoller/init.d/statspoller /etc/init.d/statspoller
* sudo chmod -R 744 /opt/StatsPoller/bin/*
* sudo chmod 744 /opt/StatsPoller/StatsPoller.jar
* sudo chmod 755 /etc/init.d/statspoller
* sudo chkconfig --add statspoller

### Manual installation on Windows

* Instructions coming shortly...

<br>

## Configuration

After installing StatsPoller, one typically only needs to edit a single configuration file. This file is [application.properties](./conf/application.properties). If you're just looking to use the basic metric collectors (ex- Linux OS), then you may only need to configure one of the 'output modules' (Graphite or OpenTSDB). A full listing of StatsPoller's available configuration options, documentation, and examples can be found at [example_application.properties](./conf/example_application.properties).

<br>

If you feel constrained by putting a large number of configurations into application.properties, then you can put some of them into configuration files of your own creation @ /conf/optional. Put any file with an extension of .properties into /conf/optional & StatsPoller will read its configuration fields. Please note that several fields can only be set in the main application.properties file. Configurations that can be read from /conf/optional/*.properties include: JMX, ApacheHTTP, FileCounter, ProcessCounter, MySQL, MongoDB, external metric-collectors.

<br>

JMX configuration can be particularly complicated. Here are some default JMX configurations that may help make setting up JMX metric collectors easier.
...

<br>

## Thanks to...
* Grafana : http://grafana.org/
* Graphite : Orbitz @ https://github.com/graphite-project/
* OpenTSDB : http://opentsdb.net/
* Pearson Assessments, a division of Pearson Education: http://www.pearsonassessments.com/




