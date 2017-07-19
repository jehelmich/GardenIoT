package com.mycompany.app;

import java.io.IOException;
import com.microsoft.azure.eventhubs.*;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceMethod;
import com.microsoft.azure.servicebus.*;

import java.nio.charset.Charset;
import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * Hello world!
 *
 */
public class App 
{
    // TODO: Configure for your personal IoT Hub
    private static String connStr = "Enter your Endpoint String here";
    public static final String iotHubConnectionString = "Enter your connection String here";
    public static final String deviceId = "YourDeviceID;

    public static final Long responseTimeout = TimeUnit.SECONDS.toSeconds(30);
    public static final Long connectTimeout = TimeUnit.SECONDS.toSeconds(5);


    public static void main( String[] args ) throws IOException {
        // Create receivers for partitions 0 and 1.
        EventHubClient client0 = receiveMessages("0");
        EventHubClient client1 = receiveMessages("1");
        EventHubClient client2 = receiveMessages("2");
        EventHubClient client3 = receiveMessages("3");
        System.out.println("Press ENTER to exit.");
        System.in.read();
        try {
            client0.closeSync();
            client1.closeSync();
            client2.closeSync();
            client3.closeSync();
            System.exit(0);
        } catch (ServiceBusException sbe) {
            System.exit(1);
        }
    }


    // Create a receiver on a partition.
    private static EventHubClient receiveMessages(final String partitionId) {
        EventHubClient client = null;
        try {
            client = EventHubClient.createFromConnectionStringSync(connStr);
        } catch (Exception e) {
            System.out.println("Failed to create client: " + e.getMessage());
            System.exit(1);
        }
        try {

            // Create a receiver using the
            // default Event Hubs consumer group
            // that listens for messages from now on.
            client.createReceiver(EventHubClient.DEFAULT_CONSUMER_GROUP_NAME, partitionId, Instant.now())
                    .thenAccept(new Consumer<PartitionReceiver>() {
                        public void accept(PartitionReceiver receiver) {
                            System.out.println("** Created receiver on partition " + partitionId);
                            try {
                                while (true) {
                                    Iterable<EventData> receivedEvents = receiver.receive(100).get();
                                    int batchSize = 0;
                                    if (receivedEvents != null) {
                                        System.out.println("Got some evenst");
                                        for (EventData receivedEvent : receivedEvents) {
                                            System.out.println(String.format("Offset: %s, SeqNo: %s, EnqueueTime: %s",
                                                    receivedEvent.getSystemProperties().getOffset(),
                                                    receivedEvent.getSystemProperties().getSequenceNumber(),
                                                    receivedEvent.getSystemProperties().getEnqueuedTime()));
                                            System.out.println(String.format("| Device ID: %s",
                                                    receivedEvent.getSystemProperties().get("iothub-connection-device-id")));

                                            String s = new String(receivedEvent.getBytes(), Charset.defaultCharset());
                                            Analyser analyser = new Analyser(25.0);
                                            analyser.toWater(s);

                                            System.out.println(String.format("| Message Payload: %s", s));
                                            batchSize++;
                                        }
                                    }
                                    System.out.println(String.format("Partition: %s, ReceivedBatch Size: %s", partitionId, batchSize));
                                }
                            } catch (Exception e) {
                                System.out.println("Failed to receive messages: " + e.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            System.out.println("Failed to create receiver: " + e.getMessage());
        }
        return client;
    }
}
