/*
 * Copyright 2011 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.server.metrics;

import com.janrain.backplane.server.BackplaneController;
import com.janrain.backplane.server.BackplaneServerException;
import com.janrain.backplane.server.config.BackplaneConfig;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.*;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.*;

/**
 * Retrieves metrics stored in SimpleDB
 *
 * @author Tom Raney
 */

@Service(value="metricAccumulator")
@Scope(value = "singleton")
public class MetricsAccumulator {

    private static final Logger logger = Logger.getLogger(MetricsAccumulator.class);
    private static final String instanceUuid = UUID.randomUUID().toString();

    /**
     * Each server instance has its own id
     * @return
     */
    public static String getInstanceUuid() {
        return instanceUuid;
    }


    /**
     * Prepare MetricMessage object from current data
     */
    public MetricMessage prepareSummary() throws BackplaneServerException {
        try {
            return new MetricMessage(instanceUuid, BackplaneConfig.ISO8601.format(new Date()), toJson(true));
        } catch (Exception e) {
            throw new BackplaneServerException(e.getMessage());
        }
    }

     public String toJson(boolean includeJvmStats) throws Exception {

        Map<String,Object> parent = new HashMap<String, Object>();

        for (Map.Entry<MetricName, Metric> entry: Metrics.allMetrics().entrySet()) {

            final String name = entry.getKey().getName();
            final Metric metric = entry.getValue();

            if ( metric instanceof MeterMetric) {
                parent.put(name, outputMeterMetric((MeterMetric) metric));
            } else if ( metric instanceof HistogramMetric) {
                parent.put(name, outputHistogram((HistogramMetric) metric));
            } else if ( metric instanceof TimerMetric) {
                parent.put(name, outputTimerMetric((TimerMetric) metric));
            }

            if (includeJvmStats) {
                parent.put("jvm", outputJVMUsage());
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(parent);
        } catch (IOException e) {
            String errMsg = "Error converting frames to JSON: " + e.getMessage();
            throw new Exception(errMsg, e);
        }
    }

    private Map<String, Object> outputMeterMetric(Metered mm) {

        Map<String,Object> out = new LinkedHashMap<String, Object>();

        out.put("type", "meter");
        out.put("event_type", mm.eventType());
        out.put("unit", mm.rateUnit().toString().toLowerCase());
        out.put("count", mm.count());
        out.put("mean", String.format("%2.2f", mm.meanRate()));
        out.put("m1", String.format("%2.2f", mm.oneMinuteRate()));
        out.put("m5", String.format("%2.2f", mm.fiveMinuteRate()));
        out.put("m15", String.format("%2.2f", mm.fifteenMinuteRate()));

        return out;

    }

    private Map<String, Object> outputHistogram(HistogramMetric histogram) {

        Map<String,Object> out = new LinkedHashMap<String, Object>();

        final double[] percentiles = histogram.percentiles(0.5);

        out.put("type", "histogram");
        out.put("min", String.format("%2.2f", histogram.min()));
        out.put("max", String.format("%2.2f", histogram.max()));
        out.put("mean", String.format("%2.2f", histogram.mean()));
        out.put("stddev", String.format("%2.2f", histogram.stdDev()));
        out.put("median", String.format("%2.2f", percentiles[0]));

        return out;

    }

    private Map<String, Object> outputTimerMetric(TimerMetric timer) {

        Map<String,Object> out = new LinkedHashMap<String, Object>();

        // Add the meter metrics to the output
        out.put("meter", outputMeterMetric(timer));

        final double[] percentiles = timer.percentiles(0.5);

        out.put("type", "timer");
        out.put("event_type", timer.eventType());
        out.put("unit", timer.durationUnit().toString().toLowerCase());
        out.put("min", String.format("%2.2f", timer.min()));
        out.put("max", String.format("%2.2f", timer.max()));
        out.put("mean", String.format("%2.2f", timer.mean()));
        out.put("stddev", String.format("%2.2f", timer.stdDev()));
        out.put("median", String.format("%2.2f", percentiles[0]));

        return out;
    }

    private Map<String, Object> outputJVMUsage() {

        long mb = 1048576;

        Map<String,Object> out = new LinkedHashMap<String, Object>();

        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        int totalLiveThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        double loadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        MemoryUsage mu = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        String startTimeString = BackplaneConfig.ISO8601.format(new Date(startTime));

        out.put("type", "jvm");
        out.put("unit", "mb");
        out.put("heap_used", mu.getUsed()/mb);
        out.put("heap_free", (mu.getMax()-mu.getUsed())/mb);
        out.put("heap_max", mu.getMax()/mb);
        out.put("jvm_start_time", startTimeString);
        out.put("total_live_threads", totalLiveThreads);
        out.put("load_average_minute", String.format("%2.2f", loadAverage));

        return out;

    }

}
