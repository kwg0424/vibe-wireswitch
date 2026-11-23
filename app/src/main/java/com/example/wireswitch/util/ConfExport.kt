package com.example.wireswitch.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.example.wireswitch.data.SecretStore
import com.example.wireswitch.data.VpnConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 가져온 VPN 구성을 다운로드 폴더에 zip(구성마다 `이름.conf`) 하나로 내보낸다.
 * 가져오기(.zip)로 그대로 다시 읽을 수 있는 형식이다.
 *
 * 개인키가 가려지지 않은 원본이 **암호화 없이** 저장되므로, 호출하는 쪽이 사용자에게 먼저 알려야 한다.
 * 다운로드 폴더 쓰기는 MediaStore 를 쓰므로 저장소 권한이 필요 없다.
 */
object ConfExport {

    /** 내보낸 파일 이름. 실패하거나 내보낼 구성이 없으면 null. */
    fun exportToDownloads(context: Context, configs: List<VpnConfig>): String? {
        val entries = configs.mapNotNull { config ->
            SecretStore.load(context, config.id)?.let { text -> config to text }
        }
        if (entries.isEmpty()) {
            return null
        }

        val fileName = "WireSwitch-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // 쓰는 동안 다른 앱에 보이지 않게 했다가 끝나면 푼다.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    val used = mutableSetOf<String>()
                    entries.forEach { (config, text) ->
                        zip.putNextEntry(ZipEntry(uniqueName(safeName(config.tunnelName.ifBlank { config.title }), used) + ".conf"))
                        zip.write(text.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("출력 스트림을 열지 못함")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            fileName
        }
        catch (e: Exception) {
            // 반쯤 쓰인 파일이 남지 않게 지운다.
            resolver.delete(uri, null, null)
            null
        }
    }

    /** 파일명에 쓸 수 없는 문자를 바꾼다. */
    private fun safeName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "vpn" }

    /** zip 안에서 이름이 겹치지 않게 번호를 붙인다. */
    private fun uniqueName(base: String, used: MutableSet<String>): String {
        var name = base
        var n = 2
        while (!used.add(name.lowercase())) {
            name = "$base-$n"
            n++
        }
        return name
    }
}
