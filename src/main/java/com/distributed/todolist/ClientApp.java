package com.distributed.todolist;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Modern Client application for the Distributed To-Do List System.
 */
public class ClientApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    private TextField hostField, portField, usernameField;
    private Button connectButton, disconnectButton, addTaskButton;
    private TextField taskTitleField, taskDescriptionField;
    private ListView<Task> taskListView;
    private ObservableList<Task> taskItems;
    private Label statusLabel, connectionLabel, userCountLabel;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private String username, host;
    private int port;
    private volatile boolean connected = false;
    private volatile boolean running = false;
    private ExecutorService clientExecutor;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Distributed To-Do List System");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(createConnectionPanel());
        mainLayout.setCenter(createTaskPanel());
        mainLayout.setBottom(createStatusBar());

        // We use a data URI for the stylesheet to keep it all in one file for you
        Scene scene = new Scene(mainLayout, 900, 700);
        scene.getStylesheets().add("data:text/css," + createStylesheet());

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            disconnectFromServer();
            Platform.exit();
        });

        primaryStage.show();
    }

    private String createStylesheet() {
        return ".root { -fx-background-color: #f0f2f5; }" +
               ".button { -fx-cursor: hand; -fx-background-radius: 8; -fx-transition: 0.3s; }" +
               ".button:hover { -fx-opacity: 0.85; -fx-scale-x: 1.05; -fx-scale-y: 1.05; }" +
               ".text-field { -fx-background-radius: 8; -fx-border-color: #dcdde1; -fx-border-radius: 8; -fx-padding: 8; }" +
               ".list-view { -fx-background-color: transparent; -fx-background-insets: 0; }" +
               ".list-cell { -fx-background-color: transparent; -fx-padding: 8; }" +
               ".list-cell:filled { -fx-background-color: transparent; }";
    }

    private VBox createConnectionPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(25));
        panel.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #4b6584); " +
                       "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 5);");

        Label titleLabel = new Label("Distributed To-Do List");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");

        HBox connectionBox = new HBox(15);
        connectionBox.setAlignment(Pos.CENTER_LEFT);

        hostField = new TextField(DEFAULT_HOST);
        portField = new TextField(String.valueOf(DEFAULT_PORT));
        usernameField = new TextField();
        usernameField.setPromptText("Your Username");

        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #26de81; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        connectButton.setOnAction(e -> connectToServer());

        disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle("-fx-background-color: #eb4d4b; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        disconnectButton.setDisable(true);
        disconnectButton.setOnAction(e -> disconnectFromServer());

        connectionLabel = new Label("Not connected");
        connectionLabel.setStyle("-fx-text-fill: #bdc3c7;");

        connectionBox.getChildren().addAll(
            new Label("Host:"), hostField, 
            new Label("Port:"), portField, 
            usernameField, connectButton, disconnectButton
        );
        
        panel.getChildren().addAll(titleLabel, connectionBox, connectionLabel);
        return panel;
    }

    private VBox createTaskPanel() {
        VBox panel = new VBox(20);
        panel.setPadding(new Insets(25));

        HBox addTaskBox = new HBox(15);
        addTaskBox.setAlignment(Pos.CENTER_LEFT);
        
        taskTitleField = new TextField();
        taskTitleField.setPromptText("What needs to be done?");
        taskTitleField.setPrefWidth(300);
        
        taskDescriptionField = new TextField();
        taskDescriptionField.setPromptText("Optional description...");
        taskDescriptionField.setPrefWidth(300);
        
        addTaskButton = new Button("Add Task");
        addTaskButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 25;");
        addTaskButton.setDisable(true);
        addTaskButton.setOnAction(e -> addTask());

        addTaskBox.getChildren().addAll(taskTitleField, taskDescriptionField, addTaskButton);

        taskItems = FXCollections.observableArrayList();
        taskListView = new ListView<>(taskItems);
        taskListView.setCellFactory(param -> new TaskListCell());

        VBox.setVgrow(taskListView, Priority.ALWAYS);
        panel.getChildren().addAll(addTaskBox, new Label("Shared Workspace:"), taskListView);
        return panel;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(10, 25, 10, 25));
        statusBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dcdde1; -fx-border-width: 1 0 0 0;");
        
        statusLabel = new Label("Ready to sync...");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
        
        userCountLabel = new Label("Connected Users: 0");
        userCountLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        statusBar.getChildren().addAll(statusLabel, spacer, userCountLabel);
        return statusBar;
    }

    // --- LOGIC METHODS ---

    private void connectToServer() {
        host = hostField.getText().trim();
        username = usernameField.getText().trim();
        
        if (username.isEmpty()) {
            showError("Please enter a username.");
            return;
        }

        try {
            port = Integer.parseInt(portField.getText().trim());
            socket = new Socket(host, port);
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());

            outputStream.writeObject(Message.createConnect(username));
            outputStream.flush();

            connected = true;
            running = true;
            clientExecutor = Executors.newSingleThreadExecutor();
            clientExecutor.submit(this::receiveMessages);

            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            addTaskButton.setDisable(false);
            connectionLabel.setText("● Connected to " + host);
            connectionLabel.setStyle("-fx-text-fill: #26de81;");
            statusLabel.setText("Loggeed in as: " + username);

        } catch (Exception e) {
            showError("Connection failed: " + e.getMessage());
        }
    }

    private void receiveMessages() {
        while (running && connected) {
            try {
                Message message = (Message) inputStream.readObject();
                handleServerMessage(message);
            } catch (Exception e) {
                if (running) Platform.runLater(() -> disconnectFromServer());
                break;
            }
        }
    }

    private void handleServerMessage(Message message) {
        Platform.runLater(() -> {
            switch (message.getType()) {
                case TASK_LIST: handleTaskList(message); break;
                case ADD_TASK: if(!message.getSender().equals(username)) taskItems.add(message.getTask()); break;
                case DELETE_TASK: taskItems.removeIf(t -> t.getId().equals(message.getTaskId())); break;
                case UPDATE_TASK: handleTaskUpdated(message); break;
                case USER_LIST_UPDATE: handleUserListUpdate(message); break;
            }
        });
    }

    private void handleTaskList(Message message) {
        try {
            byte[] decoded = Base64.getDecoder().decode(message.getContent());
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(decoded));
            List<Task> tasks = (List<Task>) ois.readObject();
            taskItems.setAll(tasks);
        } catch (Exception e) { LOGGER.severe("Sync Error"); }
    }

    private void handleTaskUpdated(Message message) {
        Task updated = message.getTask();
        for (int i = 0; i < taskItems.size(); i++) {
            if (taskItems.get(i).getId().equals(updated.getId())) {
                taskItems.set(i, updated);
                break;
            }
        }
    }

    private void handleUserListUpdate(Message message) {
        String content = message.getContent();
        int count = content.split(",").length;
        userCountLabel.setText("Connected Users: " + count);
    }

    private void addTask() {
        String title = taskTitleField.getText().trim();
        if (title.isEmpty()) return;

        try {
            Task task = new Task(title, taskDescriptionField.getText().trim(), username);
            outputStream.writeObject(Message.createAddTask(task, username));
            outputStream.flush();
            taskItems.add(task); // Optimistic UI update
            taskTitleField.clear();
            taskDescriptionField.clear();
        } catch (IOException e) { showError("Failed to send task."); }
    }

    private void deleteTask(String id) {
        try {
            outputStream.writeObject(Message.createDeleteTask(id, username));
            outputStream.flush();
        } catch (IOException e) { showError("Delete failed."); }
    }

    private void toggleTaskCompletion(Task task) {
        if (task == null) return;
        try {
            task.setCompleted(!task.isCompleted());
            outputStream.writeObject(Message.createUpdateTask(task, username));
            outputStream.flush();
            taskListView.refresh();
        } catch (IOException e) { showError("Update failed."); }
    }

    private void disconnectFromServer() {
        running = false;
        connected = false;
        try {
            if (socket != null) socket.close();
            if (clientExecutor != null) clientExecutor.shutdownNow();
        } catch (IOException e) { }
        
        Platform.runLater(() -> {
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            addTaskButton.setDisable(true);
            taskItems.clear();
            connectionLabel.setText("Not connected");
            connectionLabel.setStyle("-fx-text-fill: #bdc3c7;");
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // --- BEAUTIFUL TASK CELL ---
    class TaskListCell extends ListCell<Task> {
        private HBox content = new HBox(15);
        private CheckBox checkBox = new CheckBox();
        private Label titleLabel = new Label();
        private Label creatorLabel = new Label();
        private Button deleteBtn = new Button("Delete");

        public TaskListCell() {
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPadding(new Insets(15));
            content.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                           "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 4);");
            
            VBox textCol = new VBox(3);
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2f3640;");
            creatorLabel.setStyle("-fx-text-fill: #3498db; -fx-font-size: 11px;");
            textCol.getChildren().addAll(titleLabel, creatorLabel);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #eb4d4b; " +
                             "-fx-border-color: #eb4d4b; -fx-border-radius: 5; -fx-font-size: 11px;");
            
            content.getChildren().addAll(checkBox, textCol, deleteBtn);

            checkBox.setOnAction(e -> toggleTaskCompletion(getItem()));
            deleteBtn.setOnAction(e -> deleteTask(getItem().getId()));
        }

        @Override
        protected void updateItem(Task task, boolean empty) {
            super.updateItem(task, empty);
            if (empty || task == null) {
                setGraphic(null);
            } else {
                titleLabel.setText(task.getTitle());
                creatorLabel.setText("By: " + task.getCreatedBy());
                checkBox.setSelected(task.isCompleted());
                
                if (task.isCompleted()) {
                    titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #bdc3c7; -fx-strikethrough: true;");
                } else {
                    titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2f3640;");
                }
                setGraphic(content);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}