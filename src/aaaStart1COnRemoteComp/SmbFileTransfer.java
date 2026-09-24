package aaaStart1COnRemoteComp;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Класс для передачи файлов между локальным компьютером и удаленным Windows-сервером через SMB (JCIFS).
 * Работает с Windows Server 2003 (SMB 1.0/CIFS).
 */
public class SmbFileTransfer {

    private final NtlmPasswordAuthentication auth;
    private final String host;

    public SmbFileTransfer(String host, String username, String password) {
        this.host = host;
        this.auth = new NtlmPasswordAuthentication("", username, password);
    }

    /**
     * Загружает файл на удаленный сервер.
     *
     * @param localFilePath    Путь к файлу на локальном ПК (например, "C:\\local\\run_me.bat")
     * @param remoteSharePath  Путь к сетевой папке (например, "smb://10.1.2.171/C$/temp/")
     * @param remoteFileName   Имя файла на удаленной стороне (например, "run_me.bat")
     */
    public void upload(String localFilePath, String remoteSharePath, String remoteFileName) throws Exception {
        String remotePath = remoteSharePath + remoteFileName;
        SmbFile remoteFile = new SmbFile(remotePath, auth);

        try (FileInputStream localStream = new FileInputStream(localFilePath);
             SmbFileOutputStream remoteStream = new SmbFileOutputStream(remoteFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = localStream.read(buffer)) != -1) {
                remoteStream.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Загружено: " + remotePath);
    }

    /**
     * Скачивает файл с удаленного сервера.
     *
     * @param remoteSharePath Путь к сетевой папке (например, "smb://10.1.2.171/C$/temp/")
     * @param remoteFileName  Имя файла на удаленной стороне (например, "result.txt")
     * @param localFilePath   Путь для сохранения на локальном ПК
     */
    public void download(String remoteSharePath, String remoteFileName, String localFilePath) throws Exception {
        String remotePath = remoteSharePath + remoteFileName;
        SmbFile remoteFile = new SmbFile(remotePath, auth);

        try (InputStream remoteStream = remoteFile.getInputStream();
             FileOutputStream localStream = new FileOutputStream(localFilePath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = remoteStream.read(buffer)) != -1) {
                localStream.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Скачано: " + localFilePath);
    }

    /**
     * Проверяет существование файла на удаленном сервере.
     */
    public boolean exists(String remoteSharePath, String remoteFileName) throws Exception {
        SmbFile remoteFile = new SmbFile(remoteSharePath + remoteFileName, auth);
        return remoteFile.exists();
    }

    /**
     * Удаляет файл на удаленном сервере.
     */
    public void delete(String remoteSharePath, String remoteFileName) throws Exception {
        SmbFile remoteFile = new SmbFile(remoteSharePath + remoteFileName, auth);
        if (remoteFile.exists()) {
            remoteFile.delete();
        }
    }
}
