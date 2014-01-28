package org.opennms.forge.requisitionstonotificationstrategy;

import org.opennms.netmgt.config.destinationPaths.Path;
import org.opennms.netmgt.config.destinationPaths.Target;
import org.opennms.netmgt.config.notifications.Notification;

import java.util.ArrayList;
import java.util.Set;

public class NotifyPice {

    private final String SPLITTER = "::";
    private final Set<String> transport;
    private final Set<String> teams;
    private final Set<String> transportOverlay;

    public NotifyPice(Set<String> transport, Set<String> teams) {
        this.transport = transport;
        this.teams = teams;
        if (transport.contains("notify-sms")) {
            transportOverlay = transport;
            transportOverlay.add("notify-mail");
        } else {
            transportOverlay = transport;
        }
    }

    public Path generatePath() {
        Path path = new Path();
        path.setName(this.generateId());
        Target target;
        for (String team : teams) {
            target = new Target();
            target.setName(team);
            target.setCommand(new ArrayList<>(transportOverlay));
            path.addTarget(target);
        }
        return path;
    }

    public Notification generateNodeNotification() {
        Notification notification = new Notification();
        notification.setName(this.generateId());
        notification.setStatus("on");
        notification.setUei("uei.opennms.org/nodes/nodeDown");
        notification.setRule(this.generateNotificationRule());
        notification.setDestinationPath(this.generateId());
        notification.setSubject("[NODE: %nodelabel] #%noticeid%: %nodelabel% is down.");
        notification.setTextMessage("All services are down on node %nodelabel%.\n" +
                "\n" +
                "  http://svipcmonitor.corp.local:8980/opennms/element/node.jsp?node=%nodeid%\n" +
                "  http://svipcmonitor.corp.local:8980/opennms/notification/detail.jsp?notice=%noticeid%\n" +
                "  Notified by destination path: " + this.generateId());
        notification.setNumericMessage("#%noticeid%: %nodelabel% is down.");
        return notification;
    }

    Notification generateServiceNotification(Set<String> services) {
        Notification notification = new Notification();
        notification.setName(this.generateId(services));
        notification.setStatus("on");
        notification.setUei("uei.opennms.org/nodes/nodeLostService");
        notification.setRule(this.generateNotificationRule(services));
        notification.setDestinationPath(this.generateId());
        notification.setSubject("[SERVICE: %service%(%interface%)] #%noticeid%: %service% on %nodelabel%");
        notification.setTextMessage("%service% down on node %nodelabel%, %interface% at %time%.\n" +
                "\n" +
                "    http://svipcmonitor.corp.local:8980/opennms/element/node.jsp?node=%nodeid%\n" +
                "    http://svipcmonitor.corp.local:8980/opennms/notification/detail.jsp?notice=%noticeid%\n" +
                "    Wiki:\n" +
                "    https://srvwiki.corp.local/Services/Monitoring/Opennms/OpenNMS_Services/%service%\n" +
                "    Notified by destination path: " + this.generateId());
        notification.setNumericMessage("#%noticeid%: %service% on %nodelabel% is down");
        return notification;
    }

    public String generateId() {
        StringBuilder sb = new StringBuilder();
        for (String send : transport) {
            sb.append(send);
            sb.append(SPLITTER);
        }
        for (String team : teams) {
            sb.append(team);
            sb.append(SPLITTER);
        }
        String result = sb.toString();
        if (result.endsWith(SPLITTER)) {
            result = result.substring(0, result.length() - SPLITTER.length());
        }
        return result;
    }

    @Override
    public String toString() {
        return "NotifyPice{sends=" + transport + ", teams=" + teams + '}';
    }

    private String generateNotificationRule() {
        String result;
        StringBuilder sb = new StringBuilder();
        String[] categories = this.generateId().split("::");
        sb.append("(");
        for (String category : categories) {
            sb.append("catinc");
            sb.append(category);
            sb.append(" & ");
        }
        result = sb.toString();
        if (result.endsWith(" & ")) {
            result = result.substring(0, result.length() - " & ".length());
        }
        result = result + ")";
        return result;
    }

    private String generateNotificationRule(Set<String> services) {
        String result = "";
        StringBuilder sb = new StringBuilder(this.generateNotificationRule());
        sb.append(" & (is");
        for (String service : services) {
            sb.append(service);
            sb.append(" | is");
        }

        result = sb.toString();
        if (result.endsWith(" | is")) {
            result = result.substring(0, result.length() - " | is".length());
        }
        result = result + ")";
        return result;
    }

    private String generateId(Set<String> services) {
        String result = "";
        StringBuilder sb = new StringBuilder(this.generateId());
        sb.append(SPLITTER);
        for (String service : services) {
            sb.append(service);
            sb.append(SPLITTER);
        }

        result = sb.toString();
        if (result.endsWith(SPLITTER)) {
            result = result.substring(0, result.length() - SPLITTER.length());
        }
        return result;
    }

}
