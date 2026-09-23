package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExecutorServer {
    private static final Logger log = Logger.getLogger(ExecutorServer.class.getName());
    private final static int THREAD_POOL_SIZE = 10;

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE); 

    private void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        log.info("Server started on port " + port + " with a pool of " + THREAD_POOL_SIZE +" threads");

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
    }

    static void main() throws IOException {
        new ExecutorServer().start(6663);
    }
}
