package com.webrtc.app

data class PeerConnectionInfo(
    var userId:String? = null,
    var userName:String? = null,
    var groupId:String? = null,
    var groupName:String? = null,
    var videoWidth:Int? = null,      // 비디오 영상의 가로 크기
    var videoHeight:Int? = null,     // 비디오 영상의 세로 크기
    var videoEnabled:Boolean? = null,   // 비디오 허용 여부
    var audioEnabled:Boolean? = null,   // 오디오 허용 여부
    var videoType:String? = null,       // 비디오 유형   (normal:일반, screen:화면공유, camera:Probe용카메라)
    var audioType:String? = null,       // 오디오 유형   (normal:일반, stethoscope:청진기)
)