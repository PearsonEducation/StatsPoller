# Process Counter Collector

The 'Process Counter' collector counts of the number of processes that match a specified regular expression pattern. The user specifies a regular expression to match against, and every time it runs, will match against the full command-line path & argument list of all processes running on the operating system. This is no limit on the number of 'Process Counter' collectors. At present, this collector only works with on Linux systems, but Windows compatibility is in the works.

### Metrics

* (Process Identifier) : The 'identifier' is a user-specified identifier for the output metric name. The value of this metric will be the count of processes that have a positive match with the provided regular expression.

### Example

If the process launch command was "/usr/lib/firefox/firefox safemode taco" ... <br>
You count the number of processes running firefox with a regular expression of: "firefox" ... <br>
Or you could count the number of processes running firefox taco-edition (TE) with a regular expression of: "firefox.*taco"

### Example Output

firefox-te 1 1463373915
