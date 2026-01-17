#!/usr/bin/env groovy

/**
 * Production-Ready Dynamic Properties Manager
 * 
 * This is a complete, production-ready implementation combining the best
 * features from all approaches with additional enterprise features.
 */

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import groovy.transform.Synchronized

class DynamicPropertiesManager {
    private final Map<String, Object> properties = new ConcurrentHashMap<>()
    private final File configFile
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock()
    private volatile long lastModified = 0
    private final long checkIntervalMs
    private Thread watchThread
    private final List<Closure> changeListeners = []
    
    // Statistics
    private volatile long reloadCount = 0
    private volatile long lastReloadTime = 0
    
    DynamicPropertiesManager(String filePath, long checkIntervalMs = 1000) {
        this.configFile = new File(filePath)
        this.checkIntervalMs = checkIntervalMs
        
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: ${filePath}")
        }
        
        initialLoad()
    }
    
    /**
     * Initial load of properties
     */
    private void initialLoad() {
        lock.writeLock().lock()
        try {
            loadProperties()
            lastModified = configFile.lastModified()
            println "[PropertiesManager] Initial load completed. ${properties.size()} properties loaded."
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Load properties from file
     */
    private void loadProperties() {
        def startTime = System.currentTimeMillis()
        
        try {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            def flatConfig = config.flatten()
            
            // Detect changes
            def oldProps = new HashMap(properties)
            properties.clear()
            properties.putAll(flatConfig)
            
            reloadCount++
            lastReloadTime = System.currentTimeMillis()
            
            def loadTime = lastReloadTime - startTime
            println "[PropertiesManager] Reload #${reloadCount} completed in ${loadTime}ms"
            
            // Notify listeners if properties changed
            if (oldProps != properties) {
                notifyListeners(oldProps, properties)
            }
            
        } catch (Exception e) {
            println "[PropertiesManager] ERROR loading properties: ${e.message}"
            throw e
        }
    }
    
    /**
     * Start automatic reload monitoring
     */
    void startAutoReload() {
        if (watchThread?.isAlive()) {
            println "[PropertiesManager] Auto-reload already running"
            return
        }
        
        watchThread = Thread.start("PropertyWatcher") {
            println "[PropertiesManager] Auto-reload started (interval: ${checkIntervalMs}ms)"
            
            while (!Thread.currentThread().interrupted) {
                try {
                    long currentModified = configFile.lastModified()
                    
                    if (currentModified > lastModified) {
                        println "[PropertiesManager] Change detected in ${configFile.name}"
                        reload()
                        lastModified = currentModified
                    }
                    
                    Thread.sleep(checkIntervalMs)
                    
                } catch (InterruptedException e) {
                    println "[PropertiesManager] Auto-reload stopped"
                    break
                } catch (Exception e) {
                    println "[PropertiesManager] Error in watch thread: ${e.message}"
                }
            }
        }
        
        watchThread.daemon = true
    }
    
    /**
     * Stop automatic reload monitoring
     */
    void stopAutoReload() {
        if (watchThread?.isAlive()) {
            watchThread.interrupt()
            watchThread.join(1000)
            println "[PropertiesManager] Auto-reload stopped"
        }
    }
    
    /**
     * Manually reload properties
     */
    void reload() {
        lock.writeLock().lock()
        try {
            loadProperties()
        } finally {
            lock.writeLock().unlock()
        }
    }
    
    /**
     * Get property value with optional default
     */
    def get(String key, defaultValue = null) {
        lock.readLock().lock()
        try {
            return properties.getOrDefault(key, defaultValue)
        } finally {
            lock.readLock().unlock()
        }
    }
    
    /**
     * Get property as specific type
     */
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
    
    Long getLong(String key, Long defaultValue = null) {
        def value = get(key, defaultValue)
        value instanceof Number ? value.longValue() : defaultValue
    }
    
    /**
     * Check if property exists
     */
    boolean hasProperty(String key) {
        lock.readLock().lock()
        try {
            return properties.containsKey(key)
        } finally {
            lock.readLock().unlock()
        }
    }
    
    /**
     * Get all properties (defensive copy)
     */
    Map<String, Object> getAll() {
        lock.readLock().lock()
        try {
            return new HashMap<>(properties)
        } finally {
            lock.readLock().unlock()
        }
    }
    
    /**
     * Add change listener
     */
    void addChangeListener(Closure listener) {
        synchronized (changeListeners) {
            changeListeners.add(listener)
        }
    }
    
    /**
     * Notify all listeners of changes
     */
    private void notifyListeners(Map oldProps, Map newProps) {
        synchronized (changeListeners) {
            changeListeners.each { listener ->
                try {
                    listener(oldProps, newProps)
                } catch (Exception e) {
                    println "[PropertiesManager] Error in change listener: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Get statistics
     */
    Map getStats() {
        [
            reloadCount: reloadCount,
            lastReloadTime: new Date(lastReloadTime),
            propertyCount: properties.size(),
            autoReloadActive: watchThread?.isAlive() ?: false,
            configFile: configFile.absolutePath
        ]
    }
    
    /**
     * Property missing - allows direct property access
     */
    def propertyMissing(String name) {
        get(name)
    }
    
    @Override
    String toString() {
        "DynamicPropertiesManager[file=${configFile.name}, properties=${properties.size()}, reloads=${reloadCount}]"
    }
}

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

println "=" * 80
println "PRODUCTION EXAMPLE - Dynamic Properties Manager"
println "=" * 80

// Create config file
def configFile = new File("app-config.groovy")
configFile.text = '''
// Application Configuration
app {
    name = "ProductionApp"
    version = "2.0.0"
    environment = "production"
}

// Database Configuration
database {
    host = "db.production.com"
    port = 5432
    name = "myapp_db"
    username = "app_user"
    password = "secure_password"
    pool {
        minSize = 5
        maxSize = 20
        timeout = 30000
    }
}

// Cache Configuration
cache {
    enabled = true
    provider = "redis"
    ttl = 3600
    maxSize = 10000
}

// Feature Flags
features {
    newUI = true
    betaFeatures = false
    analytics = true
}

// Performance Settings
performance {
    maxThreads = 50
    requestTimeout = 5000
    enableCompression = true
}
'''

// Initialize properties manager
def props = new DynamicPropertiesManager("app-config.groovy", 500)

// Add change listener
props.addChangeListener { oldProps, newProps ->
    println "\n[LISTENER] Configuration changed!"
    println "  Properties count: ${oldProps.size()} -> ${newProps.size()}"
}

println "\n1. Reading Properties"
println "-" * 80
println "App name: ${props.getString('app.name')}"
println "Database host: ${props.getString('database.host')}"
println "Database port: ${props.getInt('database.port')}"
println "Cache enabled: ${props.getBoolean('cache.enabled')}"
println "Max threads: ${props.getInt('performance.maxThreads')}"
println "New UI feature: ${props.getBoolean('features.newUI')}"

println "\n2. Using Property Access Syntax"
println "-" * 80
println "App version: ${props.'app.version'}"
println "Database pool max: ${props.'database.pool.maxSize'}"
println "Cache TTL: ${props.'cache.ttl'}"

println "\n3. Type-safe Access with Defaults"
println "-" * 80
println "Timeout: ${props.getInt('performance.requestTimeout', 3000)}ms"
println "Unknown property: ${props.getString('unknown.property', 'default-value')}"
println "Has 'app.name': ${props.hasProperty('app.name')}"
println "Has 'unknown': ${props.hasProperty('unknown')}"

println "\n4. Statistics"
println "-" * 80
props.stats.each { key, value ->
    println "  ${key}: ${value}"
}

println "\n5. Starting Auto-Reload"
println "-" * 80
props.startAutoReload()

println "Waiting 2 seconds, then modifying config..."
Thread.sleep(2000)

// Modify config
configFile.text = configFile.text
    .replace('ProductionApp', 'ProductionApp-v2')
    .replace('maxThreads = 50', 'maxThreads = 100')
    .replace('newUI = true', 'newUI = false')

println "\n[SIMULATOR] Config modified!"
println "Waiting for auto-reload to detect changes..."
Thread.sleep(1500)

println "\n6. Reading Updated Properties"
println "-" * 80
println "App name (updated): ${props.getString('app.name')}"
println "Max threads (updated): ${props.getInt('performance.maxThreads')}"
println "New UI feature (updated): ${props.getBoolean('features.newUI')}"

println "\n7. Final Statistics"
println "-" * 80
props.stats.each { key, value ->
    println "  ${key}: ${value}"
}

// Cleanup
props.stopAutoReload()
Thread.sleep(500)

println "\n8. Performance Test"
println "-" * 80
def iterations = 1_000_000
def start = System.nanoTime()

iterations.times {
    props.getString('database.host')
    props.getInt('database.port')
    props.getBoolean('cache.enabled')
}

def elapsed = (System.nanoTime() - start) / 1_000_000
def opsPerMs = iterations * 3 / elapsed

println "Performed ${String.format('%,d', iterations * 3)} property reads"
println "Time: ${String.format('%,d', elapsed as long)}ms"
println "Throughput: ${String.format('%,.0f', opsPerMs)} operations/ms"
println "Average: ${String.format('%.3f', elapsed / (iterations * 3) * 1000)} microseconds per read"

println "\n" + "=" * 80
println "PRODUCTION TIPS"
println "=" * 80
println """
✓ Thread-safe: Uses ReentrantReadWriteLock for concurrent access
✓ Auto-reload: Monitors file changes automatically
✓ Type-safe: Provides typed getters (getString, getInt, getBoolean, etc.)
✓ Change listeners: Subscribe to configuration changes
✓ Statistics: Track reload count and timing
✓ Defensive: Returns defensive copies, handles errors gracefully
✓ Performance: ~${String.format('%,.0f', opsPerMs)} ops/ms with locking overhead

USAGE IN YOUR APPLICATION:
1. Initialize once at startup: 
   def config = new DynamicPropertiesManager("config.groovy")
   
2. Start auto-reload for long-running apps:
   config.startAutoReload()
   
3. Access properties anywhere:
   def dbHost = config.getString('database.host')
   def port = config.getInt('database.port', 5432)
   
4. Subscribe to changes:
   config.addChangeListener { old, new ->
       // React to configuration changes
   }
"""

// Cleanup
configFile.delete()
println "\nDemo completed! Config file cleaned up."
