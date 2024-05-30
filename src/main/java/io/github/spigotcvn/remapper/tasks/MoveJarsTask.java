package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MoveJarsTask implements ITask {
    private CVNRemapper plugin;

    @Override
    public Task init(CVNRemapper plugin, Project project) {
        this.plugin = plugin;

        return project.getTasks().create("moveJars", Jar.class, task -> {
            project.getTasks().getByName("moveExternalResources").finalizedBy(task);

            task.doLast(jarTask -> {
                moveJars();
            });
        });
    }

    private void moveJars() {
        plugin.getFilesToMove().forEach((from, to) -> {
            try {
                if(!to.isDirectory()) return;
                File newDest = new File(to, from.getName());
                Files.copy(from.toPath(), newDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(from.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
