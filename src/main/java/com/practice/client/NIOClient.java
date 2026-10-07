package com.practice.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-performance, non-blocking network client utilizing Java NIO.
 * A Selector listens for server responses. Console input is read on a separate thread,
 * because System.in cannot be registered with a Selector.
 */
public class NIOClient implements AutoCloseable {
    private static final Logger log = Logger.getLogger(NIOClient.class.getName());

    private Selector selector;
    private SocketChannel clientChannel;
    private volatile boolean running = true;

    public void connect(String host, int port) throws IOException {
        // 1. Open the Selector engine to listen for server responses
        selector = Selector.open();

        // 2. Open a non-blocking SocketChannel
        clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);

        // 3. Start the connection to the server
        log.info("Connecting to " + host + ":" + port + "...");
        clientChannel.connect(new InetSocketAddress(host, port));

        // 4. Register the channel to watch for when the connection finishes or data arrives
        clientChannel.register(selector, SelectionKey.OP_CONNECT);

        // The Main Client Event Loop
        try {
            while (running) {
                selector.select(); // Blocks efficiently until network traffic occurs (or stop() wakes it up)

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) continue;

                    // Case A: The connection handshaking is ready to finish
                    if (key.isConnectable()) {
                        handleConnect(key);
                    }
                    // Case B: The server sent an incoming message back to us
                    else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            }
        } finally {
            closeResources(); // closed here, so nothing is closed underneath a running select()
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        // Finish the connection process (returns true if successful)
        if (channel.isConnectionPending()) {
            if (channel.finishConnect()) {
                log.info("Successfully connected to the NIO Server!");
                // Keep watching this lane specifically for incoming READ messages
                channel.register(selector, SelectionKey.OP_READ);
                // Only now is it safe to let the user type messages
                startConsoleReaderThread();
            } else {
                log.severe("Failed to finalize server connection.");
                stop();
            }
        }
    }

    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(256);

        try {
            int bytesRead = channel.read(buffer);

            // -1 means the server closed the connection in an orderly way (an abrupt close throws IOException)
            if (bytesRead == -1) {
                log.warning("Server disconnected the line.");
                stop();
                return;
            }

            buffer.flip();
            String response = StandardCharsets.UTF_8.decode(buffer).toString().trim();
            System.out.println("\n[Server Response] " + response);

        } catch (IOException e) {
            log.log(Level.SEVERE, "Error reading from server pipeline", e);
            stop();
        }
    }

    public void sendMessage(String message) {
        if (!clientChannel.isConnected()) {
            log.warning("Cannot send message, client is not connected.");
            return;
        }

        try {
            // Append a newline character to stay synchronized with line framing protocols
            String formattedMessage = message + "\n";
            ByteBuffer buffer = ByteBuffer.wrap(formattedMessage.getBytes(StandardCharsets.UTF_8));

            // Non-blocking write directly into the network channel pipeline
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to dispatch message packet", e);
        }
    }

    private void startConsoleReaderThread() {
        Thread consoleThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (running) {
                    System.out.print("Enter message: ");
                    if (!scanner.hasNextLine()) { // end of input (for example Ctrl+D)
                        stop();
                        break;
                    }
                    String line = scanner.nextLine();
                    sendMessage(line);

                    if ("bye".equalsIgnoreCase(line.trim())) {
                        log.info("Exiting console session.");
                        stop();
                        break;
                    }
                }
            }
        });
        consoleThread.setDaemon(true); // Dies automatically if the main thread closes
        consoleThread.start();
    }

    @Override
    public void close() {
        stop();
    }

    // Safe to call from any thread: it only wakes the event loop, which then exits and closes everything.
    public void stop() {
        this.running = false;
        if (selector != null) selector.wakeup();
    }

    private void closeResources() {
        try {
            if (selector != null) selector.close();
            if (clientChannel != null) clientChannel.close();
            log.info("NIO Client stopped cleanly.");
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        try (NIOClient client = new NIOClient()) {
            client.connect("localhost", 6663);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Client runtime exception", e);
        }
    }
}