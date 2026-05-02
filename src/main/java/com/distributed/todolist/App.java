package com.distributed.todolist;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main application entry point.
 * Allows user to choose between Server or Client mode.
 */
public class App extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Distributed To-Do List System");
        
        VBox mainLayout = new VBox(20);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(40));
        mainLayout.setStyle("-fx-background-color: #ecf0f1;");
        
        // Title
        Label titleLabel = new Label("Distributed To-Do List System");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        Label subtitleLabel = new Label("Shared Task Management");
        subtitleLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #7f8c8d;");
        
        // Description
        TextArea descriptionArea = new TextArea(
            "This is a distributed to-do list system that allows multiple users to " +
            "share and manage tasks in real-time.\n\n" +
            "Features:\n" +
            "• Add and delete tasks\n" +
            "• View shared task list\n" +
            "• Updates reflected across all clients\n" +
            "• Real-time synchronization\n" +
            "• Shared state management"
        );
        descriptionArea.setEditable(false);
        descriptionArea.setWrapText(true);
        descriptionArea.setStyle("-fx-background-color: white; -fx-control-background-color: white;");
        descriptionArea.setPrefWidth(400);
        descriptionArea.setPrefHeight(150);
        
        // Button box
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button serverButton = new Button("Start as Server");
        serverButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 15 30 15 30;");
        serverButton.setOnAction(e -> {
            primaryStage.close();
            launchServer();
        });
        
        Button clientButton = new Button("Start as Client");
        clientButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 15 30 15 30;");
        clientButton.setOnAction(e -> {
            primaryStage.close();
            launchClient();
        });
        
        buttonBox.getChildren().addAll(serverButton, clientButton);
        
        // Info label
        Label infoLabel = new Label("Select Server to host a new server, or Client to connect to an existing server");
        infoLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");
        
        mainLayout.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            new Separator(),
            descriptionArea,
            buttonBox,
            infoLabel
        );
        
        Scene scene = new Scene(mainLayout, 500, 450);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }
    
    private void launchServer() {
        try {
            Server server = new Server(5000);
            Thread serverThread = new Thread(() -> {
                server.start();
            });
            serverThread.setDaemon(true);
            serverThread.start();
            
            // Show server started message
            showInfoDialog("Server Started", 
                "The server has been started on port 5000.\n\n" +
                "You can now connect clients to: localhost:5000");
            
        } catch (Exception e) {
            showErrorDialog("Server Error", "Failed to start server: " + e.getMessage());
        }
    }
    
    private void launchClient() {
        // Launch client in a new window using Platform.runLater to avoid thread issue
        Platform.runLater(() -> {
            Stage clientStage = new Stage();
            ClientApp clientApp = new ClientApp();
            try {
                clientApp.start(clientStage);
            } catch (Exception e) {
                showErrorDialog("Client Error", "Failed to start client: " + e.getMessage());
            }
        });
    }
    
    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}