package com.practice.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private static final int DEFAULT_READ_TIMEOUT_MS = 10_000;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private int id;

    public Client() {
    }

    Client(BufferedReader in, PrintWriter out) {
        this.in = in;
        this.out = out;
    }

    public void connect(String host, int port) throws IOException {
        connect(host, port, DEFAULT_READ_TIMEOUT_MS, 1);
    }

    public void connect(String host, int port, int readTimeoutMs, int id) throws IOException {
        this.id = id;
        logger.info("Client: " + id + " Connecting to " + host + ":" + port + "...");
        try {
            socket = new Socket(host, port);
            // Prevents readLine() from blocking forever if the server never responds.
            socket.setSoTimeout(readTimeoutMs);
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            logger.info("Connected to " + host + ":" + port);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to connect to " + host + ":" + port, e);
            cleanupPartialResources(); // Clear half-initialized structures on failure
            throw e;
        }
    }

    public String sendMessage(String message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        // Both sides talk in terms of readLine()/println(), so a message containing
        // a newline would be split into two messages on the other end. Reject it here
        // to keep the line-based framing the whole protocol depends on.
        if (message.contains("\n") || message.contains("\r")) {
            String errorMessage = "message must not contain line breaks: \"" + message + "\"";
            logger.warning(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        try {
            logger.info("Sending message from client: " + id + " :: " + message);
            out.println(message);
            String response = in.readLine();
            logger.info("Received by client: " + id + " :: " + response);
            return response;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending/receiving message: " + message, e);
            throw e;
        }
    }

    private void cleanupPartialResources() {
        if (in != null) { try { in.close(); } catch (IOException ignored) {} }
        if (out != null) { out.close(); }
        if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing client connection with id " + id);
        cleanupPartialResources();
        logger.info("Client connection closed.id " + id);
    }

    public static void main(String[] args) throws IOException {
        try (Client client = new Client();
             BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            client.connect("localhost", 6663); // Matches the server port standard

            String input;
            while ((input = consoleIn.readLine()) != null) {
                String response = client.sendMessage(input);
                System.out.println("Response: " + response);
                if ("bye".equalsIgnoreCase(input.trim())) {
                    break;
                }
            }
        }
    }
}
