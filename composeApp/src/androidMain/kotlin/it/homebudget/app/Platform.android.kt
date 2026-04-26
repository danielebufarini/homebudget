package it.homebudget.app

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val isIos: Boolean = false
}

actual fun getPlatform(): Platform = AndroidPlatform()
