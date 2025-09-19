package rc.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;

//21:00

public class server {
	public static void main(String[] args) {
		try {
			ServerSocket serverSocket = new ServerSocket(5000);
			while (true) {
				Socket socket = serverSocket.accept();
				//getInetAddress().getHostAddress() = lấy ra địa chỉ của Client
				System.out.println("Client đã kết nối " + socket.getInetAddress().getHostAddress());
				
				//Luồng thực thi
				Thread thread = new Thread(()->handleClientRequest(socket)) ;	
				thread.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void handleClientRequest(Socket socket) {
	    try (
	        Socket s = socket;
	        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
	        PrintWriter writer = new PrintWriter(s.getOutputStream(), true) // autoFlush
	    ) {
	        String request;
	        while ((request = reader.readLine()) != null) {
	            if ("shutdown".equals(request)) {
	                Runtime.getRuntime().exec("shutdown -s -t 3600");
	                writer.println("Máy tính đang tắt ....");
	            } else if ("restart".equals(request)) {
	                Runtime.getRuntime().exec("shutdown -r -t 3600");
	                writer.println("Máy tính đang khởi động lại ... ");
	            } else if ("cancel".equals(request)) {
	                Runtime.getRuntime().exec("shutdown -a");
	                writer.println("Đã hủy yêu cầu ");
	            } else {
	                writer.println("ERROR: unknown command");
	            }
	            // tiếp tục vòng lặp để chờ lệnh tiếp theo
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

}
