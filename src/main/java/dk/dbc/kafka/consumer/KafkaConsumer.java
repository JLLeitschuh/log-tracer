/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.md
 */

package dk.dbc.kafka.consumer;

import dk.dbc.kafka.logformat.LogEvent;
import dk.dbc.kafka.logformat.LogEventMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;

public class KafkaConsumer implements Consumer {
    private final Properties kafkaProperties;
    private final String topicName;
    private final LogEventMapper logEventMapper;
    private long timestamp;

    public KafkaConsumer(String hostname, Integer port, String topicName, String groupId, String offset, String clientID) {
        this.topicName = topicName;
        this.kafkaProperties = createKafkaProperties(hostname, port, groupId, offset, clientID);
        this.logEventMapper = new LogEventMapper();
    }

    public void setFromDateTime(OffsetDateTime dateTime) {
        this.timestamp = dateTime.toInstant().toEpochMilli();
    }

    @Override
    public Iterator<LogEvent> iterator() {
        return new Iterator<LogEvent>() {
            final org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> kafka =
                    new org.apache.kafka.clients.consumer.KafkaConsumer<>(kafkaProperties);
            {
                subscribe(kafka, topicName, timestamp);
            }
            
            final PriorityQueue<ConsumedItem> consumedItems =
                    new PriorityQueue<>(1200, new ConsumedItemComparator());

            @Override
            public boolean hasNext() {
                if (consumedItems.isEmpty() || consumedItems.size() < 400) {
                    kafka.poll(Duration.ofMillis(3000)).forEach(record
                            -> consumedItems.add(new ConsumedItem(record)));
                }
                return !consumedItems.isEmpty();
            }

            @Override
            public LogEvent next() {
                return consumedItems.poll().toLogEvent();
            }
        };
    }

    private Properties createKafkaProperties(String hostname, Integer port, String groupId, String offset, String clientID) {
        final Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", hostname + ":" + port);
        properties.setProperty("group.id", groupId);
        properties.setProperty("client.id", clientID); // UUID.randomUUID().toString()
        properties.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.setProperty("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        properties.put("auto.offset.reset", offset);  // The consumer can starts from the beginning of the topic or the end
        properties.put("max.poll.records", "800");
        return properties;
    }

    private void subscribe(
            org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> kafka,
            String topicName, long timestamp) {
        if (timestamp > 0) {
            seekToOffsetsForTimestamp(kafka, topicName, timestamp);
        } else {
            kafka.subscribe(Collections.singletonList(topicName));
        }
    }

    private void seekToOffsetsForTimestamp(
            org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> kafka,
            String topicName, long timestamp) {

        final Map<TopicPartition, Long> startingPointByTimestamp = new HashMap<>();
        final List<PartitionInfo> topicPartitionInfo = kafka.partitionsFor(topicName);
        for (PartitionInfo pi : topicPartitionInfo) {
            startingPointByTimestamp.put(new TopicPartition(topicName, pi.partition()), timestamp);
        }
        System.err.println("partitions and timestamps: " + startingPointByTimestamp);
        final Map<TopicPartition, OffsetAndTimestamp> startingPointByOffset =
                kafka.offsetsForTimes(startingPointByTimestamp);
        System.err.println("partitions and offsets: " + startingPointByOffset);
        kafka.assign(startingPointByOffset.keySet());
        for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : startingPointByOffset.entrySet()) {
            if (entry.getValue() != null) {
                kafka.seek(entry.getKey(), entry.getValue().offset());
            }
        }
    }

    private class ConsumedItem {
        private final LogEvent logEvent;

        ConsumedItem(ConsumerRecord<String, byte[]> consumerRecord) {
            this.logEvent = toLogEvent(consumerRecord);
        }

        private LogEvent toLogEvent(ConsumerRecord<String, byte[]> consumerRecord) {
            LogEvent logEvent;
            try {
                logEvent = logEventMapper.unmarshall(consumerRecord.value());
            } catch (UncheckedIOException e) {
                // log exception??
                logEvent = new LogEvent();
            }
            logEvent.setRaw(consumerRecord.value());
            if (logEvent.getTimestamp() == null) {
                // No timestamp in record value, use kafka timestamp instead
                logEvent.setTimestamp(OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(consumerRecord.timestamp()), ZoneId.systemDefault()));
            }
            return logEvent;
        }

        LogEvent toLogEvent() {
            return logEvent;
        }
    }

    private static class ConsumedItemComparator implements Comparator<ConsumedItem> {
        @Override
        public int compare(ConsumedItem a, ConsumedItem b) {
            long byTimestamp = a.toLogEvent().getTimestamp().toInstant().toEpochMilli()
                    - b.toLogEvent().getTimestamp().toInstant().toEpochMilli();
            return Long.signum(byTimestamp);
        }
    }
}