package rc.server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Swing-based server GUI for your remote-control project.
 * - Start button opens a ServerSocket on the given port and accepts clients.
 * - Each client is assigned a role: the first connected client becomes CONTROLLER,
 *   the next becomes CONTROLLED, and subsequent clients are assigned "VIEWER".
 * - The server logs events into the text area and replies with a small handshake message.
 *
 * To compile and run:
 * javac ServerGUI.java
 * java ServerGUI
 */
public class ServerGUI extends JFrame {
    private JTextField portField;
    private JButton startBtn;
    private JButton stopBtn;
    private JTextArea logArea;

    private volatile boolean running = false;
    private Thread acceptThread;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new ArrayList<>();

    public ServerGUI() {
        super("Remote Control - Server");
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
    }

    private void initUI() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Port:"));
        portField = new JTextField("5000", 6);
        top.add(portField);

        startBtn = new JButton("Start Server");
        stopBtn = new JButton("Stop Server");
        stopBtn.setEnabled(false);

        startBtn.addActionListener(this::onStart);
        stopBtn.addActionListener(this::onStop);

        top.add(startBtn);
        top.add(stopBtn);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane sp = new JScrollPane(logArea);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(sp, BorderLayout.CENTER);
    }

    private void onStart(ActionEvent e) {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendLog("Invalid port number.");
            return;
        }
        startBtn.setEnabled(false);
        portField.setEnabled(false);
        stopBtn.setEnabled(true);
        running = true;
        acceptThread = new Thread(() -> runServer(port), "Accept-Thread");
        acceptThread.start();
    }

    private void onStop(ActionEvent e) {
        appendLog("Stopping server...");
        running = false;
        stopBtn.setEnabled(false);
        startBtn.setEnabled(true);
        portField.setEnabled(true);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ex) {
            appendLog("Error closing server socket: " + ex.getMessage());
        }
        // close all clients
        synchronized (clients) {
            for (ClientHandler c : clients) c.shutdown();
            clients.clear();
        }
    }

    private void runServer(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            serverSocket = ss;
            appendLog("Server started on port " + port);

            while (running) {
                try {
                    Socket s = ss.accept();
                    if (!running) { // double-check after blocking accept returns
                        s.close();
                        break;
                    }
                    ClientHandler handler = new ClientHandler(s);
                    assignRole(handler);
                    synchronized (clients) {
                        clients.add(handler);
                    }
                    handler.start();
                } catch (IOException acceptEx) {
                    if (running) appendLog("Accept error: " + acceptEx.getMessage());
                }
            }
        } catch (IOException ex) {
            appendLog("Could not start server: " + ex.getMessage());
            SwingUtilities.invokeLater(() -> {
                startBtn.setEnabled(true);
                portField.setEnabled(true);
                stopBtn.setEnabled(false);
            });
            running = false;
        } finally {
            appendLog("Server stopped.");
        }
    }

    private void assignRole(ClientHandler handler) {
        int size;
        synchronized (clients) { size = clients.size(); }
        // size == 0 -> first connecting client
        if (size == 0) handler.setRole("CONTROLLER");
        else if (size == 1) handler.setRole("CONTROLLED");
        else handler.setRole("VIEWER");
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String role = "UNKNOWN";
        private volatile boolean alive = true;

        ClientHandler(Socket socket) {
            this.socket = socket;
            setName("Client-Handler-" + socket.getRemoteSocketAddress());
        }

        void setRole(String r) {
            this.role = r;
        }

        void shutdown() {
            alive = false;
            try {
                socket.close();
            } catch (IOException ignored) {}
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                appendLog("Client connected: " + socket.getRemoteSocketAddress() + " -> role=" + role);

                // Send initial handshake including assigned role
                out.println("ROLE:" + role);

                String line;
                while (alive && (line = in.readLine()) != null) {
                    // Log incoming commands
                    appendLog("From " + socket.getRemoteSocketAddress() + " [" + role + "]: " + line);

                    // For demo: respond with ACK and echo
                    out.println("ACK:" + line);

                    // Here you would implement actual handling of remote-control commands,
                    // e.g., if line.equals("SHUTDOWN") and role.equals("CONTROLLER") then execute etc.
                }
            } catch (IOException ex) {
                if (alive) appendLog("Client IO error: " + ex.getMessage());
            } finally {
                appendLog("Client disconnected: " + socket.getRemoteSocketAddress() + " (role=" + role + ")");
                try { socket.close(); } catch (IOException ignored) {}
                synchronized (clients) { clients.remove(this); }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
