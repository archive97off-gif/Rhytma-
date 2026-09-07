package echo.music.iad1tya.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import echo.music.iad1tya.R
import echo.music.iad1tya.spotify.SpotifyOAuthData
import echo.music.iad1tya.spotify.SpotifyOAuthHttp
import echo.music.iad1tya.spotify.SpotifyOAuthManager
import echo.music.iad1tya.spotify.SpotifyOAuthStore
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun createSpotifyOAuth(context: Context): SpotifyOAuthManager {
    val random = SecureRandom()
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val http = SpotifyOAuthHttp()
    return SpotifyOAuthManager(
        store = AndroidSpotifyOAuthStore(context.applicationContext),
        redirectUri = "${context.packageName}.spotify://oauth/callback",
        now = System::currentTimeMillis,
        randomSecret = { encoder.encodeToString(ByteArray(32).also(random::nextBytes)) },
        challenge = { encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.US_ASCII))) },
        exchange = http::exchange,
    )
}

/** OAuth credentials and pending PKCE state never enter the app's general backup/database paths. */
private class AndroidSpotifyOAuthStore(private val context: Context) : SpotifyOAuthStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "spotify-oauth.enc"))
    private val alias = "rhytma.spotify.oauth.aes"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    override fun read(): SpotifyOAuthData = runCatching {
        val bytes = file.readFully()
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        SpotifyOAuthData(json.optString("clientId"), json.optString("accessToken"), json.optString("refreshToken"),
            json.optLong("expiresAt"), json.optString("verifier"), json.optString("state"), json.optLong("startedAt"))
    }.getOrElse {
        // Reinstallation, missing/corrupt ciphertext or an invalidated key requires a new connection.
        SpotifyOAuthData(clientId = context.getString(R.string.spotify_oauth_client_id).trim())
    }

    override fun write(data: SpotifyOAuthData) {
        val json = JSONObject().put("clientId", data.clientId).put("accessToken", data.accessToken)
            .put("refreshToken", data.refreshToken).put("expiresAt", data.expiresAt)
            .put("verifier", data.verifier).put("state", data.state).put("startedAt", data.startedAt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw IllegalStateException("Unable to securely save Spotify authorization. Please reconnect.")
        }
    }
}
