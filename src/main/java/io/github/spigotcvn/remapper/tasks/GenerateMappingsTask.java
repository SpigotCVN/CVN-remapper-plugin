package io.github.spigotcvn.remapper.tasks;

import io.github.spigotcvn.remapper.CVNRemapper;
import io.github.spigotcvn.remapper.util.MapUtil;
import io.github.spigotcvn.remapper.util.MinecraftVersion;
import io.github.spigotcvn.merger.MappingMerger;
import io.github.spigotcvn.merger.mappings.files.CSRGMappingFile;
import io.github.spigotcvn.merger.mappings.files.TinyMappingFile;
import io.github.spigotcvn.smdownloader.SpigotMappingsDownloader;
import io.github.spigotcvn.smdownloader.mappings.MappingFile;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public class GenerateMappingsTask implements ITask {
    private CVNRemapper plugin;

    @Override
    public Task init(CVNRemapper plugin, Project project) {
        this.plugin = plugin;

        return project.getTasks().create("generateMappings", task -> {
            task.doLast(task1 -> {
                CVNRemapper.detectMinecraftVersion(project);

                try {
                    generateMappings();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void generateMappings() throws Exception {
        URL url = new URL(String.format(CVNRemapper.MAPPINGS_URL, CVNRemapper.MINECRAFT_VERSION));
        File originalMappingFile = new File(
                plugin.getMappingsDir(),
                "mappings-" + CVNRemapper.MINECRAFT_VERSION + "-intermediary-original.tiny"
        );
        File finalMappingFile = new File(
                plugin.getMappingsDir(),
                "mappings-" + CVNRemapper.MINECRAFT_VERSION + "-intermediary.tiny"
        );
        File spigotMappings = new File(
                plugin.getMappingsDir(),
                "spigot-" + CVNRemapper.MINECRAFT_VERSION
        );
        File spigotCombinedMappings = new File(
                spigotMappings,
                "mappings/bukkit-" + CVNRemapper.MINECRAFT_VERSION + "-combined.csrg"
        );

        if(!originalMappingFile.exists()) {
            System.out.println("Downloading mappings " + url + "...");
            originalMappingFile.getParentFile().mkdirs();
            originalMappingFile.createNewFile();

            try (InputStream inputStream = url.openStream();
                 OutputStream outputStream = new FileOutputStream(originalMappingFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("Downloaded mappings to: " + originalMappingFile.getAbsolutePath());
        }

        System.out.println("Downloading spigot mappings...");
        String craftBukkitRevHash;
        List<MappingFile> spigotMappingFiles;
        try(SpigotMappingsDownloader smd =
                    new SpigotMappingsDownloader(spigotMappings, CVNRemapper.MINECRAFT_VERSION)) {
            spigotMappingFiles = smd.downloadMappings(false);
            craftBukkitRevHash = smd.getVersionData().getRefs().getCraftBukkit();
        }

        System.out.println("Downloaded spigot mappings: " + spigotMappingFiles.stream()
                .map(m -> m.getFile().getAbsolutePath() + " ").collect(Collectors.joining()));

        if(!finalMappingFile.exists()) {
            System.out.println("Generating final mappings...");
            TinyMappingFile original = new TinyMappingFile();
            CSRGMappingFile spigot = new CSRGMappingFile();
            original.loadFromFile(originalMappingFile);

            if(new MinecraftVersion(CVNRemapper.MINECRAFT_VERSION).compareTo(RemapJarTask.FIRST_MOJMAP_VERSION) < 0) {
                System.out.println("Pre mojmaps version detected. Combining spigot mappings...");
                if(!spigotCombinedMappings.exists()) {
                    MapUtil mapUtil = new MapUtil();
                    mapUtil.loadBuk(spigotMappingFiles.stream()
                            .filter(m -> m.getType() == MappingFile.MappingType.CLASS)
                            .findFirst()
                            .get().getFile());
                    mapUtil.makeCombinedMaps(spigotCombinedMappings, spigotMappingFiles.stream()
                            .filter(m -> m.getType() == MappingFile.MappingType.MEMBERS)
                            .findFirst()
                            .get().getFile());
                }

                spigot.loadFromFile(spigotCombinedMappings);
            } else {
                spigot.loadFromFile(spigotMappingFiles.stream()
                        .filter(m -> m.getType() == MappingFile.MappingType.CLASS)
                        .findFirst()
                        .get().getFile());
            }

            MappingMerger.mergeTinyWithCSRG(original, spigot, "spigot");
            original.saveToFile(finalMappingFile);
            original.loadFromFile(finalMappingFile);

            if(new MinecraftVersion(CVNRemapper.MINECRAFT_VERSION).compareTo(RemapJarTask.FIRST_MOJMAP_VERSION) < 0) {
                CSRGMappingFile packageMappings = new CSRGMappingFile();
                System.out.println("Starting to pull the craftbukkit repo...");
                File cbPom = getCBPom(craftBukkitRevHash);
                System.out.println("Finished pulling the craftbukkit repo!");
                System.out.println("Finding cb notation...");
                
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(cbPom);
                doc.getDocumentElement().normalize();

                // located in: project -> properties -> minecraft_version
                String cbNotation;

                Element properties = (Element) doc.getElementsByTagName("properties").item(0);
                cbNotation = properties.getElementsByTagName("minecraft_version").item(0).getTextContent();
                
                System.out.println("CB Notation found successfully!");

                String nmsPackage = "net/minecraft/server/v" + cbNotation + "/";

                packageMappings.loadFromStream(new ByteArrayInputStream(
                        ("./ " + nmsPackage + "\n" +
                        "net/minecraft/server/ " + nmsPackage).getBytes()
                ));

                MappingMerger.applyPackageMapping(original, packageMappings, true);
                original.saveToFile(finalMappingFile);
                original.loadFromFile(finalMappingFile);
                
                System.out.println("Package mappings applied!");
            }

            MappingMerger.replaceOriginalNamespace(original, "spigot");
            original.saveToFile(finalMappingFile);
            System.out.println("Finished generating final mappings, saved to: " + finalMappingFile.getAbsolutePath());
        }
    }

    private @NotNull File getCBPom(String craftBukkitRevHash) {
        File craftBukkitDir =
                new File(plugin.getMappingsDir(), "craftbukkit-" + CVNRemapper.MINECRAFT_VERSION);

        try(SpigotMappingsDownloader smd =
                    new SpigotMappingsDownloader(
                            craftBukkitDir,
                            CVNRemapper.MINECRAFT_VERSION,
                            "https://hub.spigotmc.org/stash/scm/spigot/craftbukkit.git"
                    )) {
            if(!craftBukkitDir.exists())
                smd.pullBuildDataGit(craftBukkitRevHash);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new File(craftBukkitDir, "pom.xml");
    }
}
