package aaaStart1COnRemoteComp;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        //  ЗАМЕНИТЕ НА СВОИ ДАННЫЕ
        String remoteHost = "192.168.1.100";
        String user = "Administrator";
        String pass = "SuperSecretPass123";

        WinRSExecutor executor = new WinRSExecutor(remoteHost, user, pass, 30);

        try {
            System.out.println("Выполняем команду...");
            String result = executor.execute("ipconfig /all");
            System.out.println(result);
        } catch (IOException | InterruptedException e) {
            System.err.println("Ошибка выполнения: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
