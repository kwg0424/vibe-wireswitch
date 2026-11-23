package com.example.wireswitch.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * VPN 설정 파일(.conf) 전문을 보관한다. 개인키가 들어 있으므로 평문으로 저장하지 않고,
 * 기기의 Android Keystore 에 든 AES 키(꺼낼 수 없음)로 암호화해 앱 전용 저장소에 둔다.
 * 키가 기기에 묶여 있어 다른 기기나 백업으로는 복호화되지 않는다(그래서 allowBackup=false).
 *
 * 내용은 로그에 남기지 않는다.
 */
object SecretStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "wireswitch_conf_key"
    private const val PREFS = "wireswitch_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, configId: Long, text: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        // iv + 암호문을 한 덩어리로 저장한다.
        val packed = cipher.iv + encrypted
        prefs(context).edit().putString(name(configId), Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    /** 저장된 설정 전문. 없거나 복호화에 실패하면 null. */
    fun load(context: Context, configId: Long): String? {
        val raw = prefs(context).getString(name(configId), null) ?: return null
        return try {
            val packed = Base64.decode(raw, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val body = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }
        catch (e: Exception) {
            null
        }
    }

    fun has(context: Context, configId: Long): Boolean = prefs(context).contains(name(configId))

    fun delete(context: Context, configId: Long) {
        prefs(context).edit().remove(name(configId)).apply()
    }

    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    private fun name(configId: Long) = "conf_$configId"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
