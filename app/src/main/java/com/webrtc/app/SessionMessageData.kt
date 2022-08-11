package com.webrtc.app

/**
 * sdp --> type, sdp
 * candidate --> type, label, id, candidate
 */
data class SessionMessageData(
    val type: String? = null,
    val sdp: String? = null,
    val label: Int = -1,
    val id: String? = null,
    val candidate: String? = null,
)