package com.github.wharvex.intellijplugindemo2.services

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ConnectionManagerService(private val project: Project) {

    fun init() {
        val manager = DatabaseConnectionManager.getInstance()
        // manager.addConnectionListener(project, object : DatabaseConnectionManager.Listener {
        //     override fun connectionAdded(manager: DatabaseConnectionManager, connection: LiveConnection) {
        //         closeOtherConnections(manager, connection)
        //     }
        // })
        thisLogger().info("ConnectionManagerService initialized.")
    }

    private fun closeOtherConnections(manager: DatabaseConnectionManager, newConnection: DatabaseConnection) {
        //manager.terminateConnections(localDataSource, null)
        manager.activeConnections
            .filter { it != newConnection }
            .forEach { it.run { println(it) } }
    }
}