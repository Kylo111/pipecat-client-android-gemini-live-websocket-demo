package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {
    /**
     * Updates the context with the specified locale.
     * Note: This doesn't persist beyond the current context/activity.
     */
    fun updateLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }

    /**
     * Applies the locale to the base configuration.
     * Used in Activity.applyOverrideConfiguration or similar.
     */
    fun applyLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val resources = context.resources
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
