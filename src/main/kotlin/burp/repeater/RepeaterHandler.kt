package burp.repeater

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.core.ToolType
import burp.api.montoya.http.handler.*
import burp.api.montoya.proxy.http.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import java.util.ArrayDeque

class RepeaterHandler(private val api: MontoyaApi) {
    private var keyEventListener: AWTEventListener? = null
    private var isCleanedUp = false

    fun register() {
        try {
            // Register HTTP listener for all tools
            api.http().registerHttpHandler(object : HttpHandler {
                override fun handleHttpRequestToBeSent(requestToBeSent: HttpRequestToBeSent): RequestToBeSentAction {
                    if (requestToBeSent.toolSource().toolType() == ToolType.REPEATER) {
                        val url = requestToBeSent.url()
                        logRequestDetails(requestToBeSent)
                        
                        // Update the tab name in Swing thread
                        SwingUtilities.invokeLater {
                            updateRepeaterTabName(url.toString())
                        }
                    }
                    return RequestToBeSentAction.continueWith(requestToBeSent)
                }

                override fun handleHttpResponseReceived(responseReceived: HttpResponseReceived): ResponseReceivedAction {
                    return ResponseReceivedAction.continueWith(responseReceived)
                }
            })

            // Keep the keyboard shortcut functionality
            keyEventListener = AWTEventListener { event ->
                if (event is KeyEvent) {
                    if (!event.isConsumed && 
                        event.id == KeyEvent.KEY_PRESSED &&
                        event.keyCode == KeyEvent.VK_R && 
                        event.isControlDown) {
                        
                        event.consume()
                        SwingUtilities.invokeLater {
                            processRepeaterRequest()
                        }
                    }
                }
            }
            
            Toolkit.getDefaultToolkit().addAWTEventListener(
                keyEventListener,
                AWTEvent.KEY_EVENT_MASK
            )

            api.logging().logToOutput("Repeater Handler registered successfully")
        } catch (e: Exception) {
            api.logging().logToError("Failed to register handlers: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun logRequestDetails(request: HttpRequest) {
        try {
            val url = request.url()
            api.logging().logToOutput("\n=== New Request in Repeater ===")
            api.logging().logToOutput("URL: $url")
            api.logging().logToOutput("Method: ${request.method()}")
            api.logging().logToOutput("==============================")
        } catch (e: Exception) {
            api.logging().logToError("Error logging request details: ${e.message}")
        }
    }

    private fun processRepeaterRequest() {
        try {
            val rootTabbedPane = findRootTabbedPane() ?: return

            // Find Repeater tab
            for (i in 0 until rootTabbedPane.tabCount) {
                if (rootTabbedPane.getTitleAt(i).equals("Repeater", ignoreCase = true)) {
                    val repeaterPanel = rootTabbedPane.getComponentAt(i)
                    
                    // Find the active subtab in Repeater
                    val repeaterTabbedPane = findComponentByType(repeaterPanel as Container, JTabbedPane::class.java)
                    if (repeaterTabbedPane != null) {
                        val selectedIndex = repeaterTabbedPane.selectedIndex
                        
                        if (selectedIndex >= 0) {
                            val selectedComponent = repeaterTabbedPane.getComponentAt(selectedIndex)
                            handleRepeater(selectedComponent)
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            api.logging().logToError("Error processing Repeater request: ${e.message}")
        }
    }

    private fun handleRepeater(repeaterComponent: Component) {
        val splitPane = findComponentByType(repeaterComponent as Container, JSplitPane::class.java) ?: return
        
        val textAreas = mutableListOf<JTextArea>()
        findTextAreasInViewports(splitPane, textAreas)
        
        if (textAreas.isNotEmpty()) {
            val requestText = textAreas[0].text
            
            if (requestText.isNotEmpty()) {
                try {
                    val request = HttpRequest.httpRequest(requestText)
                    val url = request.url()
                    
                    api.logging().logToOutput("\n=== New Request in Repeater ===")
                    api.logging().logToOutput("URL: $url")
                    api.logging().logToOutput("Method: ${request.method()}")
                    api.logging().logToOutput("==============================")
                } catch (e: Exception) {
                    api.logging().logToError("Error parsing request URL: ${e.message}")
                }
            }
        }
    }

    private fun findTextAreasInViewports(container: Container, textAreas: MutableList<JTextArea>) {
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
        
        for (viewport in viewports) {
            val textArea = findComponentByType(viewport, JTextArea::class.java)
            if (textArea != null && textArea.text.isNotEmpty()) {
                textAreas.add(textArea)
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
        for (window in Window.getWindows()) {
            if (window !is JFrame) continue
            
            findComponentByType(window, JTabbedPane::class.java)?.let { tabbedPane ->
                val tabTitles = (0 until tabbedPane.tabCount).map { tabbedPane.getTitleAt(it) }
                
                if (tabTitles.any { it.equals("Repeater", ignoreCase = true) }) {
                    return tabbedPane
                }
            }
        }
        return null
    }

    private fun updateRepeaterTabName(url: String) {
        try {
            val rootTabbedPane = findRootTabbedPane() ?: return

            // Find Repeater tab
            for (i in 0 until rootTabbedPane.tabCount) {
                if (rootTabbedPane.getTitleAt(i).equals("Repeater", ignoreCase = true)) {
                    val repeaterPanel = rootTabbedPane.getComponentAt(i)
                    
                    // Find the active subtab in Repeater
                    val repeaterTabbedPane = findComponentByType(repeaterPanel as Container, JTabbedPane::class.java)
                    if (repeaterTabbedPane != null) {
                        val selectedIndex = repeaterTabbedPane.selectedIndex
                        
                        if (selectedIndex >= 0) {
                            // Get the host part of the URL for a cleaner tab name
                            val tabName = try {
                                val uri = java.net.URI(url)
                                val host = uri.host
                                val path = uri.path.takeIf { it.isNotEmpty() } ?: "/"
                                "$host$path"
                            } catch (e: Exception) {
                                url // Fallback to full URL if parsing fails
                            }
                            
                            // Set the new tab name
                            repeaterTabbedPane.setTitleAt(selectedIndex, tabName)
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            api.logging().logToError("Error updating Repeater tab name: ${e.message}")
        }
    }
} 