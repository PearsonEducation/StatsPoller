package com.pearson.statspoller.output;

import com.pearson.statspoller.utilities.core_utils.InvokerThread;
import com.pearson.statspoller.utilities.core_utils.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class OutputMetricsInvokerThread extends InvokerThread implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(OutputMetricsInvokerThread.class.getName());
    
    private final long invocationIntervalInMilliseconds_;
    private final long threadExecutorShutdownWaitTime_;
    
    public OutputMetricsInvokerThread(long invocationIntervalInMilliseconds) {
        this.invocationIntervalInMilliseconds_ = invocationIntervalInMilliseconds;
        this.threadExecutorShutdownWaitTime_ = 5000;
    }
    
    @Override
    public void run() {

        synchronized (lockObject_) {
            while (continueRunning_) {
                OutputMetricsThread outputMetricsThread = new OutputMetricsThread();
                threadExecutor_.execute(outputMetricsThread);

                try {
                    lockObject_.wait(invocationIntervalInMilliseconds_);
                    while(!outputMetricsThread.isFinished()) lockObject_.wait(50);
                }
                catch (Exception e) {}
            }
        }
        
        while (!threadExecutor_.isTerminated()) {
            Threads.sleepMilliseconds(100);
        }
        
        isShutdown_ = true;
    }
    
    public long getInvocationIntervalInMilliseconds() {
        return invocationIntervalInMilliseconds_;
    }
    
    @Override
    public long getThreadExecutorShutdownWaitTime() {
        return threadExecutorShutdownWaitTime_;
    }

}