package io.github.xinfra.lab.gateway.plugin;

public interface GatewayPlugin {

    String name();

    String version();

    void onInstall(PluginContext context);

    void onStart(PluginContext context);

    void onStop(PluginContext context);

    void onUninstall(PluginContext context);
}
