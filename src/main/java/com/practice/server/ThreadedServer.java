package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadedServer {

    private static final Logger logger = Logger.getLogger(ThreadedServer.class.getName());

    private ServerSocket serverSocket;

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        logger.info("Server started on port " + port + ". Waiting for clients...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            logger.info("Client connected from " + clientSocket.getInetAddress());
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error closing server socket", e);
        }
    }

    public static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                handleSession(in, out, socket.getInetAddress().toString());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error handling client " + socket.getInetAddress(), e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error closing socket for " + socket.getInetAddress(), e);
                }
                logger.info("Connection with " + socket.getInetAddress() + " closed.");
            }
        }

        // Package-private: the echo protocol itself, independent of the socket.
        // Takes a clientId for logging instead of reading it from the socket field,
        // so this can be unit tested with no socket at all.
        void handleSession(BufferedReader in, PrintWriter out, String clientId) throws IOException {
            String input;
            while ((input = in.readLine()) != null) {
                logger.info("[" + clientId + "] Received: " + input);
                out.println(input);
                logger.info("[" + clientId + "] Sent: " + input);

                if (input.equals("bye")) {
                    logger.info("[" + clientId + "] sent 'bye'. Ending session.");
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new ThreadedServer().start(6662);
    }
}