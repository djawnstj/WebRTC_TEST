package com.webrtc.app

import com.google.gson.annotations.JsonAdapter

data class SessionMessage(
    val sessionId: String? = null,
    val requestCode: String? = null,
    val userId: String? = null,
    val sender: String? = null,
    val receiver: String? = null,
    val command: String? = null,
    val method: String? = null,
    val code: String? = null,
    val category: String? = null,
    var direction: String? = null,

    @JsonAdapter(SessionMessageAdapter::class)
    val data: Any? = null,

    val userName: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val videoWidth: String? = null,
    val videoHeight: String? = null,
    val videoEnabled: String? = null,
    val audioEnabled: String? = null,
    val videoType: String? = null,
    val audioType: String? = null,
)