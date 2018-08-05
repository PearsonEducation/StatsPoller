package com.pearson.statspoller.output;

import com.pearson.statspoller.globals.ApplicationConfiguration;
import java.util.ArrayList;
import java.util.List;
import com.pearson.statspoller.globals.GlobalVariables;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetricFormat;
import com.pearson.statspoller.metric_formats.graphite.GraphiteOutputModule;
import com.pearson.statspoller.metric_formats.graphite.SendMetricsToGraphiteThread;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbHttpOutputModule;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetricFormat;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbTelnetOutputModule;
import com.pearson.statspoller.metric_formats.opentsdb.SendMetricsToOpenTsdbThread;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class OutputMetricsThread implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(OutputMetricsThread.class.getName());
    
    private boolean isFinished_ = false;
    private static int maxOutputTimeForAnOutputModule_ = (int) (ApplicationConfiguration.getOutputInterval() - 2500);
    private static int connectTimeoutForATcpOutputModule_ = (int) ((ApplicationConfiguration.getOutputInterval() - 3000) / 2);
    private static int connectTimeoutForAHttpOutputModule_ = (int) ((ApplicationConfiguration.getOutputInterval() - 3000) / 3);
    private static int readTimeoutForAHttpOutputModule_ = (int) (((ApplicationConfiguration.getOutputInterval() - 3000) * 2) / 3);

    @Override
    public void run() {
        // ensure that the threadpool stays alive for at least 2.5 seconds
        if (maxOutputTimeForAnOutputModule_ < 2500) maxOutputTimeForAnOutputModule_ = 2500;
        
        // ensure that at least 1 second is allowed to connect to the server (opentsdb telnet, graphite)
        if (connectTimeoutForATcpOutputModule_ < 1000) connectTimeoutForATcpOutputModule_ = 1000;
        
        // ensure that at least 1 second is allowed to connect to the server (opentsdb http)
        if (connectTimeoutForAHttpOutputModule_ < 1000) connectTimeoutForAHttpOutputModule_ = 1000;
        
        // ensure that at least 1 second is allowed for the server to reply to the http request (opentsdb http)
        if (readTimeoutForAHttpOutputModule_ < 1000) readTimeoutForAHttpOutputModule_ = 1000;
        
        List metrics = new ArrayList<>();
        metrics.addAll(getCurrentGraphiteMetricsAndRemoveMetricsFromGlobal());
        metrics.addAll(getCurrentOpenTsdbMetricsAndRemoveMetricsFromGlobal());

        List<Thread> outputThreads = new ArrayList<>();
        outputThreads.addAll(getSendMetricsToAllGraphiteOutputModuleThreads(metrics, "G-" + System.currentTimeMillis()));
        outputThreads.addAll(getSendMetricsToAllOpentsdbTelnetOutputModuleThreads(metrics, "OTSDB-T-" + System.currentTimeMillis()));
        outputThreads.addAll(getSendMetricsToAllOpentsdbHttpOutputModuleThreads(metrics, "OTSDB-H-" + System.currentTimeMillis()));
        
        Threads.threadExecutorCachedPool(outputThreads, maxOutputTimeForAnOutputModule_, TimeUnit.MILLISECONDS);
        
        isFinished_ = true;
    }
    
    public static List<Thread> getSendMetricsToAllGraphiteOutputModuleThreads(List<? extends GraphiteMetricFormat> graphiteMetrics, String threadId) {
        
        if ((graphiteMetrics == null) || graphiteMetrics.isEmpty() || (threadId == null) || threadId.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Thread> sendMetricsToGraphiteThreads = new ArrayList<>();
        List<GraphiteOutputModule> graphiteOutuputModules = ApplicationConfiguration.getGraphiteOutputModules();
        if ((graphiteOutuputModules == null) || graphiteOutuputModules.isEmpty()) return sendMetricsToGraphiteThreads;
            
        try { 
            for (GraphiteOutputModule graphiteOutputModule : graphiteOutuputModules) {
                if (!graphiteOutputModule.isOutputEnabled()) continue;
                
                SendMetricsToGraphiteThread sendMetricsToGraphiteThread = new SendMetricsToGraphiteThread(graphiteMetrics, 
                        graphiteOutputModule.isSanitizeMetrics(), graphiteOutputModule.isSubstituteCharacters(),
                        graphiteOutputModule.getHost(), graphiteOutputModule.getPort(), connectTimeoutForATcpOutputModule_,  
                        graphiteOutputModule.getNumSendRetryAttempts(), graphiteOutputModule.getMaxMetricsPerMessage(), threadId);
            
                Thread thread = new Thread(sendMetricsToGraphiteThread);
                sendMetricsToGraphiteThreads.add(thread);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return sendMetricsToGraphiteThreads;
    }
    
    public static List<Thread> getSendMetricsToAllOpentsdbTelnetOutputModuleThreads(List<? extends OpenTsdbMetricFormat> openTsdbMetrics, String threadId) {
        
        if ((openTsdbMetrics == null) || openTsdbMetrics.isEmpty() || (threadId == null) || threadId.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Thread> sendMetricsToOpenTsdbTelnetThreads = new ArrayList<>();
        List<OpenTsdbTelnetOutputModule> openTsdbTelnetOutputModules = ApplicationConfiguration.getOpenTsdbTelnetOutputModules();
        if ((openTsdbTelnetOutputModules == null) || openTsdbTelnetOutputModules.isEmpty()) return sendMetricsToOpenTsdbTelnetThreads;
            
        try { 
            for (OpenTsdbTelnetOutputModule openTsdbTelnetOutputModule : openTsdbTelnetOutputModules) {
                if (!openTsdbTelnetOutputModule.isOutputEnabled()) continue;
                
                SendMetricsToOpenTsdbThread sendMetricsToOpenTsdbThread = new SendMetricsToOpenTsdbThread(openTsdbMetrics, 
                        openTsdbTelnetOutputModule.isSanitizeMetrics(), "SP_Host", ApplicationConfiguration.getHostname(),
                        openTsdbTelnetOutputModule.getHost(), openTsdbTelnetOutputModule.getPort(), 
                        connectTimeoutForATcpOutputModule_, openTsdbTelnetOutputModule.getNumSendRetryAttempts(), threadId);

                Thread thread = new Thread(sendMetricsToOpenTsdbThread);
                sendMetricsToOpenTsdbTelnetThreads.add(thread);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return sendMetricsToOpenTsdbTelnetThreads;
    }
    
    public static List<Thread> getSendMetricsToAllOpentsdbHttpOutputModuleThreads(List<? extends OpenTsdbMetricFormat> openTsdbMetrics, String threadId) {
        
        if ((openTsdbMetrics == null) || openTsdbMetrics.isEmpty() || (threadId == null) || threadId.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Thread> sendMetricsToOpenTsdbHttpThreads = new ArrayList<>();
        List<OpenTsdbHttpOutputModule> openTsdbHttpOutputModules = ApplicationConfiguration.getOpenTsdbHttpOutputModules();
        if ((openTsdbHttpOutputModules == null) || openTsdbHttpOutputModules.isEmpty()) return sendMetricsToOpenTsdbHttpThreads;
            
        try { 
            for (OpenTsdbHttpOutputModule openTsdbHttpOutputModule : openTsdbHttpOutputModules) {
                if (!openTsdbHttpOutputModule.isOutputEnabled()) continue;
                      
                SendMetricsToOpenTsdbThread sendMetricsToOpenTsdbThread = new SendMetricsToOpenTsdbThread(openTsdbMetrics, 
                        openTsdbHttpOutputModule.isSanitizeMetrics(), "SP_Host", ApplicationConfiguration.getHostname(),
                        openTsdbHttpOutputModule.getUrl(), connectTimeoutForAHttpOutputModule_, readTimeoutForAHttpOutputModule_, 
                        openTsdbHttpOutputModule.getNumSendRetryAttempts(), openTsdbHttpOutputModule.getMaxMetricsPerMessage(), 
                        threadId);

                Thread thread = new Thread(sendMetricsToOpenTsdbThread);
                sendMetricsToOpenTsdbHttpThreads.add(thread);
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return sendMetricsToOpenTsdbHttpThreads;
    }
    
    private List<GraphiteMetric> getCurrentGraphiteMetricsAndRemoveMetricsFromGlobal() {

        if ((GlobalVariables.graphiteMetrics == null) || GlobalVariables.graphiteMetrics.isEmpty()) {
            return new ArrayList();
        }

        List<GraphiteMetric> graphiteMetrics = new ArrayList<>(GlobalVariables.graphiteMetrics.values());
 
        for (GraphiteMetric graphiteMetric : graphiteMetrics) {
            GlobalVariables.graphiteMetrics.remove(graphiteMetric.getHashKey());
        }

        return graphiteMetrics;
    }
    
    private List<OpenTsdbMetric> getCurrentOpenTsdbMetricsAndRemoveMetricsFromGlobal() {

        if ((GlobalVariables.openTsdbMetrics == null) || GlobalVariables.openTsdbMetrics.isEmpty()) {
            return new ArrayList();
        }

        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>(GlobalVariables.openTsdbMetrics.values());
 
        for (OpenTsdbMetric openTsdbMetric : openTsdbMetrics) {
            GlobalVariables.openTsdbMetrics.remove(openTsdbMetric.getHashKey());
        }

        return openTsdbMetrics;
    }
    
    public boolean isFinished() {
        return isFinished_;
    }

}
