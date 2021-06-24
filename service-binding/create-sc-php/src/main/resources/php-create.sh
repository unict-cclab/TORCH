#!/bin/bash
#This script installs PHP

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

now=$(date)
echo "PHP-CREATE --> $now" >> ~/log.txt

# Waiting for the instance boot to be finished
until [[ -f /var/lib/cloud/instance/boot-finished ]]; do
    sleep 1
done

if (( $(grep -c "127\.0\.1\.1[[:blank:]]$HOSTNAME" /etc/hosts) == 0))
  then
    sudo sed -i "/127.0.0.1[[:blank:]]/a 127.0.1.1 $HOSTNAME" /etc/hosts
fi

NAME="PHP"
LOCK="/tmp/lockaptget"

while true; do
  if mkdir "${LOCK}" &>/dev/null; then
    echo "$NAME take apt lock"
    break;
  fi
  echo "$NAME waiting apt lock to be released..."
  sleep 1
done

while sudo fuser /var/lib/dpkg/lock >/dev/null 2>&1 ; do
  echo "$NAME waiting for other software managers to finish..."
  sleep 1
done

sudo rm -f /var/lib/dpkg/lock
sudo dpkg --configure -a

sudo apt-get update
sudo apt-get -y -q install php php-common php-curl php-cli php-pear php-gd php-mcrypt php-xmlrpc php-xml-parser libapache2-mod-php
#sudo apt-get -y -q install php5 php5-common php5-curl php5-cli php-pear php5-gd php5-mcrypt php5-xmlrpc php-xml-parser

rm -rf "${LOCK}"
echo "$NAME released apt lock"

#json=$(printf "$template" "0" "PHP installation succeeded")
json=$(printf "$template" "0" "")
echo "$json"

exit 0
