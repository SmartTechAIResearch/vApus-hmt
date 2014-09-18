# Copyright 2014 (c) Sizing Servers Lab
# University College of West-Flanders, Department GKG
#
# Author(s):
# 	Dieter Vandroemme

# This is a start script for running a vapus linux monitor agent as a daemon (service).
# It searches for a file named config, in the directory holding this script, having the agent name at the first line. (Example:vApus-dstat)
# It runs that agent using nohup.
# For a correct stdOut put the default port, where the agent will listen at, at the second line of config. (Example:5556)
#
# To stop the service use ./stop-daemon.sh.
#
# Please make sure that you have read and write rights on the directory containing this script. 

# Parse the given arguments.
PORTSTATEMENT=""
if [ "$1" == --help -o "$1" == -h ]; then
    echo "Synopsis: ./start-as-daemon.sh [--help (-h) | --port (-p) X]; ommitting --port X will start listening at $PORT"
    exit 0
elif [ "$1" == --port -o "$1" == -p ]; then
    if [ "$2" != "" ]; then
        # Determine if the given port is a number.
        re='^[0-9]+$'
        if [[ $2 =~ $re ]]; then
            PORTSTATEMENT="-p $2"
        fi
    fi
fi

# Parse the config file.
AGENT=""
DEFAULTPORT="the default port"
if [ -f config ]; then
    while read line; do
        trimmedLine=$(echo "$line" | sed -e 's/^ *//' -e 's/ *$//')
        if [ "$trimmedLine" != "" ]; then
            if [ "$AGENT" == "" ]; then
                AGENT=$trimmedLine
                # Determine if the given agent is valid
                # re='^foo+$'
                # if [[ "$AGENT" !=~ $re ]]; then
                #    echo "Expected a wellformed filename for an agent as first entry of config. (vApus* without spaces)"
                #    exit -1
                # fi
            elif [ "$DEFAULTPORT" == "" ]; then
                DEFAULTPORT=$trimmedLine
                # Determine if the given port is a number.
                re='^[0-9]+$'
                if [![ "$DEFAULTPORT" =~ $re ]]; then
                    echo "Expected a numeric value for a port as second entry of config."
                    exit -1
                fi
                break
            fi
        fi
    done < config
fi

if [ "$AGENT" == "" ]; then
    echo "The agent name should be at the first line of a file config in the directory containing this script."
    echo "It is recommended to put the default port the agent will listen on on the second line."
    exit -1
fi

# Start the service if it is not running already.
PID=$(ps aux | grep -w "$AGENT.jar" | grep -v grep | awk '{print $2}')
if [ "$PID" != "" ]; then
    echo "$AGENT is already running! (PID $PID)"
    exit 0
fi


# If stuff goes wrong, you should catch it and log it yourself (log4J).
nohup java -jar "$AGENT.jar" $PORTSTATEMENT >out 2>errors &
# Debug mode
#nohup java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=5000,suspend=n -jar "$AGENT.jar" $PORTSTATEMENT >out 2>errors &

# The errors, if any, won't be written to file immediately.
sleep 2
ERRORS=""
if [ -f errors ]; then
    ERRORS=$(cat errors)
fi
if [ "$ERRORS" == "" ]; then
    if [ "$PORT" == "" ]; then
        PORT=$DEFAULTPORT
    fi
    echo "$AGENT is initializing and will listen at $PORT... (PID $!)"
    echo "Somewhere between now and a couple of minutes you should be able to connect to the agent. (tail -f out)"
    echo "Check the log file if not."
else
    echo "Failed to start $AGENT correctly now or previously! Delete the errors file if appropriate."
    echo $ERRORS
fi
exit 0
