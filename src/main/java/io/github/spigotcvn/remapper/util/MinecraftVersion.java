package io.github.spigotcvn.remapper.util;

import java.util.Objects;

public class MinecraftVersion implements Comparable<MinecraftVersion> {
    private int major;
    private int minor;
    private int patch;

    public MinecraftVersion(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers cannot be negative.");
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public MinecraftVersion(int major, int minor) {
        if(major < 0 || minor < 0) {
            throw new IllegalArgumentException("Version numbers cannot be negative.");
        }

        this.major = major;
        this.minor = minor;
        this.patch = -1;
    }

    public MinecraftVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid version format.");
        }
        this.major = Integer.parseInt(parts[0]);
        this.minor = Integer.parseInt(parts[1]);
        this.patch = (parts.length > 2) ? Integer.parseInt(parts[2]) : -1;

        if (this.major < 0 || this.minor < 0 || this.patch < -1) {
            throw new IllegalArgumentException("Version numbers cannot be negative.");
        }
    }

    // Getters
    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        if(patch == -1) {
            throw new NullPointerException("Patch is null");
        }
        return patch;
    }

    @Override
    public String toString() {
        if (patch != -1) {
            return major + "." + minor + "." + patch;
        } else {
            return major + "." + minor;
        }
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MinecraftVersion that = (MinecraftVersion) obj;
        return major == that.major &&
                minor == that.minor &&
                patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }
}