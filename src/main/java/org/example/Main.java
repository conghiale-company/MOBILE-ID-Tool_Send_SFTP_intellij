package org.example;

import com.jcraft.jsch.*;
import org.apache.log4j.Logger;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Scanner;
import java.util.TreeMap;

public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class);
    private static String HOST = "192.168.2.222";
    private static String USER = "root";
    private static String PRIVATE_KEY_PATH = "src/main/resources/priv.txt"; // Path to your private key file
    private static String LOCAL_ROOT_FOLDER = "D:/Data/MOBILE_ID/TOOL_SFTP/files_b3/2408"; // Thư mục local gốc
    private static String REMOTE_ROOT_FOLDER = "nghialc/files_b3"; // Thư mục gốc trên server
    private static int PORT = 22;

    private static int NUMBER_RETRY = 0;

    private static Scanner sc = new Scanner(System.in);

    private static String PATH_FILE_CONFIG = "";
    private static String PATH_SEND_EMAIL_CONFIG = "";
    private static String PATH_FILES_NAME = "";

    private static String FROM_EMAIL; //requires valid gmail id
    private static String PASSWORD; // correct password for gmail id
    private static String TO_EMAIL; // can be any email id
    private static String SMTP_HOST;
    private static String TLS_PORT;
    private static String ENABLE_AUTHENTICATION;
    private static String ENABLE_STARTTLS;

    private static String hash = "";
    private static String dayFolder = "";
    private static String folder1 = "";
    private static String folder2 = "";
    private static String folder3 = "";

    private static Session session = null;
    private static ChannelSftp channelSftp = null;

    public static void main(String[] args) throws Exception {
//        File file = new File("D:\\Data\\MOBILE_ID\\TOOL_SFTP\\DEMO\\test\\local_0e0fea374dd84fb0ca05ec9a1e751f4d9752b5c2");
//        uploadFile(file, "");
//        System.exit(0);

        System.out.println("Choose Function: ");
        System.out.println("0: Exit");
        System.out.println("1: Send file in txt");
        System.out.println("2: Send all files in folder root by SFTP");
        System.out.println("3: Move all file in one folder [LOCAL]");
        System.out.println("4: Copy all file in csv [LOCAL]");

        System.out.print("Enter the function you want: ");
        int function = sc.nextInt();

        if (function == 0) {
            LOG.info("The program has stopped.");
        } else if (function == 1 || function == 4) {
//            Send file follow file txt
            if (args.length > 2 && args[0] != null && !args[0].isEmpty()
                    && args[1] != null && !args[1].isEmpty() && args[2] != null && !args[2].isEmpty()) {
                PATH_FILE_CONFIG = args[0];
                PATH_SEND_EMAIL_CONFIG = args[1];
                PATH_FILES_NAME = args[2];
                getFileConfig();
            } else {
                LOG.warn("Invalid parameter");
                System.exit(0);
            }
            if (function == 1)
                connectSFTPAndReadAllFileNameInFileTXT();
            else
                copyAllFileInFileCSV();
        } else {
            if (args.length > 1 && args[0] != null && !args[0].isEmpty()
                    && args[1] != null && !args[1].isEmpty()) {
                PATH_FILE_CONFIG = args[0];
                PATH_SEND_EMAIL_CONFIG = args[1];
                getFileConfig();
            } else {
                LOG.warn("Invalid parameter");
                System.exit(0);
            }

            if (function == 2) {
//            Send all file in folder
                connectSFTPAndReadAllFileInFolderRoot();
            }   else if (function == 3) {
//            Send all file in folder
                moveAllFileInOneFolder();
            }
        }
    }

    private static void getFileConfig() {
        TreeMap<String, Object> map = Utils.readFileConfig(PATH_FILE_CONFIG);
        if (map != null) {
            for (String key : map.keySet()) {
                switch (key) {
                    case "HOST":
                        HOST = String.valueOf(map.get(key));
                        break;

                    case "PORT":
                        PORT = Integer.parseInt(String.valueOf(map.get(key)));
                        break;

                    case "USER":
                        USER = String.valueOf(map.get(key));
                        break;

                    case "PRIVATE_KEY_PATH":
                        PRIVATE_KEY_PATH = String.valueOf(map.get(key));
                        break;

                    case "LOCAL_ROOT_FOLDER":
                        LOCAL_ROOT_FOLDER = String.valueOf(map.get(key));
                        break;

                    case "REMOTE_ROOT_FOLDER":
                        REMOTE_ROOT_FOLDER = String.valueOf(map.get(key));
                        break;
                }
            }

            if (HOST == null || HOST.isEmpty() || PORT == -1 || USER == null || USER.isEmpty() ||
                    PRIVATE_KEY_PATH == null || PRIVATE_KEY_PATH.isEmpty() || LOCAL_ROOT_FOLDER == null || LOCAL_ROOT_FOLDER.isEmpty() ||
                    REMOTE_ROOT_FOLDER == null || REMOTE_ROOT_FOLDER.isEmpty()) {
                LOG.warn("Invalid configuration parameter");
                System.exit(0);
            } else
                LOG.info("Configuration connect SFTP parameters loaded successfully");

            System.out.println();
        }
    }

    public static void uploadFilesRecursively(File currentFolder, String remoteRootFolder) throws Exception {
        File[] filesAndDirs = currentFolder.listFiles();

        if (filesAndDirs != null) {
            for (File item : filesAndDirs) {
                if (item.isDirectory()) {
                    // Nếu là thư mục, tiếp tục xử lý đệ quy
                    uploadFilesRecursively(item, remoteRootFolder);
                } else {
                    // Nếu là file, gửi file qua SFTP theo cấu trúc mới
                    uploadFile(item, remoteRootFolder);
                    Utils.wait(300);
                }
            }
        }
    }

    public static void uploadFile(File file, String remoteRootFolder) throws Exception {
        System.out.println();
        String fileName = file.getName();

        if (hash == null || hash.isEmpty())
            hash = extractHashFromFileName(fileName);

        if (hash != null) {
            // Tạo cấu trúc đường dẫn mới trên server
            String _dayFolder;

            if (fileName.length() == 50) {
                _dayFolder = (dayFolder == null || dayFolder.isEmpty()) ? fileName.substring(6, 10) : dayFolder;

            } else {
                _dayFolder = getDayOfFile(file.getAbsolutePath());
            }

            String _folder1 = (folder1 == null || folder1.isEmpty()) ? hash.substring(0, 1) : folder1;
            String _folder2 = (folder2 == null || folder2.isEmpty()) ? hash.substring(0, 2) : folder2;
            String _folder3 = (folder3 == null || folder3.isEmpty()) ? hash.substring(0, 3) : folder3;

            String remoteFolderPath = remoteRootFolder + "/" + _dayFolder + "/" + _folder1 + "/" + _folder2 + "/" + _folder3;
            String path = _dayFolder + "/" + _folder1 + "/" + _folder2 + "/" + _folder3;

//            System.out.println("remoteFolderPath: " + remoteFolderPath);
//            System.out.println("path: " + path);
//            System.exit(0);

            try {
                channelSftp.stat(remoteFolderPath);
                LOG.info("FOLDER ALREADY EXISTS");
                if (channelSftp.pwd().contains(remoteRootFolder))
                    channelSftp.cd(path);
                else
                    channelSftp.cd(remoteFolderPath);
            } catch (SftpException e) {
                if (NUMBER_RETRY < 5 && e.getMessage().contains(" connection is closed by foreign host")) {
                    LOG.info("\nRETRY CONNECT: [" + NUMBER_RETRY + "]");
                    NUMBER_RETRY++;
                    connect();
                    if (file.exists()) {
                        uploadFile(file, remoteRootFolder);
                    } // else continue
                } else {
                    LOG.warn("FOLDER NOT EXIST");
                    LOG.info("CREATING FOLDER...");
                    String[] folders = path.split( "/" );

                    StringBuilder createFolderPath = new StringBuilder();
                    for ( String folder : folders ) {
                        if (!folder.isEmpty()) {
                            createFolderPath.append(folder).append("/");
                            try {
                                channelSftp.cd( folder );
                            }
                            catch ( SftpException ex) {
                                channelSftp.mkdir( folder );
                                channelSftp.cd( folder );
                            }
                        }
                    }
                    LOG.info("THE FOLDER HAS BEEN CREATED SUCCESSFULLY");
                }
            }

            // Chuyển đổi đường dẫn file và gửi file lên server
            String remoteFilePath = remoteFolderPath + "/" + fileName;

            String formattedDateTime = getDayTime();

            try (FileInputStream fis = new FileInputStream(file)) {
                channelSftp.put(fis, fileName);

                LOG.info(formattedDateTime + " - " + remoteFilePath + " - SUCCESSFULLY");
                LOG.info(remoteFilePath + " - SUCCESSFULLY");

                fis.close();
                if (file.exists()) {
                    if (file.canWrite()) {
                        if (file.delete()) {
                            LOG.info("File deleted successfully");
                            NUMBER_RETRY = 0;
                        } else {
                            LOG.warn("Failed to delete the file");
                        }
                    } else {
                        LOG.warn("File cannot be written or deleted. Check file permissions.");
                    }
                } else {
                    LOG.warn("File does not exist");
                }

            } catch (IOException e) {
                if (NUMBER_RETRY < 5 && e.getMessage().contains(" connection is closed by foreign host")) {
                    LOG.info("\nRETRY CONNECT: [" + NUMBER_RETRY + "]");
                    NUMBER_RETRY++;
                    connect();
                    if (file.exists()) {
                        uploadFile(file, remoteRootFolder);
                    } // else continue
                } else {
                    System.out.println();
                    LOG.error(formattedDateTime + " - " + file.getAbsolutePath() + " - ERROR");

                    System.out.println();
                    e.printStackTrace();

                    LOG.info(file.getAbsolutePath() + " - ERROR: " + e.getMessage());
                    LOG.warn(file.getAbsolutePath());

                    formattedDateTime = getDayTime();
                    String subject = "TOOL SEND FILE SFTP HAS AN ERROR";
                    String body = "ERROR INFORMATION: \n" +
                            "Message: " + e.getMessage() + "\n" +
                            "Day: " + formattedDateTime + "\n" +
                            "File_Name: " + fileName + "\n" +
                            "Path: " + file.getAbsolutePath();

                    sendEmail(subject, body);
                    System.exit(0);
                }
            }

            folder1 = "";
            folder2 = "";
            folder3 = "";
            hash = "";
            channelSftp.cd(REMOTE_ROOT_FOLDER);
        } else {
            String formattedDateTime = getDayTime();
            LOG.warn(formattedDateTime + " - " + file.getAbsolutePath() + " - INVALID FILE NAME FORMAT");
            LOG.info(file.getAbsolutePath() + " - ERROR: INVALID FILE NAME FORMAT");
            LOG.warn(file.getAbsolutePath());
//            String formattedDateTime = getDayTime();
//            String subject = "TOOL SEND FILE SFTP HAS AN ERROR";
//            String body = "ERROR INFORMATION: \n" +
//                    "Message: " + "INVALID FILE NAME FORMAT" + "\n" +
//                    "Day: " + formattedDateTime + "\n" +
//                    "File_Name: " + fileName + "\n" +
//                    "Path: " + file.getAbsolutePath();
//
//            sendEmail(subject, body);
//            System.exit(0);
        }
    }

    public static String extractHashFromFileName(String fileName) {
//        int underscoreIndex = fileName.indexOf('_');
//        int hashStartIndex = underscoreIndex + 5;
//        int hashEndIndex = fileName.lastIndexOf(".txt");
        if (fileName.contains("local_") && !fileName.contains(".tmp")) {
            if (fileName.length() == 50) {
                return fileName.substring(10);
            } else if (fileName.length() == 46)
                return fileName.substring(6);
        }

        return null;
    }

    private static void sendEmail(String subject, String body) {
        System.out.println();
        getSendEmailConfig(PATH_SEND_EMAIL_CONFIG); // Read file config to send email
        LOG.info("TLSEmail Start");
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST); //SMTP Host
        props.put("mail.smtp.port", TLS_PORT); //TLS Port
        props.put("mail.smtp.auth", ENABLE_AUTHENTICATION); //enable authentication
        props.put("mail.smtp.starttls.enable", ENABLE_STARTTLS); //enable STARTTLS

        //create Authenticator object to pass in Session.getInstance argument
        Authenticator auth = new Authenticator() {
            //override the getPasswordAuthentication method
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, PASSWORD);
            }
        };
        javax.mail.Session session = javax.mail.Session.getInstance(props, auth);

        Utils.sendEmail(session, TO_EMAIL,subject, body);
    }

    private static String getDayOfFile(String path) {
        Path filePath = Paths.get(path); // Đường dẫn tới file

        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

            // Lấy ngày tạo file
            Instant lastModifiedTime = attrs.lastModifiedTime().toInstant();
            LocalDateTime lastModifiedData = LocalDateTime.ofInstant(lastModifiedTime, ZoneId.systemDefault());

            // Lấy tháng và 2 số cuối của năm
            int month = lastModifiedData.getMonthValue();  // Lấy tháng
            int year = lastModifiedData.getYear() % 100;   // Lấy 2 số cuối của năm

            // Kết hợp tháng và 2 số cuối năm
            return String.format("%02d%02d", year, month);

        } catch (IOException e) {
            LOG.error("Lỗi khi lấy thông tin file: " + e.getMessage());
            return null;
        }
    }

    private static String getDayTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }

    private static void getSendEmailConfig(String pathConfig) {
        TreeMap<String, Object> map = Utils.readSendEmailConfig(pathConfig);
        if (map != null) {
            for (String key : map.keySet()) {
                switch (key) {
                    case "FROM_EMAIL":
                        FROM_EMAIL = String.valueOf(map.get(key));
                        break;

                    case "PASSWORD":
                        PASSWORD = String.valueOf(map.get(key));
                        break;

                    case "TO_EMAIL":
                        TO_EMAIL = String.valueOf(map.get(key));
                        break;

                    case "SMTP_HOST":
                        SMTP_HOST = String.valueOf(map.get(key));
                        break;

                    case "TLS_PORT":
                        TLS_PORT = String.valueOf(map.get(key));
                        break;

                    case "ENABLE_AUTHENTICATION":
                        ENABLE_AUTHENTICATION = String.valueOf(map.get(key));
                        break;

                    case "ENABLE_STARTTLS":
                        ENABLE_STARTTLS = String.valueOf(map.get(key));
                        break;
                }
            }

            if (FROM_EMAIL == null || FROM_EMAIL.isEmpty() || PASSWORD == null || PASSWORD.isEmpty() ||
                    TO_EMAIL == null || TO_EMAIL.isEmpty() || SMTP_HOST == null || SMTP_HOST.isEmpty() ||
                    TLS_PORT == null || TLS_PORT.isEmpty() || ENABLE_AUTHENTICATION == null || ENABLE_AUTHENTICATION.isEmpty() ||
                    ENABLE_STARTTLS == null || ENABLE_STARTTLS.isEmpty()) {
                LOG.warn("Invalid configuration parameter");
                System.exit(0);
            } else
                LOG.info("Configuration send Email parameters loaded successfully");
        }
    }

    private static void connectSFTPAndReadAllFileNameInFileTXT() throws JSchException {
        try {
            if (channelSftp == null || !channelSftp.isConnected())
                connect();

            // Gửi các file qua SFTP và lưu trên server
            try (BufferedReader br = new BufferedReader(new FileReader(PATH_FILES_NAME))) {
                String fileName;
                while ((fileName = br.readLine()) != null) {
                    hash = extractHashFromFileName(fileName);
                    String localFolderPath = "";

                    if (hash != null) {
                        folder1 = hash.substring(0, 1);
                        folder2 = hash.substring(0, 2);
                        folder3 = hash.substring(0, 3);

                        if (fileName.length() == 50) {
                            dayFolder = fileName.substring(6, 10);
                            localFolderPath = LOCAL_ROOT_FOLDER + "/" + dayFolder + "/" + folder1 + "/" + folder2 + "/" + folder3 + "/" + fileName;
                        } else {
                            dayFolder = "";
                            localFolderPath = LOCAL_ROOT_FOLDER + "/" + folder1 + "/" + folder2 + "/" + folder3 + "/" + fileName;
                        }
                    }

                    File file = new File(localFolderPath);
                    if (file.exists()) {
                        uploadFile(file, REMOTE_ROOT_FOLDER);
                    }
                    else {
                        LOG.warn("File does not exist with path " + localFolderPath);
                        LOG.info(file.getAbsolutePath() + " - ERROR: File does not exist with path " + localFolderPath);
                        LOG.warn(file.getAbsolutePath());
//                        String formattedDateTime = getDayTime();
//                        String subject = "TOOL SEND FILE SFTP HAS FAILED WITH FILE READING PROBLEM";
//                        String body = "ERROR INFORMATION: \n" +
//                                "Message: " + "FILE DOES NOT EXIST" + "\n" +
//                                "Day: " + formattedDateTime + "\n";
//
//                        sendEmail(subject, body);
//                        System.exit(0);
                    }
                }
            } catch (IOException e) {
                if (NUMBER_RETRY < 5 && e.getMessage().contains("connection is closed by foreign host")) {
                    LOG.info("\nRETRY CONNECT*: [" + NUMBER_RETRY + "]");
                    NUMBER_RETRY++;
                    connect();
                    connectSFTPAndReadAllFileNameInFileTXT();
                } else {
                    LOG.error("Cannot read file with path " + PATH_FILES_NAME);
                    e.printStackTrace();

                    String formattedDateTime = getDayTime();
                    String subject = "TOOL SEND FILE SFTP HAS FAILED WITH FILE READING PROBLEM";
                    String body = "ERROR INFORMATION: \n" +
                            "Message: " + e.getMessage() + "\n" +
                            "Day: " + formattedDateTime + "\n";

                    sendEmail(subject, body);
                    System.exit(0);
                }
            }

            LOG.info("\nFile transferred successfully");

        } catch (Exception e) {
            if (NUMBER_RETRY < 5 && e.getMessage().contains("connection is closed by foreign host")) {
                LOG.info("\nRETRY CONNECT**: [" + NUMBER_RETRY + "]");
                NUMBER_RETRY++;
                connect();
                connectSFTPAndReadAllFileNameInFileTXT();
            } else {
                System.out.println();
                e.printStackTrace();

                String formattedDateTime = getDayTime();
                String subject = "TOOL SEND FILE SFTP HAS FAILED WITH SERVER CONNECTION PROBLEM";
                String body = "ERROR INFORMATION: \n" +
                        "Message: " + e.getMessage() + "\n" +
                        "Day: " + formattedDateTime + "\n";

                sendEmail(subject, body);
                System.exit(0);
            }
        } finally {
            if (NUMBER_RETRY == 0) {
                if (channelSftp != null) {
                    channelSftp.exit();
                }
                if (session != null) {
                    session.disconnect();
                }

                String formattedDateTime = getDayTime();
                LOG.info(formattedDateTime + " - TOOL SEND FILE SFTP COMPLETED SENDING ALL FILES");
                String subject = "TOOL SEND FILE SFTP COMPLETED SENDING ALL FILES";
                String body = "INFORMATION: \n" +
                        "Message: " + "COMPLETED" + "\n" +
                        "Day: " + formattedDateTime + "\n";

                sendEmail(subject, body);
                System.exit(0);
            }
        }
    }

    private static void connectSFTPAndReadAllFileInFolderRoot() throws JSchException {
        try {
            if (channelSftp == null || !channelSftp.isConnected())
                connect();

            // Gửi các file qua SFTP và lưu trên server
            File localFolder = new File(LOCAL_ROOT_FOLDER);
            if (localFolder.exists() && localFolder.isDirectory()) {
                uploadFilesRecursively(localFolder, REMOTE_ROOT_FOLDER);
            } else {
                LOG.warn("The source directory does not exist or is not a directory.");
            }

            LOG.info("\nFile transferred successfully");

        } catch (Exception e) {
            if (NUMBER_RETRY < 5 && e.getMessage().contains("connection is closed by foreign host")) {
                LOG.info("\nRETRY CONNECT: [" + NUMBER_RETRY + "]");
                NUMBER_RETRY++;
                connect();
                connectSFTPAndReadAllFileInFolderRoot();
            } else {
                System.out.println();
                e.printStackTrace();

                String formattedDateTime = getDayTime();
                String subject = "TOOL SEND FILE SFTP HAS FAILED WITH SERVER CONNECTION PROBLEM";
                String body = "ERROR INFORMATION: \n" +
                        "Message: " + e.getMessage() + "\n" +
                        "Day: " + formattedDateTime + "\n";

                sendEmail(subject, body);
                System.exit(0);
            }
        } finally {
            if (NUMBER_RETRY == 0) {
                if (channelSftp != null) {
                    channelSftp.exit();
                }
                if (session != null) {
                    session.disconnect();
                }

                String formattedDateTime = getDayTime();
                LOG.info(formattedDateTime + " - TOOL SEND FILE SFTP COMPLETED SENDING ALL FILES");
                String subject = "TOOL SEND FILE SFTP COMPLETED SENDING ALL FILES";
                String body = "INFORMATION: \n" +
                        "Message: " + "COMPLETED" + "\n" +
                        "Day: " + formattedDateTime + "\n";

                sendEmail(subject, body);
                System.exit(0);
            }
        }
    }

