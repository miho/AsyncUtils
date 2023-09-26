/*
 * Copyright 2022 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * If you use this software for scientific research then please cite the following publication(s):
 *
 * M. Hoffer, C. Poliwoda, & G. Wittum. (2013). Visual reflection library:
 * a framework for declarative GUI programming on the Java platform.
 * Computing and Visualization in Science, 2013, 16(4),
 * 181–192. http://doi.org/10.1007/s00791-014-0230-y
 */
package eu.mihosoft.asyncutils;

import org.tinylog.Logger;

import java.beans.Expression;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Utility class to use virtual threads if available. If not, it will use regular threads. This allows to
 * write code that uses virtual threads if supported and still compiles and runs on older Java versions.
 *
 *
 * @author Michael Hoffer (info@michaelhoffer.de)
 */
public final class VirtualThreadUtils {
    private VirtualThreadUtils() {
        throw new AssertionError("Don't instantiate me!");
    }

    private static boolean virtualThreadsSupported;
    private static ThreadFactory threadFactory;

    static {
        virtualThreadsSupported = testIfVirtualThreadsAreSupported();
        threadFactory = newThreadFactory(true);
    }

    /**
     * Returns {@code true} if virtual threads are supported; {@code false} otherwise.
     * @return {@code true} if virtual threads are supported; {@code false} otherwise
     */
    public static boolean areVirtualThreadsSupported() {
        return virtualThreadsSupported;
    }

    /**
     * Creates a thread. If requested and virtual threads are supported, a virtual thread will be created.
     * Otherwise, a regular thread will be created.
     *
     * @param runnable runnable to execute
     * @param requestVirtualThread if {@code true}, a virtual thread is created if possible
     *
     * @return new thread
     */
    public static Thread newThread(Runnable runnable, boolean requestVirtualThread) {
        if (virtualThreadsSupported && requestVirtualThread) {
            return threadFactory.newThread(runnable);
        } else {
            return new Thread(runnable);
        }
    }

    /**
     * Creates a thread. If virtual threads are supported, a virtual thread will be created. Otherwise, a regular thread
     * will be created.
     *
     * @param runnable runnable to execute
     *
     * @return new thread
     */
    public static Thread newThread(Runnable runnable) {
        return newThread(runnable, true);
    }

    /**
     * Tests if virtual threads are supported.
     * @return {@code true} if virtual threads are supported; {@code false} otherwise
     */
    private static boolean testIfVirtualThreadsAreSupported() {
        boolean versionAtLeast19 = isJavaVersionAtLeast("19");

        if(!versionAtLeast19) {
            Logger.tag("AsyncUtils").info("Java 19 or higher required for virtual threads. Using regular threads instead.");
            return false;
        }

        boolean versionAtLeast21 = isJavaVersionAtLeast("21");

        if (versionAtLeast21) {
            Logger.tag("AsyncUtils").info("Java 21 or higher detected. Virtual threads are supported.");
            return true;
        }

        Logger.tag("AsyncUtils").info("Java 19 or higher detected. Testing if virtual threads are supported...");

        try {
            newVirtualThreadFactory(); // just for testing

            Logger.tag("AsyncUtils").info("Virtual threads are supported.");

            return true;
        } catch (Throwable throwable) {

            // stacktrace to string
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (var pw = new java.io.PrintWriter(baos)) {
                throwable.printStackTrace(pw);
            }
            String stacktrace = baos.toString();

            if(stacktrace.toLowerCase().contains("preview features not enabled")) {
                Logger.tag("AsyncUtils").warn("Virtual threads are not supported. Please start the JVM with the --enable-preview flag. Using regular threads instead.");
                return false;
            }

            Logger.tag("AsyncUtils").warn(throwable,"Virtual threads are not supported. Using regular threads instead.");

            return false;
        }

    }

    /**
     * Attempts to create a new virtual thread factory. This only works if the JVM supports virtual threads.
     * Currently, this is only the case for Java 19 and above and only if the JVM is started with the
     * {@code --enable-preview} flag.
     * @return a new virtual thread factory
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private static ThreadFactory newVirtualThreadFactory() throws Exception {

//        if (isJavaVersionAtLeast("21")) {
//            return Thread.ofVirtual().factory();
//        }

        java.beans.Expression expression = new java.beans.Expression(Thread.class, "ofVirtual", new Object[0]);

        Object ofVirtual = expression.getValue();

        // now call factory() on the ofVirtual object
        expression = new java.beans.Expression(ofVirtual, "factory", new Object[0]);

        Object factory = expression.getValue();

        return (ThreadFactory) factory;
    }

    /**
     * Creates a new {@link ThreadFactory} that creates threads. This method uses reflection to access
     * preview features of Java 19 (Project Loom, virtual Threads).
     * @param requestVirtualThread if {@code true}, a virtual thread is created if possible
     * @return a new {@link ThreadFactory}
     */
    public static ThreadFactory newThreadFactory(boolean requestVirtualThread) {
        if(virtualThreadsSupported && requestVirtualThread) {
            try {
                return newVirtualThreadFactory();

            } catch (Throwable e) {

                Logger.tag("AsyncUtils").warn(e,"Virtual threads are not supported. Using regular threads instead.");

                return (r) -> new Thread(r);
            }
        } else {
            return (r) -> new Thread(r);
        }
    }

    /**
     * Creates a new {@link ScheduledExecutorService} that uses virtual threads if possible.
     * @param corePoolSize core pool size
     * @return a new {@link ScheduledExecutorService}
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        if(virtualThreadsSupported) {
            try {
                return Executors.newScheduledThreadPool(corePoolSize, newThreadFactory(true));
            } catch (Throwable e) {
                return Executors.newScheduledThreadPool(corePoolSize);
            }
        } else {
            return Executors.newScheduledThreadPool(corePoolSize);
        }
    }

    /**
     * Determines if the given thread is a virtual thread.
     * @param thread thread to check
     * @return {@code true} if the given thread is a virtual thread; {@code false} otherwise
     */
    public static boolean isVirtual(Thread thread) {
        try {
            return (boolean) new Expression(thread,
                    "isVirtual", null).getValue();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the current Java version is at least the given version.
     *
     * @param version the version to check
     * @return {@code true} if the current Java version is at least the given version; {@code false} otherwise
     */
    private static boolean isJavaVersionAtLeast(String version) {
        // handle dash and underscore in version string
        version = version.replace("-", ".");
        version = version.replace("_", ".");
        String[] versionParts = version.split("\\.");
        String[] currentVersionParts = System.getProperty("java.version").split("\\.");
        for(int i = 0; i < versionParts.length; i++) {
            if(Integer.parseInt(currentVersionParts[i]) < Integer.parseInt(versionParts[i])) {
                return false;
            }
        }
        return true;
    }
}
