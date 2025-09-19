package rc.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;


public class interface_client extends JFrame {
    private JTextField ipField, portField;
    private JTextArea logArea;
    private JButton connectBtn, disconnectBtn;
    private JButton shutdownBtn, restartBtn, cancelBtn;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public interface_client() {
        setTitle("Client Interface");
        setSize(600, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Top: IP and Port + connect/disconnect
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new JLabel("Server IP:"));
        ipField = new JTextField("127.0.0.1", 12);
        top.add(ipField);
        top.add(new JLabel("Port:"));
        portField = new JTextField("5000", 6);
        top.add(portField);

        connectBtn = new JButton("Connect");
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);
        top.add(connectBtn);
        top.add(disconnectBtn);
        add(top, BorderLayout.NORTH);

        // Center: log
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createTitledBorder("Log"));
        add(sp, BorderLayout.CENTER);

        // Bottom: action buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        shutdownBtn = new JButton("Shutdown");
        restartBtn = new JButton("Restart");
        cancelBtn = new JButton("Cancel");
        // disable until connected
        shutdownBtn.setEnabled(false);
        restartBtn.setEnabled(false);
        cancelBtn.setEnabled(false);

        bottom.add(shutdownBtn);
        bottom.add(restartBtn);
        bottom.add(cancelBtn);
        add(bottom, BorderLayout.SOUTH);

        // Actions
        connectBtn.addActionListener(this::onConnect);
        disconnectBtn.addActionListener(e -> disconnect());
        shutdownBtn.addActionListener(e -> sendCommand("shutdown"));
        restartBtn.addActionListener(e -> sendCommand("restart"));
        cancelBtn.addActionListener(e -> sendCommand("cancel"));

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
    }

    private void onConnect(ActionEvent e) {
        connectBtn.setEnabled(false);
        String ip = ipField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendLog("Port không hợp lệ.");
            connectBtn.setEnabled(true);
            return;
        }

        appendLog("Kết nối tới " + ip + ":" + port + " ...");
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // autoFlush = true để không cần gọi flush() thủ công
                writer = new PrintWriter(socket.getOutputStream(), true);

                SwingUtilities.invokeLater(() -> {
                    appendLog("Đã kết nối.");
                    connectBtn.setEnabled(false);
                    disconnectBtn.setEnabled(true);
                    shutdownBtn.setEnabled(true);
                    restartBtn.setEnabled(true);
                    cancelBtn.setEnabled(true);
                });
            } catch (IOException ex) {
                appendLog("Không thể kết nối: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> connectBtn.setEnabled(true));
            }
        }, "Connector").start();
    }

    private void sendCommand(String cmd) {
        if (writer == null || socket == null || socket.isClosed()) {
            appendLog("Chưa kết nối tới server.");
            return;
        }

        appendLog("Gửi: " + cmd);
        // Gửi và chờ 1 dòng phản hồi từ server (đọc trong thread để không block UI)
        new Thread(() -> {
            try {
                writer.println(cmd); // autoFlush = true
                // đọc 1 dòng phản hồi (giống client console ban đầu)
                String response = reader.readLine();
                if (response != null) {
                    appendLog("Phản hồi: " + response);
                } else {
                    appendLog("Server đóng kết nối hoặc không trả lời.");
                    // nếu server đóng, cập nhật trạng thái
                    SwingUtilities.invokeLater(this::disconnect);
                }
            } catch (IOException ex) {
                appendLog("Lỗi khi gửi/nhận: " + ex.getMessage());
                SwingUtilities.invokeLater(this::disconnect);
            }
        }, "Sender-" + cmd).start();
    }

    private void disconnect() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        writer = null;
        reader = null;

        appendLog("Đã ngắt kết nối.");
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        shutdownBtn.setEnabled(false);
        restartBtn.setEnabled(false);
        cancelBtn.setEnabled(false);
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            System.out.println(s);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                interface_client ui = new interface_client();
                ui.setVisible(true);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }
}
