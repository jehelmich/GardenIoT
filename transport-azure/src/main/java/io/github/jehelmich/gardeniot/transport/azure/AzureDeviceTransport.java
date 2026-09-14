package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodPayload;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodResponse;
import com.microsoft.azure.sdk.iot.device.twin.TwinCollection;
import io.github.jehelmich.gardeniot.telemetry.Json;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * A device's connection to IoT Hub: telemetry as device-to-cloud messages, commands as direct
 * methods, state as reported twin properties.
 */
public final class AzureDeviceTransport implements DeviceTransport {

    /** Above this the message is flagged so a hub route can pick it out without parsing the body. */
    static final double TEMPERATURE_ALERT_THRESHOLD = 30.0;

    private static final Logger log = LoggerFactory.getLogger(AzureDeviceTransport.class);

    private final DeviceClient client;
    private final String deviceId;

    private AzureDeviceTransport(DeviceClient client, String deviceId) {
        this.client = client;
        this.deviceId = deviceId;
    }

    static AzureDeviceTransport open(DeviceClient client, String deviceId, CommandHandler handler)
            throws IotHubClientException, InterruptedException {
        client.setConnectionStatusChangeCallback(change -> log.info("{}: connection {} ({})",
                deviceId, change.getNewStatus(), change.getNewStatusReason()), null);
        // Retries with exponential back-off until the hub is reachable, so a device that boots
        // before its network is up still comes online.
        client.open(true);
        client.subscribeToMethods((method, payload, context) -> toDirectMethodResponse(
                handler.handle(method, toJson(payload))), null);
        return new AzureDeviceTransport(client, deviceId);
    }

    @Override
    public void publish(Telemetry telemetry) throws IotHubClientException, InterruptedException {
        client.sendEvent(toMessage(telemetry));
    }

    @Override
    public void reportState(String name, Object value) throws IotHubClientException, InterruptedException {
        // The twin holds JSON; pass Gson's tree so nested objects survive the SDK's own serialiser.
        client.updateReportedProperties(new TwinCollection(Map.of(name, Json.gson().toJsonTree(value))));
    }

    @Override
    public void close() {
        client.close();
    }

    static Message toMessage(Telemetry telemetry) {
        Message message = new Message(TelemetryCodec.toJson(telemetry));
        message.setMessageId(UUID.randomUUID().toString());
        message.setContentType("application/json");
        message.setContentEncoding("utf-8");
        message.setProperty("temperatureAlert",
                Boolean.toString(telemetry.temperature() > TEMPERATURE_ALERT_THRESHOLD));
        return message;
    }

    private static String toJson(DirectMethodPayload payload) {
        return payload == null ? null : payload.getPayloadAsJsonString();
    }

    private static DirectMethodResponse toDirectMethodResponse(CommandResult result) {
        return new DirectMethodResponse(result.status(), result.payload());
    }
}
