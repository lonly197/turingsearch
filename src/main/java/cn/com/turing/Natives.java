package cn.com.turing;

import org.elasticsearch.bootstrap.ConsoleCtrlHandler;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import java.nio.file.Path;

/**
 * Created by Lonly on 2016/11/28.
 */
final class Natives {
    /** no instantiation */
    private Natives() {}

    private static final ESLogger logger = Loggers.getLogger(Natives.class);

    // marker to determine if the JNA class files are available to the JVM
    static final boolean JNA_AVAILABLE;

    static {
        boolean v = false;
        try {
            // load one of the main JNA classes to see if the classes are available. this does not ensure that all native
            // libraries are available, only the ones necessary by JNA to function
            Class.forName("com.sun.jna.Native");
            v = true;
        } catch (ClassNotFoundException e) {
            logger.warn("JNA not found. native methods will be disabled.", e);
        } catch (UnsatisfiedLinkError e) {
            logger.warn("unable to load JNA native support library, native methods will be disabled.", e);
        }
        JNA_AVAILABLE = v;
    }

    static void tryMlockall() {
        if (!JNA_AVAILABLE) {
            logger.warn("cannot mlockall because JNA is not available");
            return;
        }
        JNANatives.tryMlockall();
    }

    static boolean definitelyRunningAsRoot() {
        if (!JNA_AVAILABLE) {
            logger.warn("cannot check if running as root because JNA is not available");
            return false;
        }
        return JNANatives.definitelyRunningAsRoot();
    }

    static void tryVirtualLock() {
        if (!JNA_AVAILABLE) {
            logger.warn("cannot mlockall because JNA is not available");
            return;
        }
        JNANatives.tryVirtualLock();
    }

    static void addConsoleCtrlHandler(ConsoleCtrlHandler handler) {
        if (!JNA_AVAILABLE) {
            logger.warn("cannot register console handler because JNA is not available");
            return;
        }
        JNANatives.addConsoleCtrlHandler(handler);
    }

    static boolean isMemoryLocked() {
        if (!JNA_AVAILABLE) {
            return false;
        }
        return JNANatives.LOCAL_MLOCKALL;
    }

    static void trySeccomp(Path tmpFile) {
        if (!JNA_AVAILABLE) {
            logger.warn("cannot install syscall filters because JNA is not available");
            return;
        }
        JNANatives.trySeccomp(tmpFile);
    }

    static boolean isSeccompInstalled() {
        if (!JNA_AVAILABLE) {
            return false;
        }
        return JNANatives.LOCAL_SECCOMP;
    }
}

