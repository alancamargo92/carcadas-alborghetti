package com.ukdev.carcadasalborghetti.model

import android.net.Uri

data class Media(
        val title: String,
        val length: String,
        var position: Int,
        val uri: Uri
)