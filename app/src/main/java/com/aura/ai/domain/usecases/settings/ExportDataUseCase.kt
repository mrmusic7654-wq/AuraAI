package com.aura.ai.domain.usecases.settings

import javax.inject.Inject

class ExportDataUseCase @Inject constructor() {
    suspend operator fun invoke(): Result<Unit> = Result.success(Unit)
}
