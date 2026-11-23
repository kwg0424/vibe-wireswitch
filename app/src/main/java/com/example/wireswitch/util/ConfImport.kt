package com.example.wireswitch.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wireguard.config.Config
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * WireGuard 설정 파일(.conf)과 그것들을 담은 zip 에서 구성을 읽어 온다.
 * 개인키가 든 파일이라 읽은 내용은 로그에 남기지 않고, 호출한 쪽이 곧바로 암호화해 보관한다.
 */
object ConfImport {

    /** 설정 파일 하나의 최대 크기. 정상 설정은 수 KB 라 이보다 크면 설정 파일이 아니라고 본다. */
    private const val MAX_CONF_BYTES = 64 * 1024

    /** zip 안에서 살펴볼 최대 항목 수 — 비정상적으로 큰 zip 을 막는다. */
    private const val MAX_ZIP_ENTRIES = 100

    /** 설정 한 건: 구성 이름(파일명, .conf 제외)과 설정 전문. */
    data class Conf(val name: String, val text: String)

    /** zip 을 읽은 결과: 유효한 설정들과 형식이 달라 건너뛴 .conf 수. */
    data class ZipResult(val confs: List<Conf>, val invalid: Int)

    fun displayName(context: Context, uri: Uri): String? =
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
        catch (e: Exception) {
            null
        }

    /** 파일 하나를 설정으로 읽는다. 크기 초과, 읽기 실패, 형식 오류면 null. */
    fun readConf(context: Context, uri: Uri): String? =
        try {
            context.contentResolver.openInputStream(uri)?.use { readLimited(it) }?.takeIf { isValid(it) }
        }
        catch (e: Exception) {
            null
        }

    /** zip 안의 .conf 들을 읽는다. zip 자체를 못 읽으면 null. 압축을 풀어 파일로 저장하지는 않는다. */
    fun readZip(context: Context, uri: Uri): ZipResult? =
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    val confs = mutableListOf<Conf>()
                    var invalid = 0
                    var count = 0
                    var entry = zip.nextEntry
                    while (entry != null && count < MAX_ZIP_ENTRIES) {
                        count++
                        if (!entry.isDirectory) {
                            val base = entry.name.substringAfterLast('/').substringAfterLast('\\')
                            if (base.endsWith(".conf", ignoreCase = true) && !base.startsWith("._")) {
                                val text = readLimited(zip)
                                if (text != null && isValid(text)) {
                                    confs += Conf(base.dropLast(".conf".length).trim(), text)
                                }
                                else {
                                    invalid++
                                }
                            }
                        }
                        entry = zip.nextEntry
                    }
                    ZipResult(confs, invalid)
                }
            }
        }
        catch (e: Exception) {
            null
        }

    /** 화면에 보여주기 위해 개인키(PrivateKey)와 사전공유키(PresharedKey)의 값을 가린다. 나머지는 그대로 둔다. */
    fun maskSecrets(text: String): String =
        text.lines().joinToString("\n") { line ->
            val key = line.substringBefore('=', "").trim()
            if (line.contains('=') && (key.equals("PrivateKey", true) || key.equals("PresharedKey", true))) {
                "$key = ••••••••"
            }
            else {
                line
            }
        }

    /** WireGuard 설정으로 해석되는가 — 라이브러리 파서로 검증한다. */
    fun isValid(text: String): Boolean =
        try {
            Config.parse(text.byteInputStream(Charsets.UTF_8))
            true
        }
        catch (e: Exception) {
            false
        }

    /** 최대 크기까지만 읽는다. 넘으면 null. */
    private fun readLimited(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            out.write(buffer, 0, read)
            if (out.size() > MAX_CONF_BYTES) {
                return null
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
