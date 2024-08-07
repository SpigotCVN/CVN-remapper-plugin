package io.github.spigotcvn.remapper.util;

import io.github.spigotcvn.remapper.CVNRemapper;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class RemapUtil {
    /**
     * Remaps a jar file from the official mappings (or ones that are given) to obfuscated/official mappings.
     * @param mappings       The mappings file to use, usually a ProGuard mappings file
     * @param jarFile        The jar file to remap
     * @param resultJarFile  The file to save the remapped jar to
     * @throws IOException   If an error occurs while reading the jar file
     */
    public static void remapJarToObfuscated(File mappings, File jarFile, File resultJarFile) throws IOException {
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
     * Remaps a jar file from the official mappings (or ones that are given) to Spigot mappings.
     * @param mappings     The spigot mappigns file to use, usually csrg
     * @param jarFile     The jar file to remap
     * @param resultJarFile The file to save the remapped jar to
     * @throws IOException If an error occurs while reading the jar file
     */
    public static void remapJarToSpigotClass(File mappings, File jarFile, File resultJarFile) throws IOException {
        System.out.println("Remapping jar to spigot mappings...");

        JarMapping jarMapping = new JarMapping();
        BufferedReader reader = new BufferedReader(new FileReader(mappings));
        jarMapping.loadMappings(reader, null, null, false);
        reader.close();

        System.out.println("Loaded mappings from: " + mappings.getAbsolutePath());

        JarRemapper remapper = new JarRemapper(jarMapping);
        remapper.remapJar(net.md_5.specialsource.Jar.init(jarFile), resultJarFile);

        System.out.println("Remapped jar to spigot mappings to: " + resultJarFile.getAbsolutePath());
    }

    public static void remapJarToSpigotFull(File classMappings, File memberMappings, File jarFile,
                                            File resultFile) throws IOException {
        MapUtil mapUtil = new MapUtil();
        mapUtil.loadBuk(classMappings);

        File combinedMappings = new File("build" + File.separator + "mappings",
                "mappings-" + CVNRemapper.MINECRAFT_VERSION + "-spigot-combined.tiny");
        mapUtil.makeCombinedMaps(combinedMappings, memberMappings);

        remapJarToSpigotFull(combinedMappings, jarFile, resultFile);
    }

    public static void remapJarToSpigotFull(File combinedMappings, File jarFile,
                                            File resultFile) throws IOException {
        System.out.println("Remapping jar to spigot full mappings...");

        JarMapping jarMapping = new JarMapping();
        BufferedReader reader = new BufferedReader(new FileReader(combinedMappings));
        jarMapping.loadMappings(reader, null, null, false);
        reader.close();

        System.out.println("Loaded mappings from: " + combinedMappings.getAbsolutePath());

        JarRemapper remapper = new JarRemapper(jarMapping);
        remapper.remapJar(net.md_5.specialsource.Jar.init(jarFile), resultFile);

        System.out.println("Remapped jar to spigot full mappings to: " + resultFile.getAbsolutePath());
    }

    /**
     * Takes in a spigot mapped jar and remaps it to the intermediary mappings.
     * @param jarFile       The jar file to remap
     * @param resultJarFile The file to save the remapped jar to
     */
    public static void remapJarToIntermediary(Path classpath, File jarFile, File resultJarFile) {
        System.out.println("Remapping jar to intermediary mappings...");

        File mappingFile = new File(
                "build" + File.separator + "mappings",
                "mappings-" + CVNRemapper.MINECRAFT_VERSION + "-intermediary.tiny"
        );
        if(!mappingFile.exists()) {
            throw new RuntimeException("Could not find the mappings file\n" +
                    "Please make sure you have ran the downloadMappings task");
        }
        System.out.println("Loaded mappings from: " + mappingFile.getAbsolutePath());

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappingFile.toPath(), "spigot", "intermediary"))
                .ignoreConflicts(true)
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
