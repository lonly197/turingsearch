package cn.com.turing;

import org.elasticsearch.common.inject.CreationException;
import org.elasticsearch.common.inject.spi.Message;

import java.io.PrintStream;

/**
 * Created by Lonly on 2016/11/28.
 */
final class StartupError extends RuntimeException {

    /** maximum length of a stacktrace, before we truncate it */
    static final int STACKTRACE_LIMIT = 30;
    /** all lines from this package are RLE-compressed */
    static final String GUICE_PACKAGE = "org.elasticsearch.common.inject";

    /**
     * Create a new StartupError that will format {@code cause}
     * to the console on failure.
     */
    StartupError(Throwable cause) {
        super(cause);
    }

    /*
     * This logic actually prints the exception to the console, its
     * what is invoked by the JVM when we throw the exception from main()
     */
    @Override
    public void printStackTrace(PrintStream s) {
        Throwable originalCause = getCause();
        Throwable cause = originalCause;
        if (cause instanceof CreationException) {
            cause = getFirstGuiceCause((CreationException)cause);
        }

        String message = cause.toString();
        s.println(message);

        if (cause != null) {
            // walk to the root cause
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            // print the root cause message, only if it differs!
            if (cause != originalCause && (message.equals(cause.toString()) == false)) {
                s.println("Likely root cause: " + cause);
            }

            // print stacktrace of cause
            StackTraceElement stack[] = cause.getStackTrace();
            int linesWritten = 0;
            for (int i = 0; i < stack.length; i++) {
                if (linesWritten == STACKTRACE_LIMIT) {
                    s.println("\t<<<truncated>>>");
                    break;
                }
                String line = stack[i].toString();

                // skip past contiguous runs of this garbage:
                if (line.startsWith(GUICE_PACKAGE)) {
                    while (i + 1 < stack.length && stack[i + 1].toString().startsWith(GUICE_PACKAGE)) {
                        i++;
                    }
                    s.println("\tat <<<guice>>>");
                    linesWritten++;
                    continue;
                }

                s.println("\tat " + line.toString());
                linesWritten++;
            }
        }
        // if its a guice exception, the whole thing really will not be in the log, its megabytes.
        // refer to the hack in bootstrap, where we don't log it
        if (originalCause instanceof CreationException == false) {
            s.println("Refer to the log for complete error details.");
        }
    }

    /**
     * Returns first cause from a guice error (it can have multiple).
     */
    static Throwable getFirstGuiceCause(CreationException guice) {
        for (Message message : guice.getErrorMessages()) {
            Throwable cause = message.getCause();
            if (cause != null) {
                return cause;
            }
        }
        return guice; // we tried
    }
}

