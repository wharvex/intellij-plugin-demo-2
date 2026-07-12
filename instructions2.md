The key is `DatabaseConnectionManager.TOPIC` and the `Listener` interface. You subscribe via the IntelliJ message bus.

## How it works

- `TOPIC` is a `com.intellij.util.messages.Topic<Listener>` — the standard IntelliJ pub/sub mechanism.
- `Listener.connectionChanged(connection, added: Boolean)` fires for both connect (`added = true`) and disconnect (`added = false`).

## Subscribing (Kotlin)

Subscribe at the **project level** (recommended, auto-disposed with the project):

```kotlin
project.messageBus.connect().subscribe(
    DatabaseConnectionManager.TOPIC,
    DatabaseConnectionManager.Listener { connection, added ->
        if (added) {
            // A new connection was just established
            println("New connection: ${connection}")
        }
    }
)
```

Or subscribe with a specific `Disposable` to control the lifetime:

```kotlin
val connection = project.messageBus.connect(myDisposable)
connection.subscribe(
    DatabaseConnectionManager.TOPIC,
    DatabaseConnectionManager.Listener { dbConnection, added ->
        if (added) {
            // do something
        }
    }
)
```

## Application-level bus

If you need to listen across all projects (not tied to one), use the application bus:

```kotlin
ApplicationManager.getApplication().messageBus.connect(myDisposable).subscribe(
    DatabaseConnectionManager.TOPIC,
    DatabaseConnectionManager.Listener { connection, added ->
        if (added) { /* ... */ }
    }
)
```

## Where to set this up

A natural place is in a **project-level service** or a `ProjectActivity`:

```kotlin
class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            DatabaseConnectionManager.TOPIC,
            DatabaseConnectionManager.Listener { connection, added ->
                if (added) {
                    // triggered when a new DB connection is created
                }
            }
        )
    }
}
```

Register it in `plugin.xml`:
```xml
<postStartupActivity implementation="com.yourplugin.MyProjectActivity"/>
```

## Key notes

- Since `Listener` is a `fun interface`, you can use a lambda directly (as shown above).
- The `added` flag distinguishes creation (`true`) from removal (`false`) — filter on `added == true` to react only to new connections.
- `connectionFailed` has a default implementation, so you only need to override it if you want to handle failures too.
