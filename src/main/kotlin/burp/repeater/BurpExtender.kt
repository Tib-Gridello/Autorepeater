package burp.repeater

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi

class BurpExtender : BurpExtension {
    override fun initialize(api: MontoyaApi) {
        try {
            api.extension().setName("Repeater URL Logger")
            
            val repeaterHandler = RepeaterHandler(api)
            repeaterHandler.register()
            
            api.logging().logToOutput("Extension loaded successfully")
        } catch (e: Exception) {
            api.logging().logToError("Error initializing extension: ${e.message}")
            e.printStackTrace()
        }
    }
} 