package cn.com.turing;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.elasticsearch.Build;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.cli.CliTool;
import org.elasticsearch.common.cli.CliToolConfig;
import org.elasticsearch.common.cli.Terminal;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.monitor.jvm.JvmInfo;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.elasticsearch.common.cli.CliToolConfig.Builder.cmd;
import static org.elasticsearch.common.cli.CliToolConfig.Builder.optionBuilder;

/**
 * Created by Lonly on 2016/11/28.
 */
final class BootstrapCLIParser extends CliTool {

    private static final CliToolConfig CONFIG = CliToolConfig.config("elasticsearch", BootstrapCLIParser.class)
            .cmds(BootstrapCLIParser.Start.CMD, BootstrapCLIParser.Version.CMD)
            .build();

    public BootstrapCLIParser() {
        super(CONFIG);
    }

    public BootstrapCLIParser(Terminal terminal) {
        super(CONFIG, terminal);
    }

    @Override
    protected Command parse(String cmdName, CommandLine cli) throws Exception {
        switch (cmdName.toLowerCase(Locale.ROOT)) {
            case BootstrapCLIParser.Start.NAME:
                return BootstrapCLIParser.Start.parse(terminal, cli);
            case BootstrapCLIParser.Version.NAME:
                return BootstrapCLIParser.Version.parse(terminal, cli);
            default:
                assert false : "should never get here, if the user enters an unknown command, an error message should be shown before parse is called";
                return null;
        }
    }

    static class Version extends CliTool.Command {

        private static final String NAME = "version";

        private static final CliToolConfig.Cmd CMD = cmd(NAME, BootstrapCLIParser.Version.class).build();

        public static Command parse(Terminal terminal, CommandLine cli) {
            return new Version(terminal);
        }

        public Version(Terminal terminal) {
            super(terminal);
        }

        @Override
        public ExitStatus execute(Settings settings, Environment env) throws Exception {
            terminal.println("Version: %s, Build: %s/%s, JVM: %s", org.elasticsearch.Version.CURRENT, Build.CURRENT.hashShort(), Build.CURRENT.timestamp(), JvmInfo.jvmInfo().version());
            return ExitStatus.OK_AND_EXIT;
        }
    }

    static class Start extends CliTool.Command {

        private static final String NAME = "start";

        private static final CliToolConfig.Cmd CMD = cmd(NAME, BootstrapCLIParser.Start.class)
                .options(
                        optionBuilder("d", "daemonize").hasArg(false).required(false),
                        optionBuilder("p", "pidfile").hasArg(true).required(false),
                        optionBuilder("V", "version").hasArg(false).required(false),
                        Option.builder("D").argName("property=value").valueSeparator('=').numberOfArgs(2)
                )
                .stopAtNonOption(true) // needed to parse the --foo.bar options, so this parser must be lenient
                .build();

        public static Command parse(Terminal terminal, CommandLine cli) {
            if (cli.hasOption("V")) {
                return BootstrapCLIParser.Version.parse(terminal, cli);
            }

            if (cli.hasOption("d")) {
                System.setProperty("es.foreground", "false");
            }

            String pidFile = cli.getOptionValue("pidfile");
            if (!Strings.isNullOrEmpty(pidFile)) {
                System.setProperty("es.pidfile", pidFile);
            }

            if (cli.hasOption("D")) {
                Properties properties = cli.getOptionProperties("D");
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    String key = (String) entry.getKey();
                    String propertyName = key.startsWith("es.") ? key : "es." + key;
                    System.setProperty(propertyName, entry.getValue().toString());
                }
            }

            // hacky way to extract all the fancy extra args, there is no CLI tool helper for this
            Iterator<String> iterator = cli.getArgList().iterator();
            while (iterator.hasNext()) {
                String arg = iterator.next();
                if (!arg.startsWith("--")) {
                    if (arg.startsWith("-D") || arg.startsWith("-d") || arg.startsWith("-p")) {
                        throw new IllegalArgumentException(
                                "Parameter [" + arg + "] starting with \"-D\", \"-d\" or \"-p\" must be before any parameters starting with --"
                        );
                    } else {
                        throw new IllegalArgumentException("Parameter [" + arg + "]does not start with --");
                    }
                }
                // if there is no = sign, we have to get the next argu
                arg = arg.replace("--", "");
                if (arg.contains("=")) {
                    String[] splitArg = arg.split("=", 2);
                    String key = splitArg[0];
                    String value = splitArg[1];
                    System.setProperty("es." + key, value);
                } else {
                    if (iterator.hasNext()) {
                        String value = iterator.next();
                        if (value.startsWith("--")) {
                            throw new IllegalArgumentException("Parameter [" + arg + "] needs value");
                        }
                        System.setProperty("es." + arg, value);
                    } else {
                        throw new IllegalArgumentException("Parameter [" + arg + "] needs value");
                    }
                }
            }

            return new Start(terminal);
        }

        public Start(Terminal terminal) {
            super(terminal);

        }

        @Override
        public ExitStatus execute(Settings settings, Environment env) throws Exception {
            return ExitStatus.OK;
        }
    }

}
