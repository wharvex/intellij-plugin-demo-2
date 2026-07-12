package com.github.wharvex.intellijplugindemo2.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.wharvex.intellijplugindemo2.services.MyProjectService
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.DasUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import com.intellij.database.util.DbImplUtil

class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()
        private val project = toolWindow.project

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()

            val model = DefaultListModel<String>()
            val list = JBList(model)
            val emptyLabel = JBLabel("No data sources or tables found.").apply {
                horizontalAlignment = JBLabel.CENTER
                isVisible = false
            }

            val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(JBScrollPane(list), BorderLayout.CENTER)
                add(emptyLabel, BorderLayout.NORTH)
            }

            val loadButton = JButton("Load Data Sources").apply {
                addActionListener {
                    model.clear()
                    val facade = DbPsiFacade.getInstance(project)
                    for (ds in facade.dataSources) {
                        model.addElement("DataSource: ${ds.name}")
                        model.addElement("    Is connected: " + if (DbImplUtil.isConnected(ds)) "Yes" else "No")
                        // DasUtil.getTables(ds).forEach { table ->
                        //     model.addElement("  Table: ${table.name}")
                        // }
                    }
                    emptyLabel.isVisible = model.isEmpty
                }
            }

            add(loadButton, BorderLayout.NORTH)
            add(centerPanel, BorderLayout.CENTER)
        }
    }
}
