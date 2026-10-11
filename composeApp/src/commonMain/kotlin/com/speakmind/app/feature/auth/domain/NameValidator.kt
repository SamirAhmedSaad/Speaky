package com.speakmind.app.feature.auth.domain

data class NameValidationResult(
    val isValid: Boolean,
    val reason: String = "",
)

object NameValidator {

    private val URL_PATTERN = Regex("https?://|www\\.|\\.(com|net|org|io)")
    private val EMAIL_PATTERN = Regex("[^@]+@[^@]+\\.[^@]+")
    private val ALL_NUMBERS = Regex("^[0-9]+$")
    private val ONLY_SPECIAL_CHARS = Regex("^[^\\p{L}]+$")

    fun validate(name: String): NameValidationResult {
        val trimmed = name.trim()

        if (trimmed.length < 2) {
            return NameValidationResult(false, "Name must be at least 2 characters")
        }

        if (trimmed.length > 50) {
            return NameValidationResult(false, "Name must be 50 characters or less")
        }

        if (URL_PATTERN.containsMatchIn(trimmed)) {
            return NameValidationResult(false, "Name cannot contain URLs")
        }

        if (EMAIL_PATTERN.matches(trimmed)) {
            return NameValidationResult(false, "Please enter your name, not email")
        }

        if (ALL_NUMBERS.matches(trimmed)) {
            return NameValidationResult(false, "Name cannot be only numbers")
        }

        if (ONLY_SPECIAL_CHARS.matches(trimmed)) {
            return NameValidationResult(false, "Name must contain letters")
        }

        return NameValidationResult(true)
    }
}
