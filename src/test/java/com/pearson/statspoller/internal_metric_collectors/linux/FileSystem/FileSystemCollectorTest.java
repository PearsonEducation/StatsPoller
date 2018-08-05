package com.pearson.statspoller.internal_metric_collectors.linux.FileSystem;

import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Jeffrey Schmidt
 */
public class FileSystemCollectorTest {
    
    public FileSystemCollectorTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    @Test
    public void testOutputWithErrors() {
        String dfOutputWithErrors = ""
                + "Filesystem         1024-blocks      Used Available   Capacity Mounted on\n"
//                + "/dev/mapper/docker-202:1-263303-2e1ddc10fbe0b0cb4a9761748a9df71f0b672674a697088a39b6369492c11021                                                                              10190136    133564   9515900   1% /\n"
                + "tmpfs                  8217156         0   8217156         0% /dev\n"
//                + "df: /sys/fs/cgroup/blkio: No such file or directory\n"
//                + "df: /sys/fs/cgroup/cpu: No such file or directory\n"
//                + "df: /sys/fs/cgroup/cpuacct: No such file or directory\n"
//                + "df: /sys/fs/cgroup/cpuset: No such file or directory\n"
//                + "df: /sys/fs/cgroup/devices: No such file or directory\n"
//                + "df: /sys/fs/cgroup/freezer: No such file or directory\n"
//                + "df: /sys/fs/cgroup/hugetlb: No such file or directory\n"
//                + "df: /sys/fs/cgroup/memory: No such file or directory\n"
//                + "df: /sys/fs/cgroup/perf_event: No such file or directory\n"
                + "/dev/xvda1             8123812    945972   7077592        12% /app/logs\n"
                + "/dev/xvda1             8123812    945972   7077592        12% /etc/resolv.conf\n"
                + "/dev/xvda1             8123812    945972   7077592        12% /etc/hostname\n"
                + "/dev/xvda1             8123812    945972   7077592        12% /etc/hosts\n"
                + "shm                      65536         0     65536         0% /dev/shm\n"
                + "/dev/xvda1             8123812    945972   7077592        12% /usr/local/confd\n"
                + "tmpfs                  8217156         0   8217156         0% /proc/kcore\n"
                + "tmpfs                  8217156         0   8217156         0% /proc/latency_stats\n"
                + "tmpfs                  8217156         0   8217156         0% /proc/timer_list\n"
                + "tmpfs                  8217156         0   8217156         0% /proc/sched_debug\n"
                + "tmpfs                  8217156         0   8217156         0% /sys/firmware\n";
        
        FileSystemCollector fileSystemCollector = new FileSystemCollector(true, 30000, "unitTest", "unitTest", false);
        List<GraphiteMetric> graphiteMetrics = fileSystemCollector.getMetricsFromDfOutput(dfOutputWithErrors, (int) (System.currentTimeMillis() / 1000));
        System.out.println();
    }
    
}
