package de.jamsintown.watchservice;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.WatchService;

@ApplicationScoped
public class WatchServiceWatcher {
    WatchService watchService;

    public WatchServiceWatcher() {
        try {
            watchService = java.nio.file.FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create WatchService", e);
        }
    }
    public WatchService getWatchService() {
        return watchService;
    }
    public void close() {
        try {
            watchService.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close WatchService", e);
        }
    }
    public void watchDirectory(String path) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(path);
            dir.register(watchService, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (Exception e) {
            throw new RuntimeException("Failed to watch directory: " + path, e);
        }
    }

}