//    Di chuyen tat ca cac file local_... trong mot folder (chạy local)
    private static void moveAllFileInOneFolder() throws JSchException, IOException {
        File sourceFolder = new File(LOCAL_ROOT_FOLDER);
        if (sourceFolder.exists() && sourceFolder.isDirectory()) {
            File[] filesAndDirs = sourceFolder.listFiles();

            if (filesAndDirs != null) {
                for (File item : filesAndDirs) {
                    if (item.isDirectory()) {
                        // Recursively process the folder
                        processFolder(item);
                    }
                    if (item.isFile() && item.getName().contains("local_")) {
                        moveFile(item, REMOTE_ROOT_FOLDER);
                    }
                }

                LOG.info("[SUCCESS] All files have been moved successfully.");
                String formattedDateTime = getDayTime();
                String subject = "TOOL MOVE FILE LOCAL COMPLETED";
                String body = "ERROR INFORMATION: \n" +
                        "Message: " + "ALL FILES HAVE BEEN MOVED SUCCESSFULLY" + "\n" +
                        "Day: " + formattedDateTime + "\n" +
                        "REMOTE_ROOT_FOLDER: " + REMOTE_ROOT_FOLDER;

                sendEmail(subject, body);
                System.exit(0);
            }
        } else {
            LOG.error("[ERROR] The source folder does not exist or is not a folder.");
        }
    }

    private static void copyAllFileInFileCSV() {
        File sourceFolder = new File(LOCAL_ROOT_FOLDER);
        if (sourceFolder.exists() && sourceFolder.isDirectory()) {
            File[] filesAndDirs = sourceFolder.listFiles();

            if (filesAndDirs != null) {
                // Read CSV file and perform processing
                try (BufferedReader csvReader = new BufferedReader(new FileReader(PATH_FILES_NAME))) {
                    String line;
                    while ((line = csvReader.readLine()) != null) {
                        String[] columns = line.split(","); // Columns in a CSV file are separated by commas.
                        if (columns.length < 4) {
                            LOG.warn("[WARM] Dòng không hợp lệ: " + line);
                            continue;
                        }

                        String originalFileName = columns[0].trim(); // Tên file gốc
                        String newFileName01 = columns[1].trim();
                        String newFileName02 = columns[2].trim();
                        String newFileName03 = columns[3].trim();
                        String newFileName;

                        if (newFileName01.split("\\.").length < 2) {
                            newFileName = newFileName01 + "_" + newFileName02 + "_" + newFileName03;
                        } else {
                            newFileName = newFileName01.split("\\.")[0] + "_" + newFileName02 + "_" + newFileName03 + "." + newFileName01.split("\\.")[1];
                        }

                        hash = extractHashFromFileName(originalFileName);
                        String localFolderPath = "";

                        if (hash != null) {
                            folder1 = hash.substring(0, 1);
                            folder2 = hash.substring(0, 2);
                            folder3 = hash.substring(0, 3);

                            if (originalFileName.length() == 50) {
                                dayFolder = originalFileName.substring(6, 10);
                                localFolderPath = LOCAL_ROOT_FOLDER + "/" + dayFolder + "/" + folder1 + "/" + folder2 + "/" + folder3 + "/" + originalFileName;
                            } else {
                                dayFolder = "";
                                localFolderPath = LOCAL_ROOT_FOLDER + "/" + folder1 + "/" + folder2 + "/" + folder3 + "/" + originalFileName;
                            }
                        }

                        File file = new File(localFolderPath);
//                        File originalFile = new File(LOCAL_ROOT_FOLDER, originalFileName);
                        if (file.exists()) {
                            copyFile(file, REMOTE_ROOT_FOLDER, newFileName);
                        }
                        else {
                            LOG.warn(originalFileName + " - ERROR: File does not exist with path " + file.getAbsolutePath());
                            LOG.fatal(file.getAbsolutePath());

//                            String formattedDateTime = getDayTime();
//                            String subject = "TOOL MOVE FILE LOCAL HAS AN ERROR";
//                            String body = "ERROR INFORMATION: \n" +
//                                    "Message: " + "FILE DOES NOT EXIST" + "\n" +
//                                    "Day: " + formattedDateTime + "\n" +
//                                    "File : " + file.getAbsolutePath();;
//
//                            sendEmail(subject, body);
//                            System.exit(0);
                        }
                    }
                } catch (IOException e) {
                    LOG.error("[ERROR] Lỗi khi đọc file CSV.");
                    LOG.error("[ERROR] " + e);

                    String formattedDateTime = getDayTime();
                    String subject = "TOOL MOVE FILE LOCAL HAS AN ERROR";
                    String body = "ERROR INFORMATION: \n" +
                            "[ERROR]: " + e + "\n" +
                            "Day: " + formattedDateTime + "\n";

                    sendEmail(subject, body);
                    System.exit(0);
                }

                LOG.info("[SUCCESS] ALL FILES HAVE BEEN PROCESSED.");
                String formattedDateTime = getDayTime();
                String subject = "TOOL COPY FILE LOCAL COMPLETED";
                String body = "ERROR INFORMATION: \n" +
                        "Message: " + "ALL FILES IN CSV FILE HAVE BEEN PROCESSED" + "\n" +
                        "Day: " + formattedDateTime + "\n" +
                        "LOCAL_ROOT_FOLDER:" + LOCAL_ROOT_FOLDER + "\n" +
                        "REMOTE_ROOT_FOLDER: " + REMOTE_ROOT_FOLDER;

                sendEmail(subject, body);
                System.exit(0);
            }
        } else {
            LOG.error("[ERROR] The source folder does not exist or is not a folder.");
        }
    }

    private static void processFolder(File folder) throws JSchException {
        File[] filesAndDirs = folder.listFiles();
        if (filesAndDirs != null) {
            for (File item : filesAndDirs) {
                if (item.isDirectory()) {
                    processFolder(item); // Recursively process subfolders
                }
                if (item.isFile() && item.getName().contains("local_")) {
                    try {
                        moveFile(item, REMOTE_ROOT_FOLDER);
                    } catch (IOException e) {
                        System.err.println("Cannot move files: " + item.getAbsolutePath());
                        e.printStackTrace();

                        String formattedDateTime = getDayTime();
                        String subject = "TOOL MOVE FILE LOCAL HAS AN ERROR";
                        String body = "ERROR INFORMATION: \n" +
                                "Message: " + e.getMessage() + "\n" +
                                "Day: " + formattedDateTime + "\n" +
                                "File Name: " + item.getAbsolutePath() + "\n" +
                                "REMOTE_ROOT_FOLDER: " + REMOTE_ROOT_FOLDER;

                        sendEmail(subject, body);
                        System.exit(0);
                    }
                }
            }
        }
    }

    public static void moveFile(File file, String targetRootFolder) throws IOException {
        System.out.println();
        String fileName = file.getName();
        String hash = extractHashFromFileName(fileName);

        if (hash != null) {
            String dayFolder;

            if (fileName.length() == 50) {
                dayFolder = fileName.substring(6, 10);

            } else {
                dayFolder = getDayOfFile(file.getAbsolutePath());
            }

            String folder1 = hash.substring(0, 1);
            String folder2 = hash.substring(0, 2);
            String folder3 = hash.substring(0, 3);

            String targetFolderPath = targetRootFolder + File.separator + dayFolder + File.separator + folder1 + File.separator + folder2 + File.separator + folder3;

            File targetFolder = new File(targetFolderPath);
            if (!targetFolder.exists()) {
                LOG.warn("Folder " + targetFolder + " does not exist");
                if (targetFolder.mkdirs()) {
                    LOG.info("The folder " + targetFolder + " has been created successfully.");
                } else {
                    LOG.warn("Cannot to create folder " + targetFolder);
                    String formattedDateTime = getDayTime();
                    String subject = "TOOL MOVE FILE LOCAL HAS AN ERROR";
                    String body = "ERROR INFORMATION: \n" +
                            "Message: " + "CANNOT CREATE FOLDER WITH PATH " + targetFolder + "\n" +
                            "Day: " + formattedDateTime + "\n" +
                            "Folder : " + targetFolder;

                    sendEmail(subject, body);
                    System.exit(0);
                }
            }

            try {
                // Di chuyển file đến vị trí mới
                Path targetPath = new File(targetFolder, fileName).toPath();
                Files.move(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                // Hiển thị thông báo
                LocalDateTime currentDateTime = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy-HH:mm:ss");
                String formattedDateTime = currentDateTime.format(formatter);

                LOG.info(formattedDateTime + " - " + targetPath + " - MOVE SUCCESSFULLY");
                LOG.info(targetPath + " - MOVE SUCCESSFULLY");
            } catch (Exception e) {
                LOG.error("Cannot move file: " + file.getAbsolutePath());
                LOG.error("ERROR: " + e.getMessage());

                LOG.error("Cannot move file " + file.getAbsolutePath());
                String formattedDateTime = getDayTime();
                String subject = "TOOL MOVE FILE LOCAL HAS AN ERROR";
                String body = "ERROR INFORMATION: \n" +
                        "Message: " + "CANNOT MOVE FILE WITH PATH " + file.getAbsolutePath() + "\n" +
                        "Day: " + formattedDateTime + "\n" +
                        "File : " + file.getAbsolutePath();

                sendEmail(subject, body);
                System.exit(0);
            }
        }
    }

    public static void copyFile(File originalFile, String targetRootFolder, String newFileName) throws IOException {
        String fileName = originalFile.getName();
        String hash = extractHashFromFileName(fileName);

        if (hash != null) {
//            String dayFolder;
//
//            if (fileName.length() == 50) {
//                dayFolder = fileName.substring(6, 10);
//
//            } else {
//                dayFolder = getDayOfFile(originalFile.getAbsolutePath());
//            }
//
//            String folder1 = hash.substring(0, 1);
//            String folder2 = hash.substring(0, 2);
//            String folder3 = hash.substring(0, 3);
//
//            String targetFolderPath = targetRootFolder + File.separator + dayFolder + File.separator + folder1 + File.separator + folder2 + File.separator + folder3;

            File targetFolder = new File(targetRootFolder);
            if (!targetFolder.exists()) {
                LOG.info("Folder " + targetFolder + " does not exist");
                if (targetFolder.mkdirs()) {
                    LOG.info("The folder " + targetFolder + " has been created successfully.");
                } else {
                    LOG.warn("[WARN]Cannot to create folder " + targetFolder);
                    String formattedDateTime = getDayTime();
                    String subject = "TOOL COPY FILE LOCAL HAS AN ERROR";
                    String body = "ERROR INFORMATION: \n" +
                            "Message: " + "CANNOT CREATE FOLDER WITH PATH " + targetFolder + "\n" +
                            "Day: " + formattedDateTime + "\n" +
                            "Folder : " + targetFolder;

                    sendEmail(subject, body);
                    System.exit(0);
                }
            }

            try {
                // Di chuyển file đến vị trí mới
                Path targetPath = new File(targetFolder, newFileName).toPath();
                Files.copy(originalFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                // Hiển thị thông báo
                LocalDateTime currentDateTime = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy-HH:mm:ss");
                String formattedDateTime = currentDateTime.format(formatter);

                LOG.info(formattedDateTime + " - " + targetPath + " - COPY SUCCESSFULLY");
            } catch (Exception e) {
                LOG.error("[ERROR] Cannot copy file: " + originalFile.getAbsolutePath());
                LOG.error("[ERROR] " + e);

                String formattedDateTime = getDayTime();
                String subject = "TOOL MOVE FILE LOCAL HAS AN ERROR";
                String body = "ERROR INFORMATION: \n" +
                        "Message: " + "CANNOT MOVE FILE WITH PATH " + originalFile.getAbsolutePath() + "\n" +
                        "[ERROR]: " + e + "\n" +
                        "Day: " + formattedDateTime + "\n" +
                        "File : " + originalFile.getAbsolutePath();

                sendEmail(subject, body);
                System.exit(0);
            }
        }
    }

    public static void connect() throws JSchException {
        // Setup JSch
        JSch jsch = new JSch();
        jsch.addIdentity(PRIVATE_KEY_PATH);

        // Create a session with the given username, host, and port
        session = jsch.getSession(USER, HOST, PORT);
        session.setConfig("StrictHostKeyChecking", "no"); // Disable host key checking for simplicity

        // Connect to the server
        session.connect();
        LOG.info("Connected to the server");

        // Open an SFTP channel
        Channel channel = session.openChannel("sftp");
        channel.connect();
        channelSftp = (ChannelSftp) channel;
    }
}