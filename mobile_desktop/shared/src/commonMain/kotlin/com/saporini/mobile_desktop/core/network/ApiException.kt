package com.saporini.mobile_desktop.core.network

/** A request the server refused: its status, its message and, for a refused sign-in, why ([code]) and until when it's locked. */
class ApiException(
    val status: Int,
    override val message: String,
    val code: String? = null,
    val lockedUntil: String? = null,
    // An ended sign-in's reason (code SIGNED_OUT), and the devices to pick from (code TOO_MANY_DEVICES).
    val reason: String? = null,
    val devices: List<SignedInDevice> = emptyList()
) : Exception(message)
