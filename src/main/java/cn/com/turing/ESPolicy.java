package cn.com.turing;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import org.elasticsearch.bootstrap.BootstrapInfo;
import org.elasticsearch.bootstrap.JarHell;
import org.elasticsearch.common.SuppressForbidden;

import java.net.SocketPermission;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Map;

/**
 * Created by Lonly on 2016/11/28.
 */
final class ESPolicy extends Policy {

    /** template policy file, the one used in tests */
    static final String POLICY_RESOURCE = "security.policy";
    /** limited policy for scripts */
    static final String UNTRUSTED_RESOURCE = "untrusted.policy";

    final Policy template;
    final Policy untrusted;
    final Policy system;
    final PermissionCollection dynamic;
    final Map<String,Policy> plugins;

    public ESPolicy(PermissionCollection dynamic, Map<String,Policy> plugins, boolean filterBadDefaults) {
        this.template = Security.readPolicy(getClass().getResource(POLICY_RESOURCE), JarHell.parseClassPath());
        this.untrusted = Security.readPolicy(getClass().getResource(UNTRUSTED_RESOURCE), new URL[0]);
        if (filterBadDefaults) {
            this.system = new ESPolicy.SystemPolicy(Policy.getPolicy());
        } else {
            this.system = Policy.getPolicy();
        }
        this.dynamic = dynamic;
        this.plugins = plugins;
    }

    @Override @SuppressForbidden(reason = "fast equals check is desired")
    public boolean implies(ProtectionDomain domain, Permission permission) {
        CodeSource codeSource = domain.getCodeSource();
        // codesource can be null when reducing privileges via doPrivileged()
        if (codeSource == null) {
            return false;
        }

        URL location = codeSource.getLocation();
        // location can be null... ??? nobody knows
        // https://bugs.openjdk.java.net/browse/JDK-8129972
        if (location != null) {
            // run scripts with limited permissions
            if (BootstrapInfo.UNTRUSTED_CODEBASE.equals(location.getFile())) {
                return untrusted.implies(domain, permission);
            }
            // check for an additional plugin permission: plugin policy is
            // only consulted for its codesources.
            Policy plugin = plugins.get(location.getFile());
            if (plugin != null && plugin.implies(domain, permission)) {
                return true;
            }
        }

        // otherwise defer to template + dynamic file permissions
        return template.implies(domain, permission) || dynamic.implies(permission) || system.implies(domain, permission);
    }

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        // code should not rely on this method, or at least use it correctly:
        // https://bugs.openjdk.java.net/browse/JDK-8014008
        // return them a new empty permissions object so jvisualvm etc work
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if ("sun.rmi.server.LoaderHandler".equals(element.getClassName()) &&
                    "loadClass".equals(element.getMethodName())) {
                return new Permissions();
            }
        }
        // return UNSUPPORTED_EMPTY_COLLECTION since it is safe.
        return super.getPermissions(codesource);
    }

    // TODO: remove this hack when insecure defaults are removed from java

    /**
     * Wraps a bad default permission, applying a pre-implies to any permissions before checking if the wrapped bad default permission
     * implies a permission.
     */
    private static class BadDefaultPermission extends Permission {

        private final Permission badDefaultPermission;
        private final Predicate<Permission> preImplies;

        /**
         * Construct an instance with a pre-implies check to apply to desired permissions.
         *
         * @param badDefaultPermission the bad default permission to wrap
         * @param preImplies           a test that is applied to a desired permission before checking if the bad default permission that
         *                             this instance wraps implies the desired permission
         */
        public BadDefaultPermission(final Permission badDefaultPermission, final Predicate<Permission> preImplies) {
            super(badDefaultPermission.getName());
            this.badDefaultPermission = badDefaultPermission;
            this.preImplies = preImplies;
        }

        @Override
        public final boolean implies(Permission permission) {
            return preImplies.apply(permission) && badDefaultPermission.implies(permission);
        }

        @Override
        public final boolean equals(Object obj) {
            return badDefaultPermission.equals(obj);
        }

        @Override
        public int hashCode() {
            return badDefaultPermission.hashCode();
        }

        @Override
        public String getActions() {
            return badDefaultPermission.getActions();
        }

    }

    private static Predicate<Permission> SOCKET_LISTEN_PERMISION_PREDICATE = new Predicate<Permission>() {
        @Override
        public boolean apply(Permission permission) {
            // we apply this pre-implies test because some SocketPermission#implies calls do expensive reverse-DNS resolves
            return permission instanceof SocketPermission && permission.getActions().contains("listen");
        }
    };

    // default policy file states:
    // "It is strongly recommended that you either remove this permission
    //  from this policy file or further restrict it to code sources
    //  that you specify, because Thread.stop() is potentially unsafe."
    // not even sure this method still works...
    private static final Permission BAD_DEFAULT_NUMBER_ONE =
            new BadDefaultPermission(new RuntimePermission("stopThread"), Predicates.<Permission>alwaysTrue());

    // default policy file states:
    // "allows anyone to listen on dynamic ports"
    // specified exactly because that is what we want, and fastest since it won't imply any
    // expensive checks for the implicit "resolve"
    // NOTE: this permission is only checked this way in 7u51+, 8, 9, ...
    private static final Permission BAD_DEFAULT_NUMBER_TWO =
            new BadDefaultPermission(new SocketPermission("localhost:0", "listen"), SOCKET_LISTEN_PERMISION_PREDICATE);

    // NOTE: same as above, but specified and checked this way in 7-7u45
    private static final Permission BAD_DEFAULT_NUMBER_THREE =
            new BadDefaultPermission(new SocketPermission("localhost:1024-", "listen"), SOCKET_LISTEN_PERMISION_PREDICATE);

    // default policy file states:
    // "permission for standard RMI registry port"
    // NOTE: this permission is only specified this way in 7u51+ (previously implicit in the above)
    private static final Permission BAD_DEFAULT_NUMBER_FOUR =
            new BadDefaultPermission(new SocketPermission("localhost:1099", "listen"), SOCKET_LISTEN_PERMISION_PREDICATE);

    /**
     * Wraps the Java system policy, filtering out bad default permissions that
     * are granted to all domains. Note, before java 8 these were even worse.
     */
    static class SystemPolicy extends Policy {
        final Policy delegate;

        SystemPolicy(Policy delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            if (BAD_DEFAULT_NUMBER_ONE.implies(permission) ||
                    BAD_DEFAULT_NUMBER_TWO.implies(permission) ||
                    BAD_DEFAULT_NUMBER_THREE.implies(permission) ||
                    BAD_DEFAULT_NUMBER_FOUR.implies(permission)) {
                return false;
            }
            return delegate.implies(domain, permission);
        }
    }

}

