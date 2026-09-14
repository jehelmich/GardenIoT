package io.github.jehelmich.gardeniot.transport.mqtt;

/**
 * The topic layout, with IoT Hub's concepts mapped onto plain MQTT 5:
 *
 * <pre>
 * {prefix}/{deviceId}/telemetry         device-to-cloud messages
 * {prefix}/{deviceId}/state/{name}      reported state, retained (the "device twin")
 * {prefix}/{deviceId}/status            "online" / "offline", retained, set by a last will
 * {prefix}/{deviceId}/alert/{alert}     what the controller thinks is wrong, retained
 * {prefix}/{deviceId}/cmd/{command}     command requests ("direct methods"), answered on the
 *                                       request's MQTT 5 response topic with its correlation data
 * {prefix}/_fleet/cmd/{command}         commands to whichever device process picks them up
 *                                       (a shared subscription)
 * {prefix}/_reply/{clientId}            where a cloud-side client receives command replies
 * </pre>
 */
public record MqttTopics(String prefix) {

    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";

    public String telemetry(String deviceId) {
        return prefix + "/" + deviceId + "/telemetry";
    }

    public String allTelemetry() {
        return prefix + "/+/telemetry";
    }

    public String state(String deviceId, String name) {
        return prefix + "/" + deviceId + "/state/" + name;
    }

    public String allState() {
        return prefix + "/+/state/+";
    }

    public String status(String deviceId) {
        return prefix + "/" + deviceId + "/status";
    }

    public String allStatus() {
        return prefix + "/+/status";
    }

    public String alert(String deviceId, String alert) {
        return prefix + "/" + deviceId + "/alert/" + alert;
    }

    public String allAlerts() {
        return prefix + "/+/alert/+";
    }

    public String command(String deviceId, String command) {
        return prefix + "/" + deviceId + "/cmd/" + command;
    }

    public String deviceCommands(String deviceId) {
        return prefix + "/" + deviceId + "/cmd/+";
    }

    public String allCommands() {
        return prefix + "/+/cmd/+";
    }

    public String fleetCommand(String command) {
        return prefix + "/_fleet/cmd/" + command;
    }

    /** MQTT 5 shared subscription: the broker hands each message to one member of the group. */
    public String fleetCommandsShared() {
        return "$share/fleet/" + prefix + "/_fleet/cmd/+";
    }

    public String reply(String clientId) {
        return prefix + "/_reply/" + clientId;
    }

    /** The device id of a per-device topic, or {@code null} if the topic is not one. */
    public String deviceIdOf(String topic) {
        String[] segments = topic.split("/");
        if (segments.length < 3 || !segments[0].equals(prefix) || segments[1].startsWith("_")) {
            return null;
        }
        return segments[1];
    }

    /** The last segment of a topic — the command name, the state name. */
    public static String lastSegment(String topic) {
        return topic.substring(topic.lastIndexOf('/') + 1);
    }
}
