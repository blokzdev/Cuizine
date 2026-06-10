package ai.cuizine

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt's component tree is rooted here
 * (`build-conventions.md` §3).
 */
@HiltAndroidApp
class CuizineApplication : Application()
