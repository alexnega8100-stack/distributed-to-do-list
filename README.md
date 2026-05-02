🚀 Key Distributed Features
1. Shared State & Consistency
The system utilizes a Centralized Server Architecture where the Server acts as the "Single Source of Truth."

Mechanism: When a client performs an action (Add/Delete/Update), the state change is sent to the server, which then broadcasts the update to all active nodes.

Goal: Ensures Eventual Consistency across all connected clients.

2. Synchronization & Concurrency Control
To handle multiple users interacting with the same data simultaneously:

Locks: Implemented ReentrantReadWriteLock in the TaskManager to manage concurrent access.

Thread Safety: Multiple clients can read tasks (Read Lock) while ensuring exclusive access during modifications (Write Lock) to prevent race conditions.

3. Logical Clocks (Total Ordering)
To solve the issue of clock drift between different laptops, we implemented Lamport-style Logical Clocks.

Ordering: The Server assigns a unique sequenceNumber to every task.

Result: Regardless of the local time on a user's computer, all tasks are displayed in the same global order for every user.

4. File Persistence (Reliability)
Demonstrates Durability in distributed systems.

Database Simulation: Every state change is automatically serialized and saved to server_storage.dat.

Recovery: If the server process is terminated, it automatically restores the task list upon restart, ensuring no data loss.

5. Fault Tolerance & Recovery
The system is designed to handle network instability.

Retry Logic: The Client includes an automated connection retry loop that attempts to re-establish a link to the server if the socket connection drops.

Graceful Degradation: Provides real-time feedback via the status bar when a connection is interrupted.

6. Real-time Concurrency Visualization
Event Notification: Features a "Who is Typing" status. When one user interacts with a field, a specialized message is broadcast to others, demonstrating low-latency event-driven communication.

🛠️ Tech Stack
Language: Java 26

GUI: JavaFX (CSS-styled for modern UI)

Networking: Java Sockets (TCP/IP)

Build Tool: Maven

📂 Project Structure
Server.java: Handles multi-threaded client connections and broadcasts.

TaskManager.java: Manages the shared state, persistence, and locking logic.

ClientApp.java: The interactive JavaFX interface with real-time listeners.

Message.java: The communication protocol (Common Object Model).

Task.java: The serializable data model with Lamport timestamps.
