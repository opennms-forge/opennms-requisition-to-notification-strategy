import org.opennms.pris.model.Requisition
import org.opennms.pris.model.RequisitionAsset
import org.opennms.pris.model.RequisitionNode
import org.opennms.pris.model.RequisitionCategory
import org.opennms.pris.model.RequisitionInterface
import org.opennms.pris.model.RequisitionMonitoredService

final String PREFIX_TRANSPORT= "notify-"
final String PREFIX_GROUP= "team-"
final String PREFIX_NOTIFY_CATEGORY = "NOTIFY"
final String SPLITTER = "__"

Requisition requisition = requisition

for (RequisitionNode node : requisition.getNodes()) {
    Set<String> notificationTokens = new TreeSet<>()
    for (RequisitionCategory category : node.getCategories()) {
        if (category.getName().startsWith(PREFIX_TRANSPORT) || category.getName().startsWith(PREFIX_GROUP)) {
            notificationTokens.add(category.getName())
        }
    }
    String notificationToken = notificationTokens.join(SPLITTER)
    if (notificationToken.contains(PREFIX_GROUP) && notificationToken.contains(PREFIX_TRANSPORT)) {
        //add custom notificationToken for the node
        node.getCategories().add(new RequisitionCategory(PREFIX_NOTIFY_CATEGORY + SPLITTER + notificationToken))

        //build custom notificationTokens for services of the node
        Set<String> serviceNotificationTokens = new TreeSet<>()
        for(RequisitionInterface reqInterface : node.getInterfaces()) {
            for (RequisitionMonitoredService service : reqInterface.getMonitoredServices()) {
                serviceNotificationTokens.add(PREFIX_NOTIFY_CATEGORY + SPLITTER + service.getServiceName() + SPLITTER + notificationToken)
            }
        }
        //add custom notficationTokens for services to the node
        for (String serviceNotificationToken : serviceNotificationTokens) {
            node.getCategories().add(new RequisitionCategory(serviceNotificationToken))
        }
    }
}
return requisition
