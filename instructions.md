Here's how to implement this. The key is using `DatabaseConnectionManager` and its `addConnectionListener` method to listen for new connections, then close all other active sessions.

## Approach

You'll use a **project-level service** (or your existing `MyProjectActivity`) to subscribe to `DatabaseConnectionManager`'s connection events. The relevant APIs are:

- `DatabaseConnectionManager.getInstance()` — manages all DB connections
- `ConnectionHandler` / `DatabaseSession` — represents an active connection session
- `addConnectionListener(project, listener)` — fires events on connection state changes

## Implementation

### 1. Create a Connection Listener Service

```kotlin
// src/main/kotlin/.../services/ConnectionManagerService.kt
package com.github.wharvex.intellijplugindemo2.services

import com.intellij.database.connection.DatabaseConnectionManager
import com.intellij.database.connection.DatabaseConnectionPoint
import com.intellij.database.connection.ConnectionHandler
import com.intellij.database.connection.live.LiveConnection
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ConnectionManagerService(private val project: Project) {

    fun init() {
        val manager = DatabaseConnectionManager.getInstance()
        manager.addConnectionListener(project, object : DatabaseConnectionManager.Listener {
            override fun connectionAdded(manager: DatabaseConnectionManager, connection: LiveConnection) {
                closeOtherConnections(manager, connection)
            }
        })
        thisLogger().info("ConnectionManagerService initialized.")
    }

    private fun closeOtherConnections(manager: DatabaseConnectionManager, newConnection: LiveConnection) {
        manager.connections
            .filter { it != newConnection }
            .forEach { it.runClose() }
    }
}
```

> **Note:** The exact class/method names (`LiveConnection`, `Listener`, `runClose()`) depend on your Database plugin version. Check your IDE's `com.intellij.database.connection` package for the actual API. Common alternatives: `DatabaseConnection`, `disconnect()`, or `closeConnection()`.

### 2. Wire it up in `MyProjectActivity`

```kotlin
// MyProjectActivity.kt
package com.github.wharvex.intellijplugindemo2.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.github.wharvex.intellijplugindemo2.services.ConnectionManagerService

class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<ConnectionManagerService>().init()
    }
}
```

### 3. Register the service in `plugin.xml`

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<projectService serviceImplementation="com.github.wharvex.intellijplugindemo2.services.ConnectionManagerService"/>
```

You can also **remove the `<toolWindow>` registration** from `plugin.xml` since you no longer need it, and delete `MyToolWindowFactory.kt`.

---

## Finding the Exact API

The Database plugin API isn't fully documented publicly. To find exact class/method names in your environment, you can browse the `com.intellij.database.connection` package in your IDE:

- Open **Project Structure → SDKs** or use **Navigate → Class** (`Ctrl+N`) and search for `DatabaseConnectionManager`
- Look at the available `addConnectionListener` overloads and what the `Listener` interface exposes
- Look at `LiveConnection` or `DatabaseConnection` for disconnect methods

The pattern above is the correct architectural approach — the exact method names may need minor adjustment based on what's available in your version of the Database plugin.
