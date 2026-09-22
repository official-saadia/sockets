package com.practice.client;

import com.practice.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Integration test: a real Server on a background thread, and the real Client
// class connecting to it over an actual socket.
class ClientIntegrationTest {

    private static final int PORT = 6661;
    private Client client;

    // Fresh server thread AND fresh client connection per test: Server currently
    // only accepts one client per start() call, so each test needs its own server.
    @BeforeEach
    void startServerAndConnect() throws IOException, InterruptedException {
        Thread serverThread = new Thread(() -> {
            try {
                new Server().start(PORT);
            } catch (IOException e) {
                // expected once the client disconnects and the session loop ends
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(200); // give the server a moment to start listening

        client = new Client();
        client.connect("localhost", PORT);
    }

    @AfterEach
    void disconnect() throws IOException {
        client.close();
    }

    @Test
    void echoesMessageBackToClient() throws IOException {
        String response = client.sendMessage("hello server");
        assertEquals("hello server", response);
    }

    @Test
    void echoesMultipleMessagesInSameSession() throws IOException {
        assertEquals("first", client.sendMessage("first"));
        assertEquals("second", client.sendMessage("second"));
    }

    @Test
    void sendMessageTimesOutIfServerNeverResponds() throws IOException {
        // A silent server: accepts the connection but never reads or writes anything.
        // Sets up its own Client with a short timeout, separate from the @BeforeEach fixture.
        try (ServerSocket silentServer = new ServerSocket(0)) {
            int silentPort = silentServer.getLocalPort();

            Thread silentServerThread = new Thread(() -> {
                try (Socket ignored = silentServer.accept()) {
                    Thread.sleep(5000); // hold the connection open without responding
                } catch (IOException | InterruptedException e) {
                    // expected once this test's socket closes
                }
            });
            silentServerThread.setDaemon(true);
            silentServerThread.start();

            try (Client silentClient = new Client()) {
                silentClient.connect("localhost", silentPort, 300); // short timeout for a fast test

                assertThrows(SocketTimeoutException.class, () -> silentClient.sendMessage("hello"));
            }
        }
    }
}