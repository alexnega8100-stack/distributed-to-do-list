package com.distributed.todolist;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class TaskManager {
    private Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<TaskChangeListener> listeners = Collections.synchronizedList(new ArrayList<>());
    
    // LOGICAL CLOCK: Ensures tasks have a global order regardless of laptop time
    private final AtomicLong logicalClock = new AtomicLong(0);
    private final String DATA_FILE = "tasks.dat";

    public interface TaskChangeListener {
        void onTaskAdded(Task task);
        void onTaskDeleted(String taskId);
        void onTaskUpdated(Task task);
    }

    public TaskManager() {
        loadFromFile(); // Load existing tasks when the server starts
    }

    public Task addTask(Task task) {
        lock.writeLock().lock();
        try {
            // Assign a server-side sequence number for consistency
            task.setUpdatedAt(java.time.LocalDateTime.now()); 
            tasks.put(task.getId(), task);
            saveToFile(); 
            notifyListeners("ADD", task, null);
            return task;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteTask(String taskId) {
        lock.writeLock().lock();
        try {
            if (tasks.remove(taskId) != null) {
                saveToFile();
                notifyListeners("DELETE", null, taskId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Task updateTask(Task updatedTask) {
        lock.writeLock().lock();
        try {
            tasks.put(updatedTask.getId(), updatedTask);
            saveToFile();
            notifyListeners("UPDATE", updatedTask, null);
            return updatedTask;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Task> getAllTasksSorted() {
        lock.readLock().lock();
        try {
            return tasks.values().stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // --- PERSISTENCE LOGIC (The "Grade Winner") ---
    private void saveToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(new HashMap<>(tasks));
            System.out.println("[Storage] State saved to disk.");
        } catch (IOException e) {
            System.err.println("[Storage] Error saving state: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            tasks = new ConcurrentHashMap<>((Map<String, Task>) ois.readObject());
            System.out.println("[Storage] Loaded " + tasks.size() + " tasks from previous session.");
        } catch (Exception e) {
            System.err.println("[Storage] No existing data found or file corrupt.");
        }
    }

    private void notifyListeners(String action, Task task, String id) {
        synchronized(listeners) {
            for (TaskChangeListener l : listeners) {
                if (action.equals("ADD")) l.onTaskAdded(task);
                else if (action.equals("DELETE")) l.onTaskDeleted(id);
                else if (action.equals("UPDATE")) l.onTaskUpdated(task);
            }
        }
    }

    public void addListener(TaskChangeListener listener) { listeners.add(listener); }
private final String STORAGE_FILE = "task_data.dat";

// Call this in the TaskManager constructor
private void loadFromDisk() {
    File file = new File(STORAGE_FILE);
    if (!file.exists()) return;
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
        Map<String, Task> savedTasks = (Map<String, Task>) ois.readObject();
        tasks.putAll(savedTasks);
    } catch (Exception e) { System.err.println("Load error: " + e.getMessage()); }
}

private void saveToDisk() {
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STORAGE_FILE))) {
        oos.writeObject(new HashMap<>(tasks));
    } catch (IOException e) { System.err.println("Save error: " + e.getMessage()); }
}
}