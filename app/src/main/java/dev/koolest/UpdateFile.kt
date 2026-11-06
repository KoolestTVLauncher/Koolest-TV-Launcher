package dev.koolest

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the downloaded update to the TV's own installer, for TVs with no install confirm screen (see installStatus).
 * Not exported: the installer can read it only through the one-time grant on the intent, and only this one file.
 */
class UpdateFile : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.lastPathSegment != NAME) throw FileNotFoundException(uri.toString())
        val f = file(context!!)
        if (!f.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = APK_TYPE
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<String>?) = 0

    companion object {
        private const val NAME = "Koolest.apk"
        const val APK_TYPE = "application/vnd.android.package-archive"
        fun file(c: Context) = File(c.cacheDir, NAME)
        fun uri(c: Context): Uri = Uri.parse("content://${c.packageName}.update/$NAME")
    }
}
