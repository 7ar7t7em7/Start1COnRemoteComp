package aaaStart1COnRemoteComp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Запускает команды на удалённом Windows-компьютере через WMI.
 *
 * Для обычных паролей сохраняется прежний запуск через WMIC.
 * Если пароль содержит символы, конфликтующие с разбором аргументов WMIC
 * или cmd.exe, используется PowerShell Get-WmiObject/Invoke-WmiMethod.
 * PowerShell запускается на локальном компьютере; удалённый сервер
 * продолжает получать запросы по WMI/DCOM (в том числе Windows Server 2003).
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
        if (requiresPowerShell()) {
            return runCommandViaPowerShell(command);
        }

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

        return executeAndRead(cmdList, timeoutSeconds,
                "Превышен таймаут запуска команды (" + timeoutSeconds + " сек)");
    }

    /**
     * Запятая разбирается WMIC как разделитель псевдонимов/аргументов.
     * Скобка может нарушить разбор команды оболочкой cmd.exe.
     * Для этих паролей используем WMI API из PowerShell, передавая пароль
     * через окружение процесса, а не в командной строке.
     */
    private boolean requiresPowerShell() {
        return password != null
                && (password.indexOf(',') >= 0
                || password.indexOf(';') >= 0
                || password.indexOf(')') >= 0);
    }

    private String runCommandViaPowerShell(String command) throws IOException, InterruptedException {
        String script =
                "$ErrorActionPreference = 'Stop'\n" +
                "$secure = ConvertTo-SecureString $env:WMI_PASSWORD -AsPlainText -Force\n" +
                "$credential = New-Object System.Management.Automation.PSCredential($env:WMI_USERNAME, $secure)\n" +
                "$result = Invoke-WmiMethod -ComputerName $env:WMI_HOST -Credential $credential " +
                "-Class Win32_Process -Name Create -ArgumentList $env:WMI_COMMAND -ErrorAction Stop\n" +
                "Write-Output ('ProcessId = ' + $result.ProcessId)\n" +
                "Write-Output ('ReturnValue = ' + $result.ReturnValue)\n";

        List<String> commandLine = powerShellCommand(script);
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        Map<String, String> env = builder.environment();
        env.put("WMI_HOST", host);
        env.put("WMI_USERNAME", username);
        env.put("WMI_PASSWORD", password);
        env.put("WMI_COMMAND", command);

        return executeAndRead(builder, timeoutSeconds,
                "Превышен таймаут запуска команды (" + timeoutSeconds + " сек)");
    }

    private String runQueryViaPowerShell(String script, long timeout, String timeoutMessage)
            throws IOException, InterruptedException {
        List<String> commandLine = powerShellCommand(script);
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        Map<String, String> env = builder.environment();
        env.put("WMI_HOST", host);
        env.put("WMI_USERNAME", username);
        env.put("WMI_PASSWORD", password);
        return executeAndRead(builder, timeout, timeoutMessage);
    }

    private List<String> powerShellCommand(String script) {
        String encodedScript = Base64.getEncoder().encodeToString(
                script.getBytes(StandardCharsets.UTF_16LE));

        List<String> commandLine = new ArrayList<>();
        commandLine.add("powershell.exe");
        commandLine.add("-NoLogo");
        commandLine.add("-NoProfile");
        commandLine.add("-NonInteractive");
        commandLine.add("-ExecutionPolicy");
        commandLine.add("Bypass");
        commandLine.add("-EncodedCommand");
        commandLine.add(encodedScript);
        return commandLine;
    }

    /**
     * Проверяет, запущен ли процесс на удалённом компьютере.
     * @param processName имя процесса (например, "1cv7s.exe")
     */
    public boolean isProcessRunning(String processName) throws IOException, InterruptedException {
        if (requiresPowerShell()) {
            String script =
                    "$ErrorActionPreference = 'Stop'\n" +
                    "$secure = ConvertTo-SecureString $env:WMI_PASSWORD -AsPlainText -Force\n" +
                    "$credential = New-Object System.Management.Automation.PSCredential($env:WMI_USERNAME, $secure)\n" +
                    "$filter = \"Name=\'\" + $env:WMI_PROCESS_NAME.Replace(\"\'\", \"\'\'\") + \"\'\"\n" +
                    "$items = Get-WmiObject -ComputerName $env:WMI_HOST -Credential $credential " +
                    "-Class Win32_Process -Filter $filter -ErrorAction Stop\n" +
                    "if ($items) { Write-Output ('ProcessId = ' + $items[0].ProcessId) } else { Write-Output 'ProcessId' }\n";
            List<String> commandLine = powerShellCommand(script);
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.environment().put("WMI_HOST", host);
            builder.environment().put("WMI_USERNAME", username);
            builder.environment().put("WMI_PASSWORD", password);
            builder.environment().put("WMI_PROCESS_NAME", processName);
            String output = executeAndRead(builder, timeoutSeconds,
                    "Превышен таймаут проверки процесса (" + timeoutSeconds + " сек)");
            return output.matches("(?s).*ProcessId\\s*=\\s*\\d+.*");
        }

        List<String> cmdList = new ArrayList<>();
        cmdList.add("cmd.exe");
        cmdList.add("/c");
        cmdList.add("wmic");
        cmdList.add("/node:" + host);
        cmdList.add("/user:\"" + username + "\"");
        cmdList.add("/password:" + password);
        cmdList.add("process");
        cmdList.add("where");
        cmdList.add("name='" + processName + "'");
        cmdList.add("get");
        cmdList.add("processid");

        String result = executeAndRead(cmdList, timeoutSeconds,
                "Превышен таймаут проверки процесса (" + timeoutSeconds + " сек)");
        return result.matches("(?s).*ProcessId\\s+\\d+.*");
    }

    public boolean isProcessAliveById(long pid) throws IOException, InterruptedException {
        if (requiresPowerShell()) {
            String script =
                    "$ErrorActionPreference = 'Stop'\n" +
                    "$secure = ConvertTo-SecureString $env:WMI_PASSWORD -AsPlainText -Force\n" +
                    "$credential = New-Object System.Management.Automation.PSCredential($env:WMI_USERNAME, $secure)\n" +
                    "$items = Get-WmiObject -ComputerName $env:WMI_HOST -Credential $credential " +
                    "-Class Win32_Process -Filter ('ProcessId=' + $env:WMI_PROCESS_ID) -ErrorAction Stop\n" +
                    "if ($items) { Write-Output ('ProcessId = ' + $items[0].ProcessId) } else { Write-Output 'ProcessId' }\n";
            List<String> commandLine = powerShellCommand(script);
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.environment().put("WMI_HOST", host);
            builder.environment().put("WMI_USERNAME", username);
            builder.environment().put("WMI_PASSWORD", password);
            builder.environment().put("WMI_PROCESS_ID", Long.toString(pid));
            String output = executeAndRead(builder, 10,
                    "Превышен таймаут проверки процесса (" + 10 + " сек)");
            return output.matches("(?s).*ProcessId\\s*=\\s*\\d+.*");
        }

        List<String> cmdList = new ArrayList<>();
        cmdList.add("cmd.exe");
        cmdList.add("/c");
        cmdList.add("wmic");
        cmdList.add("/node:" + host);
        cmdList.add("/user:\"" + username + "\"");
        cmdList.add("/password:" + password);
        cmdList.add("process");
        cmdList.add("where");
        cmdList.add("processid=" + pid);
        cmdList.add("get");
        cmdList.add("processid");

        String output = executeAndRead(cmdList, 10,
                "Превышен таймаут проверки процесса (10 сек)");
        return output.matches("(?s).*ProcessId\\s+\\d+.*");
    }

    private String executeAndRead(List<String> commandLine, long timeout, String timeoutMessage)
            throws IOException, InterruptedException {
        return executeAndRead(new ProcessBuilder(commandLine), timeout, timeoutMessage);
    }

    private String executeAndRead(ProcessBuilder builder, long timeout, String timeoutMessage)
            throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "Cp866"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException(timeoutMessage);
        }
        return output.toString();
    }
}
