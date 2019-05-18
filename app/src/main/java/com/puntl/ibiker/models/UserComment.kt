package com.puntl.ibiker.models

data class UserComment(var userID: String,
                       var publishTimeStamp: Long,
                       var commentText: String,
                       var rating: Byte)