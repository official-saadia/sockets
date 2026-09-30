# SOCKETS

A hands-on project to build the knowledge of Java sockets, focusing on
diagnosing system bottlenecks, handling network traffic bursts, and evolving
blocking thread architectures into non-blocking event loops.

## Requirements
- Java 16 or later (BoundedExecutorServer's rejection policy uses a record)

## Project Architectures

This repository walks through different socket implementations, showing the
limitations of each and how the next version solves it:

### Server Implementations
*   **Server:** A basic server that receives a message from the client and
    echoes it back. This is the most basic version. Only a single client
    can connect and interact with the server.
*   **ThreadedServer:** Solves the single-client problem by using Threads.
    Each client has its own dedicated thread to interact with the server.
    However, there is no upper bound set here, and the system can crash
    from thread exhaustion.
*   **ExecutorServer:** Optimizes thread management by using an
    `ExecutorService` with a fixed upper bound on threads. If the limit is
    reached, new clients must wait in a waiting queue. Because this queue is
    unbounded, massive load can still cause a memory crash. Under heavy load,
    delayed clients will throw `SocketTimeoutException` errors.
*   **BoundedExecutorServer:** Resolves the unbounded queue issue by capping
    it using an `ArrayBlockingQueue`. It introduces a critical pattern known
    as **Load Shedding**. When capacity overflows, a custom rejection policy
    waits briefly for 3 seconds before turning excess clients away with a
    clear "Server is busy" response.
*   **VirtualThreadServer:** Leverages Java 21 Virtual Threads to handle
    significantly more concurrent users. Threads unmount from the OS carrier
    thread during blocking actions (like `in.readLine()` and
    `Thread.sleep()`), saving their state on the Java Heap. Without
    guardrails, an unconstrained flood of connections can trigger an
    `OutOfMemoryError`.
*   **BoundedVirtualThreadServer:** Introduces backpressure to prevent memory
    crashes using a Dual-Semaphore architecture. If traffic burst exceeds
    the boundaries, excess connections are immediately routed to a
    load-shedding routine that sends a "Server is busy" message and drops
    the socket.
*   **NIOServer:** Built using the non-blocking I/O API. It handles thousands
    of concurrent connections utilizing an Event Loop architecture. It uses
    a single thread for all the clients, meaning no waiting queues, no
    rejection policies, and no out of memory errors. This makes it the best
    choice for chat applications.

### Client Implementations
*   **Client:** Client sends the message to the server and then reads the
    response back from the server. It can send and receive multiple messages.
*   **NIOClient:** A non-blocking client designed to communicate with the
    NIOServer. Instead of using blocking streams, it uses a Selector engine
    to listen for server responses and read console input asynchronously,
    preventing the network loop from hanging.

---

## Testing
Server, Client, and ThreadedServer's client-handling logic each have both
unit tests and integration tests.

*   **Unit tests:** Exercise the message-handling logic directly using
    in-memory streams, without opening any real socket.
*   **Integration tests:** Start a real server and connect to it over an
    actual socket, verifying the classes work correctly together over a real
    network connection.
*   **Load Testing:** The `LoadTester` class stress-tests how different
    server architectures handle high volume:
  *   **ExecutorServer:** Excessive concurrent requests get stuck in the
      unbounded waiting queue, causing client-side socket read timeouts.
  *   **BoundedExecutorServer:** Excessive concurrent requests hit strict
      limits and are gracefully rejected with a clear "Server is Busy"
      notification.
  *   **NIOLoadTester:** Stress-tests the non-blocking loop by spawning
      multiple concurrent non-blocking connections, verifying that a
      single-threaded server handles high concurrent traffic with flat
      memory usage.

### Running the Load Tests
To run a load test from the command line, compile your classes and execute your
chosen testing utility class. You can optionally pass custom arguments:
`[port] [clientCount] [holdConnectionOpen] [waitTimeMs]`

#### 1. Running the Standard Load Test (Thread Pools)
```bash
java com.practice.loadTest.LoadTester 6661 500 true 300
```

#### 2. Running the NIO Load Test (Non-Blocking Loop)
```bash
java com.practice.loadTest.NIOLoadTester 6663 500 true 300
```
