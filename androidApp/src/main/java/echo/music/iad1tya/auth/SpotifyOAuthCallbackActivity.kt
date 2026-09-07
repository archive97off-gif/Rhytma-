package echo.music.iad1tya.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import echo.music.iad1tya.MainActivity
import echo.music.iad1tya.spotify.SpotifyOAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/** Consumes the authorization code here, so ordinary intent logging never sees it. */
class SpotifyOAuthCallbackActivity : AppCompatActivity() {
    private val oauth: SpotifyOAuthManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent.dataString.orEmpty()
        intent.data = null
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { oauth.handleRedirect(callback) }
            startActivity(Intent(this@SpotifyOAuthCallbackActivity, MainActivity::class.java).apply {
                action = SpotifyOAuthManager.RETURN_ACTION
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }
}
