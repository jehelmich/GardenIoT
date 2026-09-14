package io.github.jehelmich.gardeniot.controller;

import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodRequestOptions;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodResponse;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/** Waters a device by invoking its {@code water} direct method through IoT Hub. */
final class DirectMethodWateringActuator implements WateringActuator {

    static final String METHOD_NAME = "water";

    private static final Logger log = LoggerFactory.getLogger(DirectMethodWateringActuator.class);

    private final DirectMethodsClient client;
    private final DirectMethodRequestOptions options;

    DirectMethodWateringActuator(DirectMethodsClient client,
                                 Duration responseTimeout,
                                 Duration connectTimeout) {
        this.client = client;
        this.options = DirectMethodRequestOptions.builder()
                .methodResponseTimeoutSeconds((int) responseTimeout.toSeconds())
                .methodConnectTimeoutSeconds((int) connectTimeout.toSeconds())
                .build();
    }

    @Override
    public void water(String deviceId) {
        log.info("Invoking '{}' on device '{}'", METHOD_NAME, deviceId);
        DirectMethodResponse response;
        try {
            response = client.invoke(deviceId, METHOD_NAME, options);
        } catch (IotHubException | IOException e) {
            throw new ActuationException("Could not invoke '" + METHOD_NAME + "' on " + deviceId, e);
        }
        Integer status = response.getStatus();
        if (status == null || status < 200 || status >= 300) {
            throw new ActuationException("Device " + deviceId + " answered '" + METHOD_NAME
                    + "' with status " + status + ": " + response.getPayloadAsJsonString());
        }
        log.info("Device '{}' answered {} {}", deviceId, status, response.getPayloadAsJsonString());
    }
}
