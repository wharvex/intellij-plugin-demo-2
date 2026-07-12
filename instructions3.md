For application-level listeners, the best place is an **Application Service** that self-subscribes on initialization.

## Option 1: Application Service (recommended)

```kotlin
@Service
class MyConnectionListener : DatabaseConnectionManager.Listener {

    init {
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            DatabaseConnectionManager.TOPIC,
            this
        )
    }

    override fun connectionChanged(connection: DatabaseConnection, added: Boolean) {
        if (added) {
            // do something
        }
    }
}
```

Register in `plugin.xml`:
```xml
<applicationService serviceImplementation="com.yourplugin.MyConnectionListener"/>
```

The service is instantiated lazily by the platform, and the subscription lives for the lifetime of the application.

## Option 2: `ApplicationActivationListener` / App-level startup

If you need it to start eagerly (not lazily), use an `AppLifecycleListener`:

```kotlin
class MyAppLifecycleListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            DatabaseConnectionManager.TOPIC,
            DatabaseConnectionManager.Listener { connection, added ->
                if (added) { /* ... */ }
            }
        )
    }
}
```

Register in `plugin.xml`:
```xml
<applicationListener implementation="com.yourplugin.MyAppLifecycleListener"
                     topic="com.intellij.ide.AppLifecycleListener"/>
```

## Which to choose?

| | App Service | AppLifecycleListener |
|---|---|---|
| Lifetime | Until service GC'd (lazy) | App lifetime (eager) |
| Boilerplate | Less | Slightly more |
| Recommended | ✅ Yes, for most cases | If you need guaranteed early init |

**Option 1 (App Service)** is the idiomatic choice for this use case. The `init` block subscription ensures it's wired up as soon as anything first touches the service, and since it subscribes to the app bus, it will receive events from all projects.
