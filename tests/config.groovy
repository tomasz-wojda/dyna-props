
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
