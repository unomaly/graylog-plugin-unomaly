const PluginWebpackConfig = require('graylog-web-plugin').PluginWebpackConfig;
const loadBuildConfig = require('graylog-web-plugin').loadBuildConfig;
const path = require('path');
const buildConfig = loadBuildConfig(path.resolve(__dirname, './build.config'));

module.exports = new PluginWebpackConfig('org.graylog.plugins.UnomalyOutputPlugin', buildConfig, {
  // Here goes your additional webpack configuration.
});
