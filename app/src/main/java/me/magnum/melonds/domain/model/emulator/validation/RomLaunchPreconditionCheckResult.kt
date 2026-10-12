package me.magnum.melonds.domain.model.emulator.validation

import me.magnum.melonds.domain.model.ConfigurationDirResult
import me.magnum.melonds.domain.model.rom.Rom

sealed class RomLaunchPreconditionCheckResult {
    data class Success(val rom: Rom) : RomLaunchPreconditionCheckResult()
    data class BiosConfigurationIncorrect(val configurationDirectoryResult: ConfigurationDirResult) : RomLaunchPreconditionCheckResult()
}