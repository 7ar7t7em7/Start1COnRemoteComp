package aaaStart1COnRemoteComp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Выполняет BAT-файлы на удалённых серверах через PsExec или WMI.
 */
public class RemoteExecutor {

    private static final String PSEXEC_PATH = "D:\\ae\\Utils\\PSTools\\PsExec.exe";
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * Результат выполнения удалённой команды.
     */
    public static class ExecutionResult {
        private final long pid;
        private final String returnValue;
        private final int exitCode;
        private final String output;
        private final String strategy;

        public ExecutionResult(String strategy, long pid, String returnValue, int exitCode, String output) {
            this.strategy = strategy;
            this.pid = pid;
            this.returnValue = returnValue;
            this.exitCode = exitCode;
            this.output = output;
        }

        public String getShortString() {
            if ("PsExec".equals(strategy)) {
                return "PsExec (ExitCode=" + exitCode + "): " + output.trim().replace("\r\n", " ");
            } else {
                return "WMI (PID=" + pid + ", ReturnValue=" + returnValue + ")";
            }
        }

        public boolean isSuccess() {
            return exitCode == 0 || "0".equals(returnValue);
        }
    }

    /**
     * Запускает BAT-файл на удалённом сервере.
     */
    public static ExecutionResult execute(ServerInfo server, String remoteBatPath) throws Exception {
        if (server.isUsePsExec()) {
            return executeViaPsExec(server, remoteBatPath);
        } else {
            return executeViaWmi(server, remoteBatPath);
        }
    }

    private static ExecutionResult executeViaPsExec(ServerInfo server, String remoteBatPath) throws Exception {
        List<String> cmdList = new ArrayList<>();
        cmdList.add(PSEXEC_PATH);
        cmdList.add("-accepteula");
        cmdList.add("-i");
        cmdList.add("\\\\" + server.getIp()); // <-- ИСПРАВЛЕНО ЗДЕСЬ (4 слэша)
        cmdList.add("-u");
        cmdList.add(server.getSmbUser());
        cmdList.add("-p");
        cmdList.add(server.getSmbPassword());
        cmdList.add(remoteBatPath);

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "Cp866"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ExecutionResult("PsExec", -1, "N/A", -1, "PsExec: Таймаут выполнения");
        }

        int exitCode = process.exitValue();
        return new ExecutionResult("PsExec", -1, "N/A", exitCode, output.toString());
    }

    private static ExecutionResult executeViaWmi(ServerInfo server, String remoteBatPath) throws Exception {
        WmiBatRunner wmi = new WmiBatRunner(
                server.getIp(),
                server.getSmbUser(),
                server.getSmbPassword(),
                60
        );
        String rawResult = wmi.runCommand(remoteBatPath);

        long pid = -1;
        String returnValue = "N/A";

        java.util.regex.Matcher mPid = java.util.regex.Pattern.compile("ProcessId\\s*=\\s*(\\d+)").matcher(rawResult);
        if (mPid.find()) pid = Long.parseLong(mPid.group(1));

        java.util.regex.Matcher mRet = java.util.regex.Pattern.compile("ReturnValue\\s*=\\s*(\\d+)").matcher(rawResult);
        if (mRet.find()) returnValue = mRet.group(1);

        return new ExecutionResult("WMI", pid, returnValue, 0, rawResult);
    }
}
