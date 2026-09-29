package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optimized server utilizing Java Virtual Threads.
 * This implementation removes fixed pool throttling entirely. Because virtual
 * threads are incredibly lightweight, the server elastically provisions an
 * independent execution thread per task, eliminating queue starvation bottlenecks
 * and the need for complex load shedding rejection policies.
 */
public class VirtualThreadServer {
    private static final Logger log = Logger.getLogger(VirtualThreadServer.class.getName());

    private ServerSocket serverSocket;
    // Spawns a brand new, unbounded virtual thread for each submitted task
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        log.info("Server started on port " + port + " utilizing an unconstrained Virtual Thread Executor.");

        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                log.info("Accepted connection from " + clientSocket.getInetAddress());

                // Directly passes the task to a new virtual thread. The loop
                // remains immediately responsive without queueing blocks.
                executor.execute(new ClientHandler(clientSocket));
            }
        } catch (SocketException e) {
            if (serverSocket.isClosed()) {
                log.info("Server socket closed gracefully.");
            } else {
                throw e;
            }
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error closing server socket", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                handleSession(in, out, clientSocket.getInetAddress().toString());

            } catch (IOException | InterruptedException e) {
                log.log(Level.SEVERE, "Error handling client session", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Error closing client socket", e);
                }
                log.info("Connection with " + clientSocket.getInetAddress() + " closed");
            }
        }

        void handleSession(BufferedReader in, PrintWriter out, String clientId) throws IOException, InterruptedException {
            String input;
            while ((input = in.readLine()) != null) {
                log.info("Message from [" + clientId + "] Received: " + input);

                // Simulated network processing delay
                Thread.sleep(1000);

                out.println(input);
                log.info("Message to [" + clientId + "] Sent: " + input);
                if ("bye".equalsIgnoreCase(input.trim())) {
                    log.info("[" + clientId + "] sent Bye. Closing session");
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new VirtualThreadServer().start(6663);
    }
}

