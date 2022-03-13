# ----
# $1 is the properties file
# ----
PROPS="$1"
if [ ! -f ${PROPS} ] ; then
    echo "${PROPS}: no such file" >&2
    exit 1
fi

# ----
# getProp()
#
#   Get a config value from the properties file.
# ----
function getProp()
{
    grep "^${1}=" ${PROPS} | sed -e "s/^${1}=//"
    # grep "^${1}=" ${PROPS} | sed -e "s/^${1}=//" -e 's/\s*$//'
}

# ----
# getCP()
#
#   Determine the CLASSPATH based on the database system.
# ----
function setCP()
{
    case "$(getProp db)" in
	oracle)
	    cp="../lib/*"
	    if [ ! -z "${ORACLE_HOME}" -a -d ${ORACLE_HOME}/lib ] ; then
		cp="${cp}:${ORACLE_HOME}/lib/*"
	    fi
	    cp="${cp}:../lib/*"
	    ;;
	postgres)
	    cp="../lib/*"
	    ;;
	firebird)
	    cp="../lib/*"
	    ;;
	mariadb)
	    cp="../lib/*"
	    ;;
	transact-sql)
	    cp="../lib/*"
	    ;;
	babelfish)
	    cp="../lib/*"
	    ;;
    esac
    myCP="./:../BenchmarkSQL.jar:${cp}"
    export myCP
}

# ----
# Make sure that the properties file does have db= and the value
# is a database, we support.
# ----
db=$(getProp db)
case "${db}" in
    oracle|postgres|firebird|mariadb|transact-sql|babelfish)
	;;
    "")	echo "ERROR: missing db= config option in ${PROPS}" >&2
	exit 1
	;;
    *)	echo "ERROR: unsupported database type db=${db} in ${PROPS}" >&2
	exit 1
	;;
esac
