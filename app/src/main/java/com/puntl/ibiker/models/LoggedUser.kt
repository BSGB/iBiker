package com.puntl.ibiker.models

data class LoggedUser(var userId: String,
                      var userEmail: String,
                      var expiresAt: Long)