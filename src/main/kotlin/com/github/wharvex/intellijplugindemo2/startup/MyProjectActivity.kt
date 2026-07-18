package com.github.wharvex.intellijplugindemo2.startup

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.database.console.session.canClose
import com.intellij.database.console.session.close
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyProjectActivity : ProjectActivity
{
  override suspend fun execute(project: Project)
  {
    coroutineScope {
      project.messageBus.connect().subscribe(
        DatabaseConnectionManager.TOPIC,
        DatabaseConnectionManager.Listener { connection, added ->
          if (added)
          {
            val newDataSource = connection.connectionPoint.dataSource
            logMessage(
              "New connection detected for datasource with UID: ${newDataSource.uniqueId}; Name: ${newDataSource.name}"
            )
            launch {
              withContext(Dispatchers.EDT) {
                val driverManager = JdbcDriverManager.getDriverManager(project)
                for (session in DatabaseSessionManager.getSessions(project))
                {
                  val maybeOldDataSource = session.connectionPoint.dataSource
                  if (maybeOldDataSource.uniqueId != newDataSource.uniqueId && canClose(session))
                  {
                    val configuration = session.configuration
                    logMessage(
                      "Closing session and driver for old datasource with UID: ${maybeOldDataSource.uniqueId}; Name: ${maybeOldDataSource.name}"
                    )
                    close(session)
                    driverManager.releaseDriver(maybeOldDataSource, configuration)
                  }
                }
              }
            }
          }
        }
      )
      awaitCancellation()
    }
  }

  private fun logMessage(message: String)
  {
    thisLogger().warn("[single-connection-plugin] Time: ${System.currentTimeMillis()}; $message")
  }
}
