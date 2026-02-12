#!/usr/bin/env groovy

/**
 * COMPLETE STANDALONE SOLUTION - Copy this into your project!
 */

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ConfigManager {
    private final Map<String, Object> properties = new ConcurrentHashMap<>()
    private final File configFile
    private final AtomicBoolean autoReloadRunning = new AtomicBoolean(false)
    private volatile long lastModified = 0
    private Thread watcherThread
    
    ConfigManager(String filePath) {
        this.configFile = new File(filePath)
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: ${filePath}")
        }
        reload()
    }
    
    synchronized void reload() {
        def config = new ConfigSlurper().parse(configFile.toURI().toURL())
        properties.clear()
        properties.putAll(config.flatten())
        lastModified = configFile.lastModified()
        println "[Config] Loaded ${properties.size()} properties"
    }
    
    void startAutoReload(long intervalMs = 1000) {
        if (autoReloadRunning.compareAndSet(false, true)) {
            watcherThread = new Thread({
                while (autoReloadRunning.get()) {
                    try {
                        if (configFile.lastModified() > lastModified) {
                            println "[Config] Change detected, reloading..."
                            reload()
                        }
                        Thread.sleep(intervalMs)
                    } catch (InterruptedException e) {
                        break
                    }
                }
            } as Runnable)
            watcherThread.daemon = true
            watcherThread.start()
        }
    }
    
    void stopAutoReload() {
        autoReloadRunning.set(false)
        watcherThread?.interrupt()
    }
    
    Object get(String key, Object defaultValue = null) {
        properties.getOrDefault(key, defaultValue)
    }
    
    String getString(String key, String defaultValue = null) {
        get(key, defaultValue)?.toString()
    }
    
    Integer getInt(String key, Integer defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Number ? value.intValue() : defaultValue
    }
    
    Boolean getBoolean(String key, Boolean defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Boolean ? value : defaultValue
    }
    
    def propertyMissing(String name) { get(name) }
}

// ============================================================================
// DEMO
// ============================================================================

println "=" * 80
println "COMPLETE SOLUTION - ConfigManager"
println "=" * 80

// Create config
new File("app.groovy").text = '''
app.name = "MyApp"
app.version = "1.0.0"
database.host = "localhost"
database.port = 5432
cache.enabled = true
'''

def config = new ConfigManager("app.groovy")

println "\n✅ Reading Properties:"
println "  App name: ${config.getString('app.name')}"
println "  DB port:  ${config.getInt('database.port')}"
println "  Cache:    ${config.getBoolean('cache.enabled')}"

println "\n✅ Performance Test:"
def start = System.nanoTime()
1_000_000.times { config.getString('app.name') }
def time = (System.nanoTime() - start) / 1_000_000
println "  1 million reads: ${time}ms → ${String.format('%,.0f', 1_000_000 / time)} ops/ms"

println "\n✅ Manual Reload:"
new File("app.groovy").text = new File("app.groovy").text.replace('1.0.0', '2.0.0')
println "  Before: ${config.getString('app.version')}"
config.reload()
println "  After:  ${config.getString('app.version')}"

println "\n" + "=" * 80
println "SUCCESS! Copy ConfigManager class into your project."
println "=" * 80

new File("app.groovy").delete()
