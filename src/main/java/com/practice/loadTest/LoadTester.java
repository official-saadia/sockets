package com.practice.loadTest;

import com.practice.client.Client;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTester {

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
                try (Client client = new Client()) {
                    client.connect(host, port, 5000, id);
                    String response = client.sendMessage("hello from client " + id);

                    if (response == null) {
                        failed.incrementAndGet(); // Connection closed with no reply
                    } else if (response.toLowerCase().contains("busy")) {
                        rejected.incrementAndGet();
                    } else {
                        handled.incrementAndGet();
                    }

                    if (holdConnectionOpen) {
                        Thread.sleep(waitTimeMs);
                    } else {
                        client.sendMessage("bye");
                    }

                } catch (SocketTimeoutException e) {
                    timedOut.incrementAndGet();
                } catch (IOException | InterruptedException e) {
                    failed.incrementAndGet();
                } finally {
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
