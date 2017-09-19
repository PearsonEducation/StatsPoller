package com.pearson.statspoller.drivers;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.pearson.statspoller.internal_metric_collectors.statspoller_native.StatsPollerNativeCollectorsThread;
import java.io.File;
import com.pearson.statspoller.globals.ApplicationConfiguration;
import com.pearson.statspoller.external_metric_collectors.ExternalMetricCollectorExecuterThread;
import com.pearson.statspoller.output.OutputMetricsInvokerThread;
import com.pearson.statspoller.external_metric_collectors.ReadMetricsFromFileThread;
import com.pearson.statspoller.external_metric_collectors.ExternalMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.apache_http.ApacheHttpMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.CadvisorMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.db_querier.DbQuerier;
import com.pearson.statspoller.internal_metric_collectors.file_counter.FileCounterMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.jmx.JmxJvmShutdownHook;
import com.pearson.statspoller.internal_metric_collectors.jmx.JmxMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Connections.ConnectionsCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Cpu.CpuCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.DiskIo.DiskIoCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.FileSystem.FileSystemCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Memory.MemoryCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Network.NetworkBandwidthCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.ProcessCounter.ProcessCounterCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.ProcessStatus.ProcessStatusCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Uptime.UptimeCollector;
import com.pearson.statspoller.internal_metric_collectors.mongo.MongoMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.mysql.MysqlMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.postgres.PostgresMetricCollector;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class Driver {
    
    private static final Logger logger = LoggerFactory.getLogger(Driver.class.getName());
    
    //private static final ExecutorService threadExecutor_ = Executors.newCachedThreadPool();
    private static final Map<String,Thread> metricCollectorThreads_ByThreadId_ = new ConcurrentHashMap<>();
    
    public static void main(String[] args) {
        // 2 second startup delay -- helps to make sure that old metric data isn't output
        Threads.sleepSeconds(2);
        
        boolean initializeSuccess = initializeApplication();
        if (!initializeSuccess) {
            String errorOutput = "StatsPoller encountered an error during initialization. Shutting down @ " + new Date();
            System.out.println(errorOutput);
            logger.error(errorOutput);
            System.exit(1);
        }
        else {
            String successOutput = "StatsPoller successfully initialized @ " + new Date();
            System.out.println(successOutput);
            logger.info(successOutput);
        }
        
        // initial launch of metric collector threads
        launchCollectorThreads();
        
        // jvm shutdown hook for jmx -- makes sure that jmx connections get closed before this program is terminated
        if ((ApplicationConfiguration.getJmxMetricCollectors() != null) && !ApplicationConfiguration.getJmxMetricCollectors().isEmpty()) {
            JmxJvmShutdownHook jmxJvmShutdownHook = new JmxJvmShutdownHook();
            Runtime.getRuntime().addShutdownHook(jmxJvmShutdownHook);
        }
        
        // start the 'output metrics' invoker thread
        Thread outputMetricsInvokerThread = new Thread(new OutputMetricsInvokerThread(ApplicationConfiguration.getOutputInterval()));
        outputMetricsInvokerThread.start();
       
        // maintainance loop -- relauches crashed threads, occasionally GCs, etc
        int i = 0;
        while(true) {
            Threads.sleepSeconds(15);
            
            if (i >= 12) { // every 3mins...
                System.gc(); // cleans up any leaked resources, keeps process memory usage down
                i = 0;
            }
            else i++;
            
            launchCollectorThreads();
        }
        
    }
    
    private static boolean initializeApplication() {
        
        boolean doesDevLogConfExist = FileIo.doesFileExist(System.getProperty("user.dir") + File.separator + "conf" + File.separator + "logback_config_dev.xml");
        
        boolean isLogbackSuccess;
        if (doesDevLogConfExist) isLogbackSuccess = readAndSetLogbackConfiguration(System.getProperty("user.dir") + File.separator + "conf", "logback_config_dev.xml");
        else isLogbackSuccess = readAndSetLogbackConfiguration(System.getProperty("user.dir") + File.separator + "conf", "logback_config.xml");
            
        boolean doesDevAppConfExist = FileIo.doesFileExist(System.getProperty("user.dir") + File.separator + "conf" + File.separator + "application_dev.properties");
        
        boolean isApplicationConfigSuccess;
        if (doesDevAppConfExist) isApplicationConfigSuccess = ApplicationConfiguration.initialize(System.getProperty("user.dir") + File.separator + "conf" + File.separator + "application_dev.properties", true);
        else isApplicationConfigSuccess = ApplicationConfiguration.initialize(System.getProperty("user.dir") + File.separator + "conf" + File.separator + "application.properties", true);
        
        try {
            File optionalConfigurationFiles_Directory = new File(System.getProperty("user.dir") + File.separator + "conf" + File.separator + "optional");
            if (optionalConfigurationFiles_Directory.exists()) {
                File[] optionalConfigurationFiles = optionalConfigurationFiles_Directory.listFiles();
                
                for (File optionalConfigurationFile : optionalConfigurationFiles) {
                    if (optionalConfigurationFile.getName().endsWith(".properties")) {
                        ApplicationConfiguration.initialize(System.getProperty("user.dir") + File.separator + "conf" + File.separator + "optional" + File.separator +
                                optionalConfigurationFile.getName(), false);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.warn(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }

        if (!isApplicationConfigSuccess || !isLogbackSuccess) {
            logger.error("An error during application initialization. Exiting...");
            return false;
        }

        logger.info("Finish - Initialize application");
        
        return true;
    }
    
    private static boolean readAndSetLogbackConfiguration(String filePath, String fileName) {
        
        boolean doesConfigFileExist = FileIo.doesFileExist(filePath, fileName);
        
        if (doesConfigFileExist) {
            File logggerConfigFile = new File(filePath + File.separator + fileName);

            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            try {
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(context);
                context.reset(); 
                configurator.doConfigure(logggerConfigFile);
                StatusPrinter.printInCaseOfErrorsOrWarnings(context);
                return true;
            } 
            catch (Exception e) {
                StatusPrinter.printInCaseOfErrorsOrWarnings(context);
                return false;
            }
        }
        else {
            return false;
        }
    }

    private static void launchCollectorThreads() {
        launchStatsPollerCollector();
        launchLinuxCollectors();               
        launchProcessCounterCollector();    
        launchFileCountCollectors();
        launchJmxCollectors();
        launchApacheHttpCollectors();
        launchCadvisorCollectors();
        launchMongoCollectors();  
        launchMysqlCollectors();
        launchPostgresCollectors();
        launchDbQueriers();
        launchExternalMetricCollectors();
    }
    
    private static void launchStatsPollerCollector() {
        
        String threadId;
        Thread currentThread;
        
        threadId = "StatsPollerNative";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread statsPollerCollectorsThread = new Thread(new StatsPollerNativeCollectorsThread(
                    ApplicationConfiguration.isStatsPollerEnableJavaMetricCollector(),
                    ApplicationConfiguration.getStatspollerJavaMetricCollectorCollectionInterval(), 
                    ApplicationConfiguration.getStatspollerMetricCollectorPrefix(), 
                    "./output/statspoller_native.out", ApplicationConfiguration.isOutputInternalMetricsToDisk(),
                    Version.getProjectVersion()));
            statsPollerCollectorsThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, statsPollerCollectorsThread);
            Threads.sleepMilliseconds(500);
        }
        
    }
    
    private static void launchFileCountCollectors() {

        if (ApplicationConfiguration.getFileCounterMetricCollectors() == null) return;
        
        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (FileCounterMetricCollector fileCounterMetricCollector : ApplicationConfiguration.getFileCounterMetricCollectors()) {
            threadId = "FileCounter" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                Thread fileCounterMetricCollectorThread = new Thread(fileCounterMetricCollector);
                fileCounterMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, fileCounterMetricCollectorThread);
                Threads.sleepMilliseconds(250);
            }
        }
        
    }
    
    private static void launchLinuxCollectors() {
        
        if (!ApplicationConfiguration.isLinuxMetricCollectorEnable()) return;
        
        String threadId;
        Thread currentThread;
        
        threadId = "Linux.Connections";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxConnectionsCollectorThread = new Thread(new ConnectionsCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(),
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Connections", "./output/linux_connections.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxConnectionsCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, linuxConnectionsCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.Cpu";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxCpuCollectorThread = new Thread(new CpuCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(),
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Cpu", "./output/linux_cpu.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxCpuCollectorThread.start();;
            metricCollectorThreads_ByThreadId_.put(threadId, linuxCpuCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.Network-Bandwidth";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxNetworkCollectorThread = new Thread(new NetworkBandwidthCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Network-Bandwidth", "./output/linux_network_bandwidth.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxNetworkCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, linuxNetworkCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.Memory";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxMemoryCollectorThread = new Thread(new MemoryCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Memory", "./output/linux_memory.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxMemoryCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, linuxMemoryCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.Uptime";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxUptimeCollectorThread = new Thread(new UptimeCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Uptime", "./output/linux_uptime.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxUptimeCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, linuxUptimeCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.FileSystem";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxFileSystemCollectorThread = new Thread(new FileSystemCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.FileSystem", "./output/linux_filesystem.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxFileSystemCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, linuxFileSystemCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.DiskIO";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread linuxDiskIoCollectorThread = new Thread(new DiskIoCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.DiskIO", "./output/linux_disk_io.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            linuxDiskIoCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, linuxDiskIoCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
        threadId = "Linux.ProcessStatus";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread processStatusCollectorThread = new Thread(new ProcessStatusCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                    ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.ProcessStatus", "./output/linux_process_status.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk()));
            processStatusCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, processStatusCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
    }
    
    private static void launchProcessCounterCollector() {
        
        String threadId;
        Thread currentThread;
        
        threadId = "ProcessCounter";
        currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
        if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
            if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
            Thread processCounterCollectorThread = new Thread(new ProcessCounterCollector(true,
                    ApplicationConfiguration.getProcessCounterMetricCollectorCollectionInterval(), "ProcessCounter", "./output/linux_process_counter.out", 
                    ApplicationConfiguration.isOutputInternalMetricsToDisk(), ApplicationConfiguration.getProcessCounterPrefixesAndRegexes()));
            processCounterCollectorThread.start();
            metricCollectorThreads_ByThreadId_.put(threadId, processCounterCollectorThread);
            Threads.sleepMilliseconds(250);
        }
        
    }
        
    private static void launchJmxCollectors() {
        
        if (ApplicationConfiguration.getJmxMetricCollectors() == null) return;
        
        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (JmxMetricCollector jmxMetricCollector : ApplicationConfiguration.getJmxMetricCollectors()) {
            threadId = "JMX" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread jmxMetricCollectorThread = new Thread(jmxMetricCollector);
                jmxMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, jmxMetricCollectorThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
    
    private static void launchApacheHttpCollectors() {
        
        if (ApplicationConfiguration.getApacheHttpMetricCollectors() == null) return;
        
        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (ApacheHttpMetricCollector apacheHttpMetricCollector : ApplicationConfiguration.getApacheHttpMetricCollectors()) {
            threadId = "ApacheHTTP" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread apacheHttpMetricCollectorThread = new Thread(apacheHttpMetricCollector);
                apacheHttpMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, apacheHttpMetricCollectorThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
    
    private static void launchCadvisorCollectors() {
        
        if (ApplicationConfiguration.getCadvisorMetricCollectors() == null) return;

        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (CadvisorMetricCollector cadvisorMetricCollector : ApplicationConfiguration.getCadvisorMetricCollectors()) {
            threadId = "cAdvisor" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread cadvisorMetricCollectorThread = new Thread(cadvisorMetricCollector);
                cadvisorMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, cadvisorMetricCollectorThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
    
    private static void launchMongoCollectors() {
        
        if (ApplicationConfiguration.getMongoMetricCollectors() == null) return;

        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (MongoMetricCollector mongoMetricCollector : ApplicationConfiguration.getMongoMetricCollectors()) {
            threadId = "Mongo" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread mongoMetricCollectorThread = new Thread(mongoMetricCollector);
                mongoMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, mongoMetricCollectorThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
    
    private static void launchMysqlCollectors() {
        
        if (ApplicationConfiguration.getMysqlMetricCollectors() == null) return;
        
        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (MysqlMetricCollector mysqlMetricCollector : ApplicationConfiguration.getMysqlMetricCollectors()) {
            threadId = "MySQL" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread mysqlMetricCollectorThread = new Thread(mysqlMetricCollector);
                mysqlMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, mysqlMetricCollectorThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
    
    private static void launchPostgresCollectors() {
        
        if (ApplicationConfiguration.getPostgresMetricCollectors() == null) return;
        
        String threadId;
        Thread currentThread;
        int counter = 1;
         
        for (PostgresMetricCollector postgresMetricCollector : ApplicationConfiguration.getPostgresMetricCollectors()) {
            threadId = "Postgres" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread postgresMetricCollectorThread = new Thread(postgresMetricCollector);
                postgresMetricCollectorThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, postgresMetricCollectorThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
    
    private static void launchDbQueriers() {
        
        if (ApplicationConfiguration.getDbQueriers() == null) return;
        
        String threadId;
        Thread currentThread;
        int counter = 1;
        
        for (DbQuerier dbQuerier : ApplicationConfiguration.getDbQueriers()) {
            threadId = "DB-Querier" + "-" + counter++;
            currentThread = metricCollectorThreads_ByThreadId_.get(threadId);
            
            if ((currentThread == null) || (!currentThread.isAlive() && currentThread.getState().toString().equals("TERMINATED"))) {
                if ((currentThread != null) && !currentThread.isAlive()) logger.warn("Dead Metric Collector Thread Detected. ThreadID=" + threadId);
                Thread dbQuerierThread = new Thread(dbQuerier);
                dbQuerierThread.start();
                metricCollectorThreads_ByThreadId_.put(threadId, dbQuerierThread);
                Threads.sleepSeconds(1);
            }
        }
        
    }
     
    // start 'metric collector' threads & the corresponding 'read from output file' threads
    private static void launchExternalMetricCollectors() {
        
        if (ApplicationConfiguration.getExternalMetricCollectors() == null) return;

        for (ExternalMetricCollector metricCollector : ApplicationConfiguration.getExternalMetricCollectors()) {
            logger.info("Message=\"Starting metric collector\", Collector=\"" + metricCollector.getMetricPrefix() + "\"");
            Thread metricCollectorExecuterThread = new Thread(new ExternalMetricCollectorExecuterThread(metricCollector));
            metricCollectorExecuterThread.start();
            
            Threads.sleepMilliseconds(500);
            
            Thread readMetricsFromFileThread = new Thread(new ReadMetricsFromFileThread(metricCollector.getFileFromOutputPathAndFilename(), 
                    ApplicationConfiguration.getCheckOutputFilesInterval(), metricCollector.getMetricPrefix()));
            readMetricsFromFileThread.start();
                  
            Threads.sleepSeconds(1);
        }
        
    }
    
}
