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
  * [StatsPoller Native](./docs/statspoller_native.md) (A platform-independent OS metric collector that collects metrics from the perspective of the JVM runtime)  
  * [Linux OS](./docs/linux_collector.md) (network, filesystem, disk i/o, cpu, uptime, memory)
  * [Java JMX](./docs/jmx.md) (Cassandra, Tomcat, JBoss/Wildfly, etc)
  * [File Counter](./docs/file_counter.md) (counts/outputs the number of files in a specified folder)
  * [Process Counter](./docs/process_counter.md) (counts/outputs the number of processes running that match a particular regex pattern)
  * [Apache HTTP Server](./docs/apache_http.md) (Apache HTTP Server server-status metrics)
  * [MongoDB](./docs/mongo.md) (Mongo status, database, and collection metrics)
  * [MySQL](./docs/mysql.md) (MySQL server metrics)
  * [PostgreSQL](./docs/postgres.md) (PostgreSQL server metrics)
  * [Database Querier](./docs/db_querier.md) (Ad-hoc queries for business metrics. Supports MySQL or PostgreSQL)
* Support for user-created metric collectors (plugins).
  * StatsPoller supports running [user-created metric collectors](./docs/external_metric_collectors.md). These plugins are executed by StatsPoller & allow StatsPoller to output the metrics that they collect.
* Bundled 'user-created metric collectors' include...
  * Windows OS (cpu, disk, iis, memory, network  -- collected via PerfMon)
  * SQL Server (collected via PerfMon)
* Advanced/flexible configuration capabilities
  * Most metric collectors that collect metrics from external services (Mongo, MySQL, Java JMX, etc) can be configured to collect from up to 10,000 independent external services.
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

### rpm install on Linux (for RedHat, CentOS, etc)

* sudo rpm -ivh statspoller_version_xyz.rpm

### rpm update on Linux (for RedHat, CentOS, etc)

* sudo rpm -Uvh statspoller_version_xyz.rpm

### rpm remove on Linux (for RedHat, CentOS, etc)

* sudo rpm -e statspoller_version_xyz

### deb installation on Linux (for Ubuntu, Mint, etc)

* deb is being worked on & is targeted at a future StatsPoller release

### Installation on Windows

* A Windows installer is planned for a future release.

<br>

## Stopping/Starting StatsPoller

### RedHat, CentOS, etc  -- Versions 6.x & older

Start StatsPoller: sudo /sbin/service statspoller start  
Stop StatsPoller: sudo /sbin/service statspoller stop  
Restart StatsPoller: sudo /sbin/service statspoller restart  
View StatsPoller status: sudo /sbin/service statspoller status  

### RedHat, CentOS, etc  -- Versions 7.x & newer

Start StatsPoller: sudo systemctl start statspoller  
Stop StatsPoller: sudo systemctl stop statspoller  
Restart StatsPoller: sudo systemctl restart statspoller  
View StatsPoller status: sudo systemctl status statspoller  

<br>

## Configuration

After installing StatsPoller, one typically only needs to edit a single configuration file. This file is [application.properties](./conf/application.properties). If you're just looking to use the basic metric collectors (ex- Linux OS), then you may only need to configure one of the 'output modules' (Graphite or OpenTSDB). A full listing of StatsPoller's available configuration options, documentation, and examples can be found at [example_application.properties](./conf/example_application.properties). A demo configuration file, with most fields filled out, can be found at [example_demo_application.properties](./conf/example_demo_application.properties).

<br>

If you feel constrained by putting a large number of configurations into application.properties, then you can put some of them into configuration files of your own creation @ /conf/optional. Put any file with an extension of .properties into /conf/optional & StatsPoller will read its configuration fields. Please note that several fields can only be set in the main application.properties file. Configurations that can be read from /conf/optional/*.properties include: JMX, ApacheHTTP, FileCounter, ProcessCounter, MySQL, MongoDB, external metric-collectors.

<br>

## Thanks to...
* Grafana : http://grafana.org/
* Graphite : Orbitz @ https://github.com/graphite-project/
* OpenTSDB : http://opentsdb.net/
* Pearson Assessments, a division of Pearson Education: http://www.pearsonassessments.com/
