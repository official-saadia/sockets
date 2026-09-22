package com.practice.server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerUnitTest {

    @Test
    void echoesEachLineBackToClient() throws IOException {
        BufferedReader in = new BufferedReader(new StringReader("hello\nhow are you\n"));
        StringWriter capturedOutput = new StringWriter();
        PrintWriter out = new PrintWriter(capturedOutput, true);

        new Server().handleSession(in, out);

        String[] lines = capturedOutput.toString().split("\\R");
        assertEquals("hello", lines[0]);
        assertEquals("how are you", lines[1]);
    }

    @Test
    void stopsProcessingAfterByeIsReceived() throws IOException {
        // A line after "bye" that should never be echoed if the break works correctly.
        BufferedReader in = new BufferedReader(new StringReader("bye\nthis should not be echoed\n"));
        StringWriter capturedOutput = new StringWriter();
        PrintWriter out = new PrintWriter(capturedOutput, true);

        new Server().handleSession(in, out);

        assertFalse(capturedOutput.toString().contains("this should not be echoed"));
    }
}