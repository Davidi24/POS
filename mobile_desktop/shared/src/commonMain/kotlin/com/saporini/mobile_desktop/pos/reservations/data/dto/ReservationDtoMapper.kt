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
    primaryTableId = primaryTableId,
    attendanceConfirmed = attendanceConfirmed,
    occasionCode = occasionCode,
    occasionOptions = occasionOptions,
    occasionNote = occasionNote
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
    primaryTableId = primaryTableId,
    occasionCode = occasionCode,
    occasionOptions = occasionOptions,
    occasionNote = occasionNote
)

internal fun ReservationActionInput.toDto(): ReservationActionRequestDto = ReservationActionRequestDto(
    reason = reason,
    arrivedGuests = arrivedGuests
)

internal fun ReservationSeatingCheckResponseDto.toDomain(): ReservationSeatingCheck = ReservationSeatingCheck(
    minutesLeft = minutesLeft ?: 0,
    nextBookingStart = nextBookingStart,
    nextBookingName = nextBookingName,
    fitsAtTables = fitsAtTables == true,
    alternatives = alternatives.map { it.toDomain() }
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
    expiredAt = expiredAt,
    holdUntil = holdUntil,
    arrivedGuests = arrivedGuests,
    needsReview = needsReview == true,
    reviewReason = reviewReason,
    attendanceConfirmedAt = attendanceConfirmedAt,
    attendanceConfirmedVia = attendanceConfirmedVia,
    attendance = attendance,
    guestNoShows = guestNoShows,
    occasionCode = occasionCode,
    occasionName = occasionName,
    occasionIcon = occasionIcon,
    occasionOptions = occasionOptions.orEmpty(),
    occasionNote = occasionNote,
    eventName = eventName,
    eventIcon = eventIcon,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tableAssignments = tableAssignments.orEmpty().map { it.toDomain() }
)

internal fun ReservationDetailsResponseDto.toDomain(): ReservationDetails = ReservationDetails(
    reservation = reservation.toDomain(),
    audit = audit?.toDomain() ?: ReservationAudit(reservationId = reservation.id),
    timeline = timeline.map { it.toDomain() },
    deposit = deposit?.toDomain() ?: ReservationDeposit(reservationId = reservation.id)
)

internal fun ReservationPageResponseDto.toDomain(): ReservationPage = ReservationPage(
    items = items.map { it.toDomain() },
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
    hasNext = hasNext,
    hasPrevious = hasPrevious
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
    upcomingCount = upcomingCount ?: 0,
    presentGuests = presentGuests,
    guestsToArrive = guestsToArrive,
    unassignedCount = unassignedCount,
    arrivingSoonCount = arrivingSoonCount,
    arrivingSoonGuests = arrivingSoonGuests,
    expiredCount = expiredCount,
    needsReviewCount = needsReviewCount,
    attendanceNotConfirmedCount = attendanceNotConfirmedCount,
    notConfirmedDueCount = notConfirmedDueCount,
    bigGroupCount = bigGroupCount
)

internal fun GuestHistoryResponseDto.toDomain(): GuestHistory = GuestHistory(
    noShowCount = noShowCount ?: 0,
    warningFrom = warningFrom ?: 1,
    noShows = noShows.map { GuestNoShow(it.reservationId, it.reservationCode, it.reservationStart, it.partySize) },
    clears = clears.map { GuestNoShowClear(it.clearedAt, it.reason) }
)

internal fun WaitlistEntryResponseDto.toDomain(): WaitlistEntry = WaitlistEntry(
    id = id,
    guestName = guestName,
    contactPhone = contactPhone,
    partySize = partySize,
    note = note,
    createdAt = createdAt,
    waitedMinutes = waitedMinutes ?: 0,
    tableFreeNow = tableFreeNow == true,
    tableFreeAround = tableFreeAround
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

internal fun ReservationOccasionDto.toDomain(): ReservationOccasion =
    ReservationOccasion(code = code.orEmpty(), name = name, icon = icon, options = options, active = active != false)

internal fun ReservationOccasion.toDto(): ReservationOccasionDto =
    ReservationOccasionDto(code = code.ifBlank { null }, name = name, icon = icon, options = options, active = active)

internal fun RestaurantEventDto.toDomain(): RestaurantEvent = RestaurantEvent(
    id = id, name = name, icon = icon, startDate = startDate, endDate = endDate, menuId = menuId, menuName = menuName,
    specialMenuOnly = specialMenuOnly == true, active = active != false
)

internal fun RestaurantEvent.toDto(): RestaurantEventDto = RestaurantEventDto(
    id = id, name = name, icon = icon, startDate = startDate, endDate = endDate, menuId = menuId,
    specialMenuOnly = specialMenuOnly, active = active
)

internal fun MoneyLineDto.toDomain(): MoneyLine = MoneyLine(
    id = id,
    kind = kind,
    description = description?.takeIf(String::isNotBlank) ?: when (kind) {
        "DEPOSIT" -> "Deposit"
        "PRE_ORDER" -> "Food pre-order"
        else -> "Extra"
    },
    amountCents = moneyCents(amount?.value) ?: 0,
    currency = currency ?: "EUR",
    status = status,
    refundDeadline = refundDeadline,
    refundedCents = moneyCents(refundedAmount?.value) ?: 0,
    refundIfCancelledNowCents = moneyCents(refundIfCancelledNow?.value) ?: 0,
    explanation = explanation
)

internal fun BookingExtraChoiceDto.toDomain(): BookingExtraChoice = BookingExtraChoice(
    menuItemId = menuItemId,
    name = name,
    description = description,
    priceCents = moneyCents(price?.value) ?: 0,
    currency = currency ?: "EUR",
    orderBeforeHours = orderBeforeHours
)
