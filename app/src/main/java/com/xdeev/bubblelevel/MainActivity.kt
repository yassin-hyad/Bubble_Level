package com.xdeev.bubblelevel

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xdeev.bubblelevel.ads.AdManager
import com.xdeev.bubblelevel.ui.BubbleLevelApp
import com.xdeev.bubblelevel.ui.theme.BubbleLevelTheme

class MainActivity : ComponentActivity() {
    private val vm: LevelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge, but the system bars stay visible so the user can always leave the app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Counted once per real start (not on rotation: the activity handles that itself).
        // Once per real start (rotation is handled by the activity itself).
        if (savedInstanceState == null) AdManager.start(this, vm.settings.registerLaunch())
        setContent { BubbleLevelTheme { BubbleLevelApp(vm) } }
    }

    // App open ads: only when the user comes BACK to the app after a while (never on a cold start).
    override fun onStop() {
        super.onStop()
        AdManager.onAppStopped()
    }

    override fun onStart() {
        super.onStart()
        AdManager.onAppStarted(this)
    }
}
