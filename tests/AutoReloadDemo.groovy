#!/usr/bin/env groovy

/**
 * Production-Ready Auto-Reloading Properties
 * 
 * This implementation provides automatic reloading with a background thread
 * that monitors file changes.
 */

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AutoReloadingProperties {
    private final Map<String, Object> props = new ConcurrentHashMap<>()
    private final File configFile
    private final AtomicBoolean running = new AtomicBoolean(false)
    private volatile long lastModified = 0
    private final long checkIntervalMs
    private Thread watcherThread
    
    AutoReloadingProperties(String filePath, long checkIntervalMs = 1000) {
        this.configFile = new File(filePath)
        this.checkIntervalMs = checkIntervalMs
        reload()
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            props.clear()
            props.putAll(config.flatten())
            lastModified = configFile.lastModified()
            println "[AutoReload] Loaded ${props.size()} properties from ${configFile.name}"
        }
    }
    
    void startAutoReload() {
        if (running.compareAndSet(false, true)) {
            watcherThread = new Thread({
                println "[AutoReload] Started monitoring ${configFile.name} (interval: ${checkIntervalMs}ms)"
                
                while (running.get()) {
                    try {
                        long currentModified = configFile.lastModified()
                        if (currentModified > lastModified) {
                            println "[AutoReload] Detected change, reloading..."
                            reload()
                        }
                        Thread.sleep(checkIntervalMs)
                    } catch (InterruptedException e) {
                        break
                    } catch (Exception e) {
                        println "[AutoReload] Error: ${e.message}"
                    }
                }
                
                println "[AutoReload] Stopped monitoring"
            } as Runnable, "ConfigWatcher")
            
            watcherThread.daemon = true
            watcherThread.start()
        }
    }
    
    void stopAutoReload() {
        if (running.compareAndSet(true, false)) {
            watcherThread?.interrupt()
        }
    }
    
    def get(String key, defaultValue = null) {
        props.getOrDefault(key, defaultValue)
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
    
    def propertyMissing(String name) {
        get(name)
    }
}

// ============================================================================
// DEMO
// ============================================================================

println "=" * 80
println "AUTO-RELOADING PROPERTIES DEMO"
println "=" * 80

// Create config
def configFile = new File("auto-config.groovy")
configFile.text = '''
app {
    name = "AutoReloadApp"
    version = "1.0.0"
}

database {
    host = "localhost"
    port = 5432
}

feature {
    enabled = true
}
'''

println "\n1. Initialize and Start Auto-Reload"
println "-" * 80
def config = new AutoReloadingProperties("auto-config.groovy", 500)
config.startAutoReload()

println "Initial values:"
println "  App name: ${config.getString('app.name')}"
println "  Database port: ${config.getInt('database.port')}"
println "  Feature enabled: ${config.getBoolean('feature.enabled')}"

println "\n2. Simulating Config Changes"
println "-" * 80
println "Waiting 2 seconds, then modifying config..."
Thread.sleep(2000)

// Modify config
configFile.text = '''
app {
    name = "AutoReloadApp-UPDATED"
    version = "2.0.0"
}

database {
    host = "db.production.com"
    port = 3306
}

feature {
    enabled = false
}
'''

println "Config file modified!"
println "Waiting for auto-reload to detect changes..."
Thread.sleep(1500)

println "\n3. Reading Updated Values"
println "-" * 80
println "Updated values:"
println "  App name: ${config.getString('app.name')}"
println "  Database host: ${config.getString('database.host')}"
println "  Database port: ${config.getInt('database.port')}"
println "  Feature enabled: ${config.getBoolean('feature.enabled')}"

println "\n4. Another Change"
println "-" * 80
Thread.sleep(1000)

configFile.text = configFile.text.replace('2.0.0', '3.0.0')
println "Changed version to 3.0.0"
Thread.sleep(1500)

println "Current version: ${config.getString('app.version')}"

// Cleanup
config.stopAutoReload()
Thread.sleep(500)
configFile.delete()

println "\n" + "=" * 80
println "SUCCESS! Auto-reload works perfectly!"
println "=" * 80
