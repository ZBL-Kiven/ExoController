package com.zj.img.cache

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Callable

internal class ImageSaveTask(private val bmp: Bitmap, private val cacheFolderPath: String, private val fileName: String, private val onGot: (String) -> Unit) : Callable<Unit> {

    companion object {
        private const val SAVED = 0xa3b7e
    }

    private val handler = Handler(Looper.getMainLooper()) {
        if (it.what == SAVED) {
            onGot(it.obj.toString())
        }
        return@Handler false
    }

    override fun call() {
        val path = putImg(fileName, bmp)
        handler.sendMessage(Message.obtain().apply {
            what = SAVED
            obj = path
        })
    }

    private fun putImg(fileName: String, bmp: Bitmap): String {
        val f = File(cacheFolderPath)
        if (!f.exists()) {
            f.mkdirs()
        }
        if (!f.isDirectory) throw NoSuchFileException(f, null, "the cached folder was not found ,is your cache directory path was point to a file?")

        val file = File(f, fileName)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
                if (file.isDirectory) f.delete()
            }
        }
        val fos = FileOutputStream(file)
        return try {
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            fos.flush()
            file.path
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
    }
}