package com.practice.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

/**
 * High-performance, single-threaded chat server utilizing Java NIO.
 * Handles thousands of concurrent connections using an Event Loop architecture.
 */
public class NIOServer {
    private static final Logger log = Logger.getLogger(NIOServer.class.getName());

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private boolean running = true;

    public void start(int port) throws IOException {
        // 1. Open the Selector (Event Loop engine)
        selector = Selector.open();

        // 2. Open the ServerSocketChannel (Equivalent to ServerSocket)
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));

        // CRITICAL STEP: Make the server channel fully non-blocking!
        serverChannel.configureBlocking(false);

        // 3. Register the server channel to the Selector to listen for incoming connections
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        log.info("NIO Chat Server started on port " + port + " (Non-Blocking Mode)");

        // The Main Event Loop (Runs on a SINGLE platform thread)
        while (running) {
            // This blocks until at least one network channel is ready for action
            selector.select();

            // Get the set of all keys (channels) that have pending network traffic
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove(); // Remove immediately to prevent duplicate processing

                if (!key.isValid()) continue;

                // Case A: A new client is trying to connect
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                // Case B: An existing client has sent data over the socket
                else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            log.info("NIO Accepted connection from: " + clientChannel.getRemoteAddress());

            // 🟢 PRODUCTION FIX: Allocate a dedicated buffer just for this client
            ByteBuffer clientBuffer = ByteBuffer.allocate(256);

            // Register the channel AND attach their private buffer to the selector
            clientChannel.register(selector, SelectionKey.OP_READ, clientBuffer);
        }
    }


    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();

        ByteBuffer buffer = (ByteBuffer) key.attachment();

        // Clear old data markers from the previous message so it's ready for a fresh read
        buffer.clear();

        try {
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                closeChannelGracefully(clientChannel);
                return;
            }

            buffer.flip();
            String message = StandardCharsets.UTF_8.decode(buffer).toString().trim();
            log.info("NIO Received message: " + message);

            if (!message.isEmpty()) {
                String response = "Echo: " + message + "\n";
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                clientChannel.write(responseBuffer);

                if ("bye".equalsIgnoreCase(message)) {
                    log.info("Client requested disconnect via 'bye'.");
                    closeChannelGracefully(clientChannel);
                }
            }

        } catch (IOException e) {
            log.warning("Forced connection drop from client: " + e.getMessage());
            closeChannelGracefully(clientChannel);
        }
    }


    private void closeChannelGracefully(SocketChannel channel) {
        try {
            log.info("Closing connection with: " + channel.getRemoteAddress());
            channel.close(); // Automatically deregisters the channel from the Selector
        } catch (IOException ignored) {}
    }

    public void stop() throws IOException {
        this.running = false;
        if (selector != null) selector.close();
        if (serverChannel != null) serverChannel.close();
    }

    public static void main(String[] args) throws IOException {
        new NIOServer().start(6663);
    }
}

