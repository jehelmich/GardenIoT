package io.github.jehelmich.gardeniot.transport.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.jehelmich.gardeniot.config.Environment;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureServiceSettingsTest {

    @Test
    void prefersEntraIdWhenHostNamesAreGiven() {
        AzureServiceSettings settings = AzureServiceSettings.fromEnvironment(new Environment(Map.of(
                AzureServiceSettings.HUB_HOSTNAME, "garden.azure-devices.net",
                AzureServiceSettings.EVENTHUB_NAMESPACE, "iothub-ns-garden-1.servicebus.windows.net",
                AzureServiceSettings.EVENTHUB_NAME, "garden")));

        assertThat(settings.usesIdentityForHub()).isTrue();
        assertThat(settings.usesIdentityForEventHub()).isTrue();
        assertThat(settings.consumerGroup()).isEqualTo("$Default");
    }

    @Test
    void fallsBackToConnectionStrings() {
        AzureServiceSettings settings = AzureServiceSettings.fromEnvironment(new Environment(Map.of(
                AzureServiceSettings.SERVICE_CONNECTION_STRING,
                        "HostName=h;SharedAccessKeyName=service;SharedAccessKey=k",
                AzureServiceSettings.EVENTHUB_CONNECTION_STRING, "Endpoint=sb://h/;EntityPath=hub",
                AzureServiceSettings.CONSUMER_GROUP, "controller")));

        assertThat(settings.usesIdentityForHub()).isFalse();
        assertThat(settings.usesIdentityForEventHub()).isFalse();
        assertThat(settings.consumerGroup()).isEqualTo("controller");
    }

    @Test
    void mixesTheTwoWhenOnlyOneSideHasAnIdentity() {
        AzureServiceSettings settings = AzureServiceSettings.fromEnvironment(new Environment(Map.of(
                AzureServiceSettings.HUB_HOSTNAME, "garden.azure-devices.net",
                AzureServiceSettings.EVENTHUB_CONNECTION_STRING, "Endpoint=sb://h/;EntityPath=hub")));

        assertThat(settings.usesIdentityForHub()).isTrue();
        assertThat(settings.usesIdentityForEventHub()).isFalse();
    }

    @Test
    void explainsWhatIsMissing() {
        assertThatIllegalStateException()
                .isThrownBy(() -> AzureServiceSettings.fromEnvironment(new Environment(Map.of())))
                .withMessageContaining(AzureServiceSettings.HUB_HOSTNAME)
                .withMessageContaining(AzureServiceSettings.SERVICE_CONNECTION_STRING);
        assertThatIllegalStateException()
                .isThrownBy(() -> AzureServiceSettings.fromEnvironment(new Environment(Map.of(
                        AzureServiceSettings.HUB_HOSTNAME, "garden.azure-devices.net",
                        AzureServiceSettings.EVENTHUB_NAMESPACE, "ns"))))
                .withMessageContaining(AzureServiceSettings.EVENTHUB_NAME);
    }
}
