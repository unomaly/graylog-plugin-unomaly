package com.unomaly.plugins.graylog;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

public class UnomalyOutputMetaData implements PluginMetaData {
    @Override
    public String getUniqueId() {
        return UnomalyOutput.class.getCanonicalName();
    }

    @Override
    public String getName() {
        return "Unomaly Output Plugin";
    }

    @Override
    public String getAuthor() {
        return "Unomaly AB";
    }

    @Override
    public URI getURL() {
        return URI.create("https://unomaly.com");
    }

    @Override
    public Version getVersion() {
        return new Version(0, 0, 1);
    }

    @Override
    public String getDescription() {
        return "Output plugin that writes messages to a Unomaly instance.";
    }

    @Override
    public Version getRequiredVersion() {
        return new Version(2, 0, 0);
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return EnumSet.of(ServerStatus.Capability.SERVER);
    }
}
