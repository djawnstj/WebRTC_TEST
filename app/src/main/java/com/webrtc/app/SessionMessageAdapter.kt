package com.webrtc.app

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class SessionMessageAdapter<T>: JsonDeserializer<T> {

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?, ): T? {

        return if (json.isJsonObject) {
            context?.deserialize(json, SessionMessageData::class.java)
        } else {
            context?.deserialize(json, String::class.java)
        }
    }

}