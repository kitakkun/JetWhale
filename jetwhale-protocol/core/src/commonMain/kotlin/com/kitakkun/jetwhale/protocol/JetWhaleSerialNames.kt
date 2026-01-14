package com.kitakkun.jetwhale.protocol

/**
 * Serial names used in JetWhale protocol messages.
 * Caution: Editing these values breaks compatibility with older JetWhale implementations.
 */
internal object JetWhaleSerialNames {
    // negotiation/*
    const val NEGOTIATION_AGENT = "negotiation/agent"
    const val NEGOTIATION_HOST = "negotiation/host"

    // negotiation/agent/*
    const val NEGOTIATION_AGENT_SESSION = "negotiation/agent/session"
    const val NEGOTIATION_AGENT_PROTOCOL_VERSION = "negotiation/agent/protocol_version"
    const val NEGOTIATION_AGENT_CAPABILITIES = "negotiation/agent/capabilities"
    const val NEGOTIATION_AGENT_AVAILABLE_PLUGINS = "negotiation/agent/available_plugins"

    // negotiation/host/*
    const val NEGOTIATION_HOST_ACCEPT_SESSION = "negotiation/host/accept_session"
    const val NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE = "negotiation/host/protocol_version_response"
    const val NEGOTIATION_HOST_CAPABILITIES_RESPONSE = "negotiation/host/capabilities_response"
    const val NEGOTIATION_HOST_AVAILABLE_PLUGINS_RESPONSE = "negotiation/host/available_plugins_response"
    const val NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE_ACCEPT = "negotiation/host/protocol_version_response/accept"
    const val NEGOTIATION_HOST_PROTOCOL_VERSION_RESPONSE_REJECT = "negotiation/host/protocol_version_response/reject"

    // event/*
    const val EVENT_AGENT = "event/agent"
    const val EVENT_HOST = "event/host"

    // event/agent/*
    const val EVENT_AGENT_PLUGIN_MESSAGE = "event/agent/plugin_message"
    const val EVENT_AGENT_METHOD_RESULT_RESPONSE = "event/agent/method_result_response"

    // event/host/*
    const val EVENT_HOST_PLUGIN_METHOD_REQUEST = "event/host/plugin_method_request"

    // event/agent/method_result_response/*
    const val EVENT_AGENT_METHOD_RESULT_RESPONSE_SUCCESS = "event/agent/method_result_response/success"
    const val EVENT_AGENT_METHOD_RESULT_RESPONSE_FAILURE = "event/agent/method_result_response/failure"

    // model/*
    const val MODEL_PLUGIN_INFO = "model/plugin_info"
    const val MODEL_PROTOCOL_VERSION = "model/protocol_version"
}
