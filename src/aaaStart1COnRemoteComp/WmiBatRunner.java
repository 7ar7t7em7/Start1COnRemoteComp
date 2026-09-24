package aaaStart1COnRemoteComp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ����� ��� ������� BAT-����� �� ��������� Windows-���������� ����� WMI (wmic.exe).
 * �������� ��� Windows Server 2003, ��� ��� WinRM.
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
     * ��������� BAT-���� �� ��������� ����������.
     *
     * @param remoteBatPath ������ ���� � .bat �� ��������� �� (��������, "C:\\temp\\run_me.bat")
     * @return ����� wmic (ProcessId, ReturnValue)
     */
    public String runBat(String remoteBatPath) throws IOException, InterruptedException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("wmic");
        cmdList.add("/node:" + host);
        cmdList.add("/user:\"" + username + "\"");
        cmdList.add("/password:" + password);
        cmdList.add("process");
        cmdList.add("call");
        cmdList.add("create");
        // Без кавычек вокруг пути к bat, кавычки только вокруг рабочей директории
        cmdList.add(remoteBatPath + ",\"C:\\ae\"");

        String command = "wmic /node:" + host + " /user:\"" + username + "\" /password:" + password 
                + " process call create \"" + remoteBatPath + "\"";
        Process process = Runtime.getRuntime().exec(command);

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
