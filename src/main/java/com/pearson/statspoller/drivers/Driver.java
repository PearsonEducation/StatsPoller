package com.pearson.statspoller.drivers;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.pearson.statspoller.internal_metric_collectors.statspoller_native.StatsPollerNativeCollectorsThread;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.pearson.statspoller.globals.ApplicationConfiguration;
import com.pearson.statspoller.external_metric_collectors.ExternalMetricCollectorExecuterThread;
import com.pearson.statspoller.output.OutputMetricsInvokerThread;
import com.pearson.statspoller.external_metric_collectors.ReadMetricsFromFileThread;
import com.pearson.statspoller.external_metric_collectors.ExternalMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.apache_http.ApacheHttpMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.file_counter.FileCounterMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.jmx.JmxJvmShutdownHook;
import com.pearson.statspoller.internal_metric_collectors.jmx.JmxMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Connections.ConnectionsCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Cpu.CpuCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.DiskIo.DiskIoCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.FileSystem.FileSystemCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Memory.MemoryCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Network.NetworkBandwidthCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.ProcessCounter.ProcessCounterMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.linux.Uptime.UptimeCollector;
import com.pearson.statspoller.internal_metric_collectors.mongo.MongoMetricCollector;
import com.pearson.statspoller.internal_metric_collectors.mysql.MysqlMetricCollector;
import com.pearson.statspoller.utilities.FileIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class Driver {
    
    private static final Logger logger = LoggerFactory.getLogger(Driver.class.getName());
    
    private static final ExecutorService threadExecutor_ = Executors.newCachedThreadPool();
    
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
            logger.error(successOutput);
        }
        
        launchStatsPollerCollector();
                
        launchLinuxCollectors();
                
        launchFileCountCollectors();
        
        launchJmxCollectors();

        launchApacheCollectors();
        
        launchMongoCollectors();
                
        launchMysqlCollectors();
        
        launchExternalMetricCollectors();
        
        // start the 'output metrics' invoker thread
        Thread outputMetricsInvokerThread = new Thread(new OutputMetricsInvokerThread(ApplicationConfiguration.getOutputInterval()));
        outputMetricsInvokerThread.start();
       
        while(true) {
            Threads.sleepSeconds(1000);
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

    private static void launchStatsPollerCollector() {
        
        // start StatsPoller's built-in metric collectors
        Thread statsPollerCollectorsThread = new Thread(new StatsPollerNativeCollectorsThread(
                ApplicationConfiguration.isStatsPollerEnableJavaMetricCollector(),
                ApplicationConfiguration.getStatspollerJavaMetricCollectorCollectionInterval(), 
                ApplicationConfiguration.getStatspollerMetricCollectorPrefix(), 
                "./output/statspoller_native.out", ApplicationConfiguration.isOutputInternalMetricsToDisk(),
                Version.getProjectVersion()));
        
        threadExecutor_.execute(statsPollerCollectorsThread);
    }
    
    private static void launchFileCountCollectors() {

        for (FileCounterMetricCollector fileCounterMetricCollector : ApplicationConfiguration.getFileCounterMetricCollectors()) {
            threadExecutor_.execute(fileCounterMetricCollector);
        }
        
    }
    
    private static void launchLinuxCollectors() {
        
        // start StatsPoller's built-in linux metric collectors
        
        Thread linuxConnectionsCollectorThread = new Thread(new ConnectionsCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(),
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Connections", "./output/linux_connections.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxConnectionsCollectorThread);
        
        Thread linuxCpuCollectorThread = new Thread(new CpuCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(),
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Cpu", "./output/linux_cpu.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxCpuCollectorThread);
        
        Thread linuxNetworkCollectorThread = new Thread(new NetworkBandwidthCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Network-Bandwidth", "./output/linux_network_bandwidth.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxNetworkCollectorThread);
        
        Thread linuxMemoryCollectorThread = new Thread(new MemoryCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Memory", "./output/linux_memory.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxMemoryCollectorThread);
        
        Thread linuxUptimeCollectorThread = new Thread(new UptimeCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.Uptime", "./output/linux_uptime.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxUptimeCollectorThread);
        
        Thread linuxFileSystemCollectorThread = new Thread(new FileSystemCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.FileSystem", "./output/linux_filesystem.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxFileSystemCollectorThread);
        
        Thread linuxDiskIoCollectorThread = new Thread(new DiskIoCollector(ApplicationConfiguration.isLinuxMetricCollectorEnable(), 
                ApplicationConfiguration.getLinuxMetricCollectorCollectionInterval(), "Linux.DiskIO", "./output/linux_disk_io.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk()));
        threadExecutor_.execute(linuxDiskIoCollectorThread);
        
        Thread processCounterCollectorThread = new Thread(new ProcessCounterMetricCollector(true,
                ApplicationConfiguration.getProcessCounterMetricCollectorCollectionInterval(), "ProcessCounter", "./output/linux_process_counter.out", 
                ApplicationConfiguration.isOutputInternalMetricsToDisk(), ApplicationConfiguration.getProcessCounterPrefixesAndRegexes()));
        threadExecutor_.execute(processCounterCollectorThread);
    }
    
    private static void launchJmxCollectors() {
        
        // start 'jmx metric collector' threads
        for (JmxMetricCollector jmxMetricCollector : ApplicationConfiguration.getJmxMetricCollectors()) {
            threadExecutor_.execute(jmxMetricCollector);
        }
        
        // jvm shutdown hook for jmx -- makes sure that jmx connections get closed before this program is terminated
        if (!ApplicationConfiguration.getJmxMetricCollectors().isEmpty()) {
            JmxJvmShutdownHook jmxJvmShutdownHook = new JmxJvmShutdownHook();
            Runtime.getRuntime().addShutdownHook(jmxJvmShutdownHook);
        }
        
    }
    
    private static void launchApacheCollectors() {
              
        for (ApacheHttpMetricCollector apacheHttpMetricCollector : ApplicationConfiguration.getApacheHttpMetricCollectors()) {
            threadExecutor_.execute(apacheHttpMetricCollector);
            Threads.sleepSeconds(1);
        }
        
    }
    
    private static void launchMongoCollectors() {
              
        for (MongoMetricCollector mongoMetricCollector : ApplicationConfiguration.getMongoMetricCollectors()) {
            threadExecutor_.execute(mongoMetricCollector);
        }
        
    }
    
    private static void launchMysqlCollectors() {
              
        for (MysqlMetricCollector mysqlMetricCollector : ApplicationConfiguration.getMysqlMetricCollectors()) {
            threadExecutor_.execute(mysqlMetricCollector);
        }
        
    }
        
    private static void launchExternalMetricCollectors() {
        
        // start 'metric collector' threads & the corresponding 'read from output file' threads
        for (ExternalMetricCollector metricCollector : ApplicationConfiguration.getExternalMetricCollectors()) {
            logger.info("Message=\"Starting metric collector\", Collector=\"" + metricCollector.getMetricPrefix() + "\"");
            Thread metricCollectorExecuterThread = new Thread(new ExternalMetricCollectorExecuterThread(metricCollector));
            threadExecutor_.execute(metricCollectorExecuterThread);
            
            Threads.sleepMilliseconds(500);
            
            Thread readMetricsFromFileThread = new Thread(new ReadMetricsFromFileThread(metricCollector.getFileFromOutputPathAndFilename(), 
                    ApplicationConfiguration.getCheckOutputFilesInterval(), metricCollector.getMetricPrefix()));
            threadExecutor_.execute(readMetricsFromFileThread);
            
            Threads.sleepSeconds(1);
        }
        
    }
    
}
