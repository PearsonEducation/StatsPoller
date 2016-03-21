#*********************************************
# Format needs to stay the same
# Read the notes below 
#
# 1. Inside the dictionary you will have two keys, connection and query. Contain the info for the connection in the first
# and the query in the second.
#
# 2. To call this from the command line we will need to be at the parent directory. /StatsPoller
# python /bin/mysql_query_poller.py 'chk_all_wf_pct_full'. This passes the first key-value pair of the 
# dictionary to the script. After that it pulls the keys connection and query and acts accordingly 
#
# 3. In the query statement the query needs to return two columns forcibly named STAT_NAME and STAT_VALUE.
# These can contain as many rows as needed. However, STAT_VALUE needs to be numeric for StatsAgg to understand.
#*********************************************

