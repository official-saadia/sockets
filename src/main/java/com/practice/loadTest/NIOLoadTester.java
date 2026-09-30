package com.practice.loadTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NIOLoadTester {

    public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6663;
        int clientCount = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        boolean holdConnectionOpen = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;
        long waitTimeMs = args.length > 3 ? Long.parseLong(args[3]) : 300;

        System.out.println("Connecting to " + host + ":" + port + " with " + clientCount + " concurrent client(s).");

        AtomicInteger handled = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger timedOut = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        CountDownLatch countDownLatch = new CountDownLatch(clientCount);
        ExecutorService loadGenerator = Executors.newVirtualThreadPerTaskExecutor();
        long start = System.currentTimeMillis();

        for (int i = 0; i < clientCount; i++) {
            int id = i;
            loadGenerator.submit(() -> {
                Selector selector = null;
                SocketChannel channel = null;
                try {
                    selector = Selector.open();
                    channel = SocketChannel.open();
                    channel.configureBlocking(false);
                    channel.connect(new InetSocketAddress(host, port));
                    channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);

                    boolean sessionRunning = true;
                    long sessionStart = System.currentTimeMillis();

                    while (sessionRunning) {
                        // Enforce a 5-second deadline limit matching client timeout scenario
                        if (selector.select(1000) == 0) {
                            if (System.currentTimeMillis() - sessionStart > 5000) {
                                timedOut.incrementAndGet();
                                break;
                            }
                            continue;
                        }

                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = keys.iterator();

                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();

                            if (!key.isValid()) continue;

                            if (key.isConnectable()) {
                                SocketChannel ch = (SocketChannel) key.channel();
                                if (ch.finishConnect()) {
                                    String msg = "hello from client " + id + "\n";
                                    ch.write(ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8)));
                                    ch.register(selector, SelectionKey.OP_READ);
                                }
                            } else if (key.isReadable()) {
                                SocketChannel ch = (SocketChannel) key.channel();
                                ByteBuffer buffer = ByteBuffer.allocate(256);
                                int read = ch.read(buffer);

                                if (read == -1) {
                                    failed.incrementAndGet(); // Connection closed with no reply
                                    sessionRunning = false;
                                    break;
                                }

                                buffer.flip();
                                String response = StandardCharsets.UTF_8.decode(buffer).toString().trim();

                                if (!response.isEmpty()) {
                                    if (response.toLowerCase().contains("busy")) {
                                        rejected.incrementAndGet();
                                    } else {
                                        handled.incrementAndGet();
                                    }

                                    if (holdConnectionOpen) {
                                        Thread.sleep(waitTimeMs);
                                    }

                                    ch.write(ByteBuffer.wrap("bye\n".getBytes(StandardCharsets.UTF_8)));
                                    sessionRunning = false;
                                }
                            }
                        }
                    }
                } catch (java.net.SocketTimeoutException e) {
                    timedOut.incrementAndGet();
                } catch (IOException | InterruptedException e) {
                    failed.incrementAndGet();
                } finally {
                    try {
                        if (channel != null) channel.close();
                        if (selector != null) selector.close();
                    } catch (IOException ignored) {}
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();
        loadGenerator.shutdown();

        long duration = System.currentTimeMillis() - start;
        System.out.println("Finished in " + duration + "ms");
        System.out.println("Handled: " + handled.get());
        System.out.println("Rejected (server busy): " + rejected.get());
        System.out.println("Timed out: " + timedOut.get());
        System.out.println("Failed (other errors): " + failed.get());
    }
}
