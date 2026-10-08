package com.saporini.mobile_desktop.admin.people.ui

import com.saporini.mobile_desktop.admin.people.RoleDto
import com.saporini.mobile_desktop.core.format.humanize

/** A role's name for people ("Head waiter"), falling back to its code. */
internal fun RoleDto.label(): String = name.ifBlank { humanize(code) }
