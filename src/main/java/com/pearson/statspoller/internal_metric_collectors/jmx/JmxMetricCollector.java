package com.pearson.statspoller.internal_metric_collectors.jmx;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class JmxMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(JmxMetricCollector.class.getName());

    private final String host_;
    private final int port_;
    private final String jmxServiceUrl_;
    private final int numConnectionAttemptRetries_;
    private final long sleepAfterConnectTime_;
    private final long queryMetricTreeInterval_;
    private final boolean collectStringAttributes_;
    private final boolean isDerivedMetricsEnabled_;
    private final String username_;
    private final String password_;
    private final List<String> blacklistObjectNameRegexs_;
    private final List<String> blacklistRegexs_;
    private final List<String> whitelistRegexs_;
    private final List<String> numericDerivedRegexs_;
    private final List<String> stringDerivedRegexs_;
    
    private final JmxDerivedMetrics jmxDerivedMetrics_ = new JmxDerivedMetrics();
    
    // jmx connection variables
    private JMXConnector jmxConnection_ = null;
    private MBeanServerConnection mBeanServerConnection_ = null;
    private boolean didConnectOnThisInterval_ = false;
    
    // timestamp of the iteration
    private int currentTimestamp_ = -1;
    
    // contains cached compiled patters for blacklist & whitelist regexs
    private final Map<String,Pattern> regexPatterns_;
    
    // k=ObjectInstanceName, v=Allow getting object attributes? (true = ObjectInstanceName is not blacklisted)
    private Map<String,Boolean> blacklistObjectNameRegexMatchCache_ = new HashMap<>();
    
    //k=MetricPath, v="do the whitelist & blacklist rules allow this metric to be output"
    private Map<String,Integer> isMeticPathAllowedCache_ = new HashMap<>();
    
    // k=objectInstanceName, v=list of attributeNames
    // alwaysDownload overrules neverDownload
    private Map<String,List<String>> neverDownloadTheseMetricAttributes_ = new HashMap<>();
    private Map<String,List<String>> alwaysDownloadTheseMetricAttributes_ = new HashMap<>();
   
    // the current working set of objectInstances & mbeaninfos
    private long previousGetObjectInstancesTime_ = 0;
    private Set<ObjectInstance> objectInstances_ = new HashSet<>();
    private Map<ObjectInstance,MBeanInfo> mBeanInfoByObjectInstance_ = new HashMap<>();
    private Map<String,ObjectInstance> objectInstancesByObjectInstanceNames_ = new HashMap<>();
    
    // a list of (objectinstance name, attribute name) pairs that are the downloaded as part of the dervied metrics routine
    private List<JmxObjectInstanceNameAndAttributeName> numericDerivedMetricDownloadList_ = null;
    private List<JmxObjectInstanceNameAndAttributeName> stringDerivedMetricDownloadList_ = null;

    // k=unformatted graphite metric path, v=formatted graphite metric path
    private Map<String,String> graphiteFormattedMetricPaths_ = new HashMap<>();

    public JmxMetricCollector(boolean isEnabled, long collectionInterval, String jmxMetricPrefix, String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String jmxServiceUrl, int numConnectionAttemptRetries, long sleepAfterConnectTime, long queryMetricTreeInterval,
            boolean collectStringAttributes, boolean isDerivedMetricsEnabled,
            String username, String password, List<String> blacklistObjectNameRegexs, List<String> blacklistRegexs, List<String> whitelistRegexs) {
        
        super(isEnabled, collectionInterval, jmxMetricPrefix, outputFilePathAndFilename, writeOutputFiles);

        this.host_ = host;
        this.port_ = port;
        this.jmxServiceUrl_ = jmxServiceUrl;
        this.numConnectionAttemptRetries_ = numConnectionAttemptRetries;
        this.sleepAfterConnectTime_ = sleepAfterConnectTime;
        this.queryMetricTreeInterval_ = queryMetricTreeInterval;
        this.collectStringAttributes_ = collectStringAttributes;
        this.isDerivedMetricsEnabled_ = isDerivedMetricsEnabled;
        this.username_ = username;
        this.password_ = password;
        this.blacklistObjectNameRegexs_ = blacklistObjectNameRegexs;
        this.blacklistRegexs_ = blacklistRegexs;
        this.whitelistRegexs_ = whitelistRegexs;
        
        if (isDerivedMetricsEnabled_) {
            this.numericDerivedRegexs_ = jmxDerivedMetrics_.getNumericDerivedMetricRegexs();
            this.stringDerivedRegexs_ = jmxDerivedMetrics_.getStringDerivedMetricRegexs();
        }
        else {
            this.numericDerivedRegexs_ = new ArrayList<>();
            this.stringDerivedRegexs_ = new ArrayList<>();
        }
        
        this.regexPatterns_ = compileRegexs();
    }
    
    private HashMap<String,Pattern> compileRegexs() {
        
        HashMap<String,Pattern> compiledRegexs = new HashMap<>();
        
        if (blacklistObjectNameRegexs_ != null) {
            for (String regex : blacklistObjectNameRegexs_) {
                Pattern pattern = Pattern.compile(regex);
                compiledRegexs.put(regex, pattern);
            }
        }
        
        if (blacklistRegexs_ != null) {
            for (String regex : blacklistRegexs_) {
                Pattern pattern = Pattern.compile(regex);
                compiledRegexs.put(regex, pattern);
            }
        }

        if (whitelistRegexs_ != null) {
            for (String regex : whitelistRegexs_) {
                Pattern pattern = Pattern.compile(regex);
                compiledRegexs.put(regex, pattern);
            }
        }
        
        if (numericDerivedRegexs_ != null) {
            for (String regex : numericDerivedRegexs_) {
                Pattern pattern = Pattern.compile(regex);
                compiledRegexs.put(regex, pattern);
            }
        }
        
        if (stringDerivedRegexs_ != null) {
            for (String regex : stringDerivedRegexs_) {
                Pattern pattern = Pattern.compile(regex);
                compiledRegexs.put(regex, pattern);
            }
        }
        
        return compiledRegexs;
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            
            long routineStartTime = System.currentTimeMillis();
            
            long makeConnectionStartTime = System.currentTimeMillis();
            didConnectOnThisInterval_ = false;
            boolean isConnected = connect();
            long makeConnectionTimeElapsed = System.currentTimeMillis() - makeConnectionStartTime;

            currentTimestamp_ = (int) (System.currentTimeMillis() / 1000);
            List<GraphiteMetric> allJmxGraphiteMetricsForOutput = new ArrayList<>();
            
            if (!isConnected) {
                GraphiteMetric isAvailable = createGraphiteMetric("Availability.Available", BigDecimal.ZERO, currentTimestamp_);
                allJmxGraphiteMetricsForOutput.add(isAvailable);
                
                super.outputMetrics(allJmxGraphiteMetricsForOutput, true);
                
                long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
                
                logger.info("Finished JMX metric collection routine. JmxConnection=" + host_ + ":" + port_ + 
                        ", TotalJmxMetricsCollected=" + allJmxGraphiteMetricsForOutput.size() +
                        ", JmxMetricCollectionTime=" + routineTimeElapsed);
            }
            else {
                GraphiteMetric isAvailable = createGraphiteMetric("Availability.Available", BigDecimal.ONE, currentTimestamp_);
                allJmxGraphiteMetricsForOutput.add(isAvailable);
                
                logger.debug("JMX - Start Refresh Available Metrics");
                long queryMBeansStartTime = System.currentTimeMillis();
                boolean didRefreshObjectInstancesAndMBeanInfos = getObjectInstancesAndMBeanInfos(mBeanServerConnection_);
                long queryMBeansTimeElapsed = System.currentTimeMillis() - queryMBeansStartTime;
                logger.debug("JMX - End Refresh Available Metrics. TimeElapsed=" + queryMBeansTimeElapsed);

                logger.debug("JMX - Start Fetch Attributes");
                long fetchMetricAttributesStartTime = System.currentTimeMillis();
                List<JmxMetricRaw> jmxMetricsRaw = getJmxMetrics_Filtered(mBeanServerConnection_);
                long fetchMetricAttributesTimeElapsed = System.currentTimeMillis() - fetchMetricAttributesStartTime;
                logger.debug("JMX - End Fetch Attributes. TimeElapsed=" + fetchMetricAttributesTimeElapsed);

                List<GraphiteMetric> jmxGraphiteMetrics = createGraphiteMetrics_Filtered(jmxMetricsRaw);
                allJmxGraphiteMetricsForOutput.addAll(jmxGraphiteMetrics);
                 
                logger.debug("JMX - Start Fetch Derived Attributes");
                long fetchDerivedMetricAttributesStartTime = System.currentTimeMillis();
                if (isDerivedMetricsEnabled_) {
                    List<JmxMetricRaw> derivedJmxMetricsRaw = jmxDerivedMetrics_.createDerivedMetrics(jmxMetricsRaw);
                    List<GraphiteMetric> derivedJmxGraphiteMetrics = createGraphiteMetrics_Unfiltered(derivedJmxMetricsRaw);
                    allJmxGraphiteMetricsForOutput.addAll(derivedJmxGraphiteMetrics);
                }
                long fetchDerivedMetricAttributesTimeElapsed = System.currentTimeMillis() - fetchDerivedMetricAttributesStartTime;
                logger.debug("JMX - End Fetch Derived Attributes. TimeElapsed=" + fetchDerivedMetricAttributesTimeElapsed);

                super.outputMetrics(allJmxGraphiteMetricsForOutput, true);
                
                long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
                long adjustedRoutineTimeElasped = (didConnectOnThisInterval_) ? (routineTimeElapsed - sleepAfterConnectTime_) : routineTimeElapsed;
                
                if (adjustedRoutineTimeElasped >= 10000) {
                    logger.warn(("JMX routine excessive runtime. " + 
                            "ConnectionTime=" + makeConnectionTimeElapsed +
                            ", QueryMBeansTime=" + queryMBeansTimeElapsed +
                            ", FetchMetricAttributesTime=" + fetchMetricAttributesTimeElapsed + 
                            ", FetchDerivedMetricAttributesTime=" + fetchDerivedMetricAttributesTimeElapsed));
                }
                
                if ((jmxServiceUrl_ == null) || jmxServiceUrl_.isEmpty()) {
                    logger.info("Finished JMX metric collection routine. JmxConnection=\"" + host_ + ":" + port_ + "\"" + 
                            ", JmxMetricsCollected=" + jmxMetricsRaw.size() +
                            ", OutputJmxMetrics=" + allJmxGraphiteMetricsForOutput.size() +
                            ", JmxMetricCollectionTime=" + routineTimeElapsed);
                }
                else {
                    logger.info("Finished JMX metric collection routine. JmxConnection=\"" + jmxServiceUrl_ + "\"" + 
                            ", JmxMetricsCollected=" + jmxMetricsRaw.size() +
                            ", OutputJmxMetrics=" + allJmxGraphiteMetricsForOutput.size() +
                            ", JmxMetricCollectionTime=" + routineTimeElapsed);                    
                }
            }

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) {
                Threads.sleepMilliseconds(sleepTimeInMs);
            }
        }
        
    }
    
    private boolean connect() {
        
        for (int i = 0; i < numConnectionAttemptRetries_; i++) {
            try {
                if (jmxConnection_ == null) {
                    jmxConnection_ = makeJmxConnection();
                    
                    if ((jmxConnection_ != null) && sleepAfterConnectTime_ > 0) {
                        logger.info("JMX connection established. Sleeping for " + sleepAfterConnectTime_ + "ms");
                        Threads.sleepMilliseconds(sleepAfterConnectTime_);
                    }
                }
            }
            catch (Exception e) {
                close();
                logger.error("Error making JMX connection. " + e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }

            try {
                if (jmxConnection_ != null) {
                    mBeanServerConnection_ = jmxConnection_.getMBeanServerConnection();
                    
                    String defaultDomain = mBeanServerConnection_.getDefaultDomain();
                    
                    if ((defaultDomain != null) && !defaultDomain.isEmpty()) {
                        return true;
                    }
                }
            }
            catch (Exception e) {
                close();
                logger.error("Error making mBeanServerConnection. " + e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }
        
        return false;
    }
    
    private JMXConnector makeJmxConnection() {

        if ((jmxServiceUrl_ == null) || jmxServiceUrl_.isEmpty()) {
            if ((host_ == null) || host_.isEmpty()) {
                logger.error("JMX host cannot be null or empty");
                return null;
            }

            if ((port_ > 65535) || (port_ < 0)) {
                logger.error("JMX port is invalid");
                return null;
            }
        }

        HashMap credentialsMap = new HashMap();
        String[] credentials = new String[2];
        credentials[0] = username_;
        credentials[1] = password_;
        credentialsMap.put("jmx.remote.credentials", credentials);

        JMXConnector jmxConnection = null;
        
        try {
            JMXServiceURL connectionJmxServiceUrl;
            
            if ((jmxServiceUrl_ == null) || jmxServiceUrl_.isEmpty()) connectionJmxServiceUrl = new JMXServiceURL("rmi", "", 0, "/jndi/rmi://" + host_ + ":" + port_ + "/jmxrmi");
            else connectionJmxServiceUrl = new JMXServiceURL(jmxServiceUrl_);
            
            jmxConnection = JMXConnectorFactory.newJMXConnector(connectionJmxServiceUrl, credentialsMap);
            jmxConnection.connect(); 
            didConnectOnThisInterval_ = true;
        }
        catch (Exception e) {
            if ((jmxServiceUrl_ == null) || jmxServiceUrl_.isEmpty()) logger.error("Error establishing JMX connection to JVM. Host=" + host_ + ", Port=" + port_ + ", Username=" + username_);
            else logger.error("Error establishing JMX connection to JVM. Username=" + username_ + ", ServiceUrl=" + jmxServiceUrl_);
            
            try {
                close();
            }
            catch (Exception e2) {}
            
            jmxConnection = null;
        }
        
        return jmxConnection;
    }

    public void close() {
        
        if (mBeanServerConnection_ != null) {
            mBeanServerConnection_ = null;
        }
        
        if (jmxConnection_ != null) {
            try {
                jmxConnection_.close();
                
                if ((jmxServiceUrl_ == null) || jmxServiceUrl_.isEmpty()) logger.error("Closed JMX connection. Host=" + host_ + ", Port=" + port_ + ", Username=" + username_);
                else logger.error("Closed JMX connection. Username=" + username_ + ", ServiceUrl=" + jmxServiceUrl_);
            }
            catch (Exception e) {
                logger.error("Error closing JMX connection. " + e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }
        }

        jmxConnection_ = null;
        
        resetVariables();
    }
    
    private void resetVariables() {
        jmxDerivedMetrics_.reset();
                
        blacklistObjectNameRegexMatchCache_.clear();
        blacklistObjectNameRegexMatchCache_ = new HashMap<>();
        
        isMeticPathAllowedCache_.clear();
        isMeticPathAllowedCache_ = new HashMap<>();
         
        neverDownloadTheseMetricAttributes_.clear();
        neverDownloadTheseMetricAttributes_ = new HashMap<>();
        alwaysDownloadTheseMetricAttributes_.clear();
        alwaysDownloadTheseMetricAttributes_ = new HashMap<>();
        
        previousGetObjectInstancesTime_ = 0;
        objectInstances_.clear();
        objectInstances_ = new HashSet<>();
        mBeanInfoByObjectInstance_.clear();
        mBeanInfoByObjectInstance_ = new HashMap<>();
        objectInstancesByObjectInstanceNames_.clear();
        objectInstancesByObjectInstanceNames_ = new HashMap<>();
        
        if (numericDerivedMetricDownloadList_ != null) numericDerivedMetricDownloadList_.clear();
        numericDerivedMetricDownloadList_ = null;
        if (stringDerivedMetricDownloadList_ != null) stringDerivedMetricDownloadList_.clear();
        stringDerivedMetricDownloadList_ = null;
        
        graphiteFormattedMetricPaths_.clear();
        graphiteFormattedMetricPaths_ = new HashMap<>();
    }
    
    private boolean getObjectInstancesAndMBeanInfos(MBeanServerConnection mBeanServerConnection) {
        
        if (mBeanServerConnection == null) {
            return false;
        }
        
        boolean doQuery = false;
        
        try {
            long currentTime = System.currentTimeMillis();
            
            if ((previousGetObjectInstancesTime_ == 0) || (queryMetricTreeInterval_ <= -1)) {
                doQuery = true; 
            }
            else if ((queryMetricTreeInterval_ >= 1) && (currentTime - previousGetObjectInstancesTime_) >= queryMetricTreeInterval_) {
                doQuery = true; 
            }
            
            if (doQuery) {
                objectInstances_.clear();
                mBeanInfoByObjectInstance_.clear();
                objectInstancesByObjectInstanceNames_.clear();
                
                Set<ObjectInstance> allObjectInstances = mBeanServerConnection.queryMBeans(new ObjectName("*:*"), null);
                previousGetObjectInstancesTime_ = currentTime;

                for (ObjectInstance objectInstance : allObjectInstances) {
                    boolean isObjectNameAllowed = isObjectNameAllowed(objectInstance);
                    
                    if (isObjectNameAllowed) {
                        objectInstances_.add(objectInstance);
                        objectInstancesByObjectInstanceNames_.put(objectInstance.getObjectName().toString(), objectInstance);
                        MBeanInfo mBeanInfo = mBeanServerConnection.getMBeanInfo(objectInstance.getObjectName());
                        mBeanInfoByObjectInstance_.put(objectInstance, mBeanInfo);
                    }
                }
            }
            
            return doQuery;
        }
        catch (Exception e) {
            previousGetObjectInstancesTime_ = 0;
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            return doQuery;
        }
            
    }
    
    /*
    Filters out object instance names that are blacklisted by the 'blacklist object name regex' criteria
    */
    private boolean isObjectNameAllowed(ObjectInstance objectInstance) {
        
        if ((objectInstance == null) || (objectInstance.getObjectName() == null) || (blacklistObjectNameRegexMatchCache_ == null)) {
            return false;
        }
        
        String objectName = objectInstance.getObjectName().toString();
        Boolean isObjectNameAllowed = blacklistObjectNameRegexMatchCache_.get(objectName);
        if (isObjectNameAllowed != null) return isObjectNameAllowed;
        
        Boolean isMetricAllowed = true;
        
        if (blacklistObjectNameRegexs_ != null) {
            for (String regex : blacklistObjectNameRegexs_) {
                Pattern pattern = regexPatterns_.get(regex);
                Boolean matcherResult = pattern.matcher(objectName).find();
                
                if (matcherResult) {
                    isMetricAllowed = false;
                    break;
                }
            }
        }
        
        blacklistObjectNameRegexMatchCache_.put(objectName, isMetricAllowed);
        
        return isMetricAllowed;
    }
    
    private List<JmxMetricRaw> getJmxMetrics_Filtered(MBeanServerConnection mBeanServerConnection) {

        if (mBeanServerConnection == null) {
            return new ArrayList<>();
        }
        
        List<JmxMetricRaw> jmxMetricsRaw = new ArrayList<>();
        
        try {
            for (ObjectInstance objectInstance : objectInstances_) {
                
                String objectInstanceName = objectInstance.getObjectName().toString();
                MBeanInfo mBeanInfo = mBeanInfoByObjectInstance_.get(objectInstance);
                MBeanAttributeInfo[] mBeanAttributeInfos = mBeanInfo.getAttributes();
                String[] attributeNames = getObjectInstanceAttributeNames_Filtered(objectInstance, mBeanAttributeInfos);

                if ((objectInstance.getObjectName() != null) && (attributeNames != null) && (attributeNames.length > 0)) {
                    HashMap<String,Object> attributesObjects = new HashMap<>();
                    HashMap<String,Long> attributesRetrievalTimestamps = new HashMap<>();

                    try {
                        AttributeList attributeList = mBeanServerConnection.getAttributes(objectInstance.getObjectName(), attributeNames);
                        Long metricRetrievalTimestampInMs = System.currentTimeMillis();
                        
                        for (Object attributeObject : attributeList) {
                            Attribute attribute = (Attribute) attributeObject;
                            attributesObjects.put(attribute.getName(), attribute.getValue());
                            attributesRetrievalTimestamps.put(attribute.getName(), metricRetrievalTimestampInMs);
                        }
                    }
                    catch (Exception e) {
                        logger.debug(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));

                        for (String attributeName : attributeNames) {
                            Object attributeValue = getObjectInstanceAttributeValue_Filtered(mBeanServerConnection, objectInstance, attributeName);
                            Long metricRetrievalTimestampInMs = System.currentTimeMillis();
                            
                            if (attributeValue != null) {
                                attributesObjects.put(attributeName, attributeValue);
                                attributesRetrievalTimestamps.put(attributeName, metricRetrievalTimestampInMs);
                            }
                            else {
                                addMetricToNeverDownloadTheseMetricAttributes(objectInstanceName, attributeName);
                            }
                        }
                    }

                    for (String attributeName : attributesObjects.keySet()) {
                        createJmxMetricRaw(objectInstanceName, attributesObjects.get(attributeName), attributeName, 
                                attributesRetrievalTimestamps.get(attributeName), jmxMetricsRaw, collectStringAttributes_);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }

        return jmxMetricsRaw;
    }

    private String[] getObjectInstanceAttributeNames_Filtered(ObjectInstance objectInstance, MBeanAttributeInfo[] mBeanAttributeInfos) {
        
        if ((objectInstance == null) || (mBeanAttributeInfos == null)) {
            return null;
        }
        
        ArrayList<String> attributeNames = new ArrayList<>();
        
        for (MBeanAttributeInfo mBeanAttributeInfo : mBeanAttributeInfos) {
            String attributeName = mBeanAttributeInfo.getName();
            String objectInstanceName = objectInstance.getObjectName().toString();
            
            if (neverDownloadTheseMetricAttributes_.containsKey(objectInstanceName)) {
                List<String> alwaysDownladTheseAttributesForThisObjectInstance = null;
                if (alwaysDownloadTheseMetricAttributes_.containsKey(objectInstanceName)) {
                    alwaysDownladTheseAttributesForThisObjectInstance = alwaysDownloadTheseMetricAttributes_.get(objectInstanceName);
                }
                
                List<String> neverDownladTheseAttributesForThisObjectInstance = neverDownloadTheseMetricAttributes_.get(objectInstanceName);
                
                if ((alwaysDownladTheseAttributesForThisObjectInstance != null) && alwaysDownladTheseAttributesForThisObjectInstance.contains(attributeName)) {
                    attributeNames.add(attributeName);
                }
                else if (!neverDownladTheseAttributesForThisObjectInstance.contains(attributeName)) {
                    attributeNames.add(attributeName);
                }
            }
            else {
                attributeNames.add(attributeName);
            }
        }

        if (attributeNames.size() > 0) {
            String[] attributesArray = new String[attributeNames.size()];
            attributeNames.toArray(attributesArray);
            return attributesArray;
        }

        return null;
    }
    
    private Object getObjectInstanceAttributeValue_Filtered(MBeanServerConnection mBeanServerConnection, ObjectInstance objectInstance, String attributeName) {
        
        if ((mBeanServerConnection == null) || (objectInstance == null) || (objectInstance.getObjectName() == null) || (attributeName == null)) {
            return null;
        }
        
        try {
            boolean allowQuery = true;

            String objectInstanceName = objectInstance.getObjectName().toString();
            
            if (neverDownloadTheseMetricAttributes_.containsKey(objectInstanceName)) {
                List<String> alwaysDownladTheseAttributesForThisObjectInstance = null;
                if (alwaysDownloadTheseMetricAttributes_.containsKey(objectInstanceName)) {
                    alwaysDownladTheseAttributesForThisObjectInstance = alwaysDownloadTheseMetricAttributes_.get(objectInstanceName);
                }
                
                List<String> neverDownladTheseAttributesForThisObjectInstance = neverDownloadTheseMetricAttributes_.get(objectInstanceName);

                if ((alwaysDownladTheseAttributesForThisObjectInstance != null) && alwaysDownladTheseAttributesForThisObjectInstance.contains(attributeName)) {
                    allowQuery = true;
                }
                else if (!neverDownladTheseAttributesForThisObjectInstance.contains(attributeName)) {
                    allowQuery = true;
                }
                else {
                    allowQuery = false;
                }
            }

            if (allowQuery) {
                Object attributeObject = mBeanServerConnection.getAttribute(objectInstance.getObjectName(), attributeName);
                return attributeObject;
            }
        }
        catch (Exception e) {
            logger.debug(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }

        return null;
    }
    
    private void createJmxMetricRaw(String objectInstanceName, Object attributeValue, String attributeName,  
            long metricRetrievalTimestampInMs, List<JmxMetricRaw> jmxMetricsRaw, boolean allowSaveString) {
        createJmxMetricRaw(objectInstanceName, attributeValue, attributeName, null, metricRetrievalTimestampInMs, jmxMetricsRaw, allowSaveString);
    }
    
    private void createJmxMetricRaw(String objectInstanceName, Object attributeValue, String attributeName, String attributeMetricPath, 
            long metricRetrievalTimestampInMs, List<JmxMetricRaw> jmxMetricsRaw, boolean allowSaveString) {

        BigDecimal metricValue = null;
        
        if (attributeMetricPath == null) {
            attributeMetricPath = attributeName;
        }
        
        try {    
            if (attributeValue instanceof Boolean) {
                if ((Boolean) attributeValue == true) {
                    metricValue = new BigDecimal(1);
                }
                else if ((Boolean) attributeValue == false) {
                    metricValue = new BigDecimal(0);
                }
            }
            else if (attributeValue instanceof CompositeDataSupport) {
                CompositeDataSupport compositeDataSupportAttribute = (CompositeDataSupport) attributeValue;
                CompositeType compositeType = compositeDataSupportAttribute.getCompositeType();
                addMetricToAlwaysDownloadTheseMetricAttributes(objectInstanceName, attributeName);
                        
                for (String key : compositeType.keySet()) {
                    Object compositeDataSupportAttributeValue = compositeDataSupportAttribute.get(key);
                    String currentAttributeMetricPath = attributeMetricPath;
                    attributeMetricPath = attributeMetricPath + "." + key;
                    createJmxMetricRaw(objectInstanceName, compositeDataSupportAttributeValue, attributeName, attributeMetricPath, metricRetrievalTimestampInMs, jmxMetricsRaw, allowSaveString);
                    attributeMetricPath = currentAttributeMetricPath;
                }
            }
            else if (attributeValue instanceof CompositeData) {
                CompositeData compositeDataAttribute = (CompositeData) attributeValue;
                CompositeType compositeType = compositeDataAttribute.getCompositeType();
                addMetricToAlwaysDownloadTheseMetricAttributes(objectInstanceName, attributeName);
                
                for (String key : compositeType.keySet()) {
                    Object compositeDataAttributeValue = compositeDataAttribute.get(key);
                    String currentAttributeMetricPath = attributeMetricPath;
                    attributeMetricPath = attributeMetricPath + "." + key;
                    createJmxMetricRaw(objectInstanceName, compositeDataAttributeValue, attributeName, attributeMetricPath, metricRetrievalTimestampInMs, jmxMetricsRaw, allowSaveString);
                    attributeMetricPath = currentAttributeMetricPath;
                }
            }
            else if (allowSaveString && (attributeValue instanceof String)) {
                metricValue = new BigDecimal(1);
                String adjustedAttributeValue = (String) attributeValue;
                adjustedAttributeValue = adjustedAttributeValue.replace('.', '_');
                attributeMetricPath = attributeMetricPath + "=" + (String) adjustedAttributeValue;
            }
            else {
                String attributeString = attributeValue.toString();
                metricValue = new BigDecimal(attributeString);
            }
            
            if (metricValue != null) {
                JmxMetricRaw jmxMetricRaw = new JmxMetricRaw(objectInstanceName, attributeMetricPath,  metricValue, metricRetrievalTimestampInMs);
                jmxMetricsRaw.add(jmxMetricRaw);
            }
        }
        catch (Exception e) {
            addMetricToNeverDownloadTheseMetricAttributes(objectInstanceName, attributeName);  
        }
    }

    private List<GraphiteMetric> createGraphiteMetrics_Filtered(List<JmxMetricRaw> jmxMetricsRaw) {
                
        if (jmxMetricsRaw == null) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> jmxGraphiteMetrics = new ArrayList<>();
        
        for (JmxMetricRaw jmxMetricRaw : jmxMetricsRaw) {    
            String unformattedGraphiteMetricPath = jmxMetricRaw.createAndGetUnformattedGraphiteMetricPath();
            String graphiteMetricPath;
            
            if (!graphiteFormattedMetricPaths_.containsKey(unformattedGraphiteMetricPath)) {
                graphiteMetricPath = jmxMetricRaw.createAndGetGraphiteFormattedMetricPath();
                graphiteFormattedMetricPaths_.put(unformattedGraphiteMetricPath, graphiteMetricPath);
            }
            else {
                graphiteMetricPath = graphiteFormattedMetricPaths_.get(unformattedGraphiteMetricPath);
            }

            Integer isMetricAllowed = isMetricAllowed(graphiteMetricPath);

            if (isMetricAllowed != null) {
                if (isMetricAllowed == 2)  {
                    GraphiteMetric graphiteMetric = createGraphiteMetric(graphiteMetricPath, jmxMetricRaw.getMetricValue(), currentTimestamp_);

                    if (graphiteMetric != null) {
                        jmxGraphiteMetrics.add(graphiteMetric); 
                    }
                }
                else if (isMetricAllowed == -1) {
                    addMetricToNeverDownloadTheseMetricAttributes(jmxMetricRaw.getObjectInstanceName(), jmxMetricRaw.getAttributePath());  
                }
            }
        }
        
        return jmxGraphiteMetrics;
    }
    
    private List<GraphiteMetric> createGraphiteMetrics_Unfiltered(List<JmxMetricRaw> jmxMetricsRaw) {
                
        if (jmxMetricsRaw == null) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> jmxGraphiteMetrics = new ArrayList<>();
        
        for (JmxMetricRaw jmxMetricRaw : jmxMetricsRaw) {    
            String unformattedGraphiteMetricPath = jmxMetricRaw.createAndGetUnformattedGraphiteMetricPath();
            String graphiteMetricPath;
            
            if (!graphiteFormattedMetricPaths_.containsKey(unformattedGraphiteMetricPath)) {
                graphiteMetricPath = jmxMetricRaw.createAndGetGraphiteFormattedMetricPath();
                graphiteFormattedMetricPaths_.put(unformattedGraphiteMetricPath, graphiteMetricPath);
            }
            else {
                graphiteMetricPath = graphiteFormattedMetricPaths_.get(unformattedGraphiteMetricPath);
            }
            
            GraphiteMetric graphiteMetric = createGraphiteMetric(graphiteMetricPath, jmxMetricRaw.getMetricValue(), currentTimestamp_);

            if (graphiteMetric != null) {
                jmxGraphiteMetrics.add(graphiteMetric); 
            }
        }
        
        return jmxGraphiteMetrics;
    }
    
    private void addMetricToNeverDownloadTheseMetricAttributes(String objectInstanceName, String attributeName) {
        
        if ((objectInstanceName == null) || (attributeName == null)) {
            return;
        }
        
        if (neverDownloadTheseMetricAttributes_.containsKey(objectInstanceName)) {
            List<String> offLimitsMetricsForObjectInstance = neverDownloadTheseMetricAttributes_.get(objectInstanceName);

            if (!offLimitsMetricsForObjectInstance.contains(attributeName)) {
                offLimitsMetricsForObjectInstance.add(attributeName);
            }
        }
        else {
            List<String> offLimitsMetricsForObjectInstance = new ArrayList<>();
            offLimitsMetricsForObjectInstance.add(attributeName);

            neverDownloadTheseMetricAttributes_.put(objectInstanceName, offLimitsMetricsForObjectInstance);
        }
        
    }
    
    private void addMetricToAlwaysDownloadTheseMetricAttributes(String objectInstanceName, String attributeName) {
        
        if ((objectInstanceName == null) || (attributeName == null)) {
            return;
        }
 
        if (alwaysDownloadTheseMetricAttributes_.containsKey(objectInstanceName)) {
            List<String> onLimitsMetricsForObjectInstance = alwaysDownloadTheseMetricAttributes_.get(objectInstanceName);

            if (!onLimitsMetricsForObjectInstance.contains(attributeName)) {
                onLimitsMetricsForObjectInstance.add(attributeName);
            }
        }
        else {
            List<String> onLimitsMetricsForObjectInstance = new ArrayList<>();
            onLimitsMetricsForObjectInstance.add(attributeName);
            
            alwaysDownloadTheseMetricAttributes_.put(objectInstanceName, onLimitsMetricsForObjectInstance);
        }
        
    }

    /*
    Returns: 
    -1 if metric is not allowed to be downloaded or outputted to graphite 
    1 if metric is allowed to be downloaded, but not allowed to be outputted to graphite 
    2 if metric is allowed to be downloaded & allowed to be outputted to graphite 
    */
    private int isMetricAllowed(String metricPath) {
        
        if (metricPath == null) {
            return -1;
        }

        if (isMeticPathAllowedCache_.containsKey(metricPath)) {
            return isMeticPathAllowedCache_.get(metricPath);
        }
        else {
            boolean isBlacklisted = isMetricOnAccessControlList(metricPath, blacklistRegexs_);
            
            boolean isWhitelisted;
            if ((whitelistRegexs_ == null) || whitelistRegexs_.isEmpty()) isWhitelisted = true;
            else isWhitelisted = isMetricOnAccessControlList(metricPath, whitelistRegexs_);
            
            boolean isDerivedNumeric = isMetricOnAccessControlList(metricPath, numericDerivedRegexs_);
            boolean isDerivedString = isMetricOnAccessControlList(metricPath, stringDerivedRegexs_);
            
            if (isWhitelisted && !isBlacklisted) {
                isMeticPathAllowedCache_.put(metricPath, 2);
                return 2;
            }
            if (isDerivedNumeric || isDerivedString) {
                isMeticPathAllowedCache_.put(metricPath, 1);
                return 1;
            }
            else {
                isMeticPathAllowedCache_.put(metricPath, -1);
                return -1;  
            }
        }
        
    }
    
    private boolean isMetricOnAccessControlList(String metricPath, List<String> regexs) {
        
        if ((regexs == null) || regexs.isEmpty() || (metricPath == null) || (regexPatterns_ == null)) {
            return false;
        }
        
        boolean isOnAccessControlList = false;

        for (String regex : regexs) {
            boolean isMatch;

            try {
                Pattern pattern = regexPatterns_.get(regex);
                isMatch = pattern.matcher(metricPath).find();
            }
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
                isMatch = false;
            }
        
            if (isMatch) {
                isOnAccessControlList = true;
                break;
            }
        }
                
        return isOnAccessControlList;
    }

    public GraphiteMetric createGraphiteMetric(String meticPath, BigDecimal metricValue, int timestamp) {
        
        if ((meticPath == null) || (metricValue == null) || (timestamp < 0)) {
            return null;
        }
  
        GraphiteMetric graphiteMetric = new GraphiteMetric(meticPath, metricValue, timestamp);

        return graphiteMetric;
    }

    public String getHost() {
        return host_;
    }

    public int getPort() {
        return port_;
    }

    public String getUsername() {
        return username_;
    }

    public String getPassword() {
        return password_;
    }
    
    public int getNumConnectionAttemptRetries() {
        return numConnectionAttemptRetries_;
    }

    public long getJmxSleepAfterConnectTime() {
        return sleepAfterConnectTime_;
    }
    
    public long getJmxQueryMetricTreeInterval() {
        return queryMetricTreeInterval_;
    }
    
    public List<String> getBlacklistObjectNameRegexs() {
        return blacklistObjectNameRegexs_;
    }
    
    public List<String> getBlacklistRegexs() {
        return blacklistRegexs_;
    }

    public List<String> getWhitelistRegexs() {
        return whitelistRegexs_;
    }

}
