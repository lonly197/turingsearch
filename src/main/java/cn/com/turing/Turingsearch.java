package cn.com.turing;

import java.security.Permission;

/**
 * Created by Lonly on 2016/11/28.
 */
public class Turingsearch {
    /** no instantiation */
    private Turingsearch() {}

    /**
     * Main entry point for starting elasticsearch
     */
    public static void main(String[] args) throws StartupError {
        // we want the JVM to think there is a security manager installed so that if internal policy decisions that would be based on the
        // presence of a security manager or lack thereof act as if there is a security manager present (e.g., DNS cache policy)
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(Permission perm) {
                // grant all permissions so that we can later set the security manager to the one that we want
            }
        });
        try {
            Bootstrap.init(args);
        } catch (Throwable t) {
            // format exceptions to the console in a special way
            // to avoid 2MB stacktraces from guice, etc.
            throw new StartupError(t);
        }
    }

    /**
     * Required method that's called by Apache Commons procrun when
     * running as a service on Windows, when the service is stopped.
     *
     * http://commons.apache.org/proper/commons-daemon/procrun.html
     *
     * NOTE: If this method is renamed and/or moved, make sure to update service.bat!
     */
    static void close(String[] args) {
        Bootstrap.stop();
    }
}
