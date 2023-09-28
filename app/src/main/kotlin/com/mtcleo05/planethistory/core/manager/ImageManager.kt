package com.mtcleo05.planethistory.core.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources

class ImageManager {
    fun bitmapFromDrawableRes(context: Context, @DrawableRes resId: Int): Bitmap? {
        val drawable: Drawable? = AppCompatResources.getDrawable(context, resId)
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap: Bitmap? = if ((drawable?.intrinsicWidth ?: 0) > 0 && (drawable?.intrinsicHeight
                ?: 0) > 0
        ) {
            Bitmap.createBitmap(
                drawable?.intrinsicWidth ?: 0,
                drawable?.intrinsicHeight ?: 0,
                Bitmap.Config.ARGB_8888
            )
        } else {
            null
        }

        if (bitmap != null) {
            val canvas = Canvas(bitmap)
            drawable?.setBounds(0, 0, canvas.width, canvas.height)
            drawable?.draw(canvas)
        }

        return bitmap
    }
}