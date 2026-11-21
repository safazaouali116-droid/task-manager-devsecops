package tn.rsi.taskmanager.service;

import tn.rsi.taskmanager.model.Task;
import tn.rsi.taskmanager.model.TaskStatus;
import tn.rsi.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    @Test
    void createTaskSavesTask() {
        Task task = new Task();
        task.setTitle("Réviser DevOps");
        when(repository.save(task)).thenReturn(task);

        Task result = service.create(task);

        assertEquals("Réviser DevOps", result.getTitle());
        verify(repository).save(task);
    }

    @Test
    void updateTaskChangesFields() {
        Task existing = new Task();
        existing.setTitle("Ancien titre");
        Task update = new Task();
        update.setTitle("Nouveau titre");
        update.setDescription("Préparer le pipeline");
        update.setStatus(TaskStatus.IN_PROGRESS);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Task result = service.update(1L, update);

        assertEquals("Nouveau titre", result.getTitle());
        assertEquals(TaskStatus.IN_PROGRESS, result.getStatus());
        verify(repository).save(existing);
    }

    @Test
    void deleteTaskDeletesExistingTask() {
        Task existing = new Task();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(repository).delete(existing);
    }
}
