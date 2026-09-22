package com.practice.server;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreadedServerIntegrationTest {

    private static final int PORT = 6662;

    // @BeforeAll, not @BeforeEach: unlike single-client Server, ThreadedServer
    // never stops accepting, so one instance can serve every test in this class.
    @BeforeAll
    static void startServer() throws InterruptedException {
        Thread serverThread = new Thread(() -> {
            try {
                new ThreadedServer().start(PORT);
            } catch (IOException e) {
                // expected if the server socket is ever closed during shutdown
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(200); // give the server a moment to start listening
    }

    @Test
    void echoesMessageForASingleClient() throws IOException {
        assertEquals("hello server", sendAndReceive("hello server"));
    }

    @Test
    void handlesTwoClientsConcurrentlyWithoutCrossTalk() throws Exception {
        ExecutorService clients = Executors.newFixedThreadPool(2);
        try {
            Future<String> clientA = clients.submit(() -> sendAndReceive("message from client A"));
            Future<String> clientB = clients.submit(() -> sendAndReceive("message from client B"));

            assertEquals("message from client A", clientA.get(2, TimeUnit.SECONDS));
            assertEquals("message from client B", clientB.get(2, TimeUnit.SECONDS));
        } finally {
            clients.shutdown();
        }
    }

    private String sendAndReceive(String message) throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(message);
            return in.readLine();
        }
    }
}