#!/bin/bash
#This script configure mysql server

set -o pipefail  # trace ERR through pipes
set -o nounset   # trace ERR through 'time command' and other functions
set -o errtrace  # set -u : exit the script if you try to use an uninitialised variable
set -o errexit   # set -e : exit the script if any statement returns a non-true return value

TERM=linux
DEBIAN_FRONTEND=noninteractive

stderr_log="/dev/shm/stderr.log"
exec 2>"$stderr_log"

template='{"code":"%s","message":"%s"}'
json=''

###~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~##
#
# FUNCTION: EXIT_HANDLER
#
###~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~##

exit_handler() {

    local code="$?"

    test $code == 0 && return;

    #
    # LOCAL VARIABLES:
    # ------------------------------------------------------------------
    #
    local i=0
    local regex=''
    local mem=''

    local error_file=''
    local error_lineno=''
    local error_message='unknown'

    local lineno=''

    #
    # GETTING LAST ERROR OCCURRED:
    # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ #

    #
    # Read last file from the error log
    # ------------------------------------------------------------------
    #
    if test -f "$stderr_log"
        then
            stderr=$( tail -n 1 "$stderr_log" )
            sudo rm "$stderr_log"
    fi

    #
    # Managing the line to extract information:
    # ------------------------------------------------------------------
    #

    if test -n "$stderr"
        then
            # Exploding stderr on :
            mem="$IFS"
            local shrunk_stderr=$( echo "$stderr" | sed 's/\: /\:/g' )
            IFS=':'
            local stderr_parts=( $shrunk_stderr )
            IFS="$mem"

            # Storing information on the error
            error_file="${stderr_parts[0]}"
            error_lineno="${stderr_parts[1]}"
            error_message=""

            for (( i = 3; i <= ${#stderr_parts[@]}; i++ ))
                do
                    error_message="$error_message "${stderr_parts[$i-1]}": "
            done

            # Removing last ':' (colon character)
            error_message="${error_message%:*}"

            # Trim
            error_message="$( echo "$error_message" | sed -e 's/^[ \t]*//' | sed -e 's/[ \t]*$//' )"
    fi

    #
    # EXITING:
    # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ #

    json=$(printf "$template" "$code" "$error_message")
    echo "$json" 

    exit "$code"
}

trap exit ERR
trap 'exit_handler' EXIT

#
# MAIN CODE:
#
###~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~##

type mysql >/dev/null 2>&1 || (json=$(printf "$template" "1" "MySQL is not installed"); echo "$json"; exit 0)
db_port=$1
db_ip_address=$2

regex="5.([1-9][0-9]+|[7-9]).[0-9]+"
version=$(mysql --version|awk '{ print $5 }'|awk -F\, '{ print $1 }')

if [[ $version =~ $regex ]]; then
  path="/etc/mysql/mysql.conf.d/mysqld.cnf"
else
  path="/etc/mysql/my.cnf"
fi

sed --regexp-extended "s/(port\s*=\s*)[0-9]*/\1$db_port/g" < $path >/tmp/my.cnf
sudo mv -f /tmp/my.cnf $path

sed --regexp-extended "s/(bind-address\s*=\s*)127.0.0.1/\1$db_ip_address/g" < $path > /tmp/my.cnf
sudo mv -f /tmp/my.cnf $path

#sudo /etc/init.d/mysql stop
#sudo /etc/init.d/mysql start

if (( $(ps -ef | grep -v grep | grep mysql | wc -l) > 0 ))
then
  sudo /etc/init.d/mysql restart
else
  sudo /etc/init.d/mysql start
fi  
  
#json=$(printf "$template" "0" "MySQL configuration succeeded")
json=$(printf "$template" "0" "{\\\"db.port\\\":$db_port}")
echo "$json"

exit 0
