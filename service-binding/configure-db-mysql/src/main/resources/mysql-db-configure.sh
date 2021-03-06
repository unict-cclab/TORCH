#!/bin/bash
#This script create a mysql database

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

db_name=$1
db_root_password=$2
db_user=$3
db_password=$4
app_ip_address=$5

mysqlshow -u root --password=$db_root_password "$db_name" > /dev/null 2>&1 || (json=$(printf "$template" "1" "MySQL database has not been created yet"); echo "$json"; exit 0)

#mysql -u root --password=$db_root_password << EOF
#CREATE USER IF NOT EXISTS "$db_user"@"$app_ip_address" IDENTIFIED BY "$db_password";
#GRANT ALL PRIVILEGES ON $db_name.* TO "$db_user"@"$app_ip_address";
#FLUSH PRIVILEGES;
#EXIT
#EOF

mysql -u root --password=$db_root_password << EOF
GRANT ALL PRIVILEGES ON $db_name.* TO "$db_user"@"$app_ip_address" IDENTIFIED BY "$db_password";
FLUSH PRIVILEGES;
EXIT
EOF

now=$(date)
echo "MySQL-DB-CONFIGURE --> $now" >> ~/log.txt

#json=$(printf "$template" "0" "MySQL database configuration succeeded")
json=$(printf "$template" "0" "{\\\"db.usr\\\":\\\"$db_user\\\",\\\"db.pwd\\\":\\\"$db_password\\\"}")
echo "$json"

exit 0
