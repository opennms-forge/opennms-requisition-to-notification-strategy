package org.opennms.forge.requisitionstonotificationstrategy;

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
import org.opennms.netmgt.provision.persist.requisition.RequisitionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationTokenBasedNotificationGenerator {

    private final static Logger LOGGER = LoggerFactory.getLogger(NotificationTokenBasedNotificationGenerator.class);
    private final String DESTINATION_FILE_NAME = "destinationPaths.xml";
    private final String NOTIFICATION_FILE_NAME = "notifications.xml";
    private final String PREFIX_TEAM = "team";
    private final String PREFIX_TRANSPORT = "send";
    private final String SPLITTER = "::";
    private final String PREFIX_NOTIFICATION_TOKEN = "NOTIFY";

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

        org.opennms.netmgt.config.notifications.Header notHeader = new org.opennms.netmgt.config.notifications.Header();
        notHeader.setCreated("");
        notHeader.setRev("");
        notHeader.setMstation("");
        Notifications notifications = new Notifications();
        notifications.setHeader(notHeader);
        for (Entry notification : uniquNotifications.entrySet()) {
            notifications.addNotification((Notification) notification.getValue());
        }
        LOGGER.debug("Writing {} to {}", notificationsList.size(), outFolder.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME);
        notifications.marshal(new BufferedWriter(new FileWriter(outFolder.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME)));

        org.opennms.netmgt.config.destinationPaths.Header destHeader = new Header();
        destHeader.setCreated("");
        destHeader.setRev("");
        destHeader.setMstation("");
        DestinationPaths destinationPaths = new DestinationPaths();
        destinationPaths.setHeader(destHeader);
        for (Path path : generateDestinationPathForNotifications(uniquNotifications.values())) {
            destinationPaths.addPath(path);
        }
        LOGGER.debug("destination paths amount {}", destinationPaths.getPathCollection().size());
        destinationPaths.marshal(new BufferedWriter(new FileWriter(outFolder.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME)));

    }

    private List<Notification> generateNotifications(RequisitionNode node) {
        List<Notification> notifications = new ArrayList<>();
        Set<String> notificationTokens = new TreeSet<>();
        for (RequisitionCategory category : node.getCategories()) {
            if (category.getName().startsWith(PREFIX_NOTIFICATION_TOKEN)) {
                LOGGER.debug("found notification_token {}", category.getName());
                notificationTokens.add(category.getName());
            }
        }

        for (String notificationToken : notificationTokens) {
            if (notificationToken.startsWith(PREFIX_NOTIFICATION_TOKEN + SPLITTER + PREFIX_TRANSPORT)) {
                LOGGER.debug("Building NodeDownNotification for {}", notificationToken);
                notifications.add(generateNodeDownNotification(notificationToken));
            } else {
                LOGGER.debug("Building NodeLostServie for {}", notificationToken);
                notifications.add(generateNodeLostServiceNotification(notificationToken));
            }
        }
        return notifications;
    }

    private List<Path> generateDestinationPathForNotifications(Collection<Notification> notifications) {
        List<Path> paths = new ArrayList<>();
        Set<String> pathIDs = new TreeSet<>();
        for (Notification notification : notifications) {
            LOGGER.debug("get destinationpath from {} for {}", notification.getName(), notification.getDestinationPath());
            pathIDs.add(notification.getDestinationPath());
        }

        LOGGER.debug("Builder PathIDs");
        for (String pathID : pathIDs) {
            Set<String> transports = new TreeSet<>();
            Set<String> teams = new TreeSet<>();
            String[] parts = pathID.split(SPLITTER);
            for (String part : parts) {
                if (part.startsWith(PREFIX_TRANSPORT)) {
                    transports.add(part);
                } else if (part.startsWith(PREFIX_TEAM)) {
                    teams.add(part);
                }
            }
            if (!transports.isEmpty() && !teams.isEmpty()) {
                LOGGER.debug("transports and teams are not empty on this one {}", pathID);
                String transportName = "";
                StringBuilder sb = new StringBuilder();
                for (String transport : transports) {
                    sb.append(transport);
                    sb.append(SPLITTER);
                }
                transportName = sb.toString();
                if (transportName.endsWith(SPLITTER)) {
                    transportName = transportName.substring(0, transportName.length() - SPLITTER.length());
                }

                String teamName = "";
                sb = new StringBuilder();
                for (String team : teams) {
                    sb.append(team);
                    sb.append(SPLITTER);
                }
                teamName = sb.toString();
                if (teamName.endsWith(SPLITTER)) {
                    teamName = teamName.substring(0, teamName.length() - SPLITTER.length());
                }

                Path path = new Path();
                LOGGER.debug(PREFIX_NOTIFICATION_TOKEN + SPLITTER + transportName + SPLITTER + teamName);
                path.setName(PREFIX_NOTIFICATION_TOKEN + SPLITTER + transportName + SPLITTER + teamName);
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

    private Notification generateNodeDownNotification(String notificationToken) {
        String destinationPath = getDestinationPathFromNotificationToken(notificationToken);
        Notification nodeDown = new Notification();
        nodeDown.setName(notificationToken);
        nodeDown.setStatus("on");
        nodeDown.setUei("uei.opennms.org/nodes/nodeDown");
        nodeDown.setRule("catinc" + notificationToken);
        nodeDown.setDestinationPath(destinationPath);
        nodeDown.setSubject("[NODE: %nodelabel] #%noticeid%: %nodelabel% is down.");
        nodeDown.setTextMessage("All services are down on node %nodelabel%.\n"
                + "\n"
                + "http://svipcmonitor.corp.local:8980/opennms/element/node.jsp?node=%nodeid%\n"
                + "http://svipcmonitor.corp.local:8980/opennms/notification/detail.jsp?notice=%noticeid%\n"
                + "Notified by destination path: " + destinationPath);
        nodeDown.setNumericMessage("#%noticeid%: %nodelabel% is down.");
        LOGGER.debug("NodeDown {}, {}, {}", nodeDown.getName(), nodeDown.getRule(), nodeDown.getDestinationPath());
        return nodeDown;
    }

    private String getDestinationPathFromNotificationToken(String notificationToken) {
        if (notificationToken.startsWith(PREFIX_NOTIFICATION_TOKEN + SPLITTER + PREFIX_TRANSPORT)) {
            return notificationToken.substring((PREFIX_NOTIFICATION_TOKEN + SPLITTER).length(), notificationToken.length());
        } else {
            String destinationPath = notificationToken.substring((PREFIX_NOTIFICATION_TOKEN + SPLITTER).length(), notificationToken.length());
            destinationPath = destinationPath.substring(destinationPath.indexOf(SPLITTER) + SPLITTER.length());
            return destinationPath;
        }
    }

    private String getServiceFromNotificationToken(String notificationToken) {
        if (notificationToken.startsWith(PREFIX_NOTIFICATION_TOKEN + SPLITTER + PREFIX_TRANSPORT)) {
            return null;
        }
        String[] notificationTokenPices = notificationToken.split(SPLITTER);
        return notificationTokenPices[1];
    }

    private Notification generateNodeLostServiceNotification(String notificationToken) {
        String destinationPath = getDestinationPathFromNotificationToken(notificationToken);

        Notification nodeLostService = new Notification();
        nodeLostService.setName(notificationToken);
        nodeLostService.setUei("uei.opennms.org/nodes/nodeLostService");
        nodeLostService.setDestinationPath(destinationPath);
        String rule = "is" + getServiceFromNotificationToken(notificationToken) + " & " + "catinc" + destinationPath;
        nodeLostService.setRule(rule);
        nodeLostService.setStatus("on");
        nodeLostService.setSubject("[SERVICE: %service%(%interface%)] #%noticeid%: %service% on %nodelabel%");
        nodeLostService.setNumericMessage("#%noticeid%: %service% on %nodelabel% is down");
        nodeLostService.setTextMessage("%service% down on node %nodelabel%, %interface% at %time%.\n"
                + "\n"
                + "http://svipcmonitor.corp.local:8980/opennms/element/node.jsp?node=%nodeid%\n"
                + "http://svipcmonitor.corp.local:8980/opennms/notification/detail.jsp?notice=%noticeid%\n"
                + "Wiki:\n"
                + "https://srvwiki.corp.local/Services/Monitoring/Opennms/OpenNMS_Services/%service%\n"
                + "Notified by destination path: " + destinationPath);

        LOGGER.debug("NodeLostService {}, {}, {}", nodeLostService.getName(), nodeLostService.getRule(), nodeLostService.getDestinationPath());
        return nodeLostService;
    }
}
