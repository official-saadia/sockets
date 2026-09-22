package com.practice.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Integration test: a real Server, a real port, a raw Socket as the client
// (Client isn't used here, so this isolates Server's real networking behavior).
class ServerIntegrationTest {

    private static final int PORT = 6661;

    // Fresh server thread per test: Server currently only accepts one client
    // per start() call, so each test needs its own instance to accept.
    @BeforeEach
    void startServer() {
        Thread serverThread = new Thread(() -> {
            try {
                new Server().start(PORT);
            } catch (IOException e) {
                // expected once the client disconnects and the session loop ends
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @Test
    void echoesMessageOverARealSocketConnection() throws IOException, InterruptedException {
        Thread.sleep(200); // give the server a moment to start listening

        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("hello server");
            String response = in.readLine();

            assertEquals("hello server", response);
        }
    }
}
