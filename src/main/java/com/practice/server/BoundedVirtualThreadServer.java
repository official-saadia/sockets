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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A highly scalable, fully bounded server leveraging Java Virtual Threads.
 * Protects system resources using a Dual-Semaphore architecture to explicitly
 * constrain concurrent processing and waiting-queue overhead.
 */
public class BoundedVirtualThreadServer {
    private static final Logger log = Logger.getLogger(BoundedVirtualThreadServer.class.getName());

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final int MAX_EXECUTION_SLOTS = 5000;
    private static final int MAX_WAITING_SLOTS = 2000;

    // Pass constants into the Semaphores
    private final Semaphore executionCeiling = new Semaphore(MAX_EXECUTION_SLOTS);
    private final Semaphore waitingQueueCeiling = new Semaphore(MAX_WAITING_SLOTS);

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        log.info(String.format(
                "BoundedVirtualServer started on port %d. [Max Execution: %d | Max Waiting Queue: %d]",
                port, MAX_EXECUTION_SLOTS, MAX_WAITING_SLOTS
        ));

        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                if (waitingQueueCeiling.tryAcquire()) {
                    executor.execute(new ClientTask(clientSocket));
                } else {
                    log.warning("Server is completely maxed out! Load-shedding client: " + clientSocket.getInetAddress());
                    handleSheddingGracefully(clientSocket);
                }
            }
        } catch (SocketException e) {
            if (serverSocket.isClosed()) {
                log.info("Server socket closed gracefully.");
            } else {
                throw e;
            }
        }
    }

    /**
     * Gracefully sheds a client connection without clogging or blocking the main accept loop.
     */
    private void handleSheddingGracefully(Socket socket) {

        Thread.startVirtualThread(() -> {
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                out.println("Server is busy. Please try again later.");
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error writing load-shedding payload", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        });
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

    /**
     * Internal Task architecture managing the multi-stage lifecycle of the client socket.
     */
    private class ClientTask implements Runnable {
        private final Socket clientSocket;

        public ClientTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            boolean acquiredExecution = false;
            try {
                executionCeiling.acquire();
                acquiredExecution = true;
                waitingQueueCeiling.release();

                try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                    handleSession(in, out, clientSocket.getInetAddress().toString());
                }

            } catch (InterruptedException e) {
                log.log(Level.WARNING, "Client task interrupted while waiting in queue", e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.log(Level.SEVERE, "I/O error during client session execution", e);
            } finally {
                if (acquiredExecution) {
                    executionCeiling.release();
                } else {
                    waitingQueueCeiling.release();
                }

                try {
                    clientSocket.close();
                } catch (IOException ignored) {}
            }
        }

        private void handleSession(BufferedReader in, PrintWriter out, String clientId) throws IOException {
            String input;
            while ((input = in.readLine()) != null) {
                log.info("Message from [" + clientId + "] Received: " + input);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                out.println(input);
                log.info("Message to [" + clientId + "] Sent: " + input);

                if ("bye".equalsIgnoreCase(input.trim())) {
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new BoundedVirtualThreadServer().start(6663);
    }
}

