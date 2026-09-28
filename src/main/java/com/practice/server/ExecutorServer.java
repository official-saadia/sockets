package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExecutorServer {
    private static final Logger log = Logger.getLogger(ExecutorServer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    private ServerSocket serverSocket;

    // In a fixed thread pool, the waiting queue is unbounded. Under the hood newFixedThreadPool implements ThreadPoolExecutor.
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    // If you want a bounded waiting queue, uncomment the following code:
//    private final ExecutorService executor = new ThreadPoolExecutor(
//            THREAD_POOL_SIZE,                              // 10 threads
//            THREAD_POOL_SIZE,                              // 10 threads maximum
//            0L, TimeUnit.MILLISECONDS,                     // Keep threads alive forever
//            new ArrayBlockingQueue<>(20)                   // Bounded backup queue of size 20
//    );

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        log.info("Server started on port " + port + " with a pool of " + THREAD_POOL_SIZE + " threads");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            log.info("Accepted connection from " + clientSocket.getInetAddress());
            executor.submit(new ClientHandler(clientSocket));
        }
    }

    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error closing server socket", e);
        } finally {
            executor.shutdown();
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
                log.log(Level.SEVERE, "Error handling client", e);
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

                // Simulated network delay/processing time to push overflow clients into the waiting queue
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
        new ExecutorServer().start(6663);
    }
}
