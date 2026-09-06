package io.heapy.ktc.quarkus.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Applies the system properties the plugin computed at build time. A Kotlin Toolchain plugin cannot set them on the
 * test JVM itself, and this listener runs before the first test class is loaded.
 */
public final class QuarkusTestListener implements LauncherSessionListener {

    private static final String PROPERTIES_RESOURCE = "META-INF/ktc-quarkus-test.properties";

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Properties properties = new Properties();
        try (InputStream input = QuarkusTestListener.class.getClassLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (input == null) {
                return;
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + PROPERTIES_RESOURCE, e);
        }
        for (String name : properties.stringPropertyNames()) {
            if (System.getProperty(name) == null) {
                System.setProperty(name, properties.getProperty(name));
            }
        }
    }
}
