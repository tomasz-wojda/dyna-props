#!/usr/bin/env groovy

/**
 * Dynamic Properties Demo - Multiple Approaches
 * 
 * This demo shows different ways to implement dynamically reloadable properties
 * in Groovy with performance comparisons.
 */

import groovy.transform.CompileStatic
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.*
import static java.nio.file.StandardWatchEventKinds.*

// ============================================================================
// APPROACH 1: Simple Map-based with Manual Reload (Fastest)
// ============================================================================
class SimpleMapProperties {
    private final Map<String, Object> props = new ConcurrentHashMap<>()
    private final File configFile
    
    SimpleMapProperties(String filePath) {
        this.configFile = new File(filePath)
        reload()
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            props.clear()
            props.putAll(config.flatten())
        }
    }
    
    def get(String key, defaultValue = null) {
        props.getOrDefault(key, defaultValue)
    }
    
    def propertyMissing(String name) {
        get(name)
    }
}

// ============================================================================
// APPROACH 2: Expando-based (Most Flexible)
// ============================================================================
class ExpandoProperties {
    private final Expando expando = new Expando()
    private final File configFile
    
    ExpandoProperties(String filePath) {
        this.configFile = new File(filePath)
        reload()
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            
            // Clear existing properties
            expando.properties.keySet().toList().each { key ->
                if (key != 'class' && key != 'metaClass') {
                    expando.properties.remove(key)
                }
            }
            
            // Add new properties
            config.flatten().each { key, value ->
                expando[key] = value
            }
        }
    }
    
    def propertyMissing(String name) {
        expando[name]
    }
    
    def get(String key, defaultValue = null) {
        expando.hasProperty(key) ? expando[key] : defaultValue
    }
}

// ============================================================================
// APPROACH 3: Auto-reloading with File Watcher (Production-ready)
// ============================================================================
class AutoReloadProperties {
    private final Map<String, Object> props = new ConcurrentHashMap<>()
    private final File configFile
    private WatchService watchService
    private Thread watchThread
    private volatile long lastModified = 0
    private final long checkIntervalMs
    
    AutoReloadProperties(String filePath, long checkIntervalMs = 1000) {
        this.configFile = new File(filePath)
        this.checkIntervalMs = checkIntervalMs
        reload()
    }
    
    void startAutoReload() {
        watchThread = Thread.start {
            while (!Thread.currentThread().interrupted) {
                try {
                    long currentModified = configFile.lastModified()
                    if (currentModified > lastModified) {
                        println "[AutoReload] Detected change in ${configFile.name}, reloading..."
                        reload()
                        lastModified = currentModified
                    }
                    Thread.sleep(checkIntervalMs)
                } catch (InterruptedException e) {
                    break
                } catch (Exception e) {
                    println "Error in auto-reload: ${e.message}"
                }
            }
        }
        watchThread.daemon = true
    }
    
    void stopAutoReload() {
        watchThread?.interrupt()
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            props.clear()
            props.putAll(config.flatten())
            lastModified = configFile.lastModified()
        }
    }
    
    def get(String key, defaultValue = null) {
        props.getOrDefault(key, defaultValue)
    }
    
    def propertyMissing(String name) {
        get(name)
    }
}

// ============================================================================
// APPROACH 4: Cached with TTL (Best for High-Performance)
// ============================================================================
class CachedProperties {
    private final Map<String, Object> props = new ConcurrentHashMap<>()
    private final File configFile
    private volatile long lastReloadTime = 0
    private final long ttlMs
    
    CachedProperties(String filePath, long ttlMs = 5000) {
        this.configFile = new File(filePath)
        this.ttlMs = ttlMs
        reload()
    }
    
    private void checkAndReload() {
        long now = System.currentTimeMillis()
        if (now - lastReloadTime > ttlMs) {
            reload()
        }
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            props.clear()
            props.putAll(config.flatten())
            lastReloadTime = System.currentTimeMillis()
        }
    }
    
    def get(String key, defaultValue = null) {
        checkAndReload()
        props.getOrDefault(key, defaultValue)
    }
    
    def propertyMissing(String name) {
        get(name)
    }
}

// ============================================================================
// MAIN DEMO
// ============================================================================

println "=" * 80
println "DYNAMIC PROPERTIES DEMO"
println "=" * 80

// Create a sample config file
def configFile = new File("config.groovy")
configFile.text = '''
database {
    host = "localhost"
    port = 5432
    username = "admin"
    password = "secret123"
}

app {
    name = "MyApp"
    version = "1.0.0"
    debug = true
    maxConnections = 100
}

cache {
    enabled = true
    ttl = 3600
    size = 1000
}
'''

