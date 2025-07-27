package com.aman.agent.commands

import android.content.Context
import android.util.Log
import com.aman.agent.security.SecurityManager
import com.aman.agent.utils.CameraManager
import com.aman.agent.utils.SmsManager
import com.aman.agent.modules.ModuleManager
import com.aman.agent.data.entities.CommandLog
import com.aman.agent.AmanApplication
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * CommandProcessor - Processes and executes commands from SMS and WhatsApp
 * Handles all command types including system commands and module commands
 */
class CommandProcessor(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    companion object {
        private const val TAG = "CommandProcessor"
        private const val MAX_COMMAND_HISTORY = 100
    }

    private val commandParser = CommandParser()
    private val smsManager = SmsManager(context)
    private val cameraManager = CameraManager(context)
    private lateinit var moduleManager: ModuleManager
    
    private val pendingCommands = ConcurrentLinkedQueue<PendingCommand>()
    private val commandHistory = mutableListOf<CommandLog>()
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class PendingCommand(
        val command: String,
        val sender: String,
        val timestamp: Long,
        val source: String // "SMS" or "WhatsApp"
    )

    init {
        // Initialize module manager
        moduleManager = ModuleManager(context, securityManager)
    }

    /**
     * Process a command immediately
     */
    suspend fun processCommand(command: String, sender: String, source: String = "SMS") {
        try {
            Log.i(TAG, "Processing command: $command from $sender via $source")
            
            // Parse the command
            val parsedCommand = commandParser.parseCommand(command)
            if (parsedCommand == null) {
                sendResponse(sender, "Invalid command format", source)
                return
            }

            // Log the command
            logCommand(parsedCommand.action, sender, source)

            // Execute the command
            val result = executeCommand(parsedCommand, sender, source)
            
            // Send response if needed
            if (result.shouldRespond) {
                sendResponse(sender, result.message, source)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing command", e)
            sendResponse(sender, "Command execution failed: ${e.message}", source)
        }
    }

    /**
     * Add command to pending queue
     */
    fun addPendingCommand(command: String, sender: String, source: String = "SMS") {
        pendingCommands.offer(PendingCommand(command, sender, System.currentTimeMillis(), source))
    }

    /**
     * Process all pending commands
     */
    suspend fun processPendingCommands() {
        while (pendingCommands.isNotEmpty()) {
            val pendingCommand = pendingCommands.poll() ?: break
            
            try {
                processCommand(pendingCommand.command, pendingCommand.sender, pendingCommand.source)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing pending command", e)
            }
        }
    }

    /**
     * Process scheduled tasks
     */
    suspend fun processScheduledTasks() {
        // Implementation for scheduled/recurring tasks
        // This could include periodic status reports, cleanup tasks, etc.
    }

    private suspend fun executeCommand(
        parsedCommand: CommandParser.ParsedCommand,
        sender: String,
        source: String
    ): CommandResult {
        return when (parsedCommand.action.lowercase()) {
            "status" -> handleStatusCommand()
            "pause" -> handlePauseCommand()
            "unpause", "resume" -> handleUnpauseCommand()
            "sms" -> handleSmsCommand(parsedCommand.parameters)
            "photo", "camera" -> handlePhotoCommand(parsedCommand.parameters, sender, source)
            "add_number" -> handleAddNumberCommand(parsedCommand.parameters, sender)
            "remove_number" -> handleRemoveNumberCommand(parsedCommand.parameters, sender)
            "list_numbers" -> handleListNumbersCommand(sender)
            "learn" -> handleLearnCommand(parsedCommand.parameters)
            "load_module" -> handleLoadModuleCommand(parsedCommand.parameters, sender)
            "list_modules" -> handleListModulesCommand()
            "update" -> handleUpdateCommand(parsedCommand.parameters, sender)
            "help" -> handleHelpCommand()
            "shutdown" -> handleShutdownCommand(sender)
            else -> handleModuleCommand(parsedCommand, sender, source)
        }
    }

    private suspend fun handleStatusCommand(): CommandResult {
        val app = context.applicationContext as AmanApplication
        val status = buildString {
            appendLine("AMAN Agent Status:")
            appendLine("- Active: ${app.isAgentActive()}")
            appendLine("- Security Enhanced: ${securityManager.isEnhancedSecurityEnabled()}")
            appendLine("- Modules Loaded: ${moduleManager.getLoadedModuleCount()}")
            appendLine("- Commands Processed: ${commandHistory.size}")
        }
        return CommandResult(status, true)
    }

    private suspend fun handlePauseCommand(): CommandResult {
        securityManager.setAgentPaused(true)
        return CommandResult("AMAN Agent paused", true)
    }

    private suspend fun handleUnpauseCommand(): CommandResult {
        securityManager.setAgentPaused(false)
        return CommandResult("AMAN Agent resumed", true)
    }

    private suspend fun handleSmsCommand(parameters: Map<String, String>): CommandResult {
        val number = parameters["to"] ?: parameters["number"]
        val message = parameters["message"] ?: parameters["text"]
        
        if (number == null || message == null) {
            return CommandResult("Usage: sms to=<number> message=<text>", true)
        }

        return try {
            smsManager.sendSms(number, message)
            CommandResult("SMS sent to $number", true)
        } catch (e: Exception) {
            CommandResult("Failed to send SMS: ${e.message}", true)
        }
    }

    private suspend fun handlePhotoCommand(
        parameters: Map<String, String>,
        sender: String,
        source: String
    ): CommandResult {
        return try {
            val camera = parameters["camera"] ?: "back" // front or back
            val quality = parameters["quality"]?.toIntOrNull() ?: 80
            
            val photoFile = cameraManager.capturePhoto(camera, quality)
            
            // Send photo based on parameters
            val sendTo = parameters["send_to"] ?: sender
            val method = parameters["method"] ?: source.lowercase()
            
            when (method) {
                "sms" -> {
                    // Send photo via MMS (if supported)
                    smsManager.sendMms(sendTo, "Photo captured", photoFile)
                }
                "whatsapp" -> {
                    // This would require WhatsApp integration
                    // For now, just save and notify
                    CommandResult("Photo captured and saved. WhatsApp sending not yet implemented.", true)
                }
                else -> {
                    CommandResult("Photo captured: ${photoFile.absolutePath}", true)
                }
            }
            
            CommandResult("Photo captured and sent", true)
            
        } catch (e: Exception) {
            CommandResult("Failed to capture photo: ${e.message}", true)
        }
    }

    private suspend fun handleAddNumberCommand(parameters: Map<String, String>, sender: String): CommandResult {
        // Only master can add numbers
        if (!securityManager.isMasterNumber(sender)) {
            return CommandResult("Unauthorized: Only master can add numbers", true)
        }
        
        val number = parameters["number"]
        if (number == null) {
            return CommandResult("Usage: add_number number=<phone_number>", true)
        }
        
        return if (securityManager.addAuthorizedNumber(number)) {
            CommandResult("Number $number added to authorized list", true)
        } else {
            CommandResult("Failed to add number", true)
        }
    }

    private suspend fun handleRemoveNumberCommand(parameters: Map<String, String>, sender: String): CommandResult {
        // Only master can remove numbers
        if (!securityManager.isMasterNumber(sender)) {
            return CommandResult("Unauthorized: Only master can remove numbers", true)
        }
        
        val number = parameters["number"]
        if (number == null) {
            return CommandResult("Usage: remove_number number=<phone_number>", true)
        }
        
        return if (securityManager.removeAuthorizedNumber(number)) {
            CommandResult("Number $number removed from authorized list", true)
        } else {
            CommandResult("Failed to remove number", true)
        }
    }

    private suspend fun handleListNumbersCommand(sender: String): CommandResult {
        // Only master can list numbers
        if (!securityManager.isMasterNumber(sender)) {
            return CommandResult("Unauthorized: Only master can list numbers", true)
        }
        
        // This would require implementing a method to get authorized numbers
        return CommandResult("Authorized numbers list (implementation needed)", true)
    }

    private suspend fun handleLearnCommand(parameters: Map<String, String>): CommandResult {
        val command = parameters["command"]
        val action = parameters["action"]
        
        if (command == null || action == null) {
            return CommandResult("Usage: learn command=<name> action=<action>", true)
        }
        
        // Store learned command (implementation needed)
        return CommandResult("Command '$command' learned", true)
    }

    private suspend fun handleLoadModuleCommand(parameters: Map<String, String>, sender: String): CommandResult {
        // Only master can load modules
        if (!securityManager.isMasterNumber(sender)) {
            return CommandResult("Unauthorized: Only master can load modules", true)
        }
        
        val moduleUrl = parameters["url"]
        val moduleName = parameters["name"]
        
        if (moduleUrl == null || moduleName == null) {
            return CommandResult("Usage: load_module name=<name> url=<url>", true)
        }
        
        return try {
            moduleManager.loadModule(moduleName, moduleUrl)
            CommandResult("Module '$moduleName' loaded successfully", true)
        } catch (e: Exception) {
            CommandResult("Failed to load module: ${e.message}", true)
        }
    }

    private suspend fun handleListModulesCommand(): CommandResult {
        val modules = moduleManager.getLoadedModules()
        val moduleList = modules.joinToString("\n") { "- ${it.name}: ${it.version}" }
        return CommandResult("Loaded modules:\n$moduleList", true)
    }

    private suspend fun handleUpdateCommand(parameters: Map<String, String>, sender: String): CommandResult {
        // Only master can update
        if (!securityManager.isMasterNumber(sender)) {
            return CommandResult("Unauthorized: Only master can update", true)
        }
        
        val updateUrl = parameters["url"]
        if (updateUrl == null) {
            return CommandResult("Usage: update url=<update_url>", true)
        }
        
        // Implementation for self-update (would be complex and require careful security)
        return CommandResult("Update functionality not yet implemented", true)
    }

    private suspend fun handleHelpCommand(): CommandResult {
        val helpText = """
            AMAN Agent Commands:
            - status: Get agent status
            - pause/unpause: Pause/resume agent
            - sms to=<number> message=<text>: Send SMS
            - photo camera=<front/back> send_to=<number>: Take photo
            - add_number number=<number>: Add authorized number (master only)
            - remove_number number=<number>: Remove authorized number (master only)
            - list_numbers: List authorized numbers (master only)
            - learn command=<name> action=<action>: Learn new command
            - load_module name=<name> url=<url>: Load module (master only)
            - list_modules: List loaded modules
            - help: Show this help
        """.trimIndent()
        
        return CommandResult(helpText, true)
    }

    private suspend fun handleShutdownCommand(sender: String): CommandResult {
        // Only master can shutdown
        if (!securityManager.isMasterNumber(sender)) {
            return CommandResult("Unauthorized: Only master can shutdown", true)
        }
        
        // Perform emergency shutdown
        val app = context.applicationContext as AmanApplication
        app.emergencyShutdown()
        
        return CommandResult("AMAN Agent shutting down", true)
    }

    private suspend fun handleModuleCommand(
        parsedCommand: CommandParser.ParsedCommand,
        sender: String,
        source: String
    ): CommandResult {
        return try {
            moduleManager.executeModuleCommand(parsedCommand.action, parsedCommand.parameters, sender)
        } catch (e: Exception) {
            CommandResult("Unknown command: ${parsedCommand.action}", true)
        }
    }

    private suspend fun sendResponse(recipient: String, message: String, source: String) {
        try {
            when (source.lowercase()) {
                "sms" -> smsManager.sendSms(recipient, message)
                "whatsapp" -> {
                    // WhatsApp response would need special implementation
                    Log.i(TAG, "WhatsApp response: $message (to: $recipient)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
        }
    }

    private fun logCommand(action: String, sender: String, source: String) {
        val commandLog = CommandLog(
            action = action,
            sender = sender,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        
        commandHistory.add(commandLog)
        
        // Keep only recent commands
        if (commandHistory.size > MAX_COMMAND_HISTORY) {
            commandHistory.removeAt(0)
        }
        
        // Save to database
        processingScope.launch {
            try {
                val app = context.applicationContext as AmanApplication
                app.database.commandLogDao().insert(commandLog)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save command log", e)
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        processingScope.cancel()
        cameraManager.cleanup()
    }

    data class CommandResult(
        val message: String,
        val shouldRespond: Boolean
    )
}
