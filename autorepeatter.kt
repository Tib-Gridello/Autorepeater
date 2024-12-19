package org.example.handlers

import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.editor.HttpRequestEditor
import burp.api.montoya.ui.editor.HttpResponseEditor
import org.example.tabs.ReportingTab
import org.example.utils.VulnerabilityDialogHelper
import javax.swing.*
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import burp.api.montoya.http.message.HttpRequestResponse
import java.awt.event.AWTEventListener
import java.awt.AWTEvent
import java.util.ArrayDeque
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import org.example.network.handlers.RequestShareHandler
import org.example.network.utils.HttpConverter
import org.example.network.managers.SocketClientManager
import org.example.tabs.SharedWorkspaceTab

class KeyboardShortcutHandler(
    private val api: MontoyaApi,
    private val reportingTab: ReportingTab,
    private val sharedWorkspaceTab: SharedWorkspaceTab
) {
    companion object {
        private const val DEBUG = true
    }
    
    private val dialogHelper = VulnerabilityDialogHelper(api, reportingTab)
    private var lastCtrlPressTime = 0L
    private var isCtrlDown = false
    private var keyEventListener: AWTEventListener? = null
    private var isCleanedUp = false
    
    private val requestShareHandler by lazy {
        RequestShareHandler(
            api = api,
            sharedWorkspaceTab = sharedWorkspaceTab,
            httpConverter = HttpConverter(api),
            socketClient = SocketClientManager.getInstance(api).getSocketClient()
        )
    }
    
    private fun debug(message: String) {
        if (DEBUG) {
            api.logging().logToOutput(message)
        }
    }
    
    private fun debugBlock(block: () -> String) {
        if (DEBUG) {
            api.logging().logToOutput(block())
        }
    }
    
    fun register() {
        api.logging().logToOutput("\n=== Initializing Keyboard Shortcut Handler ===")
        
        try {
            keyEventListener = AWTEventListener { event ->
                if (event is KeyEvent) {
                    // Only log key combinations in debug mode
                    if (event.id == KeyEvent.KEY_PRESSED && event.modifiers != 0) {
                        debugBlock {
                            """
                            |=== Key Combination ===
                            |Key: ${KeyEvent.getKeyText(event.keyCode)}
                            |CTRL: ${event.isControlDown}
                            |ALT: ${event.isAltDown}
                            |SHIFT: ${event.isShiftDown}
                            |===================
                            """.trimMargin()
                        }
                    }

                    // Handle Ctrl+M (Send to Milou)
                    if (!event.isConsumed && 
                        event.id == KeyEvent.KEY_PRESSED &&
                        event.keyCode == KeyEvent.VK_M && 
                        event.isControlDown &&
                        !event.isShiftDown) {
                        
                        api.logging().logToOutput("\n!!! CTRL+M Detected !!!")
                        event.consume()
                        SwingUtilities.invokeLater {
                            processSelectedRequests()
                        }
                        return@AWTEventListener
                    }

                    // Handle Ctrl+Shift+M (Share Request)
                    if (!event.isConsumed && 
                        event.id == KeyEvent.KEY_PRESSED &&
                        event.keyCode == KeyEvent.VK_M && 
                        event.isControlDown &&
                        event.isShiftDown) {
                        
                        api.logging().logToOutput("\n!!! CTRL+SHIFT+M Detected !!!")
                        event.consume()
                        SwingUtilities.invokeLater {
                            processSelectedRequestsForSharing()
                        }
                        return@AWTEventListener
                    }
                }
            }
            
            Toolkit.getDefaultToolkit().addAWTEventListener(
                keyEventListener,
                AWTEvent.KEY_EVENT_MASK
            )
            debug("Successfully registered AWT Event Listener")
        } catch (e: Exception) {
            api.logging().logToError("Failed to register keyboard handler: ${e.message}")
        }
    }
    
    fun unregister() {
        api.logging().logToOutput("\n=== Unregistering Keyboard Shortcut Handler ===")
        try {
            if (!isCleanedUp) {
                keyEventListener?.let { listener ->
                    api.logging().logToOutput("Removing AWT Event Listener...")
                    Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
                    api.logging().logToOutput("Successfully removed AWT Event Listener")
                }
                keyEventListener = null
                
                isCleanedUp = true
                
                api.logging().logToOutput("=== Keyboard Shortcut Handler Cleanup Complete ===")
            } else {
                api.logging().logToOutput("Handler already cleaned up, skipping...")
            }
        } catch (e: Exception) {
            api.logging().logToError("Failed to unregister keyboard handler: ${e.message}")
            api.logging().logToError("Stack trace: ${e.stackTraceToString()}")
        }
    }
    
    private fun processSelectedRequests() {
        // First try proxy table
        findProxyTable()?.let { table ->
            handleProxyTable(table)
            return
        }

        // Then try repeater
        debug("\n=== Searching for Repeater ===")
        try {
            // Get the root tabbed pane
            val rootTabbedPane = findRootTabbedPane()
            if (rootTabbedPane == null) {
                debug("Root tabbed pane not found")
                return
            }

            debug("Found root tabbed pane with ${rootTabbedPane.tabCount} tabs")

            // Find Repeater tab
            for (i in 0 until rootTabbedPane.tabCount) {
                val title = rootTabbedPane.getTitleAt(i)
                debug("Checking tab $i: $title")
                
                if (title.equals("Repeater", ignoreCase = true)) {
                    val repeaterPanel = rootTabbedPane.getComponentAt(i)
                    debug("Found Repeater tab at index $i")
                    
                    // Find the active subtab in Repeater
                    val repeaterTabbedPane = findRepeaterTabbedPane(repeaterPanel)
                    if (repeaterTabbedPane != null) {
                        debug("Found Repeater tabbed pane with ${repeaterTabbedPane.tabCount} tabs")
                        val selectedIndex = repeaterTabbedPane.selectedIndex
                        debug("Selected tab index: $selectedIndex")
                        
                        if (selectedIndex >= 0) {
                            val selectedComponent = repeaterTabbedPane.getComponentAt(selectedIndex)
                            debug("Found selected Repeater tab at index $selectedIndex")
                            
                            // Find request/response editors in the selected tab
                            handleRepeater(selectedComponent)
                            return
                        }
                    } else {
                        debug("Could not find Repeater tabbed pane")
                    }
                }
            }
            debug("No active Repeater tab found")
        } catch (e: Exception) {
            api.logging().logToError("Error finding Repeater component: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun processSelectedRequestsForSharing() {
        // First try proxy table
        findProxyTable()?.let { table ->
            handleProxyTableForSharing(table)
            return
        }

        // Then try repeater
        debug("\n=== Searching for Repeater for Sharing ===")
        try {
            val rootTabbedPane = findRootTabbedPane()
            if (rootTabbedPane == null) {
                debug("Root tabbed pane not found")
                return
            }

            // ... existing repeater search code ...
            for (i in 0 until rootTabbedPane.tabCount) {
                if (rootTabbedPane.getTitleAt(i).equals("Repeater", ignoreCase = true)) {
                    val repeaterPanel = rootTabbedPane.getComponentAt(i)
                    handleRepeaterForSharing(repeaterPanel)
                    return
                }
            }
        } catch (e: Exception) {
            api.logging().logToError("Error finding Repeater component for sharing: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleProxyTable(table: JTable) {
        debugBlock {
            """
            |=== Found Proxy Table ===
            |Table Class: ${table.javaClass.name}
            |Total Rows: ${table.rowCount}
            |Selected Rows: ${table.selectedRows.joinToString()}
            |=========================
            """.trimMargin()
        }

        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            api.logging().logToOutput("No rows selected in proxy table")
            return
        }

        try {
            // Convert view indices to model indices since the table might be sorted
            val modelIndices = selectedRows.map { table.convertRowIndexToModel(it) }
            
            // Get the proxy history
            val proxyHistory = api.proxy().history()
            
            // Map selected indices to requests
            val requests = modelIndices.mapNotNull { index ->
                if (index >= 0 && index < proxyHistory.size) {
                    val proxyRequest = proxyHistory[index]
                    HttpRequestResponse.httpRequestResponse(proxyRequest.request(), proxyRequest.response())
                } else null
            }
            
            if (requests.isNotEmpty()) {
                api.logging().logToOutput("Processing ${requests.size} requests from Proxy")
                SwingUtilities.invokeLater {
                    dialogHelper.handleRequestsToVulnerability(requests)
                }
            }
        } catch (e: Exception) {
            api.logging().logToError("Error processing proxy requests: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleProxyTableForSharing(table: JTable) {
        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            api.logging().logToOutput("No rows selected in proxy table")
            return
        }

        try {
            val modelIndices = selectedRows.map { table.convertRowIndexToModel(it) }
            val proxyHistory = api.proxy().history()
            val requests = modelIndices.mapNotNull { index ->
                if (index >= 0 && index < proxyHistory.size) {
                    val proxyRequest = proxyHistory[index]
                    HttpRequestResponse.httpRequestResponse(proxyRequest.request(), proxyRequest.response())
                } else null
            }
            
            if (requests.isNotEmpty()) {
                api.logging().logToOutput("Sharing ${requests.size} requests from Proxy")
                requests.forEach { reqRes ->
                    requestShareHandler.shareRequest(reqRes)
                }
            }
        } catch (e: Exception) {
            api.logging().logToError("Error processing proxy requests for sharing: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleRepeater(repeaterComponent: Component) {
        try {
            debug("Handling Repeater component: ${repeaterComponent.javaClass.name}")
            
            // First find the split pane
            val splitPane = findComponentByType(repeaterComponent as Container, JSplitPane::class.java)
            if (splitPane == null) {
                debug("No split pane found in Repeater component")
                return
            }
            
            debug("Found split pane in Repeater")
            
            // Find all JViewports containing text areas
            val textAreas = mutableListOf<JTextArea>()
            findTextAreasInViewports(splitPane, textAreas)
            
            debug("Found ${textAreas.size} text areas")
            
            // We expect to find exactly 2 text areas - request and response
            if (textAreas.size >= 2) {
                // The request is typically the first text area
                val requestText = textAreas[0].text
                val responseText = textAreas[1].text
                
                debug("Request length: ${requestText.length}")
                debug("Response length: ${responseText.length}")
                
                if (requestText.isNotEmpty()) {
                    // Create HTTP request from text directly
                    val request = HttpRequest.httpRequest(requestText)
                    
                    // Create HTTP response from text if it exists
                    val response = if (responseText.isNotEmpty()) {
                        HttpResponse.httpResponse(responseText)
                    } else null
                    
                    val requestResponse = HttpRequestResponse.httpRequestResponse(request, response)
                    
                    SwingUtilities.invokeLater {
                        dialogHelper.handleRequestsToVulnerability(listOf(requestResponse))
                    }
                } else {
                    debug("No request text found in text areas")
                }
            } else {
                debug("Did not find expected number of text areas (found ${textAreas.size}, expected 2)")
            }
        } catch (e: Exception) {
            api.logging().logToError("Error processing Repeater request: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleRepeaterForSharing(repeaterComponent: Component) {
        try {
            val splitPane = findComponentByType(repeaterComponent as Container, JSplitPane::class.java)
            if (splitPane == null) {
                debug("No split pane found in Repeater component")
                return
            }
            
            val textAreas = mutableListOf<JTextArea>()
            findTextAreasInViewports(splitPane, textAreas)
            
            if (textAreas.size >= 2) {
                val requestText = textAreas[0].text
                val responseText = textAreas[1].text
                
                if (requestText.isNotEmpty()) {
                    val request = HttpRequest.httpRequest(requestText)
                    val response = if (responseText.isNotEmpty()) {
                        HttpResponse.httpResponse(responseText)
                    } else null
                    
                    val requestResponse = HttpRequestResponse.httpRequestResponse(request, response)
                    requestShareHandler.shareRequest(requestResponse)
                }
            }
        } catch (e: Exception) {
            api.logging().logToError("Error processing Repeater request for sharing: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun findTextAreasInViewports(container: Container, textAreas: MutableList<JTextArea>) {
        // First look for JViewports
        val viewports = mutableListOf<JViewport>()
        val queue = ArrayDeque<Container>()
        queue.add(container)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            
            if (current is JViewport) {
                viewports.add(current)
            }
            
            for (component in current.components) {
                if (component is Container) {
                    queue.add(component)
                }
            }
        }
        
        // Then look for text areas within those viewports
        for (viewport in viewports) {
            val textArea = findComponentByType(viewport, JTextArea::class.java)
            if (textArea != null && textArea.text.isNotEmpty()) {
                // Only add text areas that contain actual content
                textAreas.add(textArea)
            }
        }
    }
    
    private fun debugComponentHierarchy(component: Component, indent: String) {
        debug("$indent${component.javaClass.name}")
        if (component is Container) {
            for (child in component.components) {
                debugComponentHierarchy(child, "$indent  ")
            }
        }
    }
    
    private fun debugAllComponents(container: Container) {
        val queue = ArrayDeque<Pair<Container, Int>>()
        queue.add(container to 0)
        
        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            val indent = "  ".repeat(depth)
            
            debug("${indent}Container: ${current.javaClass.name}")
            for (component in current.components) {
                debug("${indent}- Component: ${component.javaClass.name}")
                if (component is JTextArea) {
                    debug("${indent}  Text area content length: ${component.text.length}")
                    debug("${indent}  First 100 chars: ${component.text.take(100)}")
                }
                if (component is Container) {
                    queue.add(component to depth + 1)
                }
            }
        }
    }
    
    private fun <T> findComponentByType(container: Container, type: Class<T>, maxDepth: Int = 15): T? {
        val queue = ArrayDeque<Pair<Container, Int>>()
        queue.add(container to 0)
        
        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (depth > maxDepth) continue
            
            for (component in current.components) {
                if (type.isInstance(component)) {
                    debug("Found ${type.simpleName} at depth $depth")
                    return component as T
                }
                if (component is Container) {
                    queue.add(component to depth + 1)
                }
            }
        }
        return null
    }
    
    private fun findRootTabbedPane(): JTabbedPane? {
        debug("\n=== Searching for Root Tabbed Pane ===")
        
        for (window in Window.getWindows()) {
            if (window !is JFrame) {
                debug("Skipping non-JFrame window: ${window.javaClass.name}")
                continue
            }
            
            debug("Checking window: ${window.title} (${window.javaClass.name})")
            
            // First try to find the root tabbed pane directly
            findComponentByType(window, JTabbedPane::class.java)?.let { tabbedPane ->
                // Verify it's the root pane by checking if it contains main Burp tabs
                val tabTitles = (0 until tabbedPane.tabCount).map { tabbedPane.getTitleAt(it) }
                debug("Found tabbed pane with tabs: ${tabTitles.joinToString(", ")}")
                
                if (tabTitles.any { it.equals("Repeater", ignoreCase = true) } &&
                    tabTitles.any { it.equals("Proxy", ignoreCase = true) }) {
                    debug("Confirmed root tabbed pane - contains Burp main tabs")
                    return tabbedPane
                }
            }
            
            // If not found directly, try searching in SwingUtilities.windowForComponent
            val contentPane = window.contentPane
            findComponentByType(contentPane, JTabbedPane::class.java)?.let { tabbedPane ->
                val tabTitles = (0 until tabbedPane.tabCount).map { tabbedPane.getTitleAt(it) }
                debug("Found tabbed pane in content pane with tabs: ${tabTitles.joinToString(", ")}")
                
                if (tabTitles.any { it.equals("Repeater", ignoreCase = true) } &&
                    tabTitles.any { it.equals("Proxy", ignoreCase = true) }) {
                    debug("Confirmed root tabbed pane in content pane")
                    return tabbedPane
                }
            }
        }
        
        debug("No root tabbed pane found")
        return null
    }
    
    private fun findRepeaterTabbedPane(container: Component): JTabbedPane? {
        debug("Searching for Repeater tabbed pane in ${container.javaClass.name}")
        
        if (container is JTabbedPane) {
            debug("Found JTabbedPane with ${container.tabCount} tabs")
            // Look for the first split pane in each tab
            for (i in 0 until container.tabCount) {
                val tabComponent = container.getComponentAt(i)
                debug("Checking tab component: ${tabComponent.javaClass.name}")
                
                // Find the split pane that contains request/response editors
                val splitPane = findComponentByType(tabComponent as Container, JSplitPane::class.java)
                if (splitPane != null) {
                    debug("Found split pane in tab")
                    return container  // Return the tabbed pane immediately when we find a split pane
                }
            }
        }
        
        if (container is Container) {
            for (component in container.components) {
                if (component is JTabbedPane) {
                    debug("Recursively checking tabbed pane with ${component.tabCount} tabs")
                    val result = findRepeaterTabbedPane(component)
                    if (result != null) return result
                } else if (component is Container) {
                    findRepeaterTabbedPane(component)?.let { return it }
                }
            }
        }
        return null
    }
    
    private fun findProxyTable(): JTable? {
        debug("\n=== Searching for Proxy Table ===")
        
        for (window in Window.getWindows()) {
            if (window !is JFrame) continue
            findProxyTableInContainer(window)?.let { return it }
        }
        return null
    }
    
    private fun findProxyTableInContainer(container: Container): JTable? {
        for (component in container.components) {
            when (component) {
                is JTable -> {
                    val columnNames = (0 until component.columnCount).map { component.getColumnName(it) }
                    if (columnNames.any { it.contains("URL", ignoreCase = true) } &&
                        columnNames.any { it.contains("Method", ignoreCase = true) }) {
                        return component
                    }
                }
                is Container -> {
                    if (component.isVisible && component.isShowing) {
                        findProxyTableInContainer(component)?.let { return it }
                    }
                }
            }
        }
        return null
    }
} 