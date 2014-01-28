package org.opennms.forge.requisitionstonotificationstrategy;

import com.google.common.base.Joiner;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.opennms.netmgt.config.destinationPaths.DestinationPaths;
import org.opennms.netmgt.config.destinationPaths.Header;
import org.opennms.netmgt.config.destinationPaths.Path;
import org.opennms.netmgt.config.destinationPaths.Target;
import org.opennms.netmgt.config.notifications.Notification;
import org.opennms.netmgt.config.notifications.Notifications;
import org.opennms.netmgt.provision.persist.requisition.Requisition;
import org.opennms.netmgt.provision.persist.requisition.RequisitionCategory;
import org.opennms.netmgt.provision.persist.requisition.RequisitionCollection;
import org.opennms.netmgt.provision.persist.requisition.RequisitionInterface;
import org.opennms.netmgt.provision.persist.requisition.RequisitionMonitoredService;
import org.opennms.netmgt.provision.persist.requisition.RequisitionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeBasedNotificationGenerator {

    private final static Logger LOGGER = LoggerFactory.getLogger(NodeBasedNotificationGenerator.class);
    private final String DESTINATION_FILE_NAME = "destinationPaths.xml";
    private final String NOTIFICATION_FILE_NAME = "notifications.xml";
    private final String TEAM_PREFIX = "team";
    private final String TRANSPORT_PREFIX = "notify";
    private final String SPLITER = "::";
    private final Joiner JOINER = Joiner.on(SPLITER);

    public void generateNotificationStrategy(File inFile, File outFolder) throws Exception {
        List<Requisition> requisitions = readRequisitonsFromFile(inFile);
        List<Notification> notificationsList = new ArrayList<>();
        LOGGER.debug("Generating raw-notifications for nodes...");
        for (Requisition requisition : requisitions) {
            for (RequisitionNode node : requisition.getNodes()) {
                notificationsList.addAll(generateNotifications(node));
            }
        }
        Map<String, Notification> uniquNotifications = new HashMap<>();
        for (Notification notification : notificationsList) {
            uniquNotifications.put(notification.getName(), notification);
        }
        LOGGER.debug("Deduplicated {} raw-notifications to {} unique notifications", notificationsList.size(), uniquNotifications.size());

        org.opennms.netmgt.config.destinationPaths.Header destHeader = new Header();
        destHeader.setCreated("");
        destHeader.setRev("");
        destHeader.setMstation("");
        DestinationPaths destinationPaths = new DestinationPaths();
        destinationPaths.setHeader(destHeader);
        LOGGER.debug("uniqu Not Values {}", uniquNotifications.values().size());
        for (Path path : generateDestinationPathForNotifications(uniquNotifications.values())) {
            destinationPaths.addPath(path);
        }
        LOGGER.debug("destination paths amount {}", destinationPaths.getPathCollection().size());
        destinationPaths.marshal(new BufferedWriter(new FileWriter(outFolder.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME)));

        org.opennms.netmgt.config.notifications.Header notHeader = new org.opennms.netmgt.config.notifications.Header();
        notHeader.setCreated("");
        notHeader.setRev("");
        notHeader.setMstation("");
        Notifications notifications = new Notifications();
        notifications.setHeader(notHeader);
        for (Entry notification : uniquNotifications.entrySet()) {
            notifications.addNotification((Notification) notification.getValue());
        }
        notifications.marshal(new BufferedWriter(new FileWriter(outFolder.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME)));
    }

    private List<Notification> generateNotifications(RequisitionNode node) {
        List<Notification> notifications = new ArrayList<>();
        //build destination path-id
        Set<String> destinationPathIdParts = new TreeSet<>();
        for (RequisitionCategory category : node.getCategories()) {
            if (category.getName().startsWith(TRANSPORT_PREFIX) || category.getName().startsWith(TEAM_PREFIX)) {
                destinationPathIdParts.add(category.getName());
            }
        }

        String destinatoinPathId = JOINER.join(destinationPathIdParts);
        
        String nodeFilter = "(nodelabel = '" + node.getNodeLabel() + "')";
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (String part : destinationPathIdParts) {
            sb.append("catinc");
            sb.append(part);
            sb.append(" & ");
        }
        String destinationFilter = sb.toString();
        if (destinationFilter.endsWith(" & ")) {
            destinationFilter = destinationFilter.substring(0, destinationFilter.length() - " & ".length());
        }
        destinationFilter = destinationFilter.concat(")");
        if (destinatoinPathId.contains(TEAM_PREFIX) && destinatoinPathId.contains(TRANSPORT_PREFIX)) {
            Notification nodeDown = new Notification();
            nodeDown.setName(destinatoinPathId);
            nodeDown.setStatus("on");
            nodeDown.setUei("uei.opennms.org/nodes/nodeDown");
            nodeDown.setRule(destinationFilter);
            nodeDown.setDestinationPath(destinatoinPathId);
            nodeDown.setSubject("[NODE: %nodelabel] #%noticeid%: %nodelabel% is down.");
            nodeDown.setTextMessage("All services are down on node %nodelabel%.\n"
                    + "\n"
                    + "http://svipcmonitor.corp.local:8980/opennms/element/node.jsp?node=%nodeid%\n"
                    + "http://svipcmonitor.corp.local:8980/opennms/notification/detail.jsp?notice=%noticeid%\n"
                    + "Notified by destination path: " + destinatoinPathId);
            nodeDown.setNumericMessage("#%noticeid%: %nodelabel% is down.");
            notifications.add(nodeDown);

            for (RequisitionInterface requInterface : node.getInterfaces()) {
                for (RequisitionMonitoredService service : requInterface.getMonitoredServices()) {
                    Notification notification = new Notification();

                    notification.setUei("uei.opennms.org/nodes/nodeLostService");
                    notification.setName(node.getNodeLabel() + SPLITER + service.getServiceName() + SPLITER + destinatoinPathId);
                    notification.setDestinationPath(destinatoinPathId);
                    notification.setRule(nodeFilter + " & " + destinationFilter + " & " + "(" + "is" + service.getServiceName() + ")");
                    notification.setStatus("on");
                    notification.setSubject("[SERVICE: %service%(%interface%)] #%noticeid%: %service% on %nodelabel%");
                    notification.setNumericMessage("#%noticeid%: %service% on %nodelabel% is down");
                    notification.setTextMessage("%service% down on node %nodelabel%, %interface% at %time%.\n"
                            + "\n"
                            + "http://svipcmonitor.corp.local:8980/opennms/element/node.jsp?node=%nodeid%\n"
                            + "http://svipcmonitor.corp.local:8980/opennms/notification/detail.jsp?notice=%noticeid%\n"
                            + "Wiki:\n"
                            + "https://srvwiki.corp.local/Services/Monitoring/Opennms/OpenNMS_Services/%service%\n"
                            + "Notified by destination path: " + destinatoinPathId);
                    notifications.add(notification);
                }
            }
        }
        return notifications;
    }

    private List<Path> generateDestinationPathForNotifications(Collection<Notification> notifications) {
        List<Path> paths = new ArrayList<>();
        Set<String> pathIDs = new TreeSet<>();
        for (Notification notification : notifications) {
            pathIDs.add(notification.getDestinationPath());
        }

        for (String pathID : pathIDs) {
            Set<String> transports = new TreeSet<>();
            Set<String> teams = new TreeSet<>();
            String[] parts = pathID.split(SPLITER);
            for (String part : parts) {
                if (part.startsWith(TRANSPORT_PREFIX)) {
                    transports.add(part);
                } else if (part.startsWith(TEAM_PREFIX)) {
                    teams.add(part);
                }
            }
            if (!transports.isEmpty() && !teams.isEmpty()) {
                String transportName = "";
                StringBuilder sb = new StringBuilder();
                for (String transport : transports) {
                    sb.append(transport);
                    sb.append(SPLITER);
                }
                transportName = sb.toString();
                if (transportName.endsWith(SPLITER)) {
                    transportName = transportName.substring(0, transportName.length() - SPLITER.length());
                }

                String teamName = "";
                sb = new StringBuilder();
                for (String team : teams) {
                    sb.append(team);
                    sb.append(SPLITER);
                }
                teamName = sb.toString();
                if (teamName.endsWith(SPLITER)) {
                    teamName = teamName.substring(0, teamName.length() - SPLITER.length());
                }

                Path path = new Path();
                LOGGER.debug(transportName + SPLITER + teamName);
                path.setName(transportName + SPLITER + teamName);
                //TODO notify-strings to command names is not safe
                for (String team : teams) {
                    Target target = new Target();
                    target.setName(team);
                    List temp = new ArrayList<>();
                    if (transportName.equals("notify-sms")) {
                        temp.add("sendSMS");
                        temp.add("javaEmail");
                    } else {
                        temp.add("javaEmail");
                        target.setCommand(temp);
                        target.setCommand(new ArrayList<>(transports));
                    }
                    target.setCommand(temp);
                    path.setInitialDelay("10m");
                    path.addTarget(target);
                }
                paths.add(path);
            } else {
                LOGGER.debug("INCOMPLIED pathID {} transport or team is missing", pathID);
            }
        }
        return paths;
    }

    private List<Requisition> readRequisitonsFromFile(File requisitionsFile) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(RequisitionCollection.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        return (RequisitionCollection) jaxbUnmarshaller.unmarshal(requisitionsFile);
    }
}