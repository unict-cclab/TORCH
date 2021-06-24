#!/bin/bash
#This script configures Apache WS

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
type apache2 >/dev/null 2>&1 || (json=$(printf "$template" "1" "Apache WS is not installed"); echo "$json"; exit 0)

DEFAULT_PORT=80
port=$1
doc_root=$2

sudo /etc/init.d/apache2 stop

if [ ! -d $doc_root ]; then
  eval "sudo mkdir -p $doc_root"
fi
eval "sudo chown -R www-data:www-data $doc_root"

if [[ ("$port" == "$DEFAULT_PORT") ]]; then
  echo "Use default port for Apache : $DEFAULT_PORT"
else
  echo "Replacing port $DEFAULT_PORT with $port..."
  #sudo sed -i -e "s/$DEFAULT_PORT/$port/g" /etc/apache2/ports.conf
  sudo sed -i -e "s/$DEFAULT_PORT[0-9]*/$port/g" /etc/apache2/ports.conf
fi

echo "Change config of apache2"
if sudo test -f "/etc/apache2/sites-available/default"; then
  echo "Change the DocumentRoot of apache2 on Ubuntu < 14.04"
  sed -i -e "s#DocumentRoot /var/www#DocumentRoot $doc_root#g" /etc/apache2/sites-available/default
fi
if sudo test -f "/etc/apache2/sites-available/000-default.conf"; then
  echo "Change the DocumentRoot of Apache2 on Ubuntu >= 14.04"
  sudo sed -i -e "s#DocumentRoot /var/www/html#DocumentRoot $doc_root#g" /etc/apache2/sites-available/000-default.conf
fi

sudo bash -c "echo ServerName localhost >> /etc/apache2/apache2.conf"

echo "Start apache2 whith new conf"
sudo /etc/init.d/apache2 start

#json=$(printf "$template" "0" "Apache configuration succeeded")
json=$(printf "$template" "0" "{\\\"port\\\":$port,\\\"document.root\\\":\\\"$doc_root\\\"}")
echo "$json"

exit 0
