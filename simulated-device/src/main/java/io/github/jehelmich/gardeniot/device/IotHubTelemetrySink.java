package io.github.jehelmich.gardeniot.device;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.telemetry.TelemetryCodec;

import java.util.UUID;

/** Publishes readings as device-to-cloud messages. */
final class IotHubTelemetrySink implements TelemetrySink {

    /** Above this the message is flagged so that a hub route can pick it out without parsing the body. */
    static final double TEMPERATURE_ALERT_THRESHOLD = 30.0;

    private final DeviceClient client;

    IotHubTelemetrySink(DeviceClient client) {
        this.client = client;
    }

    @Override
    public void publish(Telemetry telemetry) throws IotHubClientException, InterruptedException {
        client.sendEvent(toMessage(telemetry));
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
}
