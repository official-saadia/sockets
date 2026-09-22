package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Server started on port " + port + ". Waiting for a client to connect...");

            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                logger.info("Client connected from " + clientSocket.getInetAddress());
                handleSession(in, out);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error while handling client connection", e);
                throw e;
            } finally {
                logger.info("Client connection closed.");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server failed to start on port " + port, e);
            throw e;
        }
    }

    void handleSession(BufferedReader in, PrintWriter out) throws IOException {
        String input;
        while ((input = in.readLine()) != null) {
            logger.info("Received from client: " + input);
            out.println(input);
            logger.info("Sent to client: " + input);

            if (input.equals("bye")) {
                logger.info("Client sent 'bye'. Ending session.");
                break;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.start(6661);
    }
}