package com.kitakkun.jetwhale.host.launcher;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * A host for the launcher's process tests, packaged as the bundled host jar. It is Java so that it
 * needs nothing from Kotlin's standard library, which the host's class loader must not see.
 *
 * <p>It writes what it finds to {@code stub-host.properties} in the app data directory, prints a
 * line, and then does what its first argument names: {@code return}, {@code throw}, {@code exit}
 * (status 3) or {@code halt} (status 134, without shutdown hooks, as a crash ends a JVM).
 */
public final class StubHost {
    public static void main(String[] args) throws Exception {
        ClassLoader own = StubHost.class.getClassLoader();
        Properties found = new Properties();
        found.setProperty("kotlinVisible", String.valueOf(canLoad(own, "kotlin.Unit")));
        found.setProperty("launcherVisible", String.valueOf(canLoad(own, "com.kitakkun.jetwhale.host.launcher.LauncherMainKt")));
        found.setProperty("contextClassLoaderIsOwn", String.valueOf(Thread.currentThread().getContextClassLoader() == own));
        found.setProperty("arguments", String.join(" ", args));
        for (String key : new String[] {
            "jetwhale.launcher.contract",
            "jetwhale.launcher.hostDir",
            "jetwhale.launcher.setAsideVersion",
            "skiko.library.path",
            "stub.common",
            "stub.platform",
        }) {
            found.setProperty(key, String.valueOf(System.getProperty(key)));
        }
        try (OutputStream output = Files.newOutputStream(Path.of(System.getProperty("jetwhale.appDataDir"), "stub-host.properties"))) {
            found.store(output, null);
        }
        System.out.println("stub host output");

        switch (args[0]) {
            case "throw":
                throw new IllegalStateException("stub host failed");
            case "exit":
                System.exit(3);
                break;
            case "halt":
                Runtime.getRuntime().halt(134);
                break;
            default:
                break;
        }
    }

    private static boolean canLoad(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
