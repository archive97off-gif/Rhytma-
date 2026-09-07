package echo.music.iad1tya.crashlytics

import android.content.Context
import android.util.Log
import echo.music.iad1tya.domain.data.player.PlayerError

fun reportCrash(throwable: Throwable) {
    Log.e("Crashlytics", "Crash reported locally: ${throwable.localizedMessage}", throwable)
}

@Suppress("UNUSED_PARAMETER")
fun configCrashlytics(applicationContext: Context, dsn: String) {
    Log.d("Crashlytics", "Crash reporting disabled in Rhytma")
}

fun pushPlayerError(error: PlayerError) {
    Log.e(
        "Crashlytics",
        "Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}",
    )
}