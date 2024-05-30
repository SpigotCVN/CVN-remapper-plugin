package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.*;
import java.net.URL;

public class DownloadMappingsTask implements ITask {
    private CVNRemapper plugin;

    @Override
    public Task init(CVNRemapper plugin, Project project) {
        this.plugin = plugin;

        return project.getTasks().create("downloadMappings", task -> {
            task.doLast(task1 -> {
                CVNRemapper.detectMinecraftVersion(project);

                try {
                    downloadMappings();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void downloadMappings() throws IOException {
        URL url = new URL(String.format(CVNRemapper.MAPPINGS_URL, CVNRemapper.MINECRAFT_VERSION));
        File mappingFile = new File(
                plugin.getMappingsDir(),
                "mappings-" + CVNRemapper.MINECRAFT_VERSION + "-intermediary.tiny"
        );

        if(!mappingFile.exists()) {
            System.out.println("Downloading mappings...");
            mappingFile.getParentFile().mkdirs();
            mappingFile.createNewFile();

            try (InputStream inputStream = url.openStream();
                 OutputStream outputStream = new FileOutputStream(mappingFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("Downloaded mappings to: " + mappingFile.getAbsolutePath());
        }
    }
}
