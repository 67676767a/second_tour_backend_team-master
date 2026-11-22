package ru.nto.team

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ValidatedCreateRequest(
    val fullName: String,
    val phone: String,
    val organization: String?,
    val purpose: String,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
)

data class ValidatedDecision(
    val status: RequestStatus,
    val comment: String?,
)

object RequestValidation {
    private val CODE_REGEX = Regex("^[A-Za-z0-9]{4}$")
    private val PHONE_REGEX = Regex("^\\+7[0-9]{10}$")
    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT)

    fun validateCode(code: String) {
        if (!CODE_REGEX.matches(code)) {
            throw ApiException(400, "Invalid code format")
        }
    }

    fun parseStatusFilter(rawStatus: String?): RequestStatus? {
        if (rawStatus == null) return null
        return when (rawStatus) {
            "PENDING" -> RequestStatus.PENDING
            "APPROVED" -> RequestStatus.APPROVED
            "REJECTED" -> RequestStatus.REJECTED
            "CANCELLED" -> RequestStatus.CANCELLED
            else -> throw ApiException(400, "Invalid status")
        }
    }

    fun parseStrictDateTime(raw: String?): LocalDateTime? {
        if (raw == null || raw.length != 19) return null
        return try {
            LocalDateTime.parse(raw, DATE_TIME_FORMATTER)
        } catch (_: Exception) {
            null
        }
    }

    fun validateCreate(body: CreateRequestBody, now: LocalDateTime): ValidatedCreateRequest {
        val errors = linkedMapOf<String, String>()

        val fullName = body.fullName?.trim().orEmpty()
        if (fullName.length !in 5..150) {
            errors["fullName"] = "Full name must be between 5 and 150 characters"
        }

        val phone = body.phone?.trim().orEmpty()
        if (!PHONE_REGEX.matches(phone)) {
            errors["phone"] = "Phone must match +7 followed by 10 digits"
        }

        val trimmedOrg = body.organization?.trim()
        val organization = if (trimmedOrg.isNullOrEmpty()) null else trimmedOrg
        if (organization != null && organization.length > 150) {
            errors["organization"] = "Organization must not exceed 150 characters"
        }

        val purpose = body.purpose?.trim().orEmpty()
        if (purpose.length !in 5..200) {
            errors["purpose"] = "Purpose must be between 5 and 200 characters"
        }

        val trimmedFrom = body.validFrom?.trim()
        val parsedFrom = parseStrictDateTime(trimmedFrom)
        if (parsedFrom == null) {
            errors["validFrom"] = "validFrom must be a valid datetime in format yyyy-MM-dd'T'HH:mm:ss"
        } else if (parsedFrom.isBefore(now)) {
            errors["validFrom"] = "validFrom must not be earlier than current time"
        }

        val trimmedTo = body.validTo?.trim()
        val parsedTo = parseStrictDateTime(trimmedTo)
        if (parsedTo == null) {
            errors["validTo"] = "validTo must be a valid datetime in format yyyy-MM-dd'T'HH:mm:ss"
        } else if (parsedFrom != null) {
            if (!parsedTo.isAfter(parsedFrom) || parsedTo.toLocalDate() != parsedFrom.toLocalDate()) {
                errors["validTo"] = "validTo must be after validFrom and on the same calendar day"
            }
        }

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }

        return ValidatedCreateRequest(
            fullName = fullName,
            phone = phone,
            organization = organization,
            purpose = purpose,
            validFrom = parsedFrom!!,
            validTo = parsedTo!!,
        )
    }

    fun validateDecision(body: DecisionBody): ValidatedDecision {
        val errors = linkedMapOf<String, String>()

        val trimmedStatus = body.status?.trim()
        val targetStatus = when (trimmedStatus) {
            "APPROVED" -> RequestStatus.APPROVED
            "REJECTED" -> RequestStatus.REJECTED
            else -> {
                errors["status"] = "Status must be APPROVED or REJECTED"
                null
            }
        }

        val trimmedComment = body.comment?.trim()
        var finalComment: String? = null

        if (targetStatus == RequestStatus.REJECTED) {
            if (trimmedComment.isNullOrEmpty() || trimmedComment.length > 500) {
                errors["comment"] = "Comment is required for REJECTED and must be 1..500 characters"
            } else {
                finalComment = trimmedComment
            }
        } else if (targetStatus == RequestStatus.APPROVED) {
            finalComment = null
        }

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }

        return ValidatedDecision(
            status = targetStatus!!,
            comment = finalComment,
        )
    }
}
