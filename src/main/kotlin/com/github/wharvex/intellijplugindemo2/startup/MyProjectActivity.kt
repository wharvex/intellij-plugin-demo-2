package com.github.wharvex.intellijplugindemo2.startup

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.DbImplUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.database.console.session.canClose
import com.intellij.database.console.session.close
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        coroutineScope {
            // --- TEMPORARY DIAGNOSTIC ---
            // Logs every driver termination project-wide, ours and the platform's own internal ones
            // alike, so we can see whether/when the *new* data source's driver gets torn down and
            // correlate it against our own close/release timestamps below. Remove once the collateral
            // -kill mechanism is confirmed or ruled out.
            project.messageBus.connect().subscribe(JdbcDriverManager.TOPIC, object : JdbcDriverManager.Listener {
                override fun onTerminated(dataSource: LocalDataSource, configuration: ConsoleRunConfiguration?) {
                    thisLogger().warn(
                        "[single-conn-diag] onTerminated t=${System.currentTimeMillis()} " +
                                "uniqueId=${dataSource.uniqueId} name=${dataSource.name} config=$configuration"
                    )
                }
            })

            project.messageBus.connect().subscribe(
                DatabaseConnectionManager.TOPIC,
                DatabaseConnectionManager.Listener { connection, added ->
                    if (added) {
                        // triggered when a new DB connection is created
                        val newDataSource = connection.connectionPoint.dataSource
                        thisLogger().warn(
                            "[single-conn-diag] new connection t=${System.currentTimeMillis()} " +
                                    "uniqueId=${newDataSource.uniqueId} name=${newDataSource.name}"
                        )
                        launch {
                            // JdbcDriverManagerImpl's own DbPsiFacade.TOPIC listener force-releases any
                            // active driver whose captured LocalDataSource instance isn't identity-equal
                            // to DbPsiFacade's current canonical instance for that uniqueId. Right after
                            // connecting, newDataSource can still be "unsettled" relative to the facade.
                            // releaseDriver() below is exactly what triggers that DbPsiFacade refresh, so
                            // if newDataSource is still unsettled when it fires, the new connection gets
                            // killed as collateral damage. Wait for it to settle instead of guessing a delay.
                            awaitIdentitySettled(project, newDataSource)

                            // close(session) disposes the console's UI (RunnerLayoutUiImpl, etc.), which
                            // asserts it's running on the EDT. The listener callback used to run on the EDT
                            // for free (DatabaseConnectionManager dispatches connectionChanged via
                            // invokeLater), but this coroutine's dispatcher isn't the EDT, so hop back
                            // explicitly before touching any of that UI.
                            withContext(Dispatchers.EDT) {
                                val driverManager = JdbcDriverManager.getDriverManager(project)
                                for (session in DatabaseSessionManager.getSessions(project)) {
                                    val dataSource = session.connectionPoint.dataSource
                                    // Compare by uniqueId, not reference: a connection tied to "the same"
                                    // configured data source can carry a distinct LocalDataSource instance
                                    // (e.g. from background introspection or a temporary run configuration),
                                    // so `!==` can wrongly treat the data source you're actively using as old.
                                    if (dataSource.uniqueId != newDataSource.uniqueId && canClose(session)) {
                                        val configuration = session.configuration
                                        thisLogger().warn(
                                            "[single-conn-diag] closing old session t=${System.currentTimeMillis()} " +
                                                    "uniqueId=${dataSource.uniqueId} name=${dataSource.name}"
                                        )
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
                    }
                }
            )

            // Keep this coroutine alive for the project's lifetime so the listener above always has
            // a live scope to launch into; cancelled automatically when the project closes.
            awaitCancellation()
        }
    }

    // Mirrors JdbcDriverManagerImpl's own staleness check (DbImplUtilCore.getMaybeLocalDataSource(...)
    // == pair.first) so we can tell deterministically whether the facade's canonical LocalDataSource
    // instance for this uniqueId is the same instance our connection is using.
    private fun isIdentitySettled(project: Project, dataSource: LocalDataSource): Boolean {
        val canonical = DbPsiFacade.getInstance(project).findDataSource(dataSource.uniqueId)
        return DbImplUtil.getMaybeLocalDataSource(canonical) === dataSource
    }

    private suspend fun awaitIdentitySettled(
        project: Project,
        dataSource: LocalDataSource,
        timeoutMs: Long = 5000
    ) {
        val start = System.currentTimeMillis()
        val settled = withTimeoutOrNull(timeoutMs.milliseconds) {
            while (!isIdentitySettled(project, dataSource)) {
                delay(50.milliseconds)
            }
            true
        }
        // On timeout, fall through anyway: better to proceed than to leave old connections open
        // forever because this data source never converges (e.g. a temporary/detached data source
        // that DbPsiFacade never tracks).
        thisLogger().warn(
            "[single-conn-diag] awaitIdentitySettled uniqueId=${dataSource.uniqueId} " +
                    "result=${if (settled == true) "settled" else "TIMED OUT"} elapsedMs=${System.currentTimeMillis() - start}"
        )
    }
}
