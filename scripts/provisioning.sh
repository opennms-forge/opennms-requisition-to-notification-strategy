#!/bin/bash
# Script to implement a workflow importing and generating requisitions,
# notifications and destination paths from OpenNMS requisitions.
# 
# The script implements the following workflow:
#  1. Create XML file for each requisition in in opennms/etc/imports
#  2. Backup notifications.xml and destinationPaths.xml
#  3. Get all nodes from requisition XML files with ReST GET /rest/requisitions
#  4. Generate a new notifications.xml and destinationPaths.xml from category tokens
#     build by GenerateNotificationTokens.groovy used by Inventory Integration Server
#  5. Import all active requisitions with ReST PUSH rest/requisition/import/${name}
# uncomment to enable bash debug
# set -x

# OpenNMS home directory
OPENNMS_HOME="/opt/opennms"
OPENNMS_ETC="${OPENNMS_HOME}/etc"

# Base URL and authentication for ReST calls importing requisitions
OPENNMS_USER="rest-provisioning-user"
OPENNMS_PASS="rest-provisioning-pass"
OPENNMS_BASEURL="http://${OPENNMS_USER}:${OPENNMS_PASS}@localhost:8980/opennms"

# OpenNMS provided provision.pl script
PROVISION_SCRIPT=$(which provision.pl)

# Inventory Integration server home directory and URL
OIIS_HOME="/opt/opennms-pris"
OIIS_BASEURL="http://localhost:8000"

# Requisition to notification jar file
OIIS_REQ2NOTIFY=/opt/opennms-pris/requisition-to-notification.jar

# List with all active requisitions
REQ_LIST=requisitions.list

# Temporary file to generate all notifications from all requisitions from
# ${OPENNMS_BASEURL}/rest/requisitions
REQ_TEMP="/tmp/requisitions.xml"

# Check if provision.pl is available
if [ ! -f ${PROVISION_SCRIPT} ]; then
  echo "Script provisiong.pl is required in search path and should be found with \"which provision.pl\"."
  echo "The script should be located in \$OPENNMS_HOME/bin/provision.pl."
  exit 1
fi

# Function: downloadRequisitions()
# Download all requisitions in opennms/etc/imports based on requisition list file
downloadRequisitions() {
  for i in $(cat ${REQ_LIST}); do
    echo -n "Download ${OIIS_BASEURL}/${i} ... "
    wget -q -O ${OPENNMS_ETC}/imports/${i}.xml ${OIIS_BASEURL}/${i};
    if  [ ! $? -eq 0 ]; then
      echo "FAILED"
      exit 1
    else 
      echo "OK" 
    fi
  done
}

# Function: importRequisitions()
# Import all active requisitions based on requisition list file
importRequisitions() {
  for i in $(cat ${REQ_LIST}); do
   echo -n "Import requisition ${i} ... "
    ${PROVISION_SCRIPT} --username ${OPENNMS_USER} --password ${OPENNMS_PASS} requisition import ${i} 2>&1 > /dev/null;
    if [ ! $? -eq 0 ]; then
      echo "FAILED"
      exit 1
    else
      echo "OK"
    fi
  done
}

# Download all requisitions
downloadRequisitions

# Create backup for notifications.xml
echo -n "Backup notifications.xml ... "
cp ${OPENNMS_HOME}/etc/notifications.xml ${OPENNMS_HOME}/etc/notifications.xml.backup 2>&1 >/dev/null
if [ ! $? -eq 0 ]; then
  echo "FAILED"
  exit 1
else
  echo "OK"
fi

# Create backup for destinationPaths.xml
echo -n "Backup destinationPaths.xml ... "
cp ${OPENNMS_HOME}/etc/destinationPaths.xml ${OPENNMS_HOME}/etc/destinationPaths.xml.backup 2>&1 >/dev/null
if [ ! $? -eq 0 ]; then
  echo "FAILED"
  exit 1
else
  echo "OK"
fi

# Create temporary file with all nodes from all requisitions
echo -n "Create requisitions.xml ... "
wget -q -O ${REQ_TEMP} ${OPENNMS_BASEURL}/rest/requisitions 2>&1 >/dev/null
if [ ! $? -eq 0 ]; then
  echo "FAILED"
  exit 1
else
  echo "OK"
fi

# Generate notifications.xml and destinationPaths.xml with requisition-to-notification.jar
echo -n "Generate notification and destination path ... "
java -Dinput-xml=${REQ_TEMP} -Doutput-folder=${OPENNMS_HOME}/etc -jar ${OIIS_REQ2NOTIFY} 2>&1 >/dev/null
if [ ! $? -eq 0 ]; then
  echo "FAILED"
  exit 1
else
  echo "OK"
  #rm ${REQ_TEMP} 2>&1 >/dev/null
fi

# Import active requisitions into OpenNMS with provision.pl via ReST
importRequisitions

