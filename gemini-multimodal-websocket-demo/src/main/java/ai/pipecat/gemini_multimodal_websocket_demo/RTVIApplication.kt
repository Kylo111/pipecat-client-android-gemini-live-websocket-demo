package ai.pipecat.gemini_multimodal_websocket_demo

import android.app.Application

class RTVIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Preferences.initAppStart(this)
        ThreadSettingsManager.init(this)
        PINManager.init(this)
        ThemeManager.init(this)
        OfflineConversationManager.init(this)
    }
}