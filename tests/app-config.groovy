
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
