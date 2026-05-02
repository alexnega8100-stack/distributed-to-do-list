package com.distributed.todolist;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents messages exchanged between client and server.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        ADD_TASK,
        DELETE_TASK,
        UPDATE_TASK,
        GET_ALL_TASKS,
        TASK_LIST,
        ERROR,
        ACK,
        CONNECT,
        DISCONNECT,
        USER_LIST_UPDATE
    }

    private MessageType type;
    private Task task;
    private String taskId;
    private String content;
    private String sender;
    private LocalDateTime timestamp;
    private boolean success;
    private String errorMessage;

    public Message() {
        this.timestamp = LocalDateTime.now();
    }

    public Message(MessageType type) {
        this();
        this.type = type;
    }

    public static Message createAddTask(Task task, String sender) {
        Message msg = new Message(MessageType.ADD_TASK);
        msg.task = task;
        msg.sender = sender;
        return msg;
    }

    public static Message createDeleteTask(String taskId, String sender) {
        Message msg = new Message(MessageType.DELETE_TASK);
        msg.taskId = taskId;
        msg.sender = sender;
        return msg;
    }

    public static Message createUpdateTask(Task task, String sender) {
        Message msg = new Message(MessageType.UPDATE_TASK);
        msg.task = task;
        msg.sender = sender;
        return msg;
    }

    public static Message createGetAllTasks() {
        return new Message(MessageType.GET_ALL_TASKS);
    }

    public static Message createTaskList(java.util.List<Task> tasks) {
        Message msg = new Message(MessageType.TASK_LIST);
        msg.content = tasks.toString();
        return msg;
    }

    public static Message createError(String errorMessage) {
        Message msg = new Message(MessageType.ERROR);
        msg.success = false;
        msg.errorMessage = errorMessage;
        return msg;
    }

    public static Message createAck(boolean success, String message) {
        Message msg = new Message(MessageType.ACK);
        msg.success = success;
        msg.content = message;
        return msg;
    }

    public static Message createConnect(String username) {
        Message msg = new Message(MessageType.CONNECT);
        msg.sender = username;
        return msg;
    }

    public static Message createDisconnect(String username) {
        Message msg = new Message(MessageType.DISCONNECT);
        msg.sender = username;
        return msg;
    }

    // Getters and Setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", task=" + (task != null ? task.getTitle() : "null") +
                ", taskId='" + taskId + '\'' +
                ", sender='" + sender + '\'' +
                ", success=" + success +
                '}';
    }
}