package io.github.jehelmich.gardeniot.device;

import io.github.jehelmich.gardeniot.telemetry.Telemetry;
import io.github.jehelmich.gardeniot.transport.CommandHandler;
import io.github.jehelmich.gardeniot.transport.DeviceTransport;
import io.github.jehelmich.gardeniot.transport.DeviceTransportFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An in-memory transport that remembers what a device sent and hands out its command handler. */
final class RecordingTransport implements DeviceTransport, DeviceTransportFactory {

    final List<Telemetry> published = new ArrayList<>();
    final Map<String, Object> state = new LinkedHashMap<>();
    final List<String> connected = new ArrayList<>();
    CommandHandler handler;
    boolean closed;
    RuntimeException failure;

    @Override
    public DeviceTransport connect(String deviceId, CommandHandler handler) {
        connected.add(deviceId);
        this.handler = handler;
        return this;
    }

    @Override
    public synchronized void publish(Telemetry telemetry) {
        if (failure != null) {
            throw failure;
        }
        published.add(telemetry);
    }

    @Override
    public synchronized void reportState(String name, Object value) {
        state.put(name, value);
    }

    @Override
    public void close() {
        closed = true;
    }
}
