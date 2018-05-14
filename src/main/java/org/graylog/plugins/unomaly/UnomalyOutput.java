package org.graylog.plugins.unomaly;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.inject.assistedinject.Assisted;
import okhttp3.*;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.DropdownField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.outputs.MessageOutputConfigurationException;
import org.graylog2.plugin.streams.Stream;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class UnomalyOutput implements MessageOutput {

    private Logger log = Logger.getLogger(UnomalyOutput.class.getName());
    private URI endpoint;
    private OkHttpClient client;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    @Inject
    public UnomalyOutput(@Assisted Stream stream, @Assisted Configuration conf) throws MessageOutputConfigurationException {
        String host = conf.getString("host");
        String protocol = conf.getString("protocol").toLowerCase();
        client = new OkHttpClient();

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
        log.info("Stopping Unomaly plugin");
        isRunning.set(false);
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public void write(Message message) throws Exception {
        List<Object> body = new ArrayList<>();

        Map<String, Object> data = new HashMap<>();
        data.put("message", message.getMessage());
        data.put("source", message.getSource());
        data.put("timestamp", message.getTimestamp().toString());
        data.put("metadata", getEventFieldsAsStrings(message));

        body.add(data);

        String json = gson.toJson(body);

        postPayload(endpoint, json);
    }

    @Override
    public void write(List<Message> messages) throws Exception {
        String json = null;
        List<Object> body = new ArrayList<>();

        for (Message m: messages) {
            Map<String, Object> data = new HashMap<>();
            data.put("message", m.getMessage());
            data.put("source", m.getSource());
            data.put("timestamp", m.getTimestamp().toString());
            data.put("metadata", getEventFieldsAsStrings(m));

            body.add(data);
        }

        json = gson.toJson(body);
        postPayload(endpoint, json);
    }

    private HashMap<String, String> getEventFieldsAsStrings(Message m) {
        HashMap<String, String> metadata = new HashMap<>();
        m.getFields().forEach((k, v) ->
            metadata.put(k, String.valueOf(v))
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

            return configurationRequest;
        }
    }
}
