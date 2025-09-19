package baitaplon;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileServer extends JFrame {
    private JTextArea statusArea;
    private JProgressBar progressBar;
    private JLabel fileInfoLabel;

    private static final int PORT = 6000;
    private static final String SAVE_DIR = "received_files";
    private static final String LOG_FILE = "received_files_log.txt";

    public FileServer() {
        setTitle("TCP File Server");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // ===== Header =====
        JLabel headerLabel = new JLabel("📂 TCP FILE SERVER", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 22));
        headerLabel.setForeground(new Color(0, 102, 204));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        add(headerLabel, BorderLayout.NORTH);

        // ===== Center =====
        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(statusArea);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setPreferredSize(new Dimension(400, 25));

        fileInfoLabel = new JLabel("Chưa có file nhận...");
        fileInfoLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        fileInfoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerPanel.add(fileInfoLabel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(progressBar, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // ===== Start server in new thread =====
        new Thread(this::startServer).start();
    }

    private void startServer() {
        statusArea.append("📂 Server đang lắng nghe tại cổng " + PORT + "\n");

        File dir = new File(SAVE_DIR);
        if (!dir.exists()) dir.mkdirs();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                statusArea.append("🔗 Client đã kết nối: " + socket.getInetAddress() + "\n");
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            statusArea.append("❌ Lỗi server: " + e.getMessage() + "\n");
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            // Đọc metadata
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            long offset = dis.readLong();

            SwingUtilities.invokeLater(() -> {
                fileInfoLabel.setText("Tên file: " + fileName + " | Kích thước: "
                        + String.format("%.2f MB", fileSize / (1024.0 * 1024.0)));
                progressBar.setValue(0);
            });

            File file = new File(SAVE_DIR, "received_" + System.currentTimeMillis() + "_" + fileName);
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(offset);

                byte[] buffer = new byte[4096];
                int read;
                long totalRead = offset;

                while ((read = dis.read(buffer)) > 0) {
                    raf.write(buffer, 0, read);
                    totalRead += read;

                    int percent = (int) ((totalRead * 100) / fileSize);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        statusArea.append("⬇️ Đang nhận " + fileName + ": " + percent + "%\n");
                    });

                    if (totalRead >= fileSize) break;
                }

                SwingUtilities.invokeLater(() -> {
                    statusArea.append("✅ Đã nhận file thành công: " + file.getName() + "\n");
                    progressBar.setValue(100);
                });

                writeLog(fileName, file.getAbsolutePath(), socket.getInetAddress().toString());
            }

        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                    statusArea.append("⚠️ Lỗi khi nhận file: " + e.getMessage() + "\n"));
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void writeLog(String originalName, String savedPath, String clientAddress) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println(timestamp + " | " + originalName + " | " + savedPath + " | " + clientAddress);

        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                    statusArea.append("⚠️ Không thể ghi log: " + e.getMessage() + "\n"));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileServer().setVisible(true));
    }
}
