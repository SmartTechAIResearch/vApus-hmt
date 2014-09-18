# Copyright 2014 (c) Sizing Servers Lab
# University College of West-Flanders, Department GKG
#
# Author(s):
# 	Dieter Vandroemme

# Stops a daemon started using ./start-as-daemon.sh. 
# Please make sure that you have read and write rights on the directory containing this script.

# Parse the given arguments.
if [ "$1" == --help -o "$1" == -h ]; then
    echo "Synopsis: ./stop-daemon.sh [--help (-h)]"
    exit 0
fi

AGENT=""
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
                break
            fi
        fi
    done < config
fi

if [ "$AGENT" == "" ]; then
    echo "The agent name should be at the first line of a file config in the directory containing this script."
    echo "It is recommended to put the default port the agent will listen at on the second line."
    exit -1
fi

# Should be only one...
PIDS=$(ps aux | grep -w "$AGENT.jar" | grep -v grep | awk '{print $2}')

if [ "$PIDS" == "" ]; then
    echo "$AGENT was not running..."
else
    for PID in $PIDS; do
        kill -s SIGTERM $PID 2> /dev/null
        echo "$AGENT stopped! (PID $PID)"
    done
fi

# Cleanup files
if [ -f out ]; then
    rm -f out
fi
if [ -f log ]; then
    LOG=$(cat log)
    if [ "$LOG" == "" ]; then
        rm -f log
    fi
fi
if [ -f errors ]; then
    ERRORS=$(cat errors)
    if [ "$ERRORS" == "" ]; then
        rm -f errors
    fi
fi
exit 0
