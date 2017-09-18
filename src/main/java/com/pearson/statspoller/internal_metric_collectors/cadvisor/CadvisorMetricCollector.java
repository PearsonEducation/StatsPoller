package com.pearson.statspoller.internal_metric_collectors.cadvisor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json.Docker;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json.Memory_;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json.Network;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.docker_json.Stat;
import com.pearson.statspoller.internal_metric_collectors.cadvisor.machine_json.Machine;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbMetric;
import com.pearson.statspoller.metric_formats.opentsdb.OpenTsdbTag;
import com.pearson.statspoller.utilities.JsonUtils;
import com.pearson.statspoller.utilities.NetIo;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 * 
 * The serialized docker json stuff comes from using http://www.jsonschema2pojo.org/
 */
public class CadvisorMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(CadvisorMetricCollector.class.getName());
    
    private static final String CADVISOR_STAT_TIMESTAMP_MILLISECOND_PRECISION_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);
    private static final BigDecimal ONE_THOUSAND = new BigDecimal(1000);
    private static final BigDecimal ONE_THOUSAND_TWENTY_FOUR = new BigDecimal(1024);
    private static final BigDecimal BYTES_TO_MEGABITS_DIVISOR = new BigDecimal("125000");
    private static final int SCALE = 7;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final String protocol_;
    private final String host_;
    private final int port_;
    private final String username_;
    private final String password_;
    private final String apiVersion_;
    
    private Map<String,PreviousStatMetadata> previousStatMetadatas_ByDockerId_ = new HashMap<>();
    
    public CadvisorMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
                String outputFilePathAndFilename, boolean writeOutputFiles,
                String protocol, String host, int port, String username, String password, String apiVersion) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.protocol_ = protocol;
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
        this.apiVersion_ = apiVersion;
    }
    
    @Override
    public void run() {
         
        boolean isValidApiVersion = isValidApiVersion(apiVersion_);
        if (!isValidApiVersion) {
            logger.error("Error running cAdvisor metric collection routine. Invalid API version specified.  " +
                    "cAdvisorLocation=\"" + protocol_ + "://" + host_ + ":" + port_ + "\", Version=\"" + apiVersion_ + "\"");
            return;
        }
        
        if ((previousStatMetadatas_ByDockerId_ != null) && !previousStatMetadatas_ByDockerId_.isEmpty()) {
            previousStatMetadatas_ByDockerId_.clear();
        }
        
        while(super.isEnabled()) {

            long routineStartTime = System.currentTimeMillis();
            long routineTimeElapsed = -1;
            boolean didConnectToCadvisor = false;
            
            try {      
                List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
                Map<String,PreviousStatMetadata> previousStatMetadatas_ByDockerId = new HashMap<>();
                
                // get raw docker metrics & serialize into java objects
                String dockerJson = getCadvisorDockerJson();
                List<Docker> dockers = parseCadvisorDockerJson(dockerJson);
                
                // get raw machine metric & serialize into java objects
                String machineJson = getCadvisorMachineJson();
                Machine machine = parseCadvisorMachineJson(machineJson);
                
                if ((dockers != null) && !dockers.isEmpty() && (machine != null) && (machine.getSystemUuid() != null) && !machine.getSystemUuid().isEmpty()) {
                    didConnectToCadvisor = true;
                }
                
                // for each docker container, get metrics
                for (Docker docker : dockers) {
                    if ((docker == null) || (docker.getId() == null) || docker.getId().isEmpty() || (docker.getSpec() == null) || (docker.getStats() == null)) continue;
                    if (machine == null) continue;
                    
                    PreviousStatMetadata previousStatMetadata_CurrentIteration = previousStatMetadatas_ByDockerId_.get(docker.getId());
                                        
                    // get timestamp
                    Stat latestStat = getLatestStatForDocker(docker);
                    Date statTimestamp = getDateFromTimestampString(latestStat.getTimestamp());
                    
                    // create StatsMetadata objects. The 'previous' one one is stored for use in the next iteration, of this metric collector 
                    CurrentStatMetadata currentStatMetadata = new CurrentStatMetadata(docker, latestStat, statTimestamp, machine);
                    PreviousStatMetadata previousStatMetadata_NextIteration = new PreviousStatMetadata(latestStat, statTimestamp, machine);
                    previousStatMetadatas_ByDockerId.put(docker.getId(), previousStatMetadata_NextIteration);
                    
                    // get metric prefix
                    String cadvisorScopedMetricPrefix = getCadvisorScopedMetricPrefix(docker);
                    if (cadvisorScopedMetricPrefix == null) continue;
                    
                    // get opentsdb tags
                    List<OpenTsdbTag> openTsdbTags = getOpenTsdbTags(docker);
                    
                    // get memory metrics
                    if ((docker.getSpec().getHasMemory() != null) && docker.getSpec().getHasMemory()) {
                        List<OpenTsdbMetric> cadvisorDockerMemory_OpenTsdbMetrics = getDockerStatsMetrics_Memory(currentStatMetadata, cadvisorScopedMetricPrefix, openTsdbTags);
                        if (cadvisorDockerMemory_OpenTsdbMetrics != null) openTsdbMetrics.addAll(cadvisorDockerMemory_OpenTsdbMetrics);
                    }
                    
                    //// get filesystem metrics -- disabled because this doesn't seem to work at all
                    ///if ((docker.getSpec().getHasFilesystem() != null) && docker.getSpec().getHasFilesystem()) {
                    //    List<OpenTsdbMetric> cadvisorDockerFilesystem_OpenTsdbMetrics = getDockerStatsMetrics_Filesystem(statMetadata, cadvisorScopedMetricPrefix, openTsdbTags);
                    //    if (cadvisorDockerFilesystem_OpenTsdbMetrics != null) openTsdbMetrics.addAll(cadvisorDockerFilesystem_OpenTsdbMetrics);
                    //}
                    
                    // get network metrics
                    if ((previousStatMetadata_CurrentIteration != null) && (docker.getSpec().getHasNetwork() != null) && docker.getSpec().getHasNetwork()) {
                        List<OpenTsdbMetric> cadvisorDockerNetwork_OpenTsdbMetrics = getDockerStatsMetrics_Network(currentStatMetadata, previousStatMetadata_CurrentIteration, cadvisorScopedMetricPrefix, openTsdbTags);
                        if (cadvisorDockerNetwork_OpenTsdbMetrics != null) openTsdbMetrics.addAll(cadvisorDockerNetwork_OpenTsdbMetrics);
                    }
                    
                    // get task stats metrics -- disabled because these always seem to be 0
                    //List<OpenTsdbMetric> cadvisorDockerTaskStats_OpenTsdbMetrics = getDockerStatsMetrics_TaskStats(statMetadata, cadvisorScopedMetricPrefix, openTsdbTags);
                    //if (cadvisorDockerTaskStats_OpenTsdbMetrics != null) openTsdbMetrics.addAll(cadvisorDockerTaskStats_OpenTsdbMetrics);
                    
                    // get container uptime metrics
                    List<OpenTsdbMetric> cadvisorDockerUptime_OpenTsdbMetrics = getDockerStatsMetrics_Uptime(currentStatMetadata, cadvisorScopedMetricPrefix, openTsdbTags);
                    if (cadvisorDockerUptime_OpenTsdbMetrics != null) openTsdbMetrics.addAll(cadvisorDockerUptime_OpenTsdbMetrics);
                    
                    // get cpu metrics
                    if ((previousStatMetadata_CurrentIteration != null) && (docker.getSpec().getHasCpu() != null) && docker.getSpec().getHasCpu()) {
                        List<OpenTsdbMetric> cadvisorDockerCpu_OpenTsdbMetrics = getDockerStatsMetrics_Cpu(currentStatMetadata, previousStatMetadata_CurrentIteration, cadvisorScopedMetricPrefix, openTsdbTags);
                        if (cadvisorDockerCpu_OpenTsdbMetrics != null) openTsdbMetrics.addAll(cadvisorDockerCpu_OpenTsdbMetrics);
                    }
                }
                
                previousStatMetadatas_ByDockerId_ = previousStatMetadatas_ByDockerId;

                super.outputOpenTsdbMetrics(openTsdbMetrics);

                routineTimeElapsed = System.currentTimeMillis() - routineStartTime;

                logger.info("Finished cAdvisor metric collection routine. cAdvisorLocation=\"" + protocol_ + "://" + host_ + ":" + port_ + "\"" +
                        ", ConnectionSuccess=" + didConnectToCadvisor +
                        ", cAdvisorMetricsCollected=" + openTsdbMetrics.size() +
                        ", cAdvisorMetricCollectionTime=" + routineTimeElapsed);
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                logger.info("Finished cAdvisor metric collection routine. cAdvisorLocation=\"" + protocol_ + "://" + host_ + ":" + port_ + "\"" +
                        ", ConnectionSuccess=" + didConnectToCadvisor +
                        ", cAdvisorMetricCollectionTime=" + routineTimeElapsed);
            }
            
            if (routineTimeElapsed == -1) routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;
            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }
    
    protected String getCadvisorDockerJson() {
        String url = protocol_ + "://" + host_ + ":" + port_ + "/api/" + apiVersion_ + "/docker/";
        String docker_RawJson = NetIo.downloadUrl(url, 1, 1, true);
        return docker_RawJson;
    }   
    
    protected String getCadvisorMachineJson() {
        String url = protocol_ + "://" + host_ + ":" + port_ + "/api/" + apiVersion_ + "/machine/";
        String machine_RawJson = NetIo.downloadUrl(url, 1, 1, true);
        return machine_RawJson;
    }   
    
    protected static boolean isValidApiVersion(String version) {
        
        if ((version == null) || version.isEmpty()) {
            return false;
        }
        
        try {
            BigDecimal versionNumber = new BigDecimal(version.substring(1));
            return version.startsWith("v") && (versionNumber.intValue() >= 1);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return false;
        }
    }
     
    protected static List<Docker> parseCadvisorDockerJson(String json) {
        
        if ((json == null) || json.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Docker> dockers = new ArrayList<>();
        
        try {
            JsonObject jsonObject = JsonUtils.getJsonObjectFromRequestBody(json);
            Set<Entry<String,JsonElement>> jsonObjectEntrySet = jsonObject.entrySet();
            HashSet<String> dockerContainerKeys = new HashSet<>();
            for (Entry entry : jsonObjectEntrySet) dockerContainerKeys.add((String) entry.getKey());
            
            for (String dockerContainerKey : dockerContainerKeys) {
                try {
                    Gson gson = new Gson();
                    JsonElement jsonElement = jsonObject.get(dockerContainerKey);
                    Docker docker = gson.fromJson(jsonElement, Docker.class);
                    dockers.add(docker);
                }
                catch (Exception e) {
                    logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return dockers;
    }
    
    protected static Machine parseCadvisorMachineJson(String json) {
        
        if ((json == null) || json.isEmpty()) {
            return null;
        }
        
        try {
            Gson gson = new Gson();
            JsonElement jsonElement = JsonUtils.getJsonElementFromRequestBody(json);
            Machine machine = gson.fromJson(jsonElement, Machine.class);
            return machine;
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return null;
    }
    
    protected static String getCadvisorScopedMetricPrefix(Docker docker) {
        
        if ((docker == null) || (docker.getId() == null)) {
            return null;
        }
        
        String cadvisorScopedMetricPrefix = null;
        
        try {
            String containerId = docker.getId();
            String shortContainerId = docker.getId().substring(0, 12);
            
            // use ecs path naming convention
            if ((docker.getLabels() != null) && docker.getLabels().containsKey("com.amazonaws.ecs.container-name")) {
                String ecsTaskDefFamily = docker.getLabels().get("com.amazonaws.ecs.task-definition-family");
                String ecsContainerName = docker.getLabels().get("com.amazonaws.ecs.container-name");
                
                if ((ecsTaskDefFamily != null) && (ecsContainerName != null)) {
                    StringBuilder cadvisorScopedMetricPrefix_StringBuilder = new StringBuilder();
                    cadvisorScopedMetricPrefix_StringBuilder.append(ecsTaskDefFamily).append(".").append(ecsContainerName).append(".").append(shortContainerId);
                    cadvisorScopedMetricPrefix = cadvisorScopedMetricPrefix_StringBuilder.toString();
                }
            }
            
            // use the alias as the container prefix
            if ((cadvisorScopedMetricPrefix == null) && (docker.getAliases() != null) && (docker.getAliases().size() > 0)) {
                for (String alias : docker.getAliases()) {
                    if ((alias != null) && !alias.isEmpty() && !alias.equalsIgnoreCase(containerId)) {
                        cadvisorScopedMetricPrefix = alias;
                        break;
                    } 
                }
            }
            
            // fall back on using the short container id as the container prefix if all else fails
            if (cadvisorScopedMetricPrefix == null) { 
                cadvisorScopedMetricPrefix = shortContainerId;
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return cadvisorScopedMetricPrefix;
    }
    
    protected static List<OpenTsdbTag> getOpenTsdbTags(Docker docker) {
        
        if (docker == null) {
            return null;
        }
        
        List<OpenTsdbTag> openTsdbTags = new ArrayList<>();
        
        try {
            String containerId = docker.getId();
            
            if (containerId != null) {
                openTsdbTags.add(new OpenTsdbTag("ContainerId=" + containerId.substring(0, 12)));
            } 
            
            if ((docker.getLabels() != null)) {
                String ecsContainerName = docker.getLabels().get("com.amazonaws.ecs.container-name");
                if (ecsContainerName != null) openTsdbTags.add(new OpenTsdbTag("EcsContainerName=" + ecsContainerName));
                
                String ecsTaskDefFamily = docker.getLabels().get("com.amazonaws.ecs.task-definition-family");
                if (ecsTaskDefFamily != null) openTsdbTags.add(new OpenTsdbTag("EcsTaskDefFamily=" + ecsTaskDefFamily));
                
                String ecsTaskDefArn = docker.getLabels().get("com.amazonaws.ecs.task-arn");
                if (ecsTaskDefArn != null) {
                    int indexOfTaskDefIdStart = ecsTaskDefArn.indexOf(":task/") + 6;
                    if (indexOfTaskDefIdStart > 0) openTsdbTags.add(new OpenTsdbTag("EcsTaskDefId=" + ecsTaskDefArn.substring(indexOfTaskDefIdStart)));
                }
            }
            
            if ((docker.getAliases() != null) && (docker.getAliases().size() > 0)) {
                for (String alias : docker.getAliases()) {
                    if ((alias != null) && !alias.isEmpty() && !alias.equalsIgnoreCase(containerId)) {
                        openTsdbTags.add(new OpenTsdbTag("DockerName=" + alias));
                        break;
                    } 
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbTags;
    }
    
    protected static Date getDateFromTimestampString(String timestamp) {
        
        if (timestamp == null) {
            return null;
        }
        
        Date timestampDate = null;
        
        try {
            DateFormat cadvisorStatTimestampDateFormat_ = new SimpleDateFormat(CADVISOR_STAT_TIMESTAMP_MILLISECOND_PRECISION_DATE_FORMAT);
            
            String timestampNanoSecondPrecision = timestamp;
            String timestampMillisecondPrecision = convertNanosecondPrecisionTimestampStringToMillisecondPrecisionTimestampString(timestampNanoSecondPrecision);
            timestampDate = cadvisorStatTimestampDateFormat_.parse(timestampMillisecondPrecision);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return timestampDate;
    }
    
    protected static Stat getLatestStatForDocker(Docker docker) {
        
        if ((docker == null) || (docker.getStats() == null)) {
            return null;
        }
        
        long latestTimestamp = -1;
        Stat latestStat = null;
        
        try {
            for (Stat stat : docker.getStats()) {
                if (stat.getTimestamp() == null) continue;
                Date timestamp = getDateFromTimestampString(stat.getTimestamp());

                if ((timestamp != null) && (timestamp.getTime() > latestTimestamp)) {
                    latestTimestamp = timestamp.getTime();
                    latestStat = stat;
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return latestStat;
    }
    
    protected static List<OpenTsdbMetric> getDockerStatsMetrics_Cpu(CurrentStatMetadata currentStatMetadata, PreviousStatMetadata previousStatMetadata, 
            String cadvisorScopedMetricPrefix, List<OpenTsdbTag> openTsdbTags) {
        
        if ((currentStatMetadata == null) || currentStatMetadata.areCoreFieldsNull() || (previousStatMetadata == null) || previousStatMetadata.areCoreFieldsNull() ) {
            return new ArrayList<>();
        }
        
        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
        
        try {
            List<OpenTsdbTag> openTsdbTags_Cpu = new ArrayList<>(openTsdbTags);
            if ((currentStatMetadata.getDocker().getSpec() != null) || (currentStatMetadata.getDocker().getSpec().getCpu() != null) && (currentStatMetadata.getDocker().getSpec().getCpu().getLimit() != null)) {
                openTsdbTags_Cpu.add(new OpenTsdbTag("CpuShares=" + currentStatMetadata.getDocker().getSpec().getCpu().getLimit()));
            }
            
            Double cpuCoreCount = currentStatMetadata.getMachine().getNumCores().doubleValue();
            Long timestamp_Difference_Ms = currentStatMetadata.getTimestamp().getTime() - previousStatMetadata.getTimestamp().getTime();
            Long timestamp_Difference_Ns = (1000000 * timestamp_Difference_Ms);
            
            // covers the case of a container that isn't consuming any cpu at all
            if (Objects.equals(previousStatMetadata.getStat().getCpu().getUsage().getTotal(), currentStatMetadata.getStat().getCpu().getUsage().getTotal()) || (timestamp_Difference_Ms == 0)) {
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuOverallUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags_Cpu));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuUserUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags_Cpu));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuSystemUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags_Cpu));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuOtherUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags_Cpu));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuOverallUsage-RelativeToCpuShares-Pct", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags_Cpu));
                return openTsdbMetrics;
            }
            
            Long totalCpu_Previous = previousStatMetadata.getStat().getCpu().getUsage().getTotal();
            Long totalUserCpu_Previous = previousStatMetadata.getStat().getCpu().getUsage().getUser();
            Long totalSystemCpu_Previous = previousStatMetadata.getStat().getCpu().getUsage().getSystem();
            Long totalCpu_Current = currentStatMetadata.getStat().getCpu().getUsage().getTotal();
            Long totalUserCpu_Current = currentStatMetadata.getStat().getCpu().getUsage().getUser();
            Long totalSystemCpu_Current = currentStatMetadata.getStat().getCpu().getUsage().getSystem();
            Long cpuTotal_Difference = totalCpu_Current - totalCpu_Previous;
            Long cpuUser_Difference = totalUserCpu_Current - totalUserCpu_Previous;
            Long cpuSystem_Difference = totalSystemCpu_Current - totalSystemCpu_Previous;
            Long cpuOther_Difference = cpuTotal_Difference - (cpuUser_Difference + cpuSystem_Difference);
            Double totalCpuLoad = (cpuTotal_Difference.doubleValue() / timestamp_Difference_Ns.doubleValue());
            Double cpuUserTimeAsPercentOfTotalTime = cpuUser_Difference.doubleValue() / cpuTotal_Difference.doubleValue();
            Double cpuSystemTimeAsPercentOfTotalTime = cpuSystem_Difference.doubleValue() / cpuTotal_Difference.doubleValue();
            Double cpuOtherTimeAsPercentOfTotalTime = cpuOther_Difference.doubleValue() / cpuTotal_Difference.doubleValue();

            BigDecimal cpuTotalPercent_ContainerRelativeToHostResources = new BigDecimal((totalCpuLoad / cpuCoreCount) * 100).setScale(SCALE, ROUNDING_MODE);
            openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuOverallUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), cpuTotalPercent_ContainerRelativeToHostResources, openTsdbTags_Cpu));
            
            BigDecimal cpuUserPercent_ContainerRelativeToHostResources = new BigDecimal(cpuTotalPercent_ContainerRelativeToHostResources.doubleValue() * cpuUserTimeAsPercentOfTotalTime).setScale(SCALE, ROUNDING_MODE);
            openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuUserUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), cpuUserPercent_ContainerRelativeToHostResources, openTsdbTags_Cpu));

            BigDecimal cpuSystemPercent_ContainerRelativeToHostResources = new BigDecimal(cpuTotalPercent_ContainerRelativeToHostResources.doubleValue() * cpuSystemTimeAsPercentOfTotalTime).setScale(SCALE, ROUNDING_MODE);
            openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuSystemUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), cpuSystemPercent_ContainerRelativeToHostResources, openTsdbTags_Cpu));

            BigDecimal cpuOtherPercent_ContainerRelativeToHostResources = new BigDecimal(cpuTotalPercent_ContainerRelativeToHostResources.doubleValue() * cpuOtherTimeAsPercentOfTotalTime).setScale(SCALE, ROUNDING_MODE);
            openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuOtherUsage-RelativeToHost-Pct", currentStatMetadata.getTimestamp().getTime(), cpuOtherPercent_ContainerRelativeToHostResources, openTsdbTags_Cpu));

            if ((currentStatMetadata.getDocker().getSpec() != null) || (currentStatMetadata.getDocker().getSpec().getCpu() != null) && (currentStatMetadata.getDocker().getSpec().getCpu().getLimit() != null)) {
                Double cpuSharesLimit = currentStatMetadata.getDocker().getSpec().getCpu().getLimit().doubleValue();
                Double totalUsedCpuShares = totalCpuLoad * 1024;
                Double cpuSharesLimitPercent = totalUsedCpuShares / cpuSharesLimit;
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Cpu.CpuOverallUsage-RelativeToCpuShares-Pct", currentStatMetadata.getTimestamp().getTime(), new BigDecimal(cpuSharesLimitPercent).multiply(ONE_HUNDRED).setScale(SCALE, ROUNDING_MODE), openTsdbTags_Cpu));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }   
    
    protected static List<OpenTsdbMetric> getDockerStatsMetrics_Memory(CurrentStatMetadata currentStatMetadata, String cadvisorScopedMetricPrefix, List<OpenTsdbTag> openTsdbTags) {
        
        if ((currentStatMetadata == null) || (currentStatMetadata.getDocker() == null) || (currentStatMetadata.getStat() == null) || (currentStatMetadata.getTimestamp() == null)) {
            return new ArrayList<>();
        }
        
        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();

        try {
            Date timestamp = getDateFromTimestampString(currentStatMetadata.getStat().getTimestamp()) ;
            if (timestamp == null) return openTsdbMetrics;

            Memory_ memory = currentStatMetadata.getStat().getMemory();
            if (memory == null) return openTsdbMetrics;

            if (memory.getWorkingSet() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.WorkingSet-Bytes", timestamp.getTime(), new BigDecimal(memory.getWorkingSet()), openTsdbTags));
            if (memory.getRss() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.RSS-Bytes", timestamp.getTime(), new BigDecimal(memory.getRss()), openTsdbTags));
            if (memory.getCache() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.Cache-Bytes", timestamp.getTime(), new BigDecimal(memory.getCache()), openTsdbTags));
            if (memory.getSwap() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.Swap-Bytes", timestamp.getTime(), new BigDecimal(memory.getSwap()), openTsdbTags));
            if (memory.getUsage() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.Usage-Bytes", timestamp.getTime(), new BigDecimal(memory.getUsage()), openTsdbTags));
            //if (memory.getFailcnt()!= null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.Fail-Count", timestamp.getTime(), new BigDecimal(memory.getFailcnt()), openTsdbTags));

            if ((memory.getContainerData() != null) && (memory.getContainerData().getPgfault() != null)) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.PageFault-Container-Count", timestamp.getTime(), new BigDecimal(memory.getContainerData().getPgfault()), openTsdbTags));
            if ((memory.getContainerData() != null) && (memory.getContainerData().getPgmajfault() != null)) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.PageFaultMajor-Container-Count", timestamp.getTime(), new BigDecimal(memory.getContainerData().getPgmajfault()), openTsdbTags));
            if ((memory.getHierarchicalData() != null) && (memory.getHierarchicalData().getPgfault() != null)) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.PageFault-Hierarchical-Count", timestamp.getTime(), new BigDecimal(memory.getHierarchicalData().getPgfault()), openTsdbTags));
            if ((memory.getHierarchicalData() != null) && (memory.getHierarchicalData().getPgmajfault() != null)) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.PageFaultMajor-Hierarchical-Count", timestamp.getTime(), new BigDecimal(memory.getHierarchicalData().getPgmajfault()), openTsdbTags));

            if ((currentStatMetadata.getDocker().getSpec() == null) || (currentStatMetadata.getDocker().getSpec().getMemory() == null) || (currentStatMetadata.getDocker().getSpec().getMemory().getReservation() == null)) return openTsdbMetrics;
            Long memorySoftLimit = (currentStatMetadata.getDocker().getSpec().getMemory().getReservation() > currentStatMetadata.getMachine().getMemoryCapacity()) ? currentStatMetadata.getMachine().getMemoryCapacity() : currentStatMetadata.getDocker().getSpec().getMemory().getReservation();
            if ((memorySoftLimit != null) && (memory.getUsage() != null) && (memorySoftLimit != 0)) {
                BigDecimal memoryUsageRelativeToSoftLimitPercent = new BigDecimal(memory.getUsage()).divide(new BigDecimal(memorySoftLimit), SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.UsageRelativeToSoftLimit-Pct", timestamp.getTime(), memoryUsageRelativeToSoftLimitPercent, openTsdbTags));
            }
            
            if ((currentStatMetadata.getDocker().getSpec() == null) || (currentStatMetadata.getDocker().getSpec().getMemory() == null) || (currentStatMetadata.getDocker().getSpec().getMemory().getLimit() == null)) return openTsdbMetrics;
            Long memoryHardLimit = (currentStatMetadata.getDocker().getSpec().getMemory().getLimit() > currentStatMetadata.getMachine().getMemoryCapacity()) ? currentStatMetadata.getMachine().getMemoryCapacity() : currentStatMetadata.getDocker().getSpec().getMemory().getLimit();
            if ((memoryHardLimit != null) && (memory.getUsage() != null) && (memoryHardLimit != 0)) {
                BigDecimal memoryUsageRelativeToHardLimitPercent = new BigDecimal(memory.getUsage()).divide(new BigDecimal(memoryHardLimit), SCALE, ROUNDING_MODE).multiply(ONE_HUNDRED);
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Memory.UsageRelativeToHardLimit-Pct", timestamp.getTime(), memoryUsageRelativeToHardLimitPercent, openTsdbTags));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }
    
//    protected static List<OpenTsdbMetric> getDockerStatsMetrics_TaskStats(StatMetadata currentStatMetadata, String cadvisorScopedMetricPrefix, List<OpenTsdbTag> openTsdbTags) {
//        
//        if ((currentStatMetadata == null) || (currentStatMetadata.getDocker() == null) || (currentStatMetadata.getStat() == null) || (currentStatMetadata.getTimestamp() == null)) {
//            return new ArrayList<>();
//        }
//        
//        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
//
//        try {
//            Date timestamp = getDateFromTimestampString(currentStatMetadata.getStat().getTimestamp()) ;
//            if (timestamp == null) return openTsdbMetrics;
//
//            TaskStats taskStats = currentStatMetadata.getStat().getTaskStats();
//            if (taskStats == null) return openTsdbMetrics;
//
//            if (taskStats.getNrIoWait() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Tasks.WaitingOnIo-Count", timestamp.getTime(), new BigDecimal(taskStats.getNrIoWait()), openTsdbTags));
//            if (taskStats.getNrRunning() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Tasks.Running-Count", timestamp.getTime(), new BigDecimal(taskStats.getNrRunning()), openTsdbTags));
//            if (taskStats.getNrSleeping() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Tasks.Sleeping-Count", timestamp.getTime(), new BigDecimal(taskStats.getNrSleeping()), openTsdbTags));
//            if (taskStats.getNrStopped() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Tasks.Stopped-Count", timestamp.getTime(), new BigDecimal(taskStats.getNrStopped()), openTsdbTags));
//            if (taskStats.getNrUninterruptible() != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Tasks.Uninterruptible-Count", timestamp.getTime(), new BigDecimal(taskStats.getNrUninterruptible()), openTsdbTags));
//        }
//        catch (Exception e) {
//            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
//        }
//        
//        return openTsdbMetrics;
//    }
    
    protected static List<OpenTsdbMetric> getDockerStatsMetrics_Uptime(CurrentStatMetadata currentStatMetadata, String cadvisorScopedMetricPrefix, List<OpenTsdbTag> openTsdbTags) {
        
        if ((currentStatMetadata == null) || (currentStatMetadata.getDocker() == null)) {
            return new ArrayList<>();
        }
        
        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();

        try {
            Date creationTimestamp = getDateFromTimestampString(currentStatMetadata.getDocker().getSpec().getCreationTime());
            if (creationTimestamp == null) return openTsdbMetrics;

            long currentTime = System.currentTimeMillis();
            long uptimeInSeconds = (currentTime - creationTimestamp.getTime()) / 1000;
            openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Uptime.Uptime-Seconds", currentTime, new BigDecimal(uptimeInSeconds), openTsdbTags));
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }
    
//    protected static List<OpenTsdbMetric> getDockerStatsMetrics_Filesystem(StatMetadata currentStatMetadata, String cadvisorScopedMetricPrefix, List<OpenTsdbTag> openTsdbTags) {
//        
//        if ((currentStatMetadata == null) || currentStatMetadata.areCoreFieldsNull()) {
//            return new ArrayList<>();
//        }
//        
//        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
//
//        try {
//            Date timestamp = getDateFromTimestampString(currentStatMetadata.getStat().getTimestamp()) ;
//            if (timestamp == null) return openTsdbMetrics;
//
//            List<Filesystem> filesystems = currentStatMetadata.getStat().getFilesystem();
//            if (filesystems == null) return openTsdbMetrics;
//
//            for (Filesystem filesystem : filesystems) {
//                if (filesystem == null) continue;
//                if ((filesystem.getCapacity() == null) || (filesystem.getCapacity() <= 0)) continue;
//                
//                // fill in the rest of the metrics...
//            }
//        }
//        catch (Exception e) {
//            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
//        }
//        
//        return openTsdbMetrics;
//    }
    
    protected static List<OpenTsdbMetric> getDockerStatsMetrics_Network(CurrentStatMetadata currentStatMetadata, PreviousStatMetadata previousStatMetadata, 
            String cadvisorScopedMetricPrefix, List<OpenTsdbTag> openTsdbTags) {
        
        if ((currentStatMetadata == null) || currentStatMetadata.areCoreFieldsNull() || (previousStatMetadata == null) || previousStatMetadata.areCoreFieldsNull()) {
            return new ArrayList<>();
        }
        
        List<OpenTsdbMetric> openTsdbMetrics = new ArrayList<>();
        
        try {
            if (currentStatMetadata.getStat().getNetwork() == null) return openTsdbMetrics;
            if (previousStatMetadata.getStat().getNetwork() == null) return openTsdbMetrics;
            
            // get connection counts
            Long tcpv4ConnectionCount = getTcpv4ConnectionCount(currentStatMetadata.getStat().getNetwork());
            Long tcpv6ConnectionCount = getTcpv6ConnectionCount(currentStatMetadata.getStat().getNetwork());
            if (tcpv4ConnectionCount != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Connections.TcpV4-Count", currentStatMetadata.getTimestamp().getTime(), new BigDecimal(tcpv4ConnectionCount), openTsdbTags));
            if (tcpv6ConnectionCount != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Connections.TcpV6-Count", currentStatMetadata.getTimestamp().getTime(), new BigDecimal(tcpv6ConnectionCount), openTsdbTags));

            Long timestamp_Difference_Ms = currentStatMetadata.getTimestamp().getTime() - previousStatMetadata.getTimestamp().getTime();
                        
            // check to make sure there is a measuable time difference -- avoids divide by 0 exceptions when a container disappears
            if (timestamp_Difference_Ms == 0) {
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Received-Bytes-Second", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Transmitted-Bytes-Second", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Overall-Bytes-Second", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Received-Megabits-Second", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Transmitted-Megabits-Second", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags));
                openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Overall-Megabits-Second", currentStatMetadata.getTimestamp().getTime(), BigDecimal.ZERO, openTsdbTags));
            }
            else {
                Long rxBytesDiff = currentStatMetadata.getStat().getNetwork().getRxBytes() - previousStatMetadata.getStat().getNetwork().getRxBytes();
                Long txBytesDiff = currentStatMetadata.getStat().getNetwork().getTxBytes() - previousStatMetadata.getStat().getNetwork().getTxBytes();
                BigDecimal rxBytesPerSecond = new BigDecimal(rxBytesDiff).divide(new BigDecimal(timestamp_Difference_Ms).divide(ONE_THOUSAND, SCALE, ROUNDING_MODE), SCALE, ROUNDING_MODE);
                BigDecimal txBytesPerSecond = new BigDecimal(txBytesDiff).divide(new BigDecimal(timestamp_Difference_Ms).divide(ONE_THOUSAND, SCALE, ROUNDING_MODE), SCALE, ROUNDING_MODE);
                BigDecimal overallBytesPerSecond = rxBytesPerSecond.add(txBytesPerSecond);
                BigDecimal rxMegabitsPerSecond = rxBytesPerSecond.divide(BYTES_TO_MEGABITS_DIVISOR, SCALE, ROUNDING_MODE);
                BigDecimal txMegabitsPerSecond = txBytesPerSecond.divide(BYTES_TO_MEGABITS_DIVISOR, SCALE, ROUNDING_MODE);
                BigDecimal overallMegabitsPerSecond = rxMegabitsPerSecond.add(txMegabitsPerSecond);
                if (rxBytesPerSecond != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Received-Bytes-Second", currentStatMetadata.getTimestamp().getTime(), rxBytesPerSecond, openTsdbTags));
                if (txBytesPerSecond != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Transmitted-Bytes-Second", currentStatMetadata.getTimestamp().getTime(), txBytesPerSecond, openTsdbTags));
                if (overallBytesPerSecond != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Overall-Bytes-Second", currentStatMetadata.getTimestamp().getTime(), overallBytesPerSecond, openTsdbTags));
                if (rxMegabitsPerSecond != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Received-Megabits-Second", currentStatMetadata.getTimestamp().getTime(), rxMegabitsPerSecond, openTsdbTags));
                if (txMegabitsPerSecond != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Transmitted-Megabits-Second", currentStatMetadata.getTimestamp().getTime(), txMegabitsPerSecond, openTsdbTags));
                if (overallMegabitsPerSecond != null) openTsdbMetrics.add(new OpenTsdbMetric(cadvisorScopedMetricPrefix + ".Network.Bandwidth.Overall-Megabits-Second", currentStatMetadata.getTimestamp().getTime(), overallMegabitsPerSecond, openTsdbTags));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return openTsdbMetrics;
    }
    
    private static Long getTcpv4ConnectionCount(Network network) {
        
        if ((network == null) || (network.getTcp() == null)) {
            return null;
        }
        
        Long connectionCount = 0l;
        
        try {
            if (network.getTcp().getClose() != null) connectionCount += network.getTcp().getClose();
            if (network.getTcp().getCloseWait() != null) connectionCount += network.getTcp().getCloseWait();
            if (network.getTcp().getClosing() != null) connectionCount += network.getTcp().getClosing();
            if (network.getTcp().getEstablished() != null) connectionCount += network.getTcp().getEstablished();
            if (network.getTcp().getFinWait1() != null) connectionCount += network.getTcp().getFinWait1();
            if (network.getTcp().getFinWait2() != null) connectionCount += network.getTcp().getFinWait2();
            if (network.getTcp().getLastAck() != null) connectionCount += network.getTcp().getLastAck();
            if (network.getTcp().getListen() != null) connectionCount += network.getTcp().getListen();
            if (network.getTcp().getSynRecv() != null) connectionCount += network.getTcp().getSynRecv();
            if (network.getTcp().getSynSent() != null) connectionCount += network.getTcp().getSynSent();
            if (network.getTcp().getTimeWait() != null) connectionCount += network.getTcp().getTimeWait();
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return connectionCount;
    }
    
    private static Long getTcpv6ConnectionCount(Network network) {
        
        if ((network == null) || (network.getTcp6() == null)) {
            return null;
        }
        
        Long connectionCount = 0l;
        
        try {
            if (network.getTcp6().getClose() != null) connectionCount += network.getTcp6().getClose();
            if (network.getTcp6().getCloseWait() != null) connectionCount += network.getTcp6().getCloseWait();
            if (network.getTcp6().getClosing() != null) connectionCount += network.getTcp6().getClosing();
            if (network.getTcp6().getEstablished() != null) connectionCount += network.getTcp6().getEstablished();
            if (network.getTcp6().getFinWait1() != null) connectionCount += network.getTcp6().getFinWait1();
            if (network.getTcp6().getFinWait2() != null) connectionCount += network.getTcp6().getFinWait2();
            if (network.getTcp6().getLastAck() != null) connectionCount += network.getTcp6().getLastAck();
            if (network.getTcp6().getListen() != null) connectionCount += network.getTcp6().getListen();
            if (network.getTcp6().getSynRecv() != null) connectionCount += network.getTcp6().getSynRecv();
            if (network.getTcp6().getSynSent() != null) connectionCount += network.getTcp6().getSynSent();
            if (network.getTcp6().getTimeWait() != null) connectionCount += network.getTcp6().getTimeWait();
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return connectionCount;
    }
    
    // Example  input: 2017-06-19T16:32:26.562309294Z
    // Example output: 2017-06-19T16:32:26.562Z
    protected static String convertNanosecondPrecisionTimestampStringToMillisecondPrecisionTimestampString(String timestamp) {
        
        if (timestamp == null) {
            return null;
        }
        
        String millisecondPrecisionTimestamp = null;
        
        try {
            boolean foundNumericCharacter_RightIndex = false, foundAlphabeticCharacter_LeftIndex = false;
            int nanosecondRightIndex = -1, nanosecondLeftIndex = -1;
            for (int i = timestamp.length()-1; !foundNumericCharacter_RightIndex; i--) {
                if (i < 0) break;
                else if (Character.isDigit(timestamp.charAt(i))) {
                    foundNumericCharacter_RightIndex = true;
                    nanosecondRightIndex = i;
                }
            }

            if (nanosecondRightIndex > 0) {
                for (int i = nanosecondRightIndex-1; !foundAlphabeticCharacter_LeftIndex; i--) {
                    if (i < 0) break;
                    else if (!Character.isDigit(timestamp.charAt(i)) && Character.isDigit(timestamp.charAt(i+1))) {
                        foundAlphabeticCharacter_LeftIndex = true;
                        nanosecondLeftIndex = i+1;
                    }
                }
            }
            
            if ((nanosecondRightIndex > 0) && (nanosecondLeftIndex > 0)) {
                String nanosecondField = timestamp.substring(nanosecondLeftIndex, nanosecondRightIndex+1);
                
                String millisecondField = "000";
                if (nanosecondField.length() >= 3) millisecondField = nanosecondField.substring(0, 3);
                else if (nanosecondField.length() == 1) millisecondField = nanosecondField.substring(0, 1) + "00";
                else if (nanosecondField.length() == 2) millisecondField = nanosecondField.substring(0, 2) + "0";
                
                millisecondPrecisionTimestamp = timestamp.substring(0, nanosecondLeftIndex) + millisecondField + timestamp.substring(nanosecondRightIndex+1, timestamp.length());
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return millisecondPrecisionTimestamp;
    }
    
}
