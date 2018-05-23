# Unomaly Output Plugin for Graylog

[![Build Status](https://travis-ci.org/https://github.com/unomaly/graylog-plugin-unomaly.svg?branch=master)](https://travis-ci.org/https://github.com/unomaly/graylog-plugin-unomaly)

This plugin is intended for streaming logs to the Unomaly REST API.

**Required Graylog version:** 2.4.0 and later

## Installation

[Download the plugin](https://github.com/unomaly/graylog-plugin-unomaly/releases)
and place the `.jar` file in your Graylog plugin directory. The plugin directory
is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

### Configuration of Graylog

The Unomaly plugin will be available in Graylog like any other output plugin. It can
for example be attached to one or more [streams](http://docs.graylog.org/en/2.4/pages/streams.html) or [processing pipelines](http://docs.graylog.org/en/2.4/pages/pipelines.html).
The latter is the way to go if you need to perform record transformation, like
changing the source of a log event.

## Development


You can improve your development experience for the web interface part of your plugin
dramatically by making use of hot reloading. To do this, do the following:

* `git clone https://github.com/Graylog2/graylog2-server.git`
* `cd graylog2-server/graylog2-web-interface`
* `ln -s $YOURPLUGIN plugin/`
* `npm install && npm start`

## Usage

### Changing the source / key in Unomaly

By default, this plugin will use the `source` field in the Graylog events as
the source for events sent to Unomaly. This might not always be ideal if you
are using a microservice based architecture, most likely, you'd want to find
anomalies per microservice, rather than per container. You can change this
behavior by adding a *[pipeline](http://docs.graylog.org/en/2.4/pages/pipelines.html)* with *rule(s)* in Graylog. Example follows
below where we mutate the event to have the `service_name` field as the
specified `source` which Unomaly will see instead.

```
rule "transform_unomaly"
when
  has_field("service_name")
then
  let new_src = to_string($message.service_name);
  set_field("source", new_src);
end
``` 

## Getting started

This project is using Maven 3 and requires Java 7 or higher.

* Clone this repository.
* Run `mvn package` to build a JAR file.
* Optional: Run `mvn jdeb:jdeb` and `mvn rpm:rpm` to create a DEB and RPM package respectively.
* Copy generated JAR file in target directory to your Graylog plugin directory.
* Restart the Graylog.

## Plugin Release

We are using the maven release plugin:

```
$ mvn release:prepare
[...]
$ mvn release:perform
```

This sets the version numbers, creates a tag and pushes to GitHub. Travis CI will build the release artifacts and upload to GitHub automatically.
