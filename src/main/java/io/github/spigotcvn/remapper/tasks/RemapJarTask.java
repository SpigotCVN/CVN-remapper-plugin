package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import io.github.spigotcvn.remapper.util.RemapUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class RemapJarTask implements ITask {
    private CVNRemapper plugin;

    @Override
    public Task init(CVNRemapper plugin, Project project) {
        this.plugin = plugin;

        return project.getTasks().create("remapJar", task -> {
            // it always runs after the Jar task
            project.getTasks().getByName("jar").finalizedBy(task);
            // it will run the downloadMappings task before running this task
            task.dependsOn(plugin.getTasks().get("downloadMappings"));

            task.doLast(t -> {
                CVNRemapper.detectMinecraftVersion(project);

                try {
                    fullyRemap((Jar) project.getTasks().getByName("jar"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    public void fullyRemap(Jar jarTask) throws IOException {
        // find the mappings file in the local maven repository
        File mojMaps = new File(
                System.getProperty("user.home") +
                        "/.m2/repository/" +
                        CVNRemapper.SPIGOT_GROUP.replace(".", "/") +
                        "/minecraft-server/" +
                        CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
                        "minecraft-server-" + CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT-maps-mojang.txt"
        );
        if (!mojMaps.exists()) {
            throw new RuntimeException("Could not find the mojang mappings file in the local maven repository\n" +
                    "Please make sure you have ran the BuildTools with the --remapped option");
        }

        File spigotMappings = new File(
                System.getProperty("user.home") +
                        "/.m2/repository/" +
                        CVNRemapper.SPIGOT_GROUP.replace(".", "/") +
                        "/minecraft-server/" +
                        CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
                        "minecraft-server-" + CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT-maps-spigot.csrg"
        );
        if (!spigotMappings.exists()) {
            throw new RuntimeException("Could not find the spigot mappings file in the local maven repository\n" +
                    "Please make sure you have ran the BuildTools with the --remapped option");
        }

//        File spigotMemberMappings = new File(
//                System.getProperty("user.home") +
//                        "/.m2/repository/" +
//                        CVNRemapper.SPIGOT_GROUP.replace(".", "/") +
//                        "/minecraft-server/" +
//                        CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
//                        "minecraft-server-" + CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT-maps-spigot-members.csrg"
//        );
//        if (!spigotMemberMappings.exists()) {
//            throw new RuntimeException("Could not find the spigot member mappings file in the local maven repository\n" +
//                    "Please make sure you have ran the BuildTools with the --remapped option");
//        }

        File classpathJar = new File(
                System.getProperty("user.home") +
                        "/.m2/repository/" +
                        CVNRemapper.SPIGOT_GROUP.replace(".", "/") +
                        "/" + CVNRemapper.SPIGOT_ARTIFACT + "/" +
                        CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
                        CVNRemapper.SPIGOT_ARTIFACT + "-" + CVNRemapper.MINECRAFT_VERSION + "-R0.1-SNAPSHOT.jar"
        );
        if (!classpathJar.exists()) {
            throw new RuntimeException("Could not find the classpath jar in the local maven repository\n" +
                    "Please make sure you have ran the BuildTools with the --remapped option");
        }

        // copy the original jar as -original.jar
        File original = jarTask.getArchiveFile().get().getAsFile();

        File originalTmp = new File(
                plugin.getTmpDir(),
                jarTask.getArchiveFile().get().getAsFile().getName()
                        .replace(".jar", "-original.jar")
        );
        Files.copy(original.toPath(),
                originalTmp.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        File resultTmp = new File(plugin.getTmpDir(), jarTask.getArchiveFile().get().getAsFile().getName().replace(".jar", "-intermediary.jar"));
        File officialTmp = new File(plugin.getTmpDir(), jarTask.getArchiveFile().get().getAsFile().getName().replace(".jar", "-official.jar"));
        File spigotTmp = new File(plugin.getTmpDir(), jarTask.getArchiveFile().get().getAsFile().getName().replace(".jar", "-spigot.jar"));

        plugin.setFinalTmpJar(resultTmp);

        plugin.addFileToMove(originalTmp, plugin.getLibsDir());
        plugin.addFileToMove(spigotTmp, plugin.getLibsDir());

        RemapUtil.remapJarToObfuscated(mojMaps, jarTask.getArchiveFile().get().getAsFile(), officialTmp);

        RemapUtil.remapJarToSpigot(
                plugin.getMappingsDir(),
                spigotMappings,
//                spigotMemberMappings,
                mojMaps,
                new File(plugin.getMappingsDir(), "mappings-" + CVNRemapper.MINECRAFT_VERSION + "-spigot-fields.csrg"),
                officialTmp,
                spigotTmp
        );

        RemapUtil.remapJarToIntermediary(classpathJar.toPath(), officialTmp, resultTmp);

        System.out.println("Finished remapping jars. Jar:");
        System.out.println("Intermediary mapped (to be used with CVN): " + resultTmp.getName());
        System.out.println("Spigot mapped (to be used like normal): " + spigotTmp.getName());
        System.out.println("Original (Unmapped): " + originalTmp.getName());

        Files.deleteIfExists(officialTmp.toPath());
    }
}
