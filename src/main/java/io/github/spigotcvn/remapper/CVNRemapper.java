package io.github.spigotcvn.remapper;

import io.github.spigotcvn.remapper.tasks.*;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;

import java.io.*;
import java.util.*;

public class CVNRemapper implements Plugin<Project> {
    public static final String MAPPINGS_URL = "https://raw.githubusercontent.com/Cross-Version-NMS/CVN-mappings/main/mappings/%s.tiny";
    public static       String MINECRAFT_VERSION;
    public static final String NAMESPACE_FROM = "official";
    public static final String NAMESPACE_TO = "intermediary";

    public static final String SPIGOT_GROUP = "org.spigotmc";
    public static final String SPIGOT_ARTIFACT = "spigot";

    Map<String, Task> tasks = new HashMap<>();
    // file one is from where, file two is where to
    Map<File, File> filesToMove = new HashMap<>();

    private File tmpDir;
    private File libsDir;
    private File mappingsDir;
    private File finalTmpJar;

    @Override
    public void apply(Project project) {
        String buildDirPath = project.getLayout().getBuildDirectory().get().getAsFile().getAbsolutePath();

        tmpDir = new File(buildDirPath, "tmp");
        tmpDir.getParentFile().mkdirs();
        tmpDir.mkdir();

        libsDir = new File(buildDirPath, "libs");
        libsDir.getParentFile().mkdirs();
        libsDir.mkdir();

        mappingsDir = new File(buildDirPath, "mappings");
        mappingsDir.getParentFile().mkdirs();
        mappingsDir.mkdir();

        tasks.put("downloadMappings", new DownloadMappingsTask().init(this, project));
        tasks.put("compileDummyJava", new CompileDummyJavaTask().init(this, project));
        tasks.put("remapJar", new RemapJarTask().init(this, project));
        tasks.put("moveExternalResources", new MoveExternalResourcesTask().init(this, project));
        tasks.put("moveJars", new MoveJarsTask().init(this, project));
    }

    public static void detectMinecraftVersion(Project project) {
        Configuration spigotConfiguration = project.getConfigurations().getByName("compileOnly");

        spigotConfiguration.getDependencies().forEach(dependency -> {
            if (dependency.getGroup() == null || dependency.getVersion() == null) return;

            if (dependency.getGroup().equals(SPIGOT_GROUP) && dependency.getName().equals(SPIGOT_ARTIFACT)) {
                MINECRAFT_VERSION = dependency.getVersion().replace("-R0.1-SNAPSHOT", "");
            }
        });
        if (MINECRAFT_VERSION == null) {
            throw new RuntimeException("Could not find the Spigot dependency in the compile configuration\n" +
                    "Please make sure you have the Spigot dependency in your build.gradle like this:\n" +
                    "compileOnly \"org.spigotmc:spigot:1.19.4-R0.1-SMAPSHOT:remapped-mojang\"");
        }
    }

    public File getTmpDir() {
        return tmpDir;
    }

    public File getLibsDir() {
        return libsDir;
    }

    public File getMappingsDir() {
        return mappingsDir;
    }

    public Map<String, Task> getTasks() {
        return new HashMap<>(tasks);
    }

    public Map<File, File> getFilesToMove() {
        return new HashMap<>(filesToMove);
    }

    public void addFileToMove(File fileToMove, File whereToMove) {
        filesToMove.put(fileToMove, whereToMove);
    }

    public File getFinalTmpJar() {
        return finalTmpJar;
    }

    public void setFinalTmpJar(File finalTmpJar) {
        this.finalTmpJar = finalTmpJar;
    }
}
