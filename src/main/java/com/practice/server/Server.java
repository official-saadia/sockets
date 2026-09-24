package com.practice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public void start(int port) throws IOException {
        try (
                // Binds a socket to the given port and starts listening for connections.
                ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Server started on port " + port + ". Waiting for a client to connect...");

            try (
                    // Blocks until a client connects, then returns a socket for that specific connection.
                    Socket clientSocket = serverSocket.accept();
                    // Wraps the socket's input stream so incoming text can be read line by line.
                    // UTF-8 is explicit here so it matches the client's encoding exactly.
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    // Wraps the socket's output stream so text can be sent back to the client.
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                logger.info("Client connected from " + clientSocket.getInetAddress());
                handleSession(in, out);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error while handling client connection", e);
                throw e;
            } finally {
                logger.info("Client connection closed.");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server failed to start on port " + port, e);
            throw e;
        }
    }

    // Echoes each line back to the client until "bye" is received.
    void handleSession(BufferedReader in, PrintWriter out) throws IOException {
        String input;
        while ((input = in.readLine()) != null) {
            logger.info("Received from client: " + input);
            out.println(input);
            logger.info("Sent to client: " + input);

            if (input.equals("bye")) {
                logger.info("Client sent 'bye'. Ending session.");
                break;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.start(6661);
    }
}