package com.ebookvibe.tvreader.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/** A top-level place the file browser can start from. */
data class StorageRoot(val label: String, val dir: File, val removable: Boolean)

data class FileEntry(val file: File, val isDirectory: Boolean, val sizeBytes: Long)

object Storage {

    /**
     * Storage permission model by Android version:
     *  - 8.0 – 10 (API 26–29): READ_EXTERNAL_STORAGE runtime permission (+ legacy storage on 10).
     *  - 11 – 14 (API 30–34): scoped storage. READ_EXTERNAL_STORAGE / READ_MEDIA_* only expose
     *    images, video and audio – never PDFs – so a file browser needs "All files access"
     *    (MANAGE_EXTERNAL_STORAGE), granted from a system settings screen.
     */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    val needsAllFilesAccess: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Opens the "All files access" settings page. Some TV builds strip it out, so fall back to
     * the global list, then to the app-details page. Returns false if nothing could be opened.
     */
    fun openAllFilesAccessSettings(context: Context): Boolean {
        val pkg = Uri.parse("package:${context.packageName}")
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
                add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun adbGrantCommand(context: Context): String =
        if (needsAllFilesAccess) "adb shell appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        else "adb shell pm grant ${context.packageName} android.permission.READ_EXTERNAL_STORAGE"

    /** Internal storage, Downloads, Documents and every mounted removable volume (USB / SD). */
    fun roots(context: Context): List<StorageRoot> {
        val result = LinkedHashMap<String, StorageRoot>()
        fun add(label: String, dir: File?, removable: Boolean) {
            if (dir == null || !dir.isDirectory) return
            val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (key !in result) result[key] = StorageRoot(label, dir, removable)
        }

        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        @Suppress("DEPRECATION")
        add("Downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), false)
        @Suppress("DEPRECATION")
        add("Documents", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), false)
        add("Internal storage", primary, false)

        // Removable volumes reported by the framework.
        val sm = context.getSystemService(StorageManager::class.java)
        sm?.storageVolumes?.forEach { volume ->
            if (!volume.isPrimary && volume.state == Environment.MEDIA_MOUNTED) {
                add(volume.getDescription(context) ?: "USB drive", volumeDir(volume), true)
            }
        }
        // App-specific dirs reveal the mount point of secondary volumes on older builds.
        context.getExternalFilesDirs(null).filterNotNull().drop(1).forEach { appDir ->
            val root = appDir.absolutePath.substringBefore("/Android/data")
            add("USB drive (${File(root).name})", File(root), true)
        }
        // Many TV boxes mount USB sticks under /storage/<UUID> or /mnt/usb/... without
        // announcing them as StorageVolumes; probe the usual places.
        listOf("/storage", "/mnt/usb", "/mnt/usb_storage", "/mnt/media_rw").forEach { base ->
            File(base).listFiles()?.forEach { dir ->
                val name = dir.name
                if (name != "emulated" && name != "self" && !name.startsWith(".") && dir.canRead()) {
                    add("USB drive ($name)", dir, true)
                }
            }
        }
        return result.values.toList()
    }

    private fun volumeDir(volume: StorageVolume): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            // StorageVolume.getPathFile() is hidden before API 30 but present on every build.
            runCatching { volume.javaClass.getMethod("getPathFile").invoke(volume) as File? }.getOrNull()
                ?: runCatching { File(volume.javaClass.getMethod("getPath").invoke(volume) as String) }.getOrNull()
        }

    /** Folders first, then PDFs, both alphabetical. Hidden entries and other file types are skipped. */
    fun list(dir: File): List<FileEntry> {
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { !it.name.startsWith(".") }
            .filter { it.isDirectory || it.name.endsWith(".pdf", ignoreCase = true) }
            .map { FileEntry(it, it.isDirectory, if (it.isDirectory) 0 else it.length()) }
            .sortedWith(compareBy<FileEntry>({ !it.isDirectory }, { it.file.name.lowercase() }))
            .toList()
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%d KB".format(bytes / (1L shl 10))
        else -> "$bytes B"
    }
}
