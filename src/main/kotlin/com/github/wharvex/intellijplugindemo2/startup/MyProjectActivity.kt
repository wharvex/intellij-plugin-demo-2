package com.github.wharvex.intellijplugindemo2.startup

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.database.console.session.canClose
import com.intellij.database.console.session.close

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            DatabaseConnectionManager.TOPIC,
            DatabaseConnectionManager.Listener { connection, added ->
                if (added) {
                    // triggered when a new DB connection is created
                    val newDataSource = connection.connectionPoint.dataSource
                    for (session in DatabaseSessionManager.getSessions(project)) {
                        if (session.connectionPoint.dataSource !== newDataSource && canClose(session)) {
                            close(session)
                        }
                    }
                }
            }
        )
    }
}