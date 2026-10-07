# SOCKETS

A hands-on project to build the knowledge of Java sockets, focusing on
diagnosing system bottlenecks, handling network traffic bursts, and evolving
blocking thread architectures into non-blocking event loops.

## Requirements

- Java 21 or later (virtual threads are used by VirtualThreadServer,
  BoundedVirtualThreadServer, BoundedExecutorServer's rejection policy, and
  the load testers)

## Project Architectures

This repository walks through different socket implementations, showing the
limitations of each and how the next version solves it:

| Class                        | Default port |
|------------------------------|--------------|
| `Server`                     | 6661         |
| `ThreadedServer`             | 6662         |
| `ExecutorServer`             | 6663         |
| `BoundedExecutorServer`      | 6666         |
| `VirtualThreadServer`        | 6663         |
| `BoundedVirtualThreadServer` | 6663         |
| `NIOServer`                  | 6663         |

Several servers share port 6663, so run one at a time.

### Server Implementations

- **Server:** A basic server that receives a message from the client and
  echoes it back. This is the most basic version. It accepts exactly one
  client, and once that client is done (or sends `bye`) the server stops.
- **ThreadedServer:** Solves the single-client problem by using Threads.
  Each client has its own dedicated thread to interact with the server.
  However, there is no upper bound set here, and the system can crash
  from thread exhaustion.
- **ExecutorServer:** Optimizes thread management by using an
  `ExecutorService` with a fixed upper bound on threads. If the limit is
  reached, new clients must wait in a waiting queue. Because this waiting
  queue is unbounded, massive load can still exhaust memory or open-file
  limits, since every waiting client holds an open socket. Under heavy load,
  clients stuck in the waiting queue will throw `SocketTimeoutException`
  errors.
- **BoundedExecutorServer:** Resolves the unbounded queue issue by capping
  the waiting queue using an `ArrayBlockingQueue`. It introduces a critical
  pattern known as **Load Shedding**. When capacity overflows, a custom
  rejection policy waits briefly for 3 seconds (on a virtual thread, so the
  accept loop stays free) before turning excess clients away with a clear
  "Server is busy" response.
- **VirtualThreadServer:** Leverages Java 21 Virtual Threads to handle
  significantly more concurrent users. Virtual threads unmount from their
  carrier thread during blocking actions (like `in.readLine()` and
  `Thread.sleep()`), saving their state on the Java Heap. Without
  guardrails, an unconstrained flood of connections can still exhaust memory
  or open-file limits.
- **BoundedVirtualThreadServer:** Adds explicit limits using a
  Dual-Semaphore architecture: up to 5000 sessions running and up to 2000
  connections in the waiting queue. If traffic exceeds those boundaries,
  excess connections are immediately routed to a load-shedding routine that
  sends a "Server is busy" message and drops the socket.
- **NIOServer:** Built using the non-blocking I/O API. It handles many
  concurrent connections using an Event Loop architecture: a single thread
  serves all the clients, so the thread count does not grow with the number
  of clients. Memory and open-file limits still grow with connections. To keep
  the example simple, it treats each read as one complete message. A real
  server must also handle messages that arrive split across reads, or
  several in one read, and replies that can only be partly written.

### Client Implementations

- **Client:** Client sends the message to the server and then reads the
  response back from the server. It can send and receive multiple messages.
- **NIOClient:** A non-blocking client designed to communicate with the
  NIOServer. A Selector engine listens for connection and server responses,
  while console input is read on a separate thread (`System.in` cannot be
  registered with a Selector), preventing the network loop from hanging.

---

## Testing

Server, Client, and ThreadedServer's client-handling logic each have both
unit tests and integration tests.

- **Unit tests:** Exercise the message-handling logic directly using
  in-memory streams, without opening any real socket.
- **Integration tests:** Start a real server and connect to it over an
  actual socket, verifying the classes work correctly together over a real
  network connection.
- **Load Testing:** The `LoadTester` class stress-tests how different
  server architectures handle high volume:
- **ExecutorServer:** Excessive concurrent requests get stuck in the
  unbounded waiting queue, causing client-side socket read timeouts.
- **BoundedExecutorServer:** Excessive concurrent requests hit strict
  limits and are gracefully rejected with a clear "Server is Busy"
  notification.
- **NIOLoadTester:** Stress-tests the non-blocking loop by spawning
  multiple concurrent non-blocking connections against NIOServer. It reports
  how many were handled, rejected, timed out or failed (it does not measure
  memory).

### Running the Load Tests

To run a load test from the command line, compile your classes and execute your
chosen testing utility class (for example, with Maven: `mvn compile`, then add
`-cp target/classes`). You can optionally pass custom arguments:
`[port] [clientCount] [holdConnectionOpen] [waitTimeMs]`

#### 1. Running the Standard Load Test (Thread Pools)

Start ExecutorServer (port 6663) or BoundedExecutorServer (port 6666), then use
the matching port:

```
java -cp target/classes com.practice.loadTest.LoadTester 6663 500 true 300
java -cp target/classes com.practice.loadTest.LoadTester 6666 500 true 300
```

#### 2. Running the NIO Load Test (Non-Blocking Loop)

Start NIOServer (port 6663), then:

```
java -cp target/classes com.practice.loadTest.NIOLoadTester 6663 500 true 300
```