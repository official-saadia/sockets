# SOCKETS

This project is to build the knowledge about sockets.

## Requirements
- Java 16 or later (BoundedExecutorServer's rejection policy uses a record)

## Implementation
### Server
Server class implements a basic server which receives a message from the client and then echoes it back. This is the
most basic version. Only a single client can connect and interact with the server.

### ThreadedServer
This version supports multiple clients by using Threads. Each client will have its own thread and can interact
with the server on its dedicated thread.
Any number of clients can connect to the server as there is no upper bound set here and the system can crash.


### ExecutorServer
This version is an optimization of ThreadedServer. It uses ExecutorService so that Java takes care of the
thread management. It also sets an upper bound on threads.
If the upper bound is reached, more clients have to wait until a thread becomes available. The queue holding
waiting clients has no limit, so under massive load it can still grow unbounded and eventually crash the
server, just from memory instead of threads.

Whether that crash affects only this application or the whole machine depends on how much memory the JVM
is allowed to use (its max heap size). If a limit is set, the application crashes on its own once it hits
that limit, and the rest of the machine keeps running normally. If no limit is set, the queue can consume
most of the machine's available memory, which can slow down or affect other programs running on the same
machine as well.

### BoundedExecutorServer
This is an optimized version of ExecutorServer. In real world applications, clients are not kept in a waiting
state by the server indefinitely. The server makes them wait for a configured time, and if a slot is still not
available after that, a proper message is sent to the client, i.e., "Server is busy at the moment, please try
again after some time." This also caps the previously-unbounded queue, removing that remaining crash risk.

### Client
Client sends the message to the server and then reads the response back from the server. It can send and
receive multiple messages.

## Testing
Server, Client, and ThreadedServer's client-handling logic each have both unit tests and integration tests.

- **Unit tests** exercise the message-handling logic directly using in-memory streams, without opening any
  real socket.
- **Integration tests** start a real server and connect to it over an actual socket, verifying the classes
  work correctly together over a real network connection.