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

// "Fix" version: bounded queue with a wait-then-reject policy. Under the same
// load, excess clients wait briefly, then get told the server is busy instead
// of hanging indefinitely.
public class BoundedExecutorServer {
    private static final Logger log = Logger.getLogger(BoundedExecutorServer.class.getName());
    private final static int THREAD_POOL_SIZE = 10;
    private final static int WAITING_QUEUE_CAPACITY = 20; // headroom beyond the 10 active threads, for brief bursts
    private final static long REJECT_WAIT_TIMEOUT = 3;
    private final static TimeUnit REJECT_WAIT_UNIT = TimeUnit.SECONDS;

    private ServerSocket serverSocket;
    private final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(WAITING_QUEUE_CAPACITY);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            THREAD_POOL_SIZE, THREAD_POOL_SIZE,
            0L, TimeUnit.MILLISECONDS,
            workQueue,
            new WaitThenRejectPolicy(REJECT_WAIT_TIMEOUT, REJECT_WAIT_UNIT));

    private void start(int port) throws IOException {
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

    // Default rejection is instant: if the queue is full when a task is submitted,
    // Java calls this handler immediately, with no waiting at all. This handler adds
    // the "wait a bit, then reject" behavior by doing a BLOCKING offer with a timeout
    // (different from the executor's own internal, non-blocking offer) before giving up.
    private record WaitThenRejectPolicy(long timeout, TimeUnit unit) implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("Executor is shutting down");
            }
            try {
                boolean accepted = executor.getQueue().offer(task, timeout, unit);
                if (!accepted) {
                    log.warning("Task rejected after waiting " + timeout + " " + unit + " - server is overloaded");
                    if (task instanceof ClientHandler) {
                        ((ClientHandler) task).rejectDueToOverload();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting to submit task", e);
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
            try(BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(),
                    StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                handleSession(in, out, clientSocket.getInetAddress().toString());

            } catch (IOException e) {
                log.log(Level.SEVERE, "Error handling client", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Error closing client socket", e);
                }
                log.info("Connection with "+ clientSocket.getInetAddress() + " closed");
            }
        }

        void handleSession(BufferedReader in, PrintWriter out, String clientId) throws IOException {
            String input;
            while((input = in.readLine()) != null) {
                log.info("[" + clientId + "] Received: " + input);
                out.println(input);
                log.info("[" + clientId + "] Sent: " + input);
                if(input.equals("bye")) {
                    log.info("[" + clientId + "] sent Bye. Closing session");
                    break;
                }
            }
        }

        // Called when the server is overloaded and this client's request had to be
        // turned away after waiting. Tells the client why, rather than silently
        // dropping the connection with no explanation.
        void rejectDueToOverload() {
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                out.println("Server is busy at the moment please try again after sometime.");
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