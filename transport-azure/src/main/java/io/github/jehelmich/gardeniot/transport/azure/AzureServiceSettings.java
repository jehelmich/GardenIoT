package io.github.jehelmich.gardeniot.transport.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import io.github.jehelmich.gardeniot.config.Environment;
import java.time.Duration;
import java.util.Optional;

/**
 * How the cloud side reaches IoT Hub.
 *
 * <p>Preferred: Microsoft Entra ID. Set the hub host name and the built-in endpoint's namespace
 * and name, and the process authenticates with {@code DefaultAzureCredential} — a managed
 * identity in Azure, the developer's own login on a workstation — so no key is ever handed
 * around. Fallback: the two classic connection strings.
 *
 * @param hubHostName           {@code <hub>.azure-devices.net}, for Entra ID authentication
 * @param eventHubNamespace     fully qualified namespace of the built-in endpoint
 * @param eventHubName          name of the built-in endpoint's event hub
 * @param serviceConnectionString  fallback: shared access policy with {@code service connect}
 * @param eventHubConnectionString fallback: connection string of the built-in endpoint
 * @param consumerGroup         consumer group to read telemetry from
 * @param methodResponseTimeout how long to wait for a device to answer a direct method
 * @param methodConnectTimeout  how long to wait for a device to be reachable
 */
public record AzureServiceSettings(
        Optional<String> hubHostName,
        Optional<String> eventHubNamespace,
        Optional<String> eventHubName,
        Optional<String> serviceConnectionString,
        Optional<String> eventHubConnectionString,
        String consumerGroup,
        Duration methodResponseTimeout,
        Duration methodConnectTimeout) {

    public static final String HUB_HOSTNAME = "AZURE_IOTHUB_HOSTNAME";
    public static final String EVENTHUB_NAMESPACE = "AZURE_EVENTHUB_NAMESPACE";
    public static final String EVENTHUB_NAME = "AZURE_EVENTHUB_NAME";
    public static final String SERVICE_CONNECTION_STRING = "IOTHUB_SERVICE_CONNECTION_STRING";
    public static final String EVENTHUB_CONNECTION_STRING = "EVENTHUB_COMPATIBLE_CONNECTION_STRING";
    public static final String CONSUMER_GROUP = "EVENTHUB_CONSUMER_GROUP";

    public AzureServiceSettings {
        if (hubHostName.isEmpty() && serviceConnectionString.isEmpty()) {
            throw new IllegalStateException("Set " + HUB_HOSTNAME + " (Entra ID) or " + SERVICE_CONNECTION_STRING
                    + " (shared access key) to reach the hub");
        }
        boolean identity = eventHubNamespace.isPresent() && eventHubName.isPresent();
        if (!identity && eventHubConnectionString.isEmpty()) {
            throw new IllegalStateException("Set " + EVENTHUB_NAMESPACE + " and " + EVENTHUB_NAME + " (Entra ID) or "
                    + EVENTHUB_CONNECTION_STRING + " (shared access key) to read telemetry");
        }
    }

    public static AzureServiceSettings fromEnvironment(Environment env) {
        return new AzureServiceSettings(
                optional(env, HUB_HOSTNAME),
                optional(env, EVENTHUB_NAMESPACE),
                optional(env, EVENTHUB_NAME),
                optional(env, SERVICE_CONNECTION_STRING),
                optional(env, EVENTHUB_CONNECTION_STRING),
                env.optional(CONSUMER_GROUP, EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));
    }

    private static Optional<String> optional(Environment env, String name) {
        return Optional.ofNullable(env.optional(name, null));
    }

    /** True when the hub is reached with Entra ID rather than a shared access key. */
    public boolean usesIdentityForHub() {
        return hubHostName.isPresent();
    }

    /** True when the built-in endpoint is read with Entra ID rather than a shared access key. */
    public boolean usesIdentityForEventHub() {
        return eventHubNamespace.isPresent() && eventHubName.isPresent();
    }

    TokenCredential credential() {
        return AzureCredentials.defaultCredential();
    }
}
