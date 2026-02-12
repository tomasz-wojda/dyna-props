package com.yourcompany.config

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import groovy.transform.CompileStatic


@CompileStatic
class ConfigManager {
    
    private final Map<String, Object> properties = new ConcurrentHashMap<>()
    private final File configFile
    private final AtomicBoolean autoReloadRunning = new AtomicBoolean(false)
    private volatile long lastModified = 0
    private Thread watcherThread
    private long checkIntervalMs = 1000
    

    ConfigManager(String filePath) {
        this.configFile = new File(filePath)
        
        if (!configFile.exists()) {
            throw new FileNotFoundException("Configuration file not found: ${filePath}")
        }
        
        reload()
    }
    

    synchronized void reload() {
        try {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            def flatConfig = config.flatten()
            
            properties.clear()
            properties.putAll(flatConfig as Map<String, Object>)
            
            lastModified = configFile.lastModified()
            
            println "[ConfigManager] Loaded ${properties.size()} properties from ${configFile.name}"
            
        } catch (Exception e) {
            println "[ConfigManager] ERROR: Failed to reload configuration: ${e.message}"
            throw new RuntimeException("Failed to reload configuration", e)
        }
    }
    

    void startAutoReload(long intervalMs = 1000) {
        this.checkIntervalMs = intervalMs
        
        if (autoReloadRunning.compareAndSet(false, true)) {
            watcherThread = new Thread({
                println "[ConfigManager] Auto-reload started (interval: ${checkIntervalMs}ms)"
                
                while (autoReloadRunning.get()) {
                    try {
                        long currentModified = configFile.lastModified()
                        
                        if (currentModified > lastModified) {
                            println "[ConfigManager] Configuration change detected, reloading..."
                            reload()
                        }
                        
                        Thread.sleep(checkIntervalMs)
                        
                    } catch (InterruptedException e) {
                        break
                    } catch (Exception e) {
                        println "[ConfigManager] Error in auto-reload: ${e.message}"
                    }
                }
                
                println "[ConfigManager] Auto-reload stopped"
                
            } as Runnable, "ConfigWatcher-${configFile.name}")
            
            watcherThread.daemon = true
            watcherThread.start()
        } else {
            println "[ConfigManager] Auto-reload is already running"
        }
    }
    

    void stopAutoReload() {
        if (autoReloadRunning.compareAndSet(true, false)) {
            watcherThread?.interrupt()
            watcherThread?.join(2000)
        }
    }
    

    Object get(String key, Object defaultValue = null) {
        properties.getOrDefault(key, defaultValue)
    }
    

    String getString(String key, String defaultValue = null) {
        def value = get(key, defaultValue)
        value?.toString()
    }
    

    Integer getInt(String key, Integer defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Number ? ((Number) value).intValue() : defaultValue
    }
    

    Long getLong(String key, Long defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Number ? ((Number) value).longValue() : defaultValue
    }
    

    Boolean getBoolean(String key, Boolean defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Boolean ? (Boolean) value : defaultValue
    }
    

    Double getDouble(String key, Double defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Number ? ((Number) value).doubleValue() : defaultValue
    }
    

    boolean hasProperty(String key) {
        properties.containsKey(key)
    }
    

    Map<String, Object> getAllProperties() {
        new HashMap<>(properties)
    }
    

    int size() {
        properties.size()
    }
    

    def propertyMissing(String name) {
        get(name)
    }
    
    @Override
    String toString() {
        "ConfigManager[file=${configFile.name}, properties=${properties.size()}, autoReload=${autoReloadRunning.get()}]"
    }
}
