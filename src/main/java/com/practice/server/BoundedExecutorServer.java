package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optimised version of ExecutorServer using Load Shedding patterns.
 * Under high traffic, overflowing clients wait briefly for a space inside a
 * bounded backup queue before being gracefully turned away with a clear
 * "Server Busy" notification instead of hanging indefinitely.
 */
public class BoundedExecutorServer {
    private static final Logger log = Logger.getLogger(BoundedExecutorServer.class.getName());
    private final static int THREAD_POOL_SIZE = 10;
    private final static int WAITING_QUEUE_CAPACITY = 10; // Fixed headroom beyond the active threads
    private final static long REJECT_WAIT_TIMEOUT = 3;
    private final static TimeUnit REJECT_WAIT_UNIT = TimeUnit.SECONDS;

    // Maximum duration a task is allowed to sit queued before a thread picks it up.
    // This bounds the secondary waiting window to prevent processing stale requests.
    private final static long MAX_QUEUE_RESIDENCY_MS = 4000;

    private ServerSocket serverSocket;
    private final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(WAITING_QUEUE_CAPACITY);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            THREAD_POOL_SIZE, THREAD_POOL_SIZE,
            0L, TimeUnit.MILLISECONDS,
            workQueue,
            new WaitThenRejectPolicy(REJECT_WAIT_TIMEOUT, REJECT_WAIT_UNIT));

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        log.info("Server started on port " + port + " with a pool of " + THREAD_POOL_SIZE + " threads");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            log.info("Accepted connection from " + clientSocket.getInetAddress());

            // CRUCIAL: Using .execute() passes the raw ClientHandler task to the pool.
            // Using .submit() wraps it in a FutureTask which breaks 'instanceof' checks.
            executor.execute(new ClientHandler(clientSocket));
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

    /**
     * Custom policy that alters the default instant rejection behavior.
     * It offloads a blocking offer(...) onto an independent Java virtual thread,
     * ensuring the server's main accept() loop thread remains entirely responsive.
     */
    private record WaitThenRejectPolicy(long timeout, TimeUnit unit) implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("Executor is shutting down");
            }

            // Asynchronous grace period handler using lightweight virtual threads
            Thread.startVirtualThread(() -> {
                try {
                    boolean accepted = executor.getQueue().offer(task, timeout, unit);
                    if (!accepted) {
                        log.warning("Task rejected after waiting " + timeout + " " + unit + " - server is overloaded");
                        if (task instanceof ClientHandler handler) {
                            handler.rejectDueToOverload();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
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

            } catch (IOException e) {
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

        void handleSession(BufferedReader in, PrintWriter out, String clientId) throws IOException {
            String input;
            while ((input = in.readLine()) != null) {
                log.info("Message from [" + clientId + "] Received: " + input);
                out.println(input);
                log.info("[" + clientId + "] Sent: " + input);
                if (input.equals("bye")) {
                    log.info("Message to [" + clientId + "] sent Bye. Closing session");
                    break;
                }
            }
        }

        void rejectDueToOverload() {
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                out.println("Server is busy. Please try again later.");
                log.info("Server is busy. Please try again later.");
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error notifying rejected client", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Error closing rejected client socket", e);
                }
            }
        }
    }
    public static void main(String[] args) throws IOException {
        new BoundedExecutorServer().start(6666);
    }
}
