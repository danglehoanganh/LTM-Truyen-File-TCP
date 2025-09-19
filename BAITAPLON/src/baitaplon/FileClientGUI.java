package baitaplon;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileClientGUI extends JFrame {
    private JTextArea statusArea;
    private JProgressBar progressBar;
    private JButton cancelButton;
    private volatile boolean isCancelled = false;
    private DefaultListModel<String> sentFilesModel = new DefaultListModel<>();

    private File historyFile = new File("sent_files.txt"); // file log lịch sử

    public FileClientGUI() {
        setTitle("TCP File CLIENT");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // ===== Header =====
        JLabel headerLabel = new JLabel("TRUYỀN FILE TCP CLIENT", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 22));
        headerLabel.setForeground(new Color(0, 102, 204));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        add(headerLabel, BorderLayout.NORTH);

        // ===== Center panel =====
        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        statusArea.setBackground(new Color(245, 245, 245));
        statusArea.setBorder(BorderFactory.createTitledBorder("Trạng thái"));
        JScrollPane scrollPane = new JScrollPane(statusArea);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setPreferredSize(new Dimension(400, 25));

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(progressBar, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // ===== Bottom panel =====
        JButton chooseButton = new JButton("Chọn file để gửi");
        chooseButton.setFont(new Font("Arial", Font.BOLD, 14));
        chooseButton.setBackground(new Color(0, 153, 76));
        chooseButton.setForeground(Color.WHITE);
        chooseButton.setFocusPainted(false);
        chooseButton.setPreferredSize(new Dimension(180, 40));
        chooseButton.addActionListener(e -> chooseAndSendFile());

        cancelButton = new JButton("Hủy gửi");
        cancelButton.setFont(new Font("Arial", Font.BOLD, 14));
        cancelButton.setBackground(new Color(204, 0, 0));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setPreferredSize(new Dimension(120, 40));
        cancelButton.addActionListener(e -> cancelTransfer());

        JButton historyButton = new JButton("Xem file đã gửi");
        historyButton.setFont(new Font("Arial", Font.BOLD, 14));
        historyButton.setBackground(new Color(0, 102, 204));
        historyButton.setForeground(Color.WHITE);
        historyButton.setFocusPainted(false);
        historyButton.setPreferredSize(new Dimension(160, 40));
        historyButton.addActionListener(e -> showSentFiles());

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(chooseButton);
        bottomPanel.add(cancelButton);
        bottomPanel.add(historyButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Load lại lịch sử cũ nếu có
        loadHistory();
    }

    
 // ===== Chọn file để gửi =====
    private void chooseAndSendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Hỏi xác nhận trước khi gửi
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc muốn gửi file: " + file.getName() + " ?",
                    "Xác nhận gửi file",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                isCancelled = false;
                new Thread(() -> sendFile(file)).start();
            } else {
                statusArea.append("⚠️ Đã hủy gửi file: " + file.getName() + "\n");
            }
        }
    }


    
 // ===== Gửi file qua TCP =====
    private void sendFile(File file) {
        long offset = 0;
        long fileSize = file.length();
        String fileSizeMB = String.format("%.2f MB", fileSize / (1024.0 * 1024.0));

        SwingUtilities.invokeLater(() -> {
            statusArea.append("📂 Bắt đầu gửi file: " + file.getName() + " (" + fileSizeMB + ")\n");
            progressBar.setValue(0); // reset thanh progress về 0%
        });

        try (Socket socket = new Socket("localhost", 6000)) {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            dos.writeUTF(file.getName());
            dos.writeLong(fileSize);
            dos.writeLong(offset);

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            long totalSent = 0;

            long startTime = System.currentTimeMillis();

            while (!isCancelled && (read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
                totalSent += read;
                offset = totalSent;

                int percent = (int) ((totalSent * 100) / fileSize);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(percent);
                    statusArea.append("⏳ Đang gửi... " + percent + "%\n");
                });
            }

            fis.close();
            dos.close();

            if (isCancelled) {
                SwingUtilities.invokeLater(() -> {
                    statusArea.append("❌ Gửi file đã bị hủy!\n");
                    progressBar.setValue(0);
                });
            } else {
                long elapsedTime = System.currentTimeMillis() - startTime + 1;
                double speedKBs = (totalSent / 1024.0) / (elapsedTime / 1000.0);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100); // hoàn tất 100%
                    statusArea.append("✅ Đã gửi file thành công: " + file.getName() + "\n");

                    // Lưu log
                    String time = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
                    String log = file.getName() + "|" + fileSizeMB + "|" + time + "|"
                            + String.format("%.2f KB/s", speedKBs) + "|"
                            + socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                    sentFilesModel.addElement(log);
                    saveHistory(log);
                });
            }

        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                    statusArea.append("⚠️ Lỗi kết nối: " + e.getMessage() + "\n"));
        }
    }


    private void cancelTransfer() {
        isCancelled = true;
    }

    // ===== Hiển thị lịch sử file đã gửi =====
    private void showSentFiles() {
        JDialog historyDialog = new JDialog(this, "Danh sách file đã gửi", true);
        historyDialog.setSize(750, 400);
        historyDialog.setLocationRelativeTo(this);

        String[] columnNames = {"Tên file", "Dung lượng", "Thời gian gửi", "Tốc độ", "Địa chỉ"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);

        for (int i = 0; i < sentFilesModel.size(); i++) {
            String[] parts = sentFilesModel.get(i).split("\\|");
            if (parts.length == 5) {
                tableModel.addRow(parts);
            }
        }

        JTable table = new JTable(tableModel);
        table.setFont(new Font("Monospaced", Font.PLAIN, 13));
        table.setRowHeight(25);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String details = "📌 Thông tin chi tiết:\n\n"
                                + "Tên file: " + table.getValueAt(row, 0) + "\n"
                                + "Dung lượng: " + table.getValueAt(row, 1) + "\n"
                                + "Thời gian gửi: " + table.getValueAt(row, 2) + "\n"
                                + "Tốc độ trung bình: " + table.getValueAt(row, 3) + "\n"
                                + "Địa chỉ gửi đến: " + table.getValueAt(row, 4);
                        JOptionPane.showMessageDialog(historyDialog, details,
                                "Chi tiết file", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        historyDialog.add(scrollPane, BorderLayout.CENTER);
        historyDialog.setVisible(true);
    }

    // ===== Lưu lịch sử =====
    private void saveHistory(String log) {
        try (FileWriter fw = new FileWriter(historyFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(log);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Không thể lưu lịch sử: " + e.getMessage());
        }
    }

    // ===== Load lịch sử =====
    private void loadHistory() {
        if (historyFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(historyFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sentFilesModel.addElement(line);
                }
            } catch (IOException e) {
                System.err.println("Không thể đọc lịch sử: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new FileClientGUI().setVisible(true);
        });
    }
}
