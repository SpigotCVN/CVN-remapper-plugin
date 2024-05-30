package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import org.gradle.api.Project;
import org.gradle.api.Task;

public interface ITask {
    Task init(CVNRemapper plugin, Project project);
}