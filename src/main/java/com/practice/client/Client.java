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

    public Client() {
    }

    // Package-private: lets unit tests inject streams directly, without a real socket.
    Client(BufferedReader in, PrintWriter out) {
        this.in = in;
        this.out = out;
    }

    public void connect(String host, int port) throws IOException {
        connect(host, port, DEFAULT_READ_TIMEOUT_MS);
    }

    public void connect(String host, int port, int readTimeoutMs) throws IOException {
        logger.info("Connecting to " + host + ":" + port + "...");
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(readTimeoutMs);
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            logger.info("Connected to " + host + ":" + port);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to connect to " + host + ":" + port, e);
            throw e;
        }
    }

    public String sendMessage(String message) throws IOException {
        if (message.contains("\n") || message.contains("\r")) {
            String errorMessage = "message must not contain line breaks: \"" + message + "\"";
            logger.warning(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        try {
            logger.info("Sending: " + message);
            out.println(message);
            String response = in.readLine();
            logger.info("Received: " + response);
            return response;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending/receiving message: " + message, e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing client connection...");
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
        if (socket != null) {
            socket.close();
        }
        logger.info("Client connection closed.");
    }

    public static void main(String[] args) throws IOException {
        try (Client client = new Client();
             BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))) {

            client.connect("localhost", 6661);

            String input;
            while ((input = consoleIn.readLine()) != null) {
                String response = client.sendMessage(input);
                System.out.println("Response: " + response);
                if (input.equals("bye")) {
                    break;
                }
            }
        }
    }
}