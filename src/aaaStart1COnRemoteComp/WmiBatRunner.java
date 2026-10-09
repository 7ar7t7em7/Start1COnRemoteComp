package aaaStart1COnRemoteComp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Класс для запуска BAT-файла на удаленном Windows-компьютере через WMI (wmic.exe).
 * Подходит для Windows Server 2003, где нет WinRM.
 */

public class WmiBatRunner {

    private final String host;
    private final String username;
    private final String password;
    private final long timeoutSeconds;

    public WmiBatRunner(String host, String username, String password, long timeoutSeconds) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Запускает BAT-файл на удалённом компьютере. */
    public String runBat(String remoteBatPath) throws IOException, InterruptedException {
        return runCommand(remoteBatPath);
    }

    /**
     * Запускает произвольную команду на удалённом компьютере.
     * Например: "C:\\ae\\run.bat" или "cmd.exe /c mkdir C:\\ae\\Log".
     */
    public String runCommand(String command) throws IOException, InterruptedException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("cmd.exe");
        cmdList.add("/c");
        cmdList.add("wmic");
        cmdList.add("/node:" + host);
        cmdList.add("/user:\"" + username + "\"");
        cmdList.add("/password:" + password);
        cmdList.add("process");
        cmdList.add("call");
        cmdList.add("create");
        cmdList.add(command);

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

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Превышен таймаут запуска команды (" + timeoutSeconds + " сек)");
        }

        return output.toString();
    }
}