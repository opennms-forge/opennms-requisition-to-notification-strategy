OpenNMS-RequisitionsToNotificationStragety
==========================================
## Requirements:
- wget must be in the search path
- provision.pl must be in the search path, if not customize the variable PROVISION_SCRIPT in provisioning.sh script
- The requisition-template can be used to create all requisitions
- Running Inventory Integration server with XLS requisition or fitting surveillance categories for generating notificiations.

## Directories and files
- requisition-template: Can be used to copy each requisition from. Important: XLS requisitions have normally all the same requisition.properties. The example has a linked central file. It makes it possible to change only one file and modify the behavior for all requisitions in the same time. If you need specific scripts or behavior, just copy the xls/requisition.properties in the requisition-director as requisition.properties.
- scripts: contains the GenerateNotificationTokens.groovy script which builds the surveillance category for the notification
- xls: contains the global requisition.properties file
- config.properties: Allows to change the behavior for generating the destinationPaths.xml and notifications.xml
- global.properties: General configuration for PRIS
- opennms-pris.jar: The PRIS runnable jar itself (
- provisioning.sh: Script to automatically create requisition, generate notification, build destination paths and import all requisitions into OpenNMS
- README.md: This file
- requisition-to-notification.jar: runnable jar which creates the destinationPaths.xml and the notifications.xml
- requistions.list: File which defines for which requisitions the OpenNMS is responsible for. It defines which requisitions should be handled in the provisioning.sh script

## config.properties options
- prefix_generated: notifications and destinationPaths add a “GEN_” prefix to identify the auto generated notifications from the manual configured notifications and destination paths. With the prefix_generated the default “GEN_” can be customized
- prefix_team: Defines which surveillance category prefix should be used to define the the target group in OpenNMS for the destination path.
- notification_delay: If not set the initial delay is set to 10 minutes.
- notification_node_text: Text message template for nodeDown notification
- notification_service_text: Text message template for nodeLostService notification
- prefix_notification_token: 
- prefix_transport:
- splitter:
