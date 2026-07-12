package com.github.wharvex.intellijplugindemo2.startup

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.database.console.session.canClose
import com.intellij.database.console.session.close
import com.intellij.database.dataSource.LocalDataSource

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            DatabaseConnectionManager.TOPIC,
            DatabaseConnectionManager.Listener { connection, added ->
                if (added) {
                    // triggered when a new DB connection is created
                    val newDataSource = connection.connectionPoint.dataSource
                    val oldDataSources = mutableSetOf<LocalDataSource>()
                    for (session in DatabaseSessionManager.getSessions(project)) {
                        val dataSource = session.connectionPoint.dataSource
                        if (dataSource !== newDataSource && canClose(session)) {
                            close(session)
                            oldDataSources.add(dataSource)
                        }
                    }

                    // close(session) only tears down the console session UI object; it never
                    // releases the underlying JDBC driver process, so isConnected() would keep
                    // reporting these data sources as connected without this.
                    val driverManager = JdbcDriverManager.getDriverManager(project)
                    for (dataSource in oldDataSources) {
                        for (configuration in driverManager.getActiveConfigurations(dataSource)) {
                            driverManager.releaseDriver(dataSource, configuration)
                        }
                    }
                }
            }
        )
    }
}