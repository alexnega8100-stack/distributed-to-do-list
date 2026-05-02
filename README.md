# Distributed To-Do List System

A shared task management system with real-time synchronization across multiple clients.

## Features

- **Add and delete tasks** - Create and remove shared tasks
- **View shared task list** - See all tasks from all connected users
- **Updates reflected across all clients** - Real-time synchronization
- **Shared state management** - Thread-safe task management
- **Distributed consistency** - Proper locking and synchronization

## Requirements

- Java 11 or higher
- Maven 3.6+
- JavaFX (included with Java 11+)

## Building the Project

```bash
mvn clean compile
```

## Running the Application

### Option 1: Using Maven

Run the main application (chooses between Server or Client mode):
```bash
mvn javafx:run
```

### Option 2: Run Server and Client Separately

**Start the Server:**
```bash
mvn compile exec:java -Dexec.mainClass="com.distributed.todolist.Server"
```

**Start a Client:**
```bash
mvn compile exec:java -Dexec.mainClass="com.distributed.todolist.ClientApp"
```

## How to Use

1. **First, start one instance as Server:**
   - Click "Start as Server" button
   - Server will listen on port 5000

2. **Then, start multiple instances as Clients:**
   - Click "Start as Client" button
   - Enter the server host (default: localhost)
   - Enter the port (default: 5000)
   - Enter your username
   - Click "Connect"

3. **Manage tasks:**
   - Add tasks using the input fields at the top
   - Click the checkbox to mark tasks as complete
   - Right-click on a task to delete or toggle completion
   - All changes are synchronized across all connected clients

## Architecture

- **Server**: Manages shared task list and broadcasts updates to all clients
- **Client**: JavaFX GUI for interacting with the shared task list
- **Task**: Serializable task object with title, description, and completion status
- **Message**: Protocol for client-server communication
- **TaskManager**: Thread-safe task storage with read-write locking

## Error Handling

- Input validation for required fields
- Connection error handling with user feedback
- Exception handling throughout the application
- Graceful disconnection handling