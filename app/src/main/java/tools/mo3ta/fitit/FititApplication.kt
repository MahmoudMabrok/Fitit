package tools.mo3ta.fitit

import android.app.Application
import tools.mo3ta.fitit.analytics.AnalyticsManager

class FititApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsManager.init(this)
    }
}
