package aaaStart1COnRemoteComp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {

    public static void main(String[] args) {
        // ===== НАСТРОЙКИ ПОДКЛЮЧЕНИЯ =====
        String host = "10.1.2.171";
        String user = "svrologin\\admin-db";
        String pass = "22@dMIN22";

        // ===== ПУТИ =====
        // SMB-шара на сервере, куда положим bat и куда bat положит результат.
        // "ae$" — имя административной шары, за которой стоит папка на сервере.
        String remoteShare = "smb://" + host + "/ae$/";

        // Путь к bat-файлу и результату НА СЕРВЕРЕ (локальный путь в файловой системе сервера)
        String remoteBatPath = "C:\\ae\\run_ipconfig.bat";
        String remoteResultFileName = null; // сформируем ниже

        // Локальные пути
        String localBatPath = "C:\\Users\\user\\eclipse-workspace\\aaaStart1COnRemoteComp\\run_ipconfig.bat";
        String localResultDir = "D:\\aeLog\\";

        // ===== ФОРМИРУЕМ ИМЯ ФАЙЛА С ДАТОЙ/ВРЕМЕНЕМ =====
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        remoteResultFileName = "output_" + timestamp + ".txt";
        String remoteResultPath = "C:\\ae\\" + remoteResultFileName;

        System.out.println("Имя файла результата на сервере: " + remoteResultFileName);

        SmbFileTransfer transfer = new SmbFileTransfer(host, user, pass);
        WmiBatRunner runner = new WmiBatRunner(host, user, pass, 60);

        try {
            // ===== ШАГ 1: СОЗДАЁМ BAT-ФАЙЛ ЛОКАЛЬНО =====
            // Содержимое: ipconfig /all с перенаправлением в файл с датой/временем.
            // Кодировка Cp866 — чтобы русский текст внутри корректно работал на сервере.
            String batContent =
                    "@echo off\r\n" +
                    "ipconfig /all > \"" + remoteResultPath + "\" 2>&1\r\n" +
                    "echo DONE >> \"" + remoteResultPath + "\"\r\n";

            Files.write(Paths.get(localBatPath), batContent.getBytes("Cp866"));
            System.out.println("Локальный bat создан: " + localBatPath);

            // ===== ШАГ 2: ЗАГРУЖАЕМ BAT НА СЕРВЕР =====
            transfer.upload(localBatPath, remoteShare, "run_ipconfig.bat");
            System.out.println("BAT загружен на сервер.");

            // ===== ШАГ 3: ЗАПУСКАЕМ BAT ЧЕРЕЗ WMI =====
            String wmiResult = runner.runBat(remoteBatPath);
            System.out.println("WMI ответ:\n" + wmiResult);

            // ===== ШАГ 4: ЖДЁМ ПОЯВЛЕНИЯ РЕЗУЛЬТАТА НА СЕРВЕРЕ =====
            System.out.println("Ждём появления результата на сервере...");
            int attempts = 0;
            int maxAttempts = 30; // 30 * 1 сек = 30 сек максимум
            boolean resultReady = false;

            while (attempts < maxAttempts) {
                if (transfer.exists(remoteShare, remoteResultFileName)) {
                    // Дополнительно проверим, что файл не пустой и дописан до конца
                    Thread.sleep(1000);
                    resultReady = true;
                    break;
                }
                Thread.sleep(1000);
                attempts++;
                System.out.print(".");
            }
            System.out.println();

            if (!resultReady) {
                System.err.println("Результат так и не появился на сервере за " + maxAttempts + " сек.");
                return;
            }

            // ===== ШАГ 5: СКАЧИВАЕМ РЕЗУЛЬТАТ ЛОКАЛЬНО =====
            new File(localResultDir).mkdirs();
            String localResultPath = localResultDir + remoteResultFileName;
            transfer.download(remoteShare, remoteResultFileName, localResultPath);

            System.out.println("ГОТОВО. Результат сохранён: " + localResultPath);

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}