OpenNMS-RequisitionsToNotificationStragety
==========================================
## Requirements:
- `wget must` be in the search path
- `provision.pl` must be in the search path, if not customize the variable PROVISION_SCRIPT in provisioning.sh script
- Running Inventory Integration server with XLS requisition or fitting surveillance categories for generating notificiations.

## config.properties options
- `prefix_generated`: notifications and destinationPaths add a `GEN_` prefix to identify the auto generated notifications from the manual configured notifications and destination paths. With the `prefix_generated` the default `GEN_` can be customized
- `prefix_team`: Defines which surveillance category prefix should be used to define the the target group in OpenNMS for the destination path.
- `notification_delay`: If not set the initial delay is set to 10 minutes.
- `notification_node_text`: Text message template for nodeDown notification
- `notification_service_text`: Text message template for nodeLostService notification
- `prefix_notification_token`: 
- `prefix_transport`:
- `splitter`:
