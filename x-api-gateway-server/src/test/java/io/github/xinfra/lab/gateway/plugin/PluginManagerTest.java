package io.github.xinfra.lab.gateway.plugin;

import io.github.xinfra.lab.gateway.annotations.Order;
import io.github.xinfra.lab.gateway.filter.GatewayFilterFactoryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PluginManagerTest {

    static class TestPlugin implements GatewayPlugin {
        final String pluginName;
        final List<String> lifecycle = new ArrayList<>();

        TestPlugin(String name) {
            this.pluginName = name;
        }

        @Override
        public String name() { return pluginName; }

        @Override
        public String version() { return "1.0.0"; }

        @Override
        public void onInstall(PluginContext context) { lifecycle.add("install"); }

        @Override
        public void onStart(PluginContext context) { lifecycle.add("start"); }

        @Override
        public void onStop(PluginContext context) { lifecycle.add("stop"); }

        @Override
        public void onUninstall(PluginContext context) { lifecycle.add("uninstall"); }
    }

    @Test
    public void testPluginLifecycleOrder() {
        PluginManager manager = new PluginManager(new DefaultPluginContext(), "nonexistent");
        manager.loadPlugins();
        manager.startPlugins();
        manager.stopPlugins();
        Assert.assertTrue(manager.getLoadedPlugins().isEmpty());
    }

    @Test
    public void testLoadPluginsFromNonexistentDir() {
        PluginManager manager = new PluginManager(new DefaultPluginContext(), "/tmp/no-such-dir-plugins");
        manager.loadPlugins();
        Assert.assertTrue(manager.getLoadedPlugins().isEmpty());
    }

    @Test
    public void testPluginManagerStartedFlag() {
        PluginManager manager = new PluginManager(new DefaultPluginContext(), null);
        Assert.assertFalse(manager.isStarted());
        manager.loadPlugins();
        Assert.assertFalse(manager.isStarted());
        manager.startPlugins();
        Assert.assertTrue(manager.isStarted());
        manager.stopPlugins();
        Assert.assertFalse(manager.isStarted());
    }

    @Test
    public void testStopWithoutStartIsNoop() {
        PluginManager manager = new PluginManager(new DefaultPluginContext(), null);
        manager.stopPlugins();
        Assert.assertFalse(manager.isStarted());
    }

    @Test
    public void testGetLoadedPluginsIsUnmodifiable() {
        PluginManager manager = new PluginManager(new DefaultPluginContext(), null);
        manager.loadPlugins();
        try {
            manager.getLoadedPlugins().add(new TestPlugin("x"));
            Assert.fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
