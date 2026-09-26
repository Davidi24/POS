package com.saporini.mobile_desktop.pos.reservations.data.dto

import com.saporini.mobile_desktop.pos.reservations.domain.model.*

internal fun ReservationInput.toDto(): ReservationRequestDto = ReservationRequestDto(
    branchId = branchId,
    customerId = customerId,
    source = source,
    partySize = partySize,
    reservationStart = reservationStart,
    reservationEnd = reservationEnd,
    contactName = contactName,
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    seatingPreference = seatingPreference,
    specialRequests = specialRequests,
    internalNotes = internalNotes,
    depositRequired = depositRequired,
    depositAmount = depositAmount,
    initialTableIds = initialTableIds,
    primaryTableId = primaryTableId
)

internal fun UpdateReservationInput.toDto(): UpdateReservationRequestDto = UpdateReservationRequestDto(
    branchId = branchId,
    customerId = customerId,
    source = source,
    partySize = partySize,
    reservationStart = reservationStart,
    reservationEnd = reservationEnd,
    contactName = contactName,
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    seatingPreference = seatingPreference,
    specialRequests = specialRequests,
    internalNotes = internalNotes,
    depositRequired = depositRequired,
    depositAmount = depositAmount,
    tableIds = tableIds,
    primaryTableId = primaryTableId
)

internal fun ReservationActionInput.toDto(): ReservationActionRequestDto = ReservationActionRequestDto(
    reason = reason
)

internal fun ReservationTablesInput.toDto(): UpdateReservationTablesRequestDto = UpdateReservationTablesRequestDto(
    tableIds = tableIds,
    primaryTableId = primaryTableId
)

internal fun ReservationDepositInput.toDto(): UpdateReservationDepositRequestDto = UpdateReservationDepositRequestDto(
    depositRequired = depositRequired,
    depositAmount = depositAmount
)

internal fun ReservationAvailabilitySearchInput.toDto(): ReservationAvailabilitySearchRequestDto = ReservationAvailabilitySearchRequestDto(
    reservationStart = reservationStart,
    reservationEnd = reservationEnd,
    partySize = partySize,
    maxOptions = maxOptions
)

internal fun ReservationValidationInput.toDto(): ReservationValidationRequestDto = ReservationValidationRequestDto(
    reservationStart = reservationStart,
    reservationEnd = reservationEnd,
    partySize = partySize,
    tableIds = tableIds,
    primaryTableId = primaryTableId
)

internal fun ReservationResponseDto.toDomain(): Reservation = Reservation(
    id = id,
    restaurantId = restaurantId,
    branchId = branchId,
    customerId = customerId,
    customerCode = customerCode,
    customerName = customerName,
    reservationCode = reservationCode,
    source = source,
    status = status,
    partySize = partySize,
    reservationStart = reservationStart,
    reservationEnd = reservationEnd,
    contactName = contactName,
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    seatingPreference = seatingPreference,
    specialRequests = specialRequests,
    internalNotes = internalNotes,
    depositRequired = depositRequired,
    depositAmount = depositAmount,
    depositStatus = depositStatus,
    confirmedAt = confirmedAt,
    cancelledAt = cancelledAt,
    cancellationReason = cancellationReason,
    checkedInAt = checkedInAt,
    seatedAt = seatedAt,
    completedAt = completedAt,
    noShowAt = noShowAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tableAssignments = tableAssignments.orEmpty().map { it.toDomain() }
)

internal fun ReservationTableAssignmentResponseDto.toDomain(): ReservationTableAssignment = ReservationTableAssignment(
    assignmentId = assignmentId,
    tableId = tableId,
    tableNumber = tableNumber,
    tableName = tableName,
    floor = floor,
    capacity = capacity,
    primary = primary,
    assignedAt = assignedAt,
    assignedBy = assignedBy
)

internal fun ReservationNoteResponseDto.toDomain(): ReservationNote = ReservationNote(
    id = id,
    note = note,
    createdBy = createdBy,
    createdByName = createdByName,
    updatedBy = updatedBy,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ReservationDepositResponseDto.toDomain(): ReservationDeposit = ReservationDeposit(
    reservationId = reservationId,
    depositRequired = depositRequired,
    depositAmount = depositAmount,
    depositStatus = depositStatus
)

internal fun ReservationAvailabilityOptionResponseDto.toDomain(): ReservationAvailabilityOption = ReservationAvailabilityOption(
    tableIds = tableIds.orEmpty(),
    tableNumbers = tableNumbers.orEmpty(),
    primaryTableId = primaryTableId,
    tableCount = tableCount,
    totalCapacity = totalCapacity,
    exactFit = exactFit
)

internal fun ReservationValidationResponseDto.toDomain(): ReservationValidation = ReservationValidation(
    valid = valid == true,
    errors = errors.orEmpty(),
    warnings = warnings.orEmpty(),
    suggestions = suggestions.orEmpty().map { it.toDomain() }
)

internal fun ReservationSummaryResponseDto.toDomain(): ReservationSummary = ReservationSummary(
    branchId = branchId,
    from = from,
    to = to,
    totalReservations = totalReservations ?: 0,
    totalGuests = totalGuests ?: 0,
    pendingCount = pendingCount ?: 0,
    confirmedCount = confirmedCount ?: 0,
    checkedInCount = checkedInCount ?: 0,
    seatedCount = seatedCount ?: 0,
    completedCount = completedCount ?: 0,
    cancelledCount = cancelledCount ?: 0,
    noShowCount = noShowCount ?: 0,
    upcomingCount = upcomingCount ?: 0
)

internal fun ReservationCapacityResponseDto.toDomain(): ReservationCapacity = ReservationCapacity(
    branchId = branchId,
    from = from,
    to = to,
    totalRootTables = totalRootTables ?: 0,
    availableRootTables = availableRootTables ?: 0,
    totalSeats = totalSeats ?: 0,
    availableSeats = availableSeats ?: 0,
    maxSingleTableCapacity = maxSingleTableCapacity,
    maxAvailableTableCapacity = maxAvailableTableCapacity,
    requestedPartySize = requestedPartySize,
    canAccommodateRequestedPartySize = canAccommodateRequestedPartySize
)

internal fun ReservationStatusHistoryResponseDto.toDomain(): ReservationStatusHistory = ReservationStatusHistory(
    id = id,
    oldStatus = oldStatus,
    newStatus = newStatus,
    reason = reason,
    changedBy = changedBy,
    changedAt = changedAt
)

internal fun ReservationTimelineEventResponseDto.toDomain(): ReservationTimelineEvent = ReservationTimelineEvent(
    id = id,
    type = type,
    occurredAt = occurredAt,
    actorId = actorId,
    message = message,
    oldStatus = oldStatus,
    newStatus = newStatus,
    relatedTableId = relatedTableId,
    relatedNoteId = relatedNoteId
)

internal fun ReservationAuditResponseDto.toDomain(): ReservationAudit = ReservationAudit(
    reservationId = reservationId,
    createdAt = createdAt,
    createdBy = createdBy,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
    statusHistory = statusHistory.orEmpty().map { it.toDomain() },
    notes = notes.orEmpty().map { it.toDomain() },
    tableAssignments = tableAssignments.orEmpty().map { it.toDomain() }
)
