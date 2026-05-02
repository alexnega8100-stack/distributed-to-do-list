package com.distributed.todolist;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Server application for the Distributed To-Do List System.
 * Handles client connections and synchronizes task updates across all clients.
 */
public class Server {
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());
    private static final int DEFAULT_PORT = 5000;
    private static final int MAX_CLIENTS = 10;

    private final int port;
    private final TaskManager taskManager;
    private final ServerSocket serverSocket;
    private final ExecutorService clientExecutor;
    private final Set<ClientHandler> connectedClients;
    private final Map<String, ClientHandler> clientMap;
    private volatile boolean running;

    public Server(int port) throws IOException {
        this.port = port;
        this.taskManager = new TaskManager();
        this.serverSocket = new ServerSocket(port);
        this.clientExecutor = Executors.newFixedThreadPool(MAX_CLIENTS);
        this.connectedClients = Collections.synchronizedSet(new HashSet<>());
        this.clientMap = new ConcurrentHashMap<>();
        this.running = false;
        
        // Configure logging
        configureLogging();
    }

    private void configureLogging() {
        LOGGER.setLevel(Level.INFO);
        try {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.INFO);
            LOGGER.addHandler(handler);
        } catch (Exception e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
        }
    }

    /**
     * Start the server and accept client connections.
     */
    public void start() {
        running = true;
        LOGGER.info("Server starting on port " + port);
        
        // Add task change listener to broadcast updates
        taskManager.addListener(new TaskManager.TaskChangeListener() {
            @Override
            public void onTaskAdded(Task task) {
                broadcastTaskUpdate(Message.createAddTask(task, "SERVER"));
            }

            @Override
            public void onTaskDeleted(String taskId) {
                broadcastTaskUpdate(Message.createDeleteTask(taskId, "SERVER"));
            }

            @Override
            public void onTaskUpdated(Task task) {
                broadcastTaskUpdate(Message.createUpdateTask(task, "SERVER"));
            }
        });

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("New client connection from: " + clientSocket.getRemoteSocketAddress());
                
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                connectedClients.add(clientHandler);
                clientExecutor.submit(clientHandler);
                
            } catch (IOException e) {
                if (running) {
                    LOGGER.severe("Error accepting client: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        running = false;
        LOGGER.info("Server stopping...");
        
        // Notify all clients
        broadcastMessage(Message.createError("Server is shutting down"));
        
        // Close all client connections
        for (ClientHandler client : connectedClients) {
            client.close();
        }
        
        connectedClients.clear();
        clientMap.clear();
        
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.severe("Error closing server socket: " + e.getMessage());
        }
        
        clientExecutor.shutdown();
        try {
            if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                clientExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("Server stopped");
    }

    /**
     * Register a client handler.
     */
    public void registerClient(String username, ClientHandler handler) {
        clientMap.put(username, handler);
        broadcastUserList();
    }

    /**
     * Unregister a client handler.
     */
    public void unregisterClient(String username) {
        clientMap.remove(username);
        broadcastUserList();
    }

    /**
     * Get the task manager.
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Broadcast a message to all connected clients.
     */
    public void broadcastMessage(Message message) {
        synchronized (connectedClients) {
            for (ClientHandler client : connectedClients) {
                try {
                    client.sendMessage(message);
                } catch (IOException e) {
                    LOGGER.warning("Error broadcasting to client: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Broadcast task update to all clients except sender.
     */
    public void broadcastTaskUpdate(Message message) {
        synchronized (connectedClients) {
            for (ClientHandler client : connectedClients) {
                if (!client.getUsername().equals(message.getSender())) {
                    try {
                        client.sendMessage(message);
                    } catch (IOException e) {
                        LOGGER.warning("Error broadcasting to client: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Broadcast the current user list to all clients.
     */
    private void broadcastUserList() {
        List<String> users = new ArrayList<>(clientMap.keySet());
        Message userListMsg = new Message(Message.MessageType.USER_LIST_UPDATE);
        userListMsg.setContent(users.toString());
        broadcastMessage(userListMsg);
    }

    /**
     * Remove a client handler when disconnected.
     */
    public void removeClient(ClientHandler handler) {
        connectedClients.remove(handler);
        String username = handler.getUsername();
        if (username != null) {
            clientMap.remove(username);
            broadcastUserList();
        }
        LOGGER.info("Client disconnected: " + handler.getRemoteAddress());
    }

    /**
     * Get the number of connected clients.
     */
    public int getClientCount() {
        return connectedClients.size();
    }

    /**
     * Main method to start the server.
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
            }
        }

        try {
            Server server = new Server(port);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down server...");
                server.stop();
            }));
            
            server.start();
        } catch (IOException e) {
            LOGGER.severe("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}

/**
 * Handler for individual client connections.
 */
class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final Server server;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private String username;
    private volatile boolean running;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.running = false;
    }

    @Override
    public void run() {
        try {
            initializeStreams();
            handleClient();
        } catch (IOException e) {
            LOGGER.severe("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void initializeStreams() throws IOException {
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());
        running = true;
    }

    private void handleClient() {
        while (running) {
            try {
                Message message = (Message) inputStream.readObject();
                processMessage(message);
            } catch (ClassNotFoundException e) {
                LOGGER.severe("Unknown message class: " + e.getMessage());
            } catch (IOException e) {
                if (running) {
                    LOGGER.warning("Connection lost: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void processMessage(Message message) {
        LOGGER.info("Received message: " + message.getType() + " from " + message.getSender());
        
        try {
            switch (message.getType()) {
                case CONNECT:
                    handleConnect(message);
                    break;
                case ADD_TASK:
                    handleAddTask(message);
                    break;
                case DELETE_TASK:
                    handleDeleteTask(message);
                    break;
                case UPDATE_TASK:
                    handleUpdateTask(message);
                    break;
                case GET_ALL_TASKS:
                    handleGetAllTasks(message);
                    break;
                case DISCONNECT:
                    handleDisconnect(message);
                    break;
                default:
                    sendMessage(Message.createError("Unknown message type"));
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing message: " + e.getMessage());
            sendMessageSafe(Message.createError("Error processing request: " + e.getMessage()));
        }
    }

    private void handleConnect(Message message) {
        this.username = message.getSender();
        server.registerClient(username, this);
        
        // Send current task list to new client
        List<Task> tasks = server.getTaskManager().getAllTasksSorted();
        Message taskListMsg = new Message(Message.MessageType.TASK_LIST);
        // Serialize task list manually since we can't serialize List<Task> directly
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(tasks);
            oos.flush();
            taskListMsg.setContent(Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch (IOException e) {
            taskListMsg.setContent("[]");
        }
        sendMessageSafe(taskListMsg);
        
        // Send acknowledgment
        sendMessageSafe(Message.createAck(true, "Connected successfully"));
        
        LOGGER.info("User connected: " + username);
    }

    private void handleAddTask(Message message) {
        Task task = message.getTask();
        if (task == null || task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            sendMessageSafe(Message.createError("Invalid task: title is required"));
            return;
        }
        
        Task addedTask = server.getTaskManager().addTask(task);
        if (addedTask != null) {
            sendMessageSafe(Message.createAck(true, "Task added successfully"));
            // Broadcast to other clients
            server.broadcastTaskUpdate(message);
        } else {
            sendMessageSafe(Message.createError("Failed to add task"));
        }
    }

    private void handleDeleteTask(Message message) {
        String taskId = message.getTaskId();
        if (taskId == null || taskId.trim().isEmpty()) {
            sendMessageSafe(Message.createError("Invalid task ID"));
            return;
        }
        
        boolean deleted = server.getTaskManager().deleteTask(taskId);
        if (deleted) {
            sendMessageSafe(Message.createAck(true, "Task deleted successfully"));
            // Broadcast to other clients
            server.broadcastTaskUpdate(message);
        } else {
            sendMessageSafe(Message.createError("Task not found"));
        }
    }

    private void handleUpdateTask(Message message) {
        Task task = message.getTask();
        if (task == null || task.getId() == null) {
            sendMessageSafe(Message.createError("Invalid task: ID is required"));
            return;
        }
        
        Task updatedTask = server.getTaskManager().updateTask(task);
        if (updatedTask != null) {
            sendMessageSafe(Message.createAck(true, "Task updated successfully"));
            // Broadcast to other clients
            server.broadcastTaskUpdate(message);
        } else {
            sendMessageSafe(Message.createError("Task not found"));
        }
    }

    private void handleGetAllTasks(Message message) {
        List<Task> tasks = server.getTaskManager().getAllTasksSorted();
        Message taskListMsg = new Message(Message.MessageType.TASK_LIST);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(tasks);
            oos.flush();
            taskListMsg.setContent(Base64.getEncoder().encodeToString(baos.toByteArray()));
        } catch (IOException e) {
            taskListMsg.setContent("[]");
        }
        sendMessageSafe(taskListMsg);
    }

    private void handleDisconnect(Message message) {
        running = false;
        if (username != null) {
            server.unregisterClient(username);
        }
    }

    public void sendMessage(Message message) throws IOException {
        synchronized (outputStream) {
            outputStream.writeObject(message);
            outputStream.flush();
            outputStream.reset();
        }
    }

    /**
     * Send a message without throwing checked exceptions.
     * Errors are logged but not propagated.
     */
    private void sendMessageSafe(Message message) {
        try {
            sendMessage(message);
        } catch (IOException e) {
            LOGGER.severe("Error sending message: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    public void close() {
        try {
            running = false;
            socket.close();
        } catch (IOException e) {
            LOGGER.warning("Error closing client socket: " + e.getMessage());
        }
    }

    private void cleanup() {
        server.removeClient(this);
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            LOGGER.warning("Error cleaning up: " + e.getMessage());
        }
    }
}