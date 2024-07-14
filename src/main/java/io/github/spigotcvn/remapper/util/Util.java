package io.github.spigotcvn.remapper.util;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.function.Consumer;

public class Util {
    public static void compileJavaFile(String javaFilePath, String outputDir, int javaVersion, String... classpath) {
        String classpathString;
        // on windows the classpath separator is ; and on unix it's :
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            classpathString = String.join(";", classpath);
        } else {
            classpathString = String.join(":", classpath);
        }
        System.out.println("Using classpath: " + classpathString);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int compilationResult = compiler.run(null, null, null,
                "-target", String.valueOf(javaVersion), "-source", String.valueOf(javaVersion), // java version
                "-d", outputDir, "-cp", classpathString,  // classpath and output
                javaFilePath // java file to compile
        );
        if (compilationResult != 0) {
            throw new RuntimeException("Compilation failed for " + javaFilePath);
        }
    }

    public static void iterateOverFiles(Consumer<File> consumer, File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                iterateOverFiles(consumer, file);
            } else {
                consumer.accept(file);
            }
        }
    }

    public static UUID getUUIDfromString(String input) {
        // Generate MD5 hash
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());

            // Use the first 16 bytes of the hash to create a UUID
            ByteBuffer bb = ByteBuffer.wrap(hash);
            long mostSigBits = bb.getLong();
            long leastSigBits = bb.getLong();

            return new UUID(mostSigBits, leastSigBits);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
