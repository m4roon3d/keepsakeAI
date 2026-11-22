package com.keepsakeai.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Photo helpers shared by lens, the home gallery button and the share sheet. */
object ImagePrep {

    fun snapsDir(context: Context) = File(context.filesDir, "snaps").apply { mkdirs() }

    fun newSnapFile(context: Context) = File(snapsDir(context), "snap_${System.currentTimeMillis()}.jpg")

    /** Copies a picked/shared image into trove's own folder (picker access is temporary). */
    fun importToSnaps(context: Context, source: Uri): Uri? = runCatching {
        if (source.scheme == "file") return source
        val dest = newSnapFile(context)
        // Prefer the unredacted original so the copy keeps the photo's GPS location.
        val from = MediaMeta.originalUri(context, source)
        val stream = runCatching { context.contentResolver.openInputStream(from) }.getOrNull() ?: context.contentResolver.openInputStream(source)
        stream?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: return null
        Uri.fromFile(dest)
    }.getOrNull()

    /** Upright JPEG no larger than [maxSide] px, small enough to send to Gemini quickly. */
    fun jpegForAi(context: Context, uri: Uri, maxSide: Int = 1280, quality: Int = 82): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        ByteArrayOutputStream().use { o -> out.compress(Bitmap.CompressFormat.JPEG, quality, o); o.toByteArray() }
    }.getOrNull()
}
