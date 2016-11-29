package cn.com.turing;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;

import org.apache.lucene.util.Constants;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Lonly on 2016/11/28.
 */
final class JNACLibrary {

    private static final ESLogger logger = Loggers.getLogger(JNACLibrary.class);

    public static final int MCL_CURRENT = 1;
    public static final int ENOMEM = 12;
    public static final int RLIMIT_MEMLOCK = Constants.MAC_OS_X ? 6 : 8;
    public static final long RLIM_INFINITY = Constants.MAC_OS_X ? 9223372036854775807L : -1L;

    static {
        try {
            Native.register("c");
        } catch (UnsatisfiedLinkError e) {
            logger.warn("unable to link C library. native methods (mlockall) will be disabled.", e);
        }
    }

    static native int mlockall(int flags);

    static native int geteuid();

    /** corresponds to struct rlimit */
    public static final class Rlimit extends Structure implements Structure.ByReference {
        public NativeLong rlim_cur = new NativeLong(0);
        public NativeLong rlim_max = new NativeLong(0);

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "rlim_cur", "rlim_max" });
        }
    }

    static native int getrlimit(int resource, JNACLibrary.Rlimit rlimit);
    static native int setrlimit(int resource, JNACLibrary.Rlimit rlimit);

    static native String strerror(int errno);

    private JNACLibrary() {
    }
}
