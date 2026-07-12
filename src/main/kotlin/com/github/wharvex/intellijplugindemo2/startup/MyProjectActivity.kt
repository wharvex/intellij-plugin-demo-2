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
                    val driverManager = JdbcDriverManager.getDriverManager(project)
                    for (session in DatabaseSessionManager.getSessions(project)) {
                        val dataSource = session.connectionPoint.dataSource
                        // Compare by uniqueId, not reference: a connection tied to "the same"
                        // configured data source can carry a distinct LocalDataSource instance
                        // (e.g. from background introspection or a temporary run configuration),
                        // so `!==` can wrongly treat the data source you're actively using as old.
                        if (dataSource.uniqueId != newDataSource.uniqueId && canClose(session)) {
                            val configuration = session.configuration
                            close(session)

                            // close(session) only tears down the console session UI object; it
                            // never releases the underlying JDBC driver process, so isConnected()
                            // would keep reporting the data source as connected without this.
                            // Release only the configuration this specific session used, not every
                            // active configuration on the data source, so a second, still-busy
                            // session on the same data source isn't disconnected as collateral damage.
                            driverManager.releaseDriver(dataSource, configuration)
                        }
                    }
                }
            }
        )
    }
}