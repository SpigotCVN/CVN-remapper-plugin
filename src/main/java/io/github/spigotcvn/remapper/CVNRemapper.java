package io.github.spigotcvn.remapper;

import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public class CVNRemapper implements Plugin<Project> {
    public static final String MAPPINGS_URL = "https://raw.githubusercontent.com/Cross-Version-NMS/CVN-mappings/main/mappings/%s.tiny";
    public static       String MINECRAFT_VERSION;
    public static final String NAMESPACE_FROM = "official";
    public static final String NAMESPACE_TO = "intermediary";

    public static final String SPIGOT_GROUP = "org.spigotmc";
    public static final String SPIGOT_ARTIFACT = "spigot";

    private File tmpDir;
    private File libsDir;
    private File[] tmpJars;

    @Override
    public void apply(Project project) {
        tmpDir = new File(project.getLayout().getBuildDirectory().get().getAsFile().getAbsolutePath(), "tmp");
        tmpDir.getParentFile().mkdirs();
        tmpDir.mkdir();

        libsDir = new File(project.getLayout().getBuildDirectory().get().getAsFile().getAbsolutePath(), "libs");
        libsDir.getParentFile().mkdirs();
        libsDir.mkdir();

        TaskProvider<Task> downloadMappingsTask = project.getTasks().register("downloadMappings", task -> {
            task.doLast(task1 -> {
                detectMinecraftVersion(project);

                try {
                    downloadMappings();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        project.getTasks().create("inspectJar", task -> {
            task.doLast(t -> {
                File jarFile = project.getTasks().getByName("jar").getOutputs().getFiles().getSingleFile();
                System.out.println("Inspecting JAR file: " + jarFile.getAbsolutePath());
                inspectJar(jarFile);
            });
        });


        project.getTasks().create("remapJar", task -> {
            // it always runs after the Jar task
            project.getTasks().getByName("jar").finalizedBy(task);
            // it will run the downloadMappings task before running this task
            task.dependsOn(downloadMappingsTask.get());

            task.doLast(t -> {
                detectMinecraftVersion(project);

                try {
                    fullyRemap((Jar) project.getTasks().getByName("jar"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        project.getTasks().create("moveJars", Jar.class, task -> {
            project.getTasks().getByName("remapJar").finalizedBy(task);

            task.doLast(jarTask -> {
                moveJars();
            });
        });
    }

    private void detectMinecraftVersion(Project project) {
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

    public void fullyRemap(Jar jarTask) throws IOException {
        // find the mappings file in the local maven repository
        File mappings = new File(
                System.getProperty("user.home") +
                        "/.m2/repository/" +
                        SPIGOT_GROUP.replace(".", "/") +
                        "/minecraft-server/" +
                        MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
                        "minecraft-server-" + MINECRAFT_VERSION + "-R0.1-SNAPSHOT-maps-mojang.txt"
        );
        if (!mappings.exists()) {
            throw new RuntimeException("Could not find the mappings file in the local maven repository\n" +
                    "Please make sure you have ran the BuildTools with the --remapped option");
        }

        File classpathJar = new File(
                System.getProperty("user.home") +
                        "/.m2/repository/" +
                        SPIGOT_GROUP.replace(".", "/") +
                        "/" + SPIGOT_ARTIFACT + "/" +
                        MINECRAFT_VERSION + "-R0.1-SNAPSHOT/" +
                        SPIGOT_ARTIFACT + "-" + MINECRAFT_VERSION + "-R0.1-SNAPSHOT.jar"
        );
        if (!classpathJar.exists()) {
            throw new RuntimeException("Could not find the classpath jar in the local maven repository\n" +
                    "Please make sure you have ran the BuildTools with the --remapped option");
        }

        // copy the original jar as -original.jar
        File original = jarTask.getArchiveFile().get().getAsFile();

        File originalTmp = new File(
                tmpDir,
                jarTask.getArchiveFile().get().getAsFile().getName()
                        .replace(".jar", "-original.jar")
        );
        Files.copy(original.toPath(),
                originalTmp.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        File resultTmp = new File(tmpDir, jarTask.getArchiveFile().get().getAsFile().getName());
        File officialTmp = new File(tmpDir, jarTask.getArchiveFile().get().getAsFile().getName().replace(".jar", "-official.jar"));

        tmpJars = new File[] {originalTmp, officialTmp, resultTmp};

        remapJarToObfuscated(mappings, jarTask.getArchiveFile().get().getAsFile(), officialTmp);
        remapJarToIntermediary(classpathJar.toPath(), officialTmp, resultTmp);

        System.out.println("Finished remapping jars. Jar:");
        System.out.println("Intermediary mapped (to be used with CVN): " + resultTmp.getName());
        System.out.println("Official/Obfuscated (to be used like normal): " + officialTmp.getName());
        System.out.println("Original (Unmapped): " + originalTmp.getName());
    }

    private void moveJars() {
        for (File jar : tmpJars) {
            File dest = new File(libsDir, jar.getName());
            try {
                Files.copy(jar.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Moved jar to: " + dest.getAbsolutePath());
                Files.deleteIfExists(jar.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void inspectJar(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            jar.stream().forEach(entry -> System.out.println(entry.getName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void downloadMappings() throws IOException {
        URL url = new URL(String.format(MAPPINGS_URL, MINECRAFT_VERSION));
        File mappingFile = new File(
                "build" + File.separator + "mappings",
                "mappings-" + MINECRAFT_VERSION + "-intermediary.tiny"
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

    /**
     * Remaps a jar file from the official mappings (or ones that are given) to obfuscated/official mappings.
     * @param mappings       The mappings file to use, usually a ProGuard mappings file
     * @param jarFile        The jar file to remap
     * @param resultJarFile  The file to save the remapped jar to
     * @throws IOException   If an error occurs while reading the jar file
     */
    void remapJarToObfuscated(File mappings, File jarFile, File resultJarFile) throws IOException {
        System.out.println("Remapping jar to obfuscated mappings...");

        // here we'll utilize SpecialSource
        JarMapping jarMapping = new JarMapping();
        BufferedReader reader = new BufferedReader(new FileReader(mappings));
        jarMapping.loadMappings(reader, null, null, true);
        reader.close();

        System.out.println("Loaded mappings from: " + mappings.getAbsolutePath());

        JarRemapper remapper = new JarRemapper(jarMapping);
        remapper.remapJar(net.md_5.specialsource.Jar.init(jarFile), resultJarFile);

        System.out.println("Remapped jar to obfuscated mappings to: " + resultJarFile.getAbsolutePath());
    }

    /**
     * Takes in an officially mapped jar (an obfuscated one) and remaps it to the intermediary mappings.
     * @param jarFile       The jar file to remap
     * @param resultJarFile The file to save the remapped jar to
     * @throws IOException  If an error occurs while reading the jar file
     */
    void remapJarToIntermediary(Path classpath, File jarFile, File resultJarFile) {
        System.out.println("Remapping jar to intermediary mappings...");

        File mappingFile = new File(
                "build" + File.separator + "mappings",
                "mappings-" + MINECRAFT_VERSION + "-intermediary.tiny"
        );
        if(!mappingFile.exists()) {
            throw new RuntimeException("Could not find the mappings file\n" +
                    "Please make sure you have ran the downloadMappings task");
        }
        System.out.println("Loaded mappings from: " + mappingFile.getAbsolutePath());

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappingFile.toPath(), NAMESPACE_FROM, NAMESPACE_TO))
                .build();

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(resultJarFile.toPath()).build()) {
            outputConsumer.addNonClassFiles(jarFile.toPath(), NonClassCopyMode.FIX_META_INF, remapper);

            System.out.println("Reading inputs from " + jarFile.getAbsolutePath() + "...");
            remapper.readInputs(jarFile.toPath());
            System.out.println("Reading classpath from " + classpath.toAbsolutePath() + "...");
            remapper.readClassPath(classpath);

            System.out.println("Remapping jar...");
            remapper.apply(outputConsumer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            remapper.finish();
            System.out.println("Finished remapping jar to intermediary mappings to: " + resultJarFile.getAbsolutePath());
        }
    }
}