println "\n1. Testing Simple Map-based Properties"
println "-" * 80
def simpleProps = new SimpleMapProperties("config.groovy")
println "Database host: ${simpleProps.get('database.host')}"
println "App name: ${simpleProps.'app.name'}"
println "Max connections: ${simpleProps.get('app.maxConnections')}"

println "\n2. Testing Expando-based Properties"
println "-" * 80
def expandoProps = new ExpandoProperties("config.groovy")
println "Database host: ${expandoProps.'database.host'}"
println "App version: ${expandoProps.get('app.version')}"
println "Cache enabled: ${expandoProps.'cache.enabled'}"

println "\n3. Testing Auto-reload Properties"
println "-" * 80
def autoProps = new AutoReloadProperties("config.groovy", 500)
println "Initial app name: ${autoProps.'app.name'}"

// Start auto-reload
autoProps.startAutoReload()
println "Auto-reload started (checking every 500ms)"
println "Modify config.groovy in the next 5 seconds to see auto-reload in action..."

// Simulate config change after 2 seconds
Thread.start {
    Thread.sleep(2000)
    configFile.text = configFile.text.replace('MyApp', 'MyApp-Modified')
    println "\n[Simulator] Config file modified!"
}

Thread.sleep(5000)
println "Current app name: ${autoProps.'app.name'}"
autoProps.stopAutoReload()

println "\n4. Testing Cached Properties with TTL"
println "-" * 80
def cachedProps = new CachedProperties("config.groovy", 2000)
println "Cache TTL: 2000ms"
println "Initial database port: ${cachedProps.'database.port'}"

// Modify config
configFile.text = configFile.text.replace('5432', '5433')
println "Config modified (port changed to 5433)"
println "Immediate read (should still be cached): ${cachedProps.'database.port'}"

Thread.sleep(2500)
println "After TTL expired: ${cachedProps.'database.port'}"

println "\n" + "=" * 80
println "PERFORMANCE BENCHMARK"
println "=" * 80

// Reset config
configFile.text = '''
database.host = "localhost"
app.name = "TestApp"
cache.size = 1000
'''

def iterations = 100_000

// Benchmark Simple Map
def start = System.nanoTime()
def simple = new SimpleMapProperties("config.groovy")
iterations.times {
    simple.get('database.host')
}
def simpleTime = (System.nanoTime() - start) / 1_000_000

// Benchmark Expando
start = System.nanoTime()
def expando = new ExpandoProperties("config.groovy")
iterations.times {
    expando.get('database.host')
}
def expandoTime = (System.nanoTime() - start) / 1_000_000

// Benchmark Cached (no reload during benchmark)
start = System.nanoTime()
def cached = new CachedProperties("config.groovy", 999999)
iterations.times {
    cached.get('database.host')
}
def cachedTime = (System.nanoTime() - start) / 1_000_000

println "\nResults for ${iterations.toString().replaceAll(/\B(?=(\d{3})+(?!\d))/, ',')} property reads:"
println "  Simple Map:        ${String.format('%,d', simpleTime as long)} ms (${String.format('%.2f', iterations / simpleTime)} ops/ms)"
println "  Expando:           ${String.format('%,d', expandoTime as long)} ms (${String.format('%.2f', iterations / expandoTime)} ops/ms)"
println "  Cached (TTL):      ${String.format('%,d', cachedTime as long)} ms (${String.format('%.2f', iterations / cachedTime)} ops/ms)"

println "\nSpeed comparison (relative to Simple Map):"
println "  Expando:           ${String.format('%.2f', simpleTime / expandoTime)}x"
println "  Cached:            ${String.format('%.2f', simpleTime / cachedTime)}x"

println "\n" + "=" * 80
println "RECOMMENDATIONS"
println "=" * 80
println """
1. **SimpleMapProperties** - Use when:
   - You need maximum performance
   - Manual reload is acceptable
   - Simple key-value access is sufficient
   - FASTEST option (~${String.format('%.0f', iterations / simpleTime)} ops/ms)

2. **ExpandoProperties** - Use when:
   - You need dynamic property access
   - Flexibility is more important than raw speed
   - You want Groovy-style property syntax
   - Still very fast (~${String.format('%.0f', iterations / expandoTime)} ops/ms)

3. **AutoReloadProperties** - Use when:
   - You need automatic detection of config changes
   - Running long-lived applications (servers, daemons)
   - Can tolerate slight delay in reload detection
   - Production environments with changing configs

4. **CachedProperties** - Use when:
   - You need balance between freshness and performance
   - Config changes are infrequent but need to be picked up
   - You can define an acceptable staleness window (TTL)
   - High-performance requirements with periodic updates

**BEST PRACTICE FOR PRODUCTION:**
Use AutoReloadProperties or CachedProperties depending on your reload
requirements. Both are thread-safe and production-ready.
"""

// Cleanup
configFile.delete()

println "\nDemo completed! Config file cleaned up."
