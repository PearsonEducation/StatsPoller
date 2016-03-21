#!/usr/bin/python
# *****************About Section*****************
#
#  Writes desired command & calculations into output file.
#  
#  Output file format:  MetricPath MetricValue EpochTimestamp
#      MetricPath:  String representing Metric.
#           "." separates sub-groupings.
#           Should include unit of measurement.
#               eg:  CPU.User-%
#      MetricValue:  Numeric value of the output.
#           "." acceptable in the value.
#               eg:  2234
#               eg:  2234.12345
#      EpochTimestamp:  Time that measurement was taken.
#               eg:  1383148462
#  
#  Required Directory Structure
#      .../StatsPoller/output/mysql_query_output   -> Location of output
#	   .../StatsPoller/bin      -> Location of python scripts
#  
#	mysql.connector module required  
#   Currently only runs on *nix operating systems
#  
#	When calling from the command line
#    the following parameters are accepted.
#        Query name (Argument 1 Required). Name of query specification. 
#		 See config/mysql_query_config.py for details
#             eg.  python mysql_query_poller.py sql_1
#	     Config name (Argument 2 Not Required). Name of query specification. 
#		 Defaults to mysql_query_config. All configs need to be in bin/config			
#		 See config/mysql_query_config.py for details on formatting
#             eg.  python mysql_query_poller.py sql_1 query_config
#
#  Scripts may have programmed delays.  Consider this when setting up run frequency.
#  The script user needs to have access to write to the /tmp folder as well.
#
#  Author:  Judah Walker
#
# NOTE: You need to install this package below for this to work. 
# sudo yum install mysql-connector-python
# ***************End About Section****************

# ****************Import Section******************
import os
import sys
import time
import errno
import multiprocessing

try:
    import mysql.connector
except ImportError, e:
    print("Install mysql.connector")
    raise SystemExit

try:
    configname = str(sys.argv[2])
except:
    configname = "mysql_query_config"
try:
    config = __import__("config." + configname, globals(), locals(), ['object'], -1)
except:
    print("check config/" + configname)
    raise SystemExit


# ****************Import Sections*****************

# **************Universal Functions***************
def executor(sql, cnx, pidfile, queryname):
    cursor = cnx.cursor(buffered=True)
    tic = time.time()
    try:
        cursor.execute(sql)
    except:
        print("SQL Syntax Error")
        outfile = open('./output/mysql_query_output/mysql_query_poller' + queryname + '.out', 'wb')
        now = str(int(time.time()))
        print >> outfile, queryname + ".SQLSyntaxError 1 " + now
        outfile.close()
        cnx.close()
        os.unlink(pidfile)
        sys.exit()

    toc = time.time()

    if (toc - tic < 0.001):
        timetakenms = "0000"
    else:
        timetakenms = ("%.3f" % (toc - tic)).replace(".", "")

    result_queue.put(timetakenms)
    names = []
    values = []
    final = []

    for (STAT_NAME, STAT_VALUE) in cursor:
        names.append(STAT_NAME)
        values.append(STAT_VALUE)

    final.append(names)
    final.append(values)
    result_queue.put(final)

    return


def make_sure_path_exists(path):
    try:
        os.makedirs(path)
    except OSError as exception:
        # In the event of permission issues or other kinds
        if exception.errno != errno.EEXIST:
            raise


def check_pid(pid):
    # Check For the existence of a unix pid.
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    else:
        return True


# *************End Universal Functions************

# ****************Creation Section****************
script = (str(sys.argv[0])).split('.')[0]

try:
    queryname = str(sys.argv[1])
except:
    print("Error: Please add the query argument to the call")
    raise SystemExit

pidfile = "/tmp/mysql_query_" + queryname + ".pid"
pid = str(os.getpid())

if os.path.isfile(pidfile):
    f = open(pidfile, "r")
    for line in f:
        procpid = line
    isrunning = check_pid(int(procpid))
    f.close()
    if isrunning:
        # Currently running, exiting
        sys.exit()
    else:
        # Improper exit, recreating
        os.unlink(pidfile)
        file(pidfile, 'w').write(pid)
else:
    # Not running, continue on
    file(pidfile, 'w').write(pid)

# Make sure output folder exists
make_sure_path_exists("./output/mysql_query_output")

# Obtain info from config
proc = config.config[queryname]
sql = proc['query']

try:
    cnx = mysql.connector.connect(**proc['connection'])
    avblOutfile = open('./output/mysql_query_output/mysql_query_poller_available.out', 'wb')
    now = str(int(time.time()))
    print >> avblOutfile, "MySQL_Available 1 " + now
    avblOutfile.close()
except mysql.connector.Error as err:
    if err.errno == 2003:
        print("Error: Something is wrong with your user name or password")
        pass
    elif err.errno == 1049:
        print("Error: Database does not exists")
        pass
    else:
        print(err)
        pass

    # Early exit
    avblOutfile = open('./output/mysql_query_output/mysql_query_poller_available.out', 'wb')
    now = str(int(time.time()))
    print >> avblOutfile, "MySQL_Available 0 " + now
    avblOutfile.close()
    outfile = open('./output/mysql_query_output/mysql_query_poller' + queryname + '.out', 'wb')
    now = str(int(time.time()))
    print >> outfile, queryname + ".DatabaseAccountError 1 " + now
    outfile.close()
    os.unlink(pidfile)
    sys.exit()
# ************End Creation Section****************

# ************Query Process Section***************
result_queue = multiprocessing.Queue()
p = multiprocessing.Process(target=executor, args=(sql, cnx, pidfile, queryname))
p.start()
SecsSinceStart = 0

while (p.is_alive()):
    time.sleep(1)
    SecsSinceStart = SecsSinceStart + 1

    if (SecsSinceStart > 60):
        p.terminate()
        p.join()  # It is important to join() the process after terminating it in order to give the background
        # machinery time to update the status of the object to reflect the termination.
        outfile = open('./output/mysql_query_output/mysql_query_poller' + queryname + '.out', 'wb')
        now = str(int(time.time()))
        print >> outfile, queryname + ".TimeoutError 1 " + now
        outfile.close()
        cnx.close()
        os.unlink(pidfile)
        sys.exit()
# **********End Query Process Section*************

# ************Result Output Section***************
if result_queue.empty():
    sys.exit()

timetakenms = result_queue.get()
queryresult = result_queue.get()
outfile = open('./output/mysql_query_output/mysql_query_poller_' + queryname + '.out', 'wb')
now = str(int(time.time()))
print >> outfile, queryname + ".QueryTime-ms " + timetakenms + " " + now
values = queryresult.pop()
lenofvalues = len(values)
names = queryresult.pop()

for i in list(range(lenofvalues)):
    valuestring = str(values.pop())
    if valuestring == 'None':
        valuestring = '0'
    namestring = str(names.pop())
    print >> outfile, queryname + "." + namestring + " " + valuestring + " " + now
    if (i == lenofvalues - 1):
        break

print >> outfile, queryname + ".TimeoutError 0 " + now
outfile.close()
cnx.close()
os.unlink(pidfile)
# **********End Result Output Section*************
