package com.pearson.statspoller.internal_metric_collectors.mongo;

import java.util.ArrayList;
import java.util.List;
import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.StackTrace;
import com.pearson.statspoller.utilities.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import java.math.BigDecimal;
import java.util.Map.Entry;
import org.boon.Pair;
import org.bson.Document;

/**
 * @author Jeffrey Schmidt
 */
public class MongoMetricCollector extends InternalCollectorFramework implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoMetricCollector.class.getName());
    
    private final String host_;
    private final int port_;
    private final String username_;
    private final String password_;
    
    public MongoMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
            String outputFilePathAndFilename, boolean writeOutputFiles,
            String host, int port, String username, String password) {
        super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
        this.host_ = host;
        this.port_ = port;
        this.username_ = username;
        this.password_ = password;
    }
    
    @Override
    public void run() {
         
        while(super.isEnabled()) {
            long routineStartTime = System.currentTimeMillis();
            
            // put graphite metrics in here
            List<GraphiteMetric> graphiteMetrics = getMongoMetrics();
            
            // output graphite metrics
            super.outputMetrics(graphiteMetrics, true);

            long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
            
            // lay on anything worth logging here
            logger.info("Finished Mongo metric collection routine. MongoServer=" + host_ + ":" + port_ + 
                    ", MetricsCollected=" + graphiteMetrics.size() +
                    ", MetricCollectionTime=" + routineTimeElapsed);
            
            long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

            if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
        }

    }

    private List<GraphiteMetric> getMongoMetrics() {
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
                
        try {
            MongoClientURI uri = new MongoClientURI("mongodb://" + username_ + ":" + password_
                    + "@" + host_ + ":" + port_ + "/?authSource=admin");

            MongoClient mongoClient;

            //try blocks do not work with mongoclient as connection process is done by a background thread.
            if ((username_ != null) && !username_.isEmpty()) mongoClient = new MongoClient(uri);
            else mongoClient = new MongoClient(host_, port_);

            if (mongoClient == null) {
                logger.error("Invalid mongodb configuration");
                return graphiteMetrics;
            }

            MongoDatabase db = mongoClient.getDatabase("admin");

            //ServerStatus
            Document serverStatus = new Document();
            try {
                serverStatus = db.runCommand(new Document().append("serverStatus", 1));
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }

            if (serverStatus.containsKey("ok") && serverStatus.get("ok").equals(1.0)) {
                graphiteMetrics.addAll(processDocumentAndAddToMetrics(serverStatus, "serverStatus"));
            }

            //ReplStatus
            Document replSetStatus = new Document();
            try {
                replSetStatus = db.runCommand(new Document().append("replSetGetStatus", 1));
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }

            // "set" : "rs-myTS"
            if (replSetStatus.containsKey("ok") && replSetStatus.get("ok").equals(1.0)) {
                graphiteMetrics.addAll(processDocumentAndAddToMetrics(replSetStatus, "replSetStatus"));
            }

            //For each db
            Document databasesList = new Document();
            try {
                databasesList = db.runCommand(new Document().append("listDatabases", 1));
            } 
            catch (Exception e) {
                logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            }

            //logger.info(((ArrayList) databasesList.get("databases")).toString());
            if (databasesList.containsKey("ok") && databasesList.get("ok").equals(1.0)) {
                ArrayList<Document> databases;
                databases = (ArrayList) databasesList.get("databases");
                
                for (Document datab : databases) {
                    String dbName = datab.get("name").toString();
                    MongoDatabase currentDatabase = mongoClient.getDatabase(dbName);
                    //db.stats()
                    Document dbStats = currentDatabase.runCommand(new Document().append("dbStats", 1).append("scale", 1));
                    //db is the name in form "db" : "myDB"

                    //db.collection.stats()
                    MongoIterable collections = currentDatabase.listCollectionNames();
                    MongoCursor iterator = collections.iterator();
                    while (iterator.hasNext()) {
                        String col = iterator.next().toString();
                        Document collStats = currentDatabase.runCommand(new Document().append("collStats", col).append("scale", 1).append("verbose", true));
                        graphiteMetrics.addAll(processDocumentAndAddToMetrics(collStats, "collectionStats." + col));
                    }

                }
            }
            //  db.stats() & db.collection.stats()
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private List<GraphiteMetric> processDocumentAndAddToMetrics(Document document, String origin) {
        //Replace all spaces in docs with _ before writing to graphite array
        //can be Document?
        //can be int?
        //can be Long.parseLong(value)?
        //continue
        //Ignore any key named "commands"
        
        if ((document == null) || (origin == null)) {
            return new ArrayList<>();
        }
        
        List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
        
        try {
            document = filterDocument(document);

            List<Pair<String, BigDecimal>> metrics = buildMetrics(origin, document);

            int metricTimestamp = ((int) (System.currentTimeMillis() / 1000));

            for (Pair<String, BigDecimal> pair : metrics) {
                graphiteMetrics.add(new GraphiteMetric(pair.getFirst(), pair.getSecond(), metricTimestamp));
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return graphiteMetrics;
    }
    
    private Document filterDocument(Document document) {
        
        if ((document == null) || document.isEmpty()) {
            return new Document();
        }

        Document documentCopy = null;
        
        try {
            documentCopy = Document.parse(document.toJson());

            for (Entry entry : document.entrySet()) {
                String key = entry.getKey().toString();

                if (entry.getValue() instanceof Document) {
                    //Ignore commands field.
                    if (key.equalsIgnoreCase("commands")) {
                        documentCopy.remove(key);
                        continue;
                    }

                    Document passdown = (Document) entry.getValue();
                    documentCopy.replace(key, filterDocument(passdown));

                    if (((Document) documentCopy.get(key)).isEmpty()) {
                        documentCopy.remove(key);
                    }
                } 
                else if ((documentCopy.get(key) instanceof Integer) || (documentCopy.get(key) instanceof Double) || (documentCopy.get(key) instanceof Long)) {
                    if (key.equalsIgnoreCase("ok")) {
                        documentCopy.remove(key);
                    }
                } 
                else {
                    documentCopy.remove(key);
                }

            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return documentCopy;
    }

    private List<Pair<String, BigDecimal>> buildMetrics(String start, Document document) {

        if ((start == null) || (document == null) || document.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Pair<String, BigDecimal>> metrics = new ArrayList<>();
       
        try {
            Document documentCopy = Document.parse(document.toJson());

            for (Entry entry : document.entrySet()) {
                String key = entry.getKey().toString();

                if ((documentCopy.get(key) instanceof Integer) || (documentCopy.get(key) instanceof Double) || (documentCopy.get(key) instanceof Long)) {
                    if (start.isEmpty()) {
                        metrics.add(new Pair(key, new BigDecimal(documentCopy.get(key).toString())));
                    } 
                    else {
                        metrics.add(new Pair(start + "." + key, new BigDecimal(documentCopy.get(key).toString())));
                    }
                } 
                else if (entry.getValue() instanceof Document) {
                    Document passdown = (Document) entry.getValue();
                    if (start.isEmpty()) {
                        buildMetrics(key, passdown);
                    } 
                    else {
                        buildMetrics(start + "." + key, passdown);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
        }
        
        return metrics;
    }
    
}
