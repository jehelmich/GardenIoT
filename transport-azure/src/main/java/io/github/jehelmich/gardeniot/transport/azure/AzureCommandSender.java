package io.github.jehelmich.gardeniot.transport.azure;

import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodRequestOptions;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodResponse;
import com.microsoft.azure.sdk.iot.service.methods.DirectMethodsClient;
import io.github.jehelmich.gardeniot.transport.CommandException;
import io.github.jehelmich.gardeniot.transport.CommandResult;
import io.github.jehelmich.gardeniot.transport.DeviceCommandSender;

import java.io.IOException;

/** Sends commands as IoT Hub direct methods. */
public final class AzureCommandSender implements DeviceCommandSender {

    private final DirectMethodsClient client;
    private final AzureServiceSettings settings;

    public AzureCommandSender(AzureServiceSettings settings) {
        this.settings = settings;
        this.client = settings.usesIdentityForHub()
                ? new DirectMethodsClient(settings.hubHostName().orElseThrow(), settings.credential())
                : new DirectMethodsClient(settings.serviceConnectionString().orElseThrow());
    }

    @Override
    public CommandResult send(String deviceId, String command, Object payload) throws CommandException {
        DirectMethodRequestOptions.DirectMethodRequestOptionsBuilder options = DirectMethodRequestOptions.builder()
                .methodResponseTimeoutSeconds((int) settings.methodResponseTimeout().toSeconds())
                .methodConnectTimeoutSeconds((int) settings.methodConnectTimeout().toSeconds());
        if (payload != null) {
            options.payload(payload);
        }
        try {
            DirectMethodResponse response = client.invoke(deviceId, command, options.build());
            Integer status = response.getStatus();
            return new CommandResult(status == null ? CommandResult.FAILED : status, response.getPayloadAsJsonElement());
        } catch (IotHubException | IOException e) {
            throw new CommandException("Could not invoke '" + command + "' on " + deviceId + ": " + e.getMessage(), e);
        }
    }
}
