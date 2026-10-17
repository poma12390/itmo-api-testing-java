package ru.itmo.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private final Map<Long, Task> tasks = new LinkedHashMap<>();
    private long nextId = 1;

    public synchronized Task create(TaskRequest request) {
        Task task = new Task(nextId++, request.title, request.priority, request.completed);
        tasks.put(task.id(), task);
        return task;
    }

    public synchronized List<Task> list(int limit, int offset) {
        return tasks.values().stream().skip(offset).limit(limit).toList();
    }

    public synchronized Task find(long id) {
        Task task = tasks.get(id);
        if (task == null) {
            throw new TaskNotFoundException();
        }
        return task;
    }

    public synchronized Task replace(long id, TaskRequest request) {
        find(id);
        Task task = new Task(id, request.title, request.priority, request.completed);
        tasks.put(id, task);
        return task;
    }

    public synchronized void delete(long id) {
        find(id);
        tasks.remove(id);
    }

    synchronized void reset() {
        tasks.clear();
        nextId = 1;
    }
}
