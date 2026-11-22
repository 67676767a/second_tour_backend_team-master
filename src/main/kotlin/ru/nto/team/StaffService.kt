package ru.nto.team

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StaffService(
    private val staffRepository: StaffRepository,
) {
    @Transactional(readOnly = true)
    fun authenticate(code: String): Staff {
        RequestValidation.validateCode(code)
        return staffRepository.findByCode(code)
            ?: throw ApiException(401, "Unauthorized")
    }

    @Transactional(readOnly = true)
    fun profile(code: String): ProfileDto {
        val staff = authenticate(code)
        return ProfileDto(name = staff.fullName)
    }
}
