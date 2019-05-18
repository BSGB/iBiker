package com.puntl.ibiker.models

data class LoggedUser(var userID: String,
                      var userEmail: String,
                      var expiresAt: Long)