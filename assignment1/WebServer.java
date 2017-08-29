
/*
Name: Xie Peiyi
Matric: A0141123B
Is this a group submission (yes/no)?  No

If it is a group submission:
Name of 2nd group member: THE_OTHER_NAME_HERE_PLEASE
Matric of 2nd group member: THE_OTHER_MATRIC

*/

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.io.*;

public class WebServer {
	public static void main(String[] args) throws IOException {

		int port = 8000;
		try {
			port = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Usage: java webserver <port>");
			System.exit(0);
		}
		WebServer serverInstance = new WebServer();
		serverInstance.start(port);
	}

	private void start(int port) throws IOException {
		System.out.println("Starting server on port " + port);

		ServerSocket cs = new ServerSocket(port);

		while (true) {
			Socket client = cs.accept();
			handleClientSocket(client);
		}

	}

	private void handleClientSocket(Socket client) throws IOException {

		client.setSoTimeout(2000);
		BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		String input = "";
		String line;
		
		HttpRequest request = new HttpRequest();

		try {

			while ((line = in.readLine()) != null && line.length() != 0) {
				String temp = line;
				input = input + temp + "\r\n";
				while ((temp = in.readLine()) != null) {
					if (temp.length() == 0) {
						break;
					}
					input = input + temp + "\r\n";
				}
				System.out.println("request 1 is " + input + "\r\n------------------");

				// HttpRequest request2 = new HttpRequest();
				request.parseRequest(input);

				input = "";
				byte[] response = formHttpResponse(request);

				if (response == null) {
					response = form404Response(request);
				}

				sendHttpResponse(client, response);

			}

		} catch (SocketTimeoutException ste) {
			System.out.println("timed out!");
		}
		if (request.getVersion() == 0) {
			client.shutdownOutput();
			in.close();
			client.close();
		} else {
			client.shutdownOutput();
			in.close();
			client.close();
		}

	}

	private void sendHttpResponse(Socket client, byte[] response) throws IOException {

		OutputStream out = client.getOutputStream();
		out.write(response);
		// out.close();

	}

	private byte[] formHttpResponse(HttpRequest request) throws IOException {
		byte[] response = "".getBytes();
		if (request.getVersion() == 0) {
			byte[] method = "HTTP/1.0 ".getBytes();
			response = concatenate(response, method);
		} else {
			byte[] method = "HTTP/1.1 ".getBytes();
			response = concatenate(response, method);
		}
		String filePath = request.getFilePath();
		Path currentRelativePath = Paths.get("");
		String s = currentRelativePath.toAbsolutePath().toString();

		File file = new File(s + filePath);
		if (!file.exists() || file.isDirectory()) {
			return null;
		}
		if (file.exists()) {
			byte[] status = "200 OK\r\n".getBytes();
			response = concatenate(response, status);
			String headerStr = "Content-Length: " + file.length() + "\r\n\r\n";
			byte[] header = headerStr.getBytes();
			response = concatenate(response, header);
		}
		if (file.exists()) {
			Path path = Paths.get(s + filePath);
			byte[] body = null;
			try {
				body = Files.readAllBytes(path);
			} catch (IOException ex) {
				System.out.println(ex.toString());
			}
			response = concatenate(response, body);
		}

		return response;

	}

	private byte[] form404Response(HttpRequest request) {
		byte[] response = "".getBytes();

		if (request.getVersion() == 0) {
			byte[] method = "HTTP/1.0 ".getBytes();
			response = concatenate(response, method);
		} else {
			byte[] method = "HTTP/1.1 ".getBytes();
			response = concatenate(response, method);
		}
		byte[] status = "404 Not Found\r\n".getBytes();
		response = concatenate(response, status);
		String headerStr = "Content-Length: " + 161 + "\r\n\r\n";
		byte[] header = headerStr.getBytes();
		response = concatenate(response, header);
		response = concatenate(response, get404Content(request.getFilePath()).getBytes());
		return response;
	}

	private byte[] concatenate(byte[] buffer1, byte[] buffer2) {
		byte[] returnBuffer = new byte[buffer1.length + buffer2.length];
		System.arraycopy(buffer1, 0, returnBuffer, 0, buffer1.length);
		System.arraycopy(buffer2, 0, returnBuffer, buffer1.length, buffer2.length);
		return returnBuffer;
	}

	private String get404Content(String filePath) {
		// You should not change this function. Use it as it is.
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<head>");
		sb.append("<title>");
		sb.append("404 Not Found");
		sb.append("</title>");
		sb.append("</head>");
		sb.append("<body>");
		sb.append("<h1>404 Not Found</h1> ");
		sb.append("<p>The requested URL <i>" + filePath + "</i> was not found on this server.</p>");
		sb.append("</body>");
		sb.append("</html>");

		return sb.toString();
	}
}

class HttpRequest {

	private String filePath;
	private int version;

	String getFilePath() {
		return filePath;
	}

	void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	int getVersion() {
		return version;
	}

	void setVersion(int num) {
		this.version = num;
	}

	/**
	 * Parses a request and set all the private variables
	 * 
	 * @param request
	 *            a string that was read from the socket and contains the
	 *            request
	 * @return returns true if parsing was successful
	 */
	public boolean parseRequest(String request) {

		// System.out.println(request + "\r\n------------------");

		String[] lines = request.split("\\r\\n");

		String requestLine = lines[0];

		String[] requestLineItems = requestLine.split(" ");
		// if(requestLineItems[1].equals("/")){
		// setFilePath("/demo.html");
		// }
		setFilePath(requestLineItems[1]);

		if (requestLineItems[2].substring(0, 8).equals("HTTP/1.0"))
			setVersion(0);
		else if (requestLineItems[2].substring(0, 8).equals("HTTP/1.1"))
			setVersion(1);
		else {
			setVersion(-1);
		}
		return true;

	}
}