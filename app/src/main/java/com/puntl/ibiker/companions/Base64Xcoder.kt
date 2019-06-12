package com.puntl.ibiker.companions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File

class Base64Xcoder {
    companion object {
        fun imagePathToString(filePath: String): String {
            val bytes = File(filePath).readBytes()
            return Base64.encodeToString(bytes, Base64.DEFAULT)
        }

        fun stringToImage(imageString: String, options: BitmapFactory.Options): Bitmap {
            val bytes = Base64.decode(imageString, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}