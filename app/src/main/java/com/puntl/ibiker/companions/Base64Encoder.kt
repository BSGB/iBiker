package com.puntl.ibiker.companions

import android.util.Base64
import java.io.File

class Base64Encoder {
    companion object {
        fun imageToString(filePath: String): String {
            val bytes = File(filePath).readBytes()
            return Base64.encodeToString(bytes, Base64.DEFAULT)
        }
    }
}