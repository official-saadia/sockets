package com.practice.server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Unit tests: no sockets, no threads. handleSession() is exercised directly
// against in-memory streams, mirroring ServerUnitTest's approach.
class ClientHandlerUnitTest {

    @Test
    void echoesEachLineBackToClient() throws IOException {
        BufferedReader in = new BufferedReader(new StringReader("hello\nhow are you\n"));
        StringWriter capturedOutput = new StringWriter();
        PrintWriter out = new PrintWriter(capturedOutput, true);

        // socket is never touched by handleSession, so null is safe here.
        new ThreadedServer.ClientHandler(null).handleSession(in, out, "test-client");

        String[] lines = capturedOutput.toString().split("\\R");
        assertEquals("hello", lines[0]);
        assertEquals("how are you", lines[1]);
    }

    @Test
    void stopsProcessingAfterByeIsReceived() throws IOException {
        BufferedReader in = new BufferedReader(new StringReader("bye\nthis should not be echoed\n"));
        StringWriter capturedOutput = new StringWriter();
        PrintWriter out = new PrintWriter(capturedOutput, true);

        new ThreadedServer.ClientHandler(null).handleSession(in, out, "test-client");

        assertFalse(capturedOutput.toString().contains("this should not be echoed"));
    }
}