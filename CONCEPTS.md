# Java Sockets

## Problem

Applications that continuously exchange data between two machines, like a chat app, need to communicate reliably over a network. If the underlying communication mechanism loses or reorders data, it can lead to incorrect or incomplete application behavior.

So we need a communication mechanism that provides:

- **Reliable delivery** — data should not be lost silently.
- **Ordered delivery** — data should be received in the order it was sent.

A chat app also isn't a one-time exchange — both people send and receive messages throughout an ongoing conversation. So we also need:

- **Two-way communication** — both machines should be able to send and receive data.
- **Continuous communication** — the connection should support repeated data exchange without establishing a new connection for every message.

# Choosing the Communication Mode

We already know a chat app needs two-way, continuous communication. That rules out two of the three basic modes right away:

- **Simplex** — data flows in one direction only, so two-way communication is off the table.
- **Half-duplex** — both sides can send and receive, but not at the same time — one side has to wait its turn, which breaks continuous exchange.
  That leaves:

- **Full-duplex** — both sides send and receive simultaneously.
  So for applications like chat, **full-duplex** is the only mode that satisfies what we need — it isn't just the "best" option, it's the only one that survives the requirements from the Problem section.

# Why TCP?

We've already established that we need **reliable, ordered, full-duplex, and continuous** communication. **TCP (Transmission Control Protocol)** happens to satisfy all four in one package:

- **Connection-oriented** — once a connection is set up between two machines, it stays open, giving us continuous communication without reconnecting for every message.
- **Reliable** — no data is lost silently.
- **Ordered** — data arrives in the sequence it was sent.
- **Full-duplex** — once the connection is established, both sides can send and receive at the same time.
  So TCP isn't just *a* protocol with nice properties — its properties are exactly the requirements we started with. That's why it's the transport protocol behind applications like chat.

# Where Do Sockets Fit?

We've settled on TCP as the transport protocol — but TCP itself is just a set of rules for how data should move reliably over a network. An application can't "talk TCP" directly; it needs a concrete interface to open a connection, send bytes, and receive them.

That's what a **socket** is: one endpoint of a two-way communication link, identified by the combination of an **IP address** and a **port number**. The IP address gets the data to the right machine; the port gets it to the right application running on that machine.

In Java, this interface is the Socket class — it gives the application methods to send and receive data through a TCP connection, without needing to manage the underlying protocol itself.

One asymmetry worth noticing: the server's port is fixed — the developer picks it ahead of time (like 6661 here) so clients know where to find it.
The client's port isn't chosen by the developer at all; the OS assigns an available one automatically the moment the socket opens. That's why the diagram's client port (51000) looks arbitrary while the server's matches the code you're about to see.

<svg width="100%" viewBox="0 0 680 236" role="img">
<title>Client and server communicating through sockets over a TCP connection</title>
<desc>A client application connects through a client socket identified by an IP address and port, over a TCP connection, to a server socket identified by its own IP address and port, which connects to the server application.</desc>
<defs>
<marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M2 1L8 5L2 9" fill="none" stroke="#5F5E5A" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></marker>
</defs>
<rect x="60" y="40" width="200" height="44" rx="8" fill="#E6F1FB" stroke="#185FA5" stroke-width="0.5"/>
<text x="160" y="62" text-anchor="middle" dominant-baseline="central" font-size="14" font-weight="500" fill="#0C447C">Client application</text>
<rect x="420" y="40" width="200" height="44" rx="8" fill="#E1F5EE" stroke="#0F6E56" stroke-width="0.5"/>
<text x="520" y="62" text-anchor="middle" dominant-baseline="central" font-size="14" font-weight="500" fill="#085041">Server application</text>
<line x1="160" y1="84" x2="160" y2="140" stroke="#5F5E5A" stroke-width="1.5" marker-end="url(#arrow)"/>
<line x1="520" y1="84" x2="520" y2="140" stroke="#5F5E5A" stroke-width="1.5" marker-end="url(#arrow)"/>
<rect x="60" y="140" width="200" height="56" rx="8" fill="#E6F1FB" stroke="#185FA5" stroke-width="0.5"/>
<text x="160" y="158" text-anchor="middle" dominant-baseline="central" font-size="14" font-weight="500" fill="#0C447C">Client socket</text>
<text x="160" y="176" text-anchor="middle" dominant-baseline="central" font-size="12" fill="#185FA5">IP 192.168.1.5:51000</text>
<rect x="420" y="140" width="200" height="56" rx="8" fill="#E1F5EE" stroke="#0F6E56" stroke-width="0.5"/>
<text x="520" y="158" text-anchor="middle" dominant-baseline="central" font-size="14" font-weight="500" fill="#085041">Server socket</text>
<text x="520" y="176" text-anchor="middle" dominant-baseline="central" font-size="12" fill="#0F6E56">IP 192.168.1.10:6661</text>
<line x1="262" y1="168" x2="418" y2="168" stroke="#5F5E5A" stroke-width="1.5" marker-start="url(#arrow)" marker-end="url(#arrow)"/>
<text x="340" y="150" text-anchor="middle" font-size="12" fill="#5F5E5A">TCP connection</text>
</svg>

