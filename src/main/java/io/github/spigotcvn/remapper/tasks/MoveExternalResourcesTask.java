package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import io.github.spigotcvn.remapper.util.JarUtil;
import io.github.spigotcvn.remapper.util.Util;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class MoveExternalResourcesTask implements ITask {
    private CVNRemapper plugin;

    @Override
    public Task init(CVNRemapper plugin, Project project) {
        this.plugin = plugin;

        return project.getTasks().create("moveExternalResources", task -> {
            plugin.getTasks().get("remapJar").finalizedBy(task);

            task.doLast(t -> {
                // it should perform that only on the result jar which is already remapped
                // that jar is located in the tmp directory
                File jarFile = plugin.getFinalTmpJar();
                File resultJarFile = new File(plugin.getTmpDir(), jarFile.getName().replace("-intermediary.jar", ".jar"));

                plugin.addFileToMove(resultJarFile, plugin.getLibsDir());

                try {
                    moveExternalResources(jarFile, resultJarFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    void moveExternalResources(File jarFile, File resultJarFile) throws IOException {
        File unarchiveDir = new File(plugin.getTmpDir(), "unarchive");
        unarchiveDir.mkdir();

        JarUtil.copyJarContents(jarFile, unarchiveDir);

        File pluginYml = new File(unarchiveDir, "plugin.yml");
        if(!pluginYml.exists()) throw new RuntimeException("Could not find the plugin.yml file in the jar");

        // get contents of pluginYml
        Map<String, String> ymlContent = new HashMap<>();
        List<String> content = Files.readAllLines(pluginYml.toPath());
        for(String str : content) {
            String[] split = str.split(":");
            if(split.length < 2) continue;
            ymlContent.put(split[0], split[1]);
        }
        String name = ymlContent.get("name");
        if(name == null) throw new RuntimeException("name not present in plugin.yml");

        Files.copy(pluginYml.toPath(), new File(unarchiveDir, "cvn-plugin.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        try(InputStream fos = this.getClass().getResourceAsStream("/dummy-plugin.yml");
            OutputStream fis = new FileOutputStream(pluginYml)) {
            StringBuilder builder = new StringBuilder();
            Scanner scanner = new Scanner(fos);
            while(scanner.hasNextLine()) {
                builder.append(
                        scanner.nextLine().replaceAll("\\$\\{uuid}", Util.getUUIDfromString(name).toString())
                ).append("\n");
            }
            scanner.close();
            fis.write(builder.toString().getBytes());
        }

        // move the compiled java file to the jar
        File javaFile = new File(plugin.getTmpDir(), "DummyJavaPlugin.class");
        Files.copy(javaFile.toPath(), new File(unarchiveDir, "DummyJavaPlugin.class").toPath(), StandardCopyOption.REPLACE_EXISTING);

        // repackage the jar
        JarUtil.repackJar(resultJarFile, unarchiveDir);
    }
}
