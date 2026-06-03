package com.speakmind.app.feature.profile.domain

sealed class NameValidationResult {
    object Valid : NameValidationResult()
    object TooShort : NameValidationResult()
    object TooLong : NameValidationResult()
    object InvalidChars : NameValidationResult()
}

object NameValidator {
    private val VALID_CHARS = Regex("^[a-zA-Z '\\-]+$")

    fun validate(name: String): NameValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.length < 2 -> NameValidationResult.TooShort
            trimmed.length > 30 -> NameValidationResult.TooLong
            !trimmed.matches(VALID_CHARS) -> NameValidationResult.InvalidChars
            else -> NameValidationResult.Valid
        }
    }

    fun errorMessage(result: NameValidationResult): String? = when (result) {
        NameValidationResult.Valid -> null
        NameValidationResult.TooShort -> "Name must be at least 2 characters"
        NameValidationResult.TooLong -> "Name must be 30 characters or less"
        NameValidationResult.InvalidChars -> "Only letters, spaces, hyphens, and apostrophes allowed"
    }
}
