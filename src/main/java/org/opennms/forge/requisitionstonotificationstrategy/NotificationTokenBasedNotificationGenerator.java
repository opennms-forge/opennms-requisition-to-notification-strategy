package org.opennms.forge.requisitionstonotificationstrategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;
import org.opennms.netmgt.config.destinationPaths.DestinationPaths;
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
    private final String PREFIX_TEAM;
    private final String PREFIX_TRANSPORT;
    private final String SPLITTER;
    private final String PREFIX_NOTIFICATION_TOKEN;
    private final String PREFIX_GENERATED;
    private final File REQUISITIONS_FILE;
    private final File CONFIG_FOLDER;
    private final Properties PROPERTIES;
    private final String NOTIFICATION_NODE_TEXT = "All services are down on node %nodelabel%.\n" + "Notified by destination path: ";
    private final String NOTIFICATION_SERVICE_TEXT = "%service% down on node %nodelabel%, %interface% at %time%.\n" + "Notified by destination path: ";

    public NotificationTokenBasedNotificationGenerator(File requisitionsFile, File configFolder, Properties properties) {
        this.REQUISITIONS_FILE = requisitionsFile;
        this.CONFIG_FOLDER = configFolder;
        this.PROPERTIES = properties;
        this.PREFIX_GENERATED = PROPERTIES.getProperty(SupportedProperty.prefix_generated.name(), "GEN_");
        this.PREFIX_NOTIFICATION_TOKEN = PROPERTIES.getProperty(SupportedProperty.prefix_notification_token.name(), "NOTIFY");
        this.SPLITTER = PROPERTIES.getProperty(SupportedProperty.splitter.name(), "__");
        this.PREFIX_TRANSPORT = PROPERTIES.getProperty(SupportedProperty.prefix_transport.name(), "notify-");
        this.PREFIX_TEAM = PROPERTIES.getProperty(SupportedProperty.prefix_team.name(), "team-");
    }

    public void generateNotificationStrategy() throws Exception {
        List<Requisition> requisitions = readRequisitonsFromFile(REQUISITIONS_FILE);
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
        Notifications notifications = readExistingNotificatoins(CONFIG_FOLDER);
        notifications = removeGeneratedNotifications(notifications, PREFIX_GENERATED);
        LOGGER.info("Adding {} new generated notifications", uniquNotifications.size());
        for (Entry notification : uniquNotifications.entrySet()) {
            notifications.addNotification((Notification) notification.getValue());
        }
        LOGGER.info("Writing {} Notifications to {}", notifications.getNotificationCollection().size(), CONFIG_FOLDER.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME);
        notifications.marshal(new BufferedWriter(new FileWriter(CONFIG_FOLDER.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME)));

        DestinationPaths destinationPaths = readExistingDestinationPaths(CONFIG_FOLDER);
        destinationPaths = removeGeneratedDestinationPaths(destinationPaths, PREFIX_GENERATED);
        List<Path> generateDestinationPathForNotifications = generateDestinationPathForNotifications(uniquNotifications.values());
        LOGGER.info("Adding {} new generated pathes", generateDestinationPathForNotifications.size());
        for (Path path : generateDestinationPathForNotifications) {
            destinationPaths.addPath(path);
        }
        LOGGER.info("Writing {} Pathes to {}", destinationPaths.getPathCollection().size(), CONFIG_FOLDER.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME);
        destinationPaths.marshal(new BufferedWriter(new FileWriter(CONFIG_FOLDER.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME)));

        LOGGER.info("Updated Notifications and DestinationPath");
    }

    private DestinationPaths readExistingDestinationPaths(File inputFolder) throws FileNotFoundException {
        try {
            DestinationPaths existingDestinationPaths = DestinationPaths.unmarshal(new BufferedReader(new FileReader(inputFolder.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME)));
            return existingDestinationPaths;
        } catch (FileNotFoundException | MarshalException | ValidationException ex) {
            LOGGER.error("Reading the DestinationPaths failed: {}", inputFolder.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME);
            throw new FileNotFoundException("Required configuration file missing: " + inputFolder.getAbsolutePath() + File.separator + DESTINATION_FILE_NAME);
        }
    }

    private DestinationPaths removeGeneratedDestinationPaths(DestinationPaths destinationPaths, String PREFIX_GENERATED) {
        List<Path> pathesToRemove = new ArrayList<>();
        for (Path path : destinationPaths.getPathCollection()) {
            if (path.getName().startsWith(PREFIX_GENERATED)) {
                pathesToRemove.add(path);
            }
        }
        LOGGER.info("Removing {} old generated Pathes", pathesToRemove.size());
        for (Path path : pathesToRemove) {
            destinationPaths.removePath(path);
        }
        return destinationPaths;
    }

    private Notifications readExistingNotificatoins(File inputFolder) throws FileNotFoundException {
        try {
            Notifications existingNotificatoins = Notifications.unmarshal(new BufferedReader(new FileReader(inputFolder.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME)));
            return existingNotificatoins;
        } catch (FileNotFoundException | MarshalException | ValidationException ex) {
            LOGGER.error("Reading the Notifications failed: {}", inputFolder.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME);
            throw new FileNotFoundException("Required configuration file missing: " + inputFolder.getAbsolutePath() + File.separator + NOTIFICATION_FILE_NAME);
        }
    }

    private Notifications removeGeneratedNotifications(Notifications notifications, String PREFIX_GENERATED) {
        List<Notification> notificationsToRemove = new ArrayList<>();
        for (Notification notification : notifications.getNotificationCollection()) {
            if (notification.getName().startsWith(PREFIX_GENERATED)) {
                notificationsToRemove.add(notification);
            }
        }
        LOGGER.info("Removing {} old generated notifications", notificationsToRemove.size());
        for (Notification notification : notificationsToRemove) {
            notifications.removeNotification(notification);
        }
        return notifications;
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
            pathID = pathID.substring(PREFIX_GENERATED.length());
            String[] parts = pathID.split(SPLITTER);
            for (String part : parts) {
                if (part.startsWith(PREFIX_TRANSPORT)) {
                    transports.add(part);
                } else if (part.startsWith(PREFIX_TEAM)) {
                    teams.add(part);
                } else {
                    LOGGER.error("PathID contains a part that is not a team or a transport {}", part);
                }
            }
            if (!transports.isEmpty() && !teams.isEmpty()) {
                LOGGER.debug("transports and teams are not empty on this one {}", pathID);
                StringBuilder sb = new StringBuilder();
                for (String transport : transports) {
                    sb.append(transport);
                    sb.append(SPLITTER);
                }
                String transportName = sb.toString();
                if (transportName.endsWith(SPLITTER)) {
                    transportName = transportName.substring(0, transportName.length() - SPLITTER.length());
                }

                sb = new StringBuilder();
                for (String team : teams) {
                    sb.append(team);
                    sb.append(SPLITTER);
                }
                String teamName = sb.toString();
                if (teamName.endsWith(SPLITTER)) {
                    teamName = teamName.substring(0, teamName.length() - SPLITTER.length());
                }

                Path path = new Path();
                LOGGER.debug("creating path {}", transportName + SPLITTER + teamName);
                path.setName(PREFIX_GENERATED + transportName + SPLITTER + teamName);
                for (String team : teams) {
                    Target target = new Target();
                    target.setName(team);
                    target.setCommand(new ArrayList<>(transports));
                    path.setInitialDelay(PROPERTIES.getProperty(SupportedProperty.notification_delay.name(), "10m"));
                    path.addTarget(target);
                }
                paths.add(path);
            } else {
                LOGGER.debug("Incomplied pathID {} transport or team is missing", pathID);
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
        nodeDown.setName(PREFIX_GENERATED + notificationToken);
        nodeDown.setStatus("on");
        nodeDown.setUei("uei.opennms.org/nodes/nodeDown");
        nodeDown.setRule("catinc" + notificationToken);
        nodeDown.setDestinationPath(PREFIX_GENERATED + destinationPath);
        nodeDown.setSubject("[NODE: %nodelabel] #%noticeid%: %nodelabel% is down.");
        nodeDown.setTextMessage(PROPERTIES.getProperty(SupportedProperty.notification_node_text.name(), NOTIFICATION_NODE_TEXT) + destinationPath);
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
        nodeLostService.setName(PREFIX_GENERATED + notificationToken);
        nodeLostService.setUei("uei.opennms.org/nodes/nodeLostService");
        nodeLostService.setDestinationPath(PREFIX_GENERATED + destinationPath);
        String rule = "is" + getServiceFromNotificationToken(notificationToken) + " & " + "catinc" + notificationToken;
        nodeLostService.setRule(rule);
        nodeLostService.setStatus("on");
        nodeLostService.setSubject("[SERVICE: %service%(%interface%)] #%noticeid%: %service% on %nodelabel%");
        nodeLostService.setNumericMessage("#%noticeid%: %service% on %nodelabel% is down");
        nodeLostService.setTextMessage(PROPERTIES.getProperty(SupportedProperty.notification_service_text.name(), NOTIFICATION_SERVICE_TEXT) + destinationPath);
        LOGGER.debug("NodeLostService {}, {}, {}", nodeLostService.getName(), nodeLostService.getRule(), nodeLostService.getDestinationPath());
        return nodeLostService;
    }
}