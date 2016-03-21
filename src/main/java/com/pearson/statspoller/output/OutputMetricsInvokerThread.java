package com.pearson.statspoller.output;

import com.pearson.statspoller.utilities.InvokerThread;
import com.pearson.statspoller.utilities.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class OutputMetricsInvokerThread extends InvokerThread implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(OutputMetricsInvokerThread.class.getName());
    
    private final int invocationIntervalInMilliseconds_;
    private final int threadExecutorShutdownWaitTime_;
    
    public OutputMetricsInvokerThread(int invocationIntervalInMilliseconds) {
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
    
    public int getInvocationIntervalInMilliseconds() {
        return invocationIntervalInMilliseconds_;
    }
    
    @Override
    public int getThreadExecutorShutdownWaitTime() {
        return threadExecutorShutdownWaitTime_;
    }

}