# File Counter Collector

The 'File Counter' collector is a metric collector counts files in a directory. You configure it to watch a specific directory, and every time it runs, it will count the number of files in that directory. This is no limit on the number of 'File Counter' collectors. File Counter collector can optionally be configured to count the number of files in subdirectories. File Counter can also be configured to omit counts for emtry directories. File Counter ignores symlinks.

### Metrics

* File Count : The number of files in the specified directory

### Example output (Graphite Formatted)

filecount 4 1463373915
mysubfolder1.filecount 0 1463373915
mysubfolder2.filecount 5 1463373915