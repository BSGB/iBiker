package com.puntl.ibiker.models

data class UserComment(var userId: String,
                       var publishTimeStamp: Long,
                       var commentText: String,
                       var rating: Float)