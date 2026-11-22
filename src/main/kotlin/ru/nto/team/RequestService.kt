package ru.nto.team

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class RequestService(
    private val staffService: StaffService,
    private val passRequestRepository: PassRequestRepository,
    private val clock: Clock,
) {
    private fun nowExact(): LocalDateTime = LocalDateTime.now(clock)

    @Transactional(readOnly = true)
    fun list(code: String, status: String?): List<RequestListDto> {
        val staff = staffService.authenticate(code)
        val filterStatus = RequestValidation.parseStatusFilter(status)

        val requests = if (filterStatus == null) {
            passRequestRepository.findAllByHostIdOrderByValidFromAscIdAsc(staff.id)
        } else {
            passRequestRepository.findAllByHostIdAndStatusOrderByValidFromAscIdAsc(staff.id, filterStatus)
        }
        return requests.map { it.toListDto() }
    }

    @Transactional(readOnly = true)
    fun details(code: String, id: Long): RequestDetailsDto {
        val staff = staffService.authenticate(code)
        val request = passRequestRepository.findByIdAndHostId(id, staff.id)
            ?: throw ApiException(404, "Request not found")
        return request.toDetailsDto()
    }

    @Transactional
    fun create(code: String, body: CreateRequestBody): RequestDetailsDto {
        val staff = staffService.authenticate(code)
        val exactNow = nowExact()
        val validated = RequestValidation.validateCreate(body, exactNow)

        val isDuplicate = passRequestRepository.existsOverlappingDuplicate(
            hostId = staff.id,
            phone = validated.phone,
            statuses = listOf(RequestStatus.PENDING, RequestStatus.APPROVED),
            newFrom = validated.validFrom,
            newTo = validated.validTo,
        )
        if (isDuplicate) {
            throw ApiException(409, "Duplicate request")
        }

        val timestamp = exactNow.truncatedTo(ChronoUnit.SECONDS)
        val entity = PassRequest(
            host = staff,
            fullName = validated.fullName,
            phone = validated.phone,
            organization = validated.organization,
            purpose = validated.purpose,
            validFrom = validated.validFrom,
            validTo = validated.validTo,
            status = RequestStatus.PENDING,
            comment = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )

        return passRequestRepository.save(entity).toDetailsDto()
    }

    @Transactional
    fun cancel(code: String, id: Long): RequestDetailsDto {
        val staff = staffService.authenticate(code)
        val request = passRequestRepository.findByIdAndHostId(id, staff.id)
            ?: throw ApiException(404, "Request not found")

        if (request.status != RequestStatus.PENDING) {
            throw ApiException(409, "Invalid status transition")
        }

        request.status = RequestStatus.CANCELLED
        request.updatedAt = nowExact().truncatedTo(ChronoUnit.SECONDS)
        return passRequestRepository.save(request).toDetailsDto()
    }

    @Transactional
    fun decide(id: Long, body: DecisionBody): RequestDetailsDto {
        val request = passRequestRepository.findById(id).orElse(null)
            ?: throw ApiException(404, "Request not found")

        val validated = RequestValidation.validateDecision(body)

        if (request.status != RequestStatus.PENDING) {
            throw ApiException(409, "Invalid status transition")
        }

        request.status = validated.status
        request.comment = validated.comment
        request.updatedAt = nowExact().truncatedTo(ChronoUnit.SECONDS)
        return passRequestRepository.save(request).toDetailsDto()
    }
}
