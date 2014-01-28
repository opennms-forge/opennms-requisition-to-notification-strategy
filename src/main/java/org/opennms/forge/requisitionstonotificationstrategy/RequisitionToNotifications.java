package org.opennms.forge.requisitionstonotificationstrategy;

import com.google.common.collect.Sets;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.opennms.netmgt.config.destinationPaths.DestinationPaths;
import org.opennms.netmgt.config.destinationPaths.Header;
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

public class RequisitionToNotifications {

    private final static Logger LOGGER = LoggerFactory.getLogger(RequisitionToNotifications.class);
    private List<Requisition> requisitions;
    private final File requisitionsFile = new File("/tmp/A.xml");
    private final File destinationPathsFile = new File("/tmp/destinationPaths.xml");
    private final File notificationsFile = new File("/tmp/notifications.xml");
    private final String TEAM_PREFIX = "team";
    private final String TRANSPORT_PREFIX = "notify";
    private final String SPLITER = "::";

    public List<Notification> gernerateNodeServiceNotificatoins(RequisitionNode node) {
        List<Notification> notifications = new ArrayList<>();
        //build destination path-id
        Set<String> destinationPathIdParts = new TreeSet<>();
        for (RequisitionCategory category : node.getCategories()) {
            if (category.getName().startsWith(TRANSPORT_PREFIX) || category.getName().startsWith(TEAM_PREFIX)) {
                destinationPathIdParts.add(category.getName());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String part : destinationPathIdParts) {
            sb.append(part);
            sb.append(SPLITER);
        }
        String destinatoinPathId = sb.toString();
        if (destinatoinPathId.endsWith(SPLITER)) {
            destinatoinPathId = destinatoinPathId.substring(0, destinatoinPathId.length() - SPLITER.length());
        }
        String nodeFilter = "(nodelabel = '" + node.getNodeLabel() + "')";
        sb = new StringBuilder();
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
        for (RequisitionInterface requInterface : node.getInterfaces()) {
            for (RequisitionMonitoredService service : requInterface.getMonitoredServices()) {
                Notification notification = new Notification();

                notification.setUei("uei.opennms.org/nodes/nodeLostService");
                notification.setName(node.getNodeLabel() + SPLITER + service.getServiceName() + SPLITER + destinatoinPathId);
                notification.setDestinationPath(destinatoinPathId);
                notification.setRule(nodeFilter + " & " + destinationFilter + " & " + "(" + "is" + service.getServiceName() + ")");
                notification.setStatus("on");
                notification.setTextMessage("For later use");

                notifications.add(notification);
            }
        }
        return notifications;
    }

    public void runRequisition() throws Exception {
        requisitions = readRequisitonsFromFile(requisitionsFile);

        LOGGER.debug("TEAM");
//        Set<Set<String>> teamCombinations = getCategoryPermutations(TEAM_PREFIX, requisitions);
//        LOGGER.debug("Amount of teamCombinatoins is {}", teamCombinations.size());
        Set<String> teams = new TreeSet<>();
        for (Requisition requisition : requisitions) {
            for (RequisitionNode node : requisition.getNodes()) {
                for (RequisitionCategory category : node.getCategories()) {
                    if (category.getName().startsWith(TEAM_PREFIX)) {
                        teams.add(category.getName());
                    }
                }
            }
        }
//        for (Set<String> combinations : teamCombinations) {
//            LOGGER.debug("---next---");
//            for (String combinatoin : combinations) {
//                LOGGER.debug(combinatoin);
//            }
//        }

        LOGGER.debug("Transports");
        Set<String> transports = new TreeSet<>();
        for (Requisition requisition : requisitions) {
            for (RequisitionNode node : requisition.getNodes()) {
                for (RequisitionCategory category : node.getCategories()) {
                    if (category.getName().startsWith(TRANSPORT_PREFIX)) {
                        transports.add(category.getName());
                    }
                }
            }
        }
        LOGGER.debug("Amount of transports is {}", transports.size());
        for (String transport : transports) {
            LOGGER.debug(transport);
        }

        Set<String> services = getServices(requisitions);
        List<NotifyPice> notifyPices = new ArrayList<>();
        for (String transport : transports) {
            for (String team : teams) {
                if (!team.isEmpty()) {
                    Set<String> transportSet = new TreeSet<>();
                    transportSet.add(transport);

                    Set<String> teamSet = new TreeSet<>();
                    teamSet.add(team);

                    notifyPices.add(new NotifyPice(transportSet, teamSet));
                }
            }
        }

        org.opennms.netmgt.config.destinationPaths.Header destHeader = new Header();
        destHeader.setCreated("");
        destHeader.setRev("");
        destHeader.setMstation("");
        DestinationPaths destinationPaths = new DestinationPaths();
        destinationPaths.setHeader(destHeader);

        org.opennms.netmgt.config.notifications.Header notHeader = new org.opennms.netmgt.config.notifications.Header();
        notHeader.setCreated("");
        notHeader.setRev("");
        notHeader.setMstation("");
        Notifications notifications = new Notifications();

        notifications.setHeader(notHeader);
        for (NotifyPice notifyPice : notifyPices) {
            LOGGER.debug("notifyPiceId {}", notifyPice.toString());
            LOGGER.debug("notifyPiceId {}", notifyPice.generateId());
            LOGGER.debug("");
            notifications.addNotification(notifyPice.generateNodeNotification());
//            notifications.addNotification(notifyPice.generateServiceNotification(services));
            destinationPaths.addPath(notifyPice.generatePath());
        }

        for (Requisition requisition : requisitions) {
            for (RequisitionNode node : requisition.getNodes()) {
                for (Notification notification : this.gernerateNodeServiceNotificatoins(node)) {
                    if (notification != null) {
                        notifications.addNotification(notification);
                    }
                }
            }
        }
        destinationPaths.marshal(new BufferedWriter(new FileWriter(destinationPathsFile)));
        notifications.marshal(new BufferedWriter(new FileWriter(notificationsFile)));
    }

    private Set<String> getServices(List<Requisition> requisitions) {
        Set<String> services = new TreeSet<>();
        for (Requisition requisition : requisitions) {
            for (RequisitionNode node : requisition.getNodes()) {
                for (RequisitionInterface reqInterface : node.getInterfaces()) {
                    for (RequisitionMonitoredService service : reqInterface.getMonitoredServices()) {
                        services.add(service.getServiceName());
                    }
                }
            }
        }
        return services;
    }

    private Set<Set<String>> getCategoryPermutations(String prefix, List<Requisition> requisitions) {
        Set<String> relevantCategories = new TreeSet<>();
        for (Requisition requisition : requisitions) {
            for (RequisitionNode node : requisition.getNodes()) {
                for (RequisitionCategory category : node.getCategories()) {
                    if (category.getName().startsWith(prefix)) {
                        relevantCategories.add(category.getName());
                    }
                }
            }
        }
        Set<Set<String>> permutations = Sets.powerSet(relevantCategories);
        return permutations;
    }

    public List<Requisition> readRequisitonsFromFile(File requisitionsFile) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(RequisitionCollection.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        return (RequisitionCollection) jaxbUnmarshaller.unmarshal(requisitionsFile);
    }
}
