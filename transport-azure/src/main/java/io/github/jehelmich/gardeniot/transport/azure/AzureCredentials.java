package io.github.jehelmich.gardeniot.transport.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

/**
 * The process-wide Entra ID credential.
 *
 * <p>{@code DefaultAzureCredential} tries, in order, environment variables, a workload or
 * managed identity, and the developer's Azure CLI / IDE login, so the same binary runs
 * unchanged on a workstation and in a container app with a managed identity. It caches tokens,
 * so one instance is shared by every client in the process.
 */
final class AzureCredentials {

    private static final class Holder {
        static final TokenCredential DEFAULT = new DefaultAzureCredentialBuilder().build();
    }

    private AzureCredentials() {
    }

    static TokenCredential defaultCredential() {
        return Holder.DEFAULT;
    }
}
