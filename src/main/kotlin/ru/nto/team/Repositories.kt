package ru.nto.team

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface StaffRepository : JpaRepository<Staff, Long> {
    fun findByCode(code: String): Staff?
}

interface PassRequestRepository : JpaRepository<PassRequest, Long> {
    fun findAllByHostIdOrderByValidFromAscIdAsc(hostId: Long): List<PassRequest>

    fun findAllByHostIdAndStatusOrderByValidFromAscIdAsc(
        hostId: Long,
        status: RequestStatus,
    ): List<PassRequest>

    fun findByIdAndHostId(id: Long, hostId: Long): PassRequest?

    @Query(
        """
        SELECT COUNT(r) > 0
        FROM PassRequest r
        WHERE r.host.id = :hostId
          AND r.phone = :phone
          AND r.status IN :statuses
          AND r.validFrom < :newTo
          AND :newFrom < r.validTo
        """
    )
    fun existsOverlappingDuplicate(
        @Param("hostId") hostId: Long,
        @Param("phone") phone: String,
        @Param("statuses") statuses: Collection<RequestStatus>,
        @Param("newFrom") newFrom: LocalDateTime,
        @Param("newTo") newTo: LocalDateTime,
    ): Boolean
}
