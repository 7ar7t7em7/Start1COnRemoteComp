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

    /**
     * Запускает BAT-файл на удаленном компьютере.
     *
     * @param remoteBatPath Полный путь к .bat на удаленном ПК (например, "C:\\temp\\run_me.bat")
     * @return Вывод wmic (ProcessId, ReturnValue)
     */
    public String runBat(String remoteBatPath) throws IOException, InterruptedException {
        // Формируем команду для wmic:
        // process call create "cmd.exe /c \"путь_к_bat\""
        String wmiCommand = "cmd.exe /c \"" + remoteBatPath + "\"";

        List<String> cmdList = new ArrayList<>();
        cmdList.add("wmic");
        cmdList.add("/node:" + host);
        cmdList.add("/user:" + username);
        cmdList.add("/password:" + password);
        cmdList.add("process");
        cmdList.add("call");
        cmdList.add("create");
        cmdList.add(wmiCommand);

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Читаем вывод wmic (кодировка Cp866 для русского текста)
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
            throw new IOException("Превышен таймаут запуска BAT (" + timeoutSeconds + " сек)");
        }

        return output.toString();
    }
}
