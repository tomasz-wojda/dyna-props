#!/usr/bin/env groovy

/**
 * Simple and Fast Dynamic Properties - Recommended Solution
 */

import java.util.concurrent.ConcurrentHashMap

// ============================================================================
// SOLUTION 1: Simple Map-based (Fastest - Recommended for most cases)
// ============================================================================
class DynamicProperties {
    private final Map<String, Object> props = new ConcurrentHashMap<>()
    private final File configFile
    
    DynamicProperties(String filePath) {
        this.configFile = new File(filePath)
        reload()
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            props.clear()
            props.putAll(config.flatten())
            println "[DynamicProperties] Loaded ${props.size()} properties from ${configFile.name}"
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
// SOLUTION 2: Expando-based (More flexible)
// ============================================================================
class ExpandoDynamicProperties {
    private final Expando expando = new Expando()
    private final File configFile
    
    ExpandoDynamicProperties(String filePath) {
        this.configFile = new File(filePath)
        reload()
    }
    
    void reload() {
        if (configFile.exists()) {
            def config = new ConfigSlurper().parse(configFile.toURI().toURL())
            
            // Clear and reload
            expando.properties.clear()
            config.flatten().each { key, value ->
                expando[key] = value
            }
            println "[ExpandoProperties] Loaded ${expando.properties.size()} properties"
        }
    }
    
    def get(String key, defaultValue = null) {
        expando.hasProperty(key) ? expando[key] : defaultValue
    }
    
    def propertyMissing(String name) {
        expando[name]
    }
}

// ============================================================================
// DEMO & BENCHMARK
// ============================================================================

println "=" * 80
println "DYNAMIC PROPERTIES - SIMPLE & FAST SOLUTION"
println "=" * 80

// Create test config
def configFile = new File("test-config.groovy")
configFile.text = '''
database {
    host = "localhost"
    port = 5432
    username = "admin"
    password = "secret"
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
}
'''

println "\n1. SIMPLE MAP-BASED APPROACH (Recommended)"
println "-" * 80
def props = new DynamicProperties("test-config.groovy")

println "Reading properties:"
println "  Database host: ${props.getString('database.host')}"
println "  Database port: ${props.getInt('database.port')}"
println "  App name: ${props.'app.name'}"
println "  Max connections: ${props.getInt('app.maxConnections')}"
println "  Cache enabled: ${props.getBoolean('cache.enabled')}"
println "  Unknown (with default): ${props.getString('unknown', 'default-value')}"

println "\n2. MODIFYING CONFIG AND RELOADING"
println "-" * 80
println "Changing app name from 'MyApp' to 'MyApp-Updated'..."
configFile.text = configFile.text.replace('MyApp', 'MyApp-Updated')

println "Before reload: ${props.'app.name'}"
props.reload()
println "After reload: ${props.'app.name'}"

println "\n3. EXPANDO-BASED APPROACH"
println "-" * 80
def expandoProps = new ExpandoDynamicProperties("test-config.groovy")
println "  App name: ${expandoProps.'app.name'}"
println "  Database port: ${expandoProps.'database.port'}"
println "  Cache TTL: ${expandoProps.'cache.ttl'}"

println "\n4. PERFORMANCE BENCHMARK"
println "-" * 80

// Reset config
configFile.text = '''
test.value = "hello"
test.number = 42
test.flag = true
'''

def iterations = 500_000

// Benchmark Simple Map
def simple = new DynamicProperties("test-config.groovy")
def start = System.nanoTime()
iterations.times {
    simple.get('test.value')
    simple.get('test.number')
    simple.get('test.flag')
}
def simpleTime = (System.nanoTime() - start) / 1_000_000

// Benchmark Expando
def expando = new ExpandoDynamicProperties("test-config.groovy")
start = System.nanoTime()
iterations.times {
    expando.get('test.value')
    expando.get('test.number')
    expando.get('test.flag')
}
def expandoTime = (System.nanoTime() - start) / 1_000_000

def totalOps = iterations * 3

println "Results for ${String.format('%,d', totalOps)} property reads:"
println ""
println "  Simple Map:  ${String.format('%6d', simpleTime as long)} ms  →  ${String.format('%,10.0f', totalOps / simpleTime)} ops/ms"
println "  Expando:     ${String.format('%6d', expandoTime as long)} ms  →  ${String.format('%,10.0f', totalOps / expandoTime)} ops/ms"
println ""
println "Speed comparison:"
println "  Simple Map is ${String.format('%.1f', expandoTime / simpleTime)}x faster than Expando"

println "\n" + "=" * 80
println "RECOMMENDATIONS"
println "=" * 80
println """
✓ USE SIMPLE MAP-BASED (DynamicProperties):
  - Fastest performance (~${String.format('%,.0f', totalOps / simpleTime)} ops/ms)
  - Thread-safe (ConcurrentHashMap)
  - Type-safe getters (getString, getInt, getBoolean)
  - Property syntax support (props.'app.name')
  - Perfect for 99% of use cases

✓ USE EXPANDO-BASED (ExpandoDynamicProperties):
  - More flexible dynamic behavior
  - Still very fast (~${String.format('%,.0f', totalOps / expandoTime)} ops/ms)
  - Good for highly dynamic configurations

HOW TO USE IN YOUR APPLICATION:

1. Initialize once:
   def config = new DynamicProperties("config.groovy")

2. Read properties anywhere:
   def host = config.getString('database.host')
   def port = config.getInt('database.port', 5432)
   def enabled = config.getBoolean('feature.enabled', false)

3. Reload when needed:
   config.reload()  // Call this when you know config changed

4. For auto-reload, call reload() periodically:
   // In a scheduled task or timer
   Timer timer = new Timer(true)
   timer.scheduleAtFixedRate(new TimerTask() {
       void run() { config.reload() }
   }, 0, 5000)  // Check every 5 seconds

PERFORMANCE:
- Reading properties is EXTREMELY fast (${String.format('%.3f', simpleTime / totalOps * 1000)} microseconds per read)
- Reloading is fast (typically < 100ms for normal config files)
- Thread-safe for concurrent reads
- No performance concerns for production use!
"""

// Cleanup
configFile.delete()
println "\nDemo completed!"
