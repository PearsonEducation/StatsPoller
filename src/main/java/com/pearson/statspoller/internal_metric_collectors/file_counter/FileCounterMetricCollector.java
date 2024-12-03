package com.pearson.statspoller.internal_metric_collectors.file_counter;

import com.pearson.statspoller.internal_metric_collectors.InternalCollectorFramework;
import com.pearson.statspoller.metric_formats.graphite.GraphiteMetric;
import com.pearson.statspoller.utilities.core_utils.StackTrace;
import com.pearson.statspoller.utilities.core_utils.Threads;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class FileCounterMetricCollector extends InternalCollectorFramework implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(FileCounterMetricCollector.class.getName());
	
	private final String rootDirectory_;
	private final boolean countFilesInSubdirectories_;
	private final boolean countFilesInEmptyDirectories_;
	
	public FileCounterMetricCollector(boolean isEnabled, long collectionInterval, String metricPrefix, 
			String outputFilePathAndFilename, boolean writeOutputFiles,
			String rootDirectory, boolean countFilesInSubdirectories, boolean countFilesInEmptySubdirectories) {
		super(isEnabled, collectionInterval, metricPrefix, outputFilePathAndFilename, writeOutputFiles);
		
		rootDirectory_ = removeTrailingSlashes(rootDirectory);
		countFilesInSubdirectories_ = countFilesInSubdirectories;
		countFilesInEmptyDirectories_ = countFilesInEmptySubdirectories;
	}
	
	@Override
	public void run() {
		
		while(super.isEnabled()) {
			long routineStartTime = System.currentTimeMillis();
			
			// get the update stats in graphite format
			List<GraphiteMetric> graphiteMetrics = getFileCountMetrics();

			// output graphite metrics
			super.outputGraphiteMetrics(graphiteMetrics);

			long routineTimeElapsed = System.currentTimeMillis() - routineStartTime;
			
			logger.info("Finished File-Counter metric collection routine. " +
					"MetricsCollected=" + graphiteMetrics.size() +
					", MetricCollectionTime=" + routineTimeElapsed);
			
			long sleepTimeInMs = getCollectionInterval() - routineTimeElapsed;

			if (sleepTimeInMs >= 0) Threads.sleepMilliseconds(sleepTimeInMs);
		}
		
	}
	
	private List<GraphiteMetric> getFileCountMetrics() {
		
		if (rootDirectory_ == null) {
			return new ArrayList<>();
		}
		
		List<GraphiteMetric> graphiteMetrics = new ArrayList<>();
		
		try {			
			TreeMap<String,AtomicInteger> fileCountsByDirectory;
			
			if (countFilesInSubdirectories_) {
				fileCountsByDirectory = getFileAndDirectoryCountsInTree();
			}
			else {
				fileCountsByDirectory = getFileCountInSingleDirectory();
			}
			
			for (String directory : fileCountsByDirectory.keySet()) {
				try {
					int fileCount = fileCountsByDirectory.get(directory).get();

					int currentTimestampInSeconds = (int) (System.currentTimeMillis() / 1000);

					File directoryFile = new File(directory);
					String metricPath = StringUtils.removeStart(directoryFile.getCanonicalPath(), rootDirectory_);
					if (metricPath == null) metricPath = "";
					metricPath = removeLeadingSlashes(metricPath);
					if (SystemUtils.IS_OS_WINDOWS) metricPath = metricPath.replace('\\', '.');
					else metricPath = metricPath.replace('/', '.');

					String graphiteFriendlyMetricPath = GraphiteMetric.getGraphiteSanitizedString(metricPath, true, true);
					String finalMetricPath = (graphiteFriendlyMetricPath.isEmpty()) ? "filecount" : (graphiteFriendlyMetricPath + ".filecount");

					GraphiteMetric graphiteMetric = new GraphiteMetric(finalMetricPath, new BigDecimal(fileCount), currentTimestampInSeconds);
					graphiteMetrics.add(graphiteMetric);
				}
				catch (Exception e) {
					logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
				}
			}

		}
		catch (Exception e) {
			logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
		}
		
		return graphiteMetrics;
	}
	
	private TreeMap<String,AtomicInteger> getFileAndDirectoryCountsInTree() {
		
		if (rootDirectory_ == null) {
			return new TreeMap<>();
		}
		
		TreeMap<String,AtomicInteger> fileCountsByDirectory = new TreeMap<>();
		
		try {
			Path startPath = Paths.get(rootDirectory_);
			
			Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
					try {
						if (attrs.isRegularFile()) {
							String directory = path.toFile().getParent();

							if (!fileCountsByDirectory.containsKey(directory)) {
								fileCountsByDirectory.put(directory, new AtomicInteger(1));
							}
							else {
								fileCountsByDirectory.get(directory).getAndIncrement();
							}
						}
					}
					catch (Exception e) {
						logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
					}

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
					if (!countFilesInEmptyDirectories_) return FileVisitResult.CONTINUE;

					try {
						if (attrs.isDirectory()) {
							String directory = path.toString();
							if (!fileCountsByDirectory.containsKey(directory)) {
								fileCountsByDirectory.put(directory, new AtomicInteger(0));
							} 
						}
					}
					catch (Exception e) {
						logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} 
		catch (Exception e) {
			logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
		}
		
		return fileCountsByDirectory;	
	}
	
	private TreeMap<String,AtomicInteger> getFileCountInSingleDirectory() {
		
		if (rootDirectory_ == null) {
			return new TreeMap<>();
		}
		
		TreeMap<String,AtomicInteger> fileCountsByDirectory = new TreeMap<>();
		
		try {
			Path rootPath = Paths.get(rootDirectory_);
			DirectoryStream<Path> directoryStream = null;
			int fileCount = 0;
			
			try {
				directoryStream = Files.newDirectoryStream(rootPath, entry -> Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS));
				for (Path pathEntry : directoryStream) {
					fileCount++;
				}
			} 
			catch (Exception e) {
				logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
			}
			finally {
				if (directoryStream != null) {
					directoryStream.close();
				}

				directoryStream = null;
			}
			
			if (countFilesInEmptyDirectories_ || (fileCount > 0)) {
				fileCountsByDirectory.put(rootDirectory_, new AtomicInteger(fileCount));
			}
		} 
		catch (Exception e) {
			logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
		}
		
		return fileCountsByDirectory;	
	}
	
	private static String removeLeadingSlashes(String input) {
		if ((input == null) || input.isEmpty()) return input;
		
		while(input.startsWith("/")) input = org.apache.commons.lang3.StringUtils.removeStart(input, "/");
		while(input.startsWith("\\")) input = org.apache.commons.lang3.StringUtils.removeStart(input, "\\");
		
		return input;
	}
	
	private static String removeTrailingSlashes(String input) {
		if ((input == null) || input.isEmpty()) return input;
		
		while(input.endsWith("/")) input = org.apache.commons.lang3.StringUtils.removeEnd(input, "/");
		while(input.endsWith("\\")) input = org.apache.commons.lang3.StringUtils.removeEnd(input, "\\");
		
		return input;
	}
	
}