# Code Walkthrough

Now let's see how the concepts above show up as actual code — starting with the server side.

## Server

It opens a socket on a fixed IP:port, waits for a client to connect, and echoes back whatever it receives until the client sends `"bye"`.

```java
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
```

A few things worth connecting back to what we covered:

- `ServerSocket` is the thing listening on port `6661` — this is the server socket from the diagram, waiting for a connection at that IP:port.
- `serverSocket.accept()` is the moment a client actually connects — it blocks until that happens, then hands back a `Socket` representing that one connection.
- `BufferedReader` and `PrintWriter` are just conveniences for reading and writing text over the socket's raw input/output streams — the full-duplex behavior we talked about earlier is what lets `in` and `out` both be used on the same connection at once.
  One limitation worth knowing: `start()` calls `accept()` only once, so this server handles exactly one client, then stops. A real chat server would loop on `accept()` or hand each connection off to its own thread — but that's a step beyond what this article covers.

## Client

The client mirrors the server's side of the diagram — it opens a socket to the server's fixed IP:port, but its own port is assigned automatically by the OS the moment the connection is made.

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
 
public class Client implements AutoCloseable {
 
    private static final Logger logger = Logger.getLogger(Client.class.getName());
    private static final int DEFAULT_READ_TIMEOUT_MS = 10_000;
 
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
 
    public Client() {
    }
 
    // Package-private: lets unit tests inject streams directly, without a real socket.
    Client(BufferedReader in, PrintWriter out) {
        this.in = in;
        this.out = out;
    }
 
    public void connect(String host, int port) throws IOException {
        connect(host, port, DEFAULT_READ_TIMEOUT_MS);
    }
 
    public void connect(String host, int port, int readTimeoutMs) throws IOException {
        logger.info("Connecting to " + host + ":" + port + "...");
        try {
            socket = new Socket(host, port);
            // Prevents readLine() from blocking forever if the server never responds.
            socket.setSoTimeout(readTimeoutMs);
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            logger.info("Connected to " + host + ":" + port);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to connect to " + host + ":" + port, e);
            throw e;
        }
    }
 
    public String sendMessage(String message) throws IOException {
        // Both sides talk in terms of readLine()/println(), so a message containing
        // a newline would be split into two messages on the other end. Reject it here
        // to keep the line-based framing the whole protocol depends on.
        if (message.contains("\n") || message.contains("\r")) {
            String errorMessage = "message must not contain line breaks: \"" + message + "\"";
            logger.warning(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
 
        try {
            logger.info("Sending: " + message);
            out.println(message);
            String response = in.readLine();
            logger.info("Received: " + response);
            return response;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending/receiving message: " + message, e);
            throw e;
        }
    }
 
    @Override
    public void close() throws IOException {
        logger.info("Closing client connection...");
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
        if (socket != null) {
            socket.close();
        }
        logger.info("Client connection closed.");
    }
 
    public static void main(String[] args) throws IOException {
        try (Client client = new Client();
             BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))) {
 
            client.connect("localhost", 6661);
 
            String input;
            while ((input = consoleIn.readLine()) != null) {
                String response = client.sendMessage(input);
                System.out.println("Response: " + response);
                if (input.equals("bye")) {
                    break;
                }
            }
        }
    }
}
```

A few things worth connecting back to what we covered:

- `new Socket(host, port)` is what opens the client socket — the OS picks its port automatically, which is why the diagram shows an arbitrary-looking `51000` instead of a number the developer chose.
- `setSoTimeout` prevents `readLine()` from blocking forever if the server never responds — without it, a dropped connection could hang the client indefinitely.
- The line-break check in `sendMessage` isn't just input validation — both sides communicate one line at a time via `readLine()`/`println()`, so a message containing a newline would be read as two separate messages on the other end. This directly protects the ordered, line-based framing the protocol depends on.
  One thing worth knowing: if the server closes the connection unexpectedly, `in.readLine()` returns `null` rather than throwing — so `sendMessage` returns `null`, and `main` prints `"Response: null"` instead of crashing.