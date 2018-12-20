package com.unomaly.plugins.graylog;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.inject.assistedinject.Assisted;
import okhttp3.*;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.*;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.streams.Stream;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class UnomalyOutput implements MessageOutput {

    private Logger log = Logger.getLogger(UnomalyOutput.class.getName());
    private URI endpoint;
    private Boolean useGraylogTimestamp;
    private String sourceKey;
    private String messageKey;
    private Integer batchSize;
    private OkHttpClient client;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson gson = new Gson();
    private LinkedBlockingQueue queue;

    @Inject
    public UnomalyOutput(@Assisted Stream stream, @Assisted Configuration conf) throws MessageOutputConfigurationException {
        String host = conf.getString("host");
        String protocol = conf.getString("protocol").toLowerCase();
        useGraylogTimestamp = conf.getBoolean("useGraylogTimestamp");
        sourceKey = conf.getString("source_key");
        messageKey = conf.getString("message_key");
        batchSize = conf.getInt("batch_size");
        client = new OkHttpClient();
        queue = new LinkedBlockingQueue<Message>();

        try {
            endpoint = new URI(String.format("%s://%s/v1/batch",
                    protocol,
                    host));
            log.info("Creating Unomaly output, using API endpoint at: " + endpoint);
        } catch (URISyntaxException e) {
            throw new MessageOutputConfigurationException("Syntax error in Unomaly URL");
        }

        isRunning.set(true);
    }

    @Override
    public void stop() {
        log.info("Flushing queue for Unomaly plugin");
        flushQueue(queue);
        log.info("Stopping Unomaly plugin");
        isRunning.set(false);
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public void write(Message m) throws Exception {
        addToQueue(m);
    }

    @Override
    public void write(List<Message> messages) throws Exception {
        for (Message m: messages) {
            addToQueue(m);
        }
    }

    private HashMap<String, String> getAndFixMetadata(Message m) {
        HashMap<String, String> metadata = new HashMap<>();
        m.getFields().forEach((k, v) ->
                {
                    // Do not send duplicate data
                    String strVal = String.valueOf(v);
                    switch (k) {
                        case "message":
                            break;
                        default:
                            metadata.put(k, strVal);
                    }
                }
        );
        return metadata;
    }

    private void postPayload(URI endpointURI, String body) throws IOException {
        String endpoint = endpointURI.toString();
        RequestBody reqBody = RequestBody.create(JSON, body);
        Request request = new Request.Builder()
                .url(endpoint)
                .post(reqBody)
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            response.body().close();
            throw new Error("Post to Unomaly endpoint not successful");
        }

        response.body().close();
    }

    private void addToQueue(Message m) throws IllegalStateException {
        if (queue.size() >= batchSize) {
            flushQueue(queue);
        }
        queue.add(m);
    }

    private void flushQueue(Queue<Message> q) {
        while (!q.isEmpty()) {
            String json = null;
            Message m = null;
            List<Object> body = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                HashMap<String, Object> data = new HashMap<>();
                try {
                    m = (Message) queue.take();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                // Add the message field
                if ((messageKey != "message") && m.hasField(messageKey)) {
                        data.put("message", m.getFieldAs(String.class, messageKey));
                } else {
                    data.put("message", m.getMessage());
                }

                // Handle different sources
                if (sourceKey != "source") {
                    if (m.hasField(sourceKey)) {
                        data.put("source", m.getFieldAs(String.class, sourceKey));
                    } else {
                        data.put("source", m.getSource());
                    }
                }

                if (useGraylogTimestamp) {
                    data.put("timestamp", m.getTimestamp().toString());
                }
                data.put("metadata", getAndFixMetadata(m));

                body.add(data);
            }

            //log.info("Number of events in this post request " + String.valueOf(body.size()));
            json = gson.toJson(body);
            try {
                postPayload(endpoint, json);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface Factory extends MessageOutput.Factory<UnomalyOutput> {
        @Override
        UnomalyOutput create(Stream stream, Configuration configuration);

        @Override
        Config getConfig();

        @Override
        Descriptor getDescriptor();
    }

    public static class Descriptor extends MessageOutput.Descriptor {
        public Descriptor() {
            super("Unomaly Output", false, "", "Forward stream to Unomaly.");
        }
    }

    public static class Config extends MessageOutput.Config {
        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = new ConfigurationRequest();

            configurationRequest.addField(new TextField(
                    "host",
                    "Unomaly hostname",
                    "unomaly.domain.int",
                    "Unomaly instance hostname where you want to ship logs.",
                    ConfigurationField.Optional.NOT_OPTIONAL));
            final Map<String, String> protocols = ImmutableMap.of("http", "HTTP", "https", "HTTPS");
            configurationRequest.addField(new DropdownField(
                    "protocol",
                    "Protocol to use for sending logs",
                    "https",
                    protocols,
                    "The protocol used for communicating with the Unomaly API.",
                    ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new BooleanField(
                    "useGraylogTimestamp",
                    "Use Graylog Timestamps?",
                    true,
                    "If not checked, Unomaly will use the local timestamp for incoming events")
            );

            configurationRequest.addField(new TextField(
                    "source_key",
                    "Key in the message to use as source in Unomaly",
                    "source",
                    "Which field from the message to use as source. Has to exist on all messages.",
                    ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new TextField(
                    "message_key",
                    "Message source key",
                    "message",
                    "Defaults to message, but could also be full_message or similar. Messages without this field will revert back to the default.",
                    ConfigurationField.Optional.NOT_OPTIONAL)
            );

            configurationRequest.addField(new NumberField(
                    "batch_size",
                    "Unomaly API batch size",
                    200,
                    "Number of messages to be posted to the Unomaly API in each post request",
                    NumberField.Attribute.ONLY_POSITIVE)
            );

            return configurationRequest;
        }
    }
}
