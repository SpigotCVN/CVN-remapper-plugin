package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import io.github.spigotcvn.remapper.util.Util;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.*;

public class CompileDummyJavaTask implements ITask {
    @Override
    public Task init(CVNRemapper plugin, Project project) {
        return project.getTasks().create("compileDummyJava", task -> {
            project.getTasks().getByName("classes").finalizedBy(task);

            task.doLast(t -> {
                File tmpJavaFile = new File(plugin.getTmpDir(), "DummyJavaPlugin.java");
                try(InputStream is = this.getClass().getResourceAsStream("/DummyJavaPlugin.java");
                    OutputStream os = new FileOutputStream(tmpJavaFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                CVNRemapper.detectMinecraftVersion(project);
                File spigotJar = new File(
                        System.getProperty("user.home") +
                                "/.m2/repository/" +
                                CVNRemapper.SPIGOT_GROUP.replace(".", "/") +
                                "/" + CVNRemapper.SPIGOT_ARTIFACT + "-api/" +
                                CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
                                CVNRemapper.SPIGOT_ARTIFACT + "-api-" +
                                CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT-shaded.jar"
                );

                Util.compileJavaFile(
                        tmpJavaFile.getAbsolutePath(),
                        plugin.getTmpDir().getAbsolutePath(),
                        spigotJar.getAbsolutePath()
                );
            });
        });
    }
}
