package ai.cuizine

import ai.cuizine.ui.CuizineApp
import ai.cuizine.ui.theme.CuizineTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single-activity Compose host (`build-conventions.md` §3). All navigation
 * happens inside Compose; this activity never gains siblings.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CuizineTheme {
                CuizineApp()
            }
        }
    }
}
