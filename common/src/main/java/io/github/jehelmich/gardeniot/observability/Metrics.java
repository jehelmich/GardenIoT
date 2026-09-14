package io.github.jehelmich.gardeniot.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The process's meter registry, with the per-device gauges the applications need.
 *
 * <p>Micrometer holds gauges weakly, so the values are kept here, keyed by meter name and
 * device, for as long as the device is known.
 */
public final class Metrics {

    public static final String DEVICE_TAG = "device";

    private final PrometheusMeterRegistry registry;
    private final Map<String, MutableDouble> gauges = new ConcurrentHashMap<>();

    public Metrics() {
        this(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }

    Metrics(PrometheusMeterRegistry registry) {
        this.registry = registry;
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
    }

    public MeterRegistry registry() {
        return registry;
    }

    /** The Prometheus exposition of every meter. */
    public String scrape() {
        return registry.scrape();
    }

    public Counter counter(String name, String description, String... tags) {
        return Counter.builder(name).description(description).tags(tags).register(registry);
    }

    /** Sets a per-device gauge, registering it on first use. */
    public void gauge(String name, String description, String deviceId, double value) {
        gauges.computeIfAbsent(name + "|" + deviceId, key -> {
                    MutableDouble holder = new MutableDouble();
                    Gauge.builder(name, holder, MutableDouble::doubleValue)
                            .description(description)
                            .tags(Tags.of(DEVICE_TAG, deviceId))
                            .register(registry);
                    return holder;
                })
                .set(value);
    }

    /** Drops a device's gauges, for a plant that has been removed. */
    public void forget(String deviceId) {
        gauges.keySet().removeIf(key -> key.endsWith("|" + deviceId));
        registry.getMeters().stream()
                .filter(meter -> deviceId.equals(meter.getId().getTag(DEVICE_TAG)))
                .toList()
                .forEach(registry::remove);
    }

    private static final class MutableDouble extends Number {
        private static final long serialVersionUID = 1L;
        private volatile double value;

        void set(double newValue) {
            value = newValue;
        }

        @Override
        public double doubleValue() {
            return value;
        }

        @Override
        public float floatValue() {
            return (float) value;
        }

        @Override
        public long longValue() {
            return (long) value;
        }

        @Override
        public int intValue() {
            return (int) value;
        }
    }
}
