package com.practice.client;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Unit test: no socket, no server. Uses the package-private constructor to inject
// streams directly, so this tests only sendMessage()'s own logic.
class ClientUnitTest {

    @Test
    void sendMessageWritesToOutAndReturnsLineReadFromIn() throws IOException {
        BufferedReader in = new BufferedReader(new StringReader("hello back\n"));
        StringWriter capturedOutput = new StringWriter();
        PrintWriter out = new PrintWriter(capturedOutput, true);

        Client client = new Client(in, out);
        String response = client.sendMessage("hello server");

        assertEquals("hello server", capturedOutput.toString().trim());
        assertEquals("hello back", response);
    }

    @Test
    void sendMessageRejectsMessagesContainingNewline() {
        // The check happens before in/out are touched, so null streams are safe here.
        Client client = new Client(null, null);

        assertThrows(IllegalArgumentException.class, () -> client.sendMessage("hello\nworld"));
    }

    @Test
    void sendMessageRejectsMessagesContainingCarriageReturn() {
        Client client = new Client(null, null);

        assertThrows(IllegalArgumentException.class, () -> client.sendMessage("hello\rworld"));
    }
}