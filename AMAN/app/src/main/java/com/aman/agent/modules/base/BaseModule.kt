package com.aman.agent.modules.base

import android.content.Context

/**
 * BaseModule - Interface that all AMAN modules must implement
 * Provides standardized methods for module lifecycle and command handling
 */
interface BaseModule {

    /**
     * Initialize the module with application context
     * Called when the module is first loaded
     */
    fun initialize(context: Context?)

    /**
     * Get module version
     */
    fun getVersion(): String

    /**
     * Get module name
     */
    fun getName(): String

    /**
     * Get module description
     */
    fun getDescription(): String

    /**
     * Get list of commands this module supports
     */
    fun getSupportedCommands(): List<String>

    /**
     * Check if module can handle a specific command
     */
    fun canHandleCommand(command: String): Boolean

    /**
     * Execute a command with parameters
     * @param command The command to execute
     * @param parameters Command parameters as key-value pairs
     * @param sender The sender of the command (phone number)
     * @return Command execution result message
     */
    suspend fun executeCommand(
        command: String,
        parameters: Map<String, String>,
        sender: String
    ): String

    /**
     * Get command help text
     */
    fun getCommandHelp(command: String): String

    /**
     * Get all available help text
     */
    fun getAllHelp(): String

    /**
     * Check if module has update capability
     */
    fun hasUpdateCapability(): Boolean {
        return false
    }

    /**
     * Check for module updates
     */
    fun checkForUpdates(): UpdateInfo {
        return UpdateInfo(false, null, null)
    }

    /**
     * Get last used timestamp
     */
    fun getLastUsedTime(): Long

    /**
     * Get module configuration
     */
    fun getConfiguration(): ModuleConfiguration {
        return ModuleConfiguration()
    }

    /**
     * Update module configuration
     */
    fun updateConfiguration(config: ModuleConfiguration): Boolean {
        return false
    }

    /**
     * Get module statistics
     */
    fun getStatistics(): ModuleStatistics {
        return ModuleStatistics()
    }

    /**
     * Validate command parameters
     */
    fun validateParameters(command: String, parameters: Map<String, String>): ValidationResult {
        return ValidationResult(true, null)
    }

    /**
     * Get required permissions for this module
     */
    fun getRequiredPermissions(): List<String> {
        return emptyList()
    }

    /**
     * Check if module is enabled
     */
    fun isEnabled(): Boolean {
        return true
    }

    /**
     * Enable/disable module
     */
    fun setEnabled(enabled: Boolean): Boolean {
        return false
    }

    /**
     * Get module priority (higher number = higher priority)
     */
    fun getPriority(): Int {
        return 0
    }

    /**
     * Handle module-specific events
     */
    fun onEvent(event: ModuleEvent) {
        // Default implementation does nothing
    }

    /**
     * Cleanup module resources
     * Called when module is being unloaded
     */
    fun cleanup()

    // Data classes for module interface

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val newVersion: String?,
        val updateUrl: String?
    )

    data class ModuleConfiguration(
        val settings: MutableMap<String, Any> = mutableMapOf(),
        val isEnabled: Boolean = true,
        val priority: Int = 0
    )

    data class ModuleStatistics(
        val commandsExecuted: Long = 0,
        val lastExecutionTime: Long = 0,
        val averageExecutionTime: Long = 0,
        val errorCount: Long = 0,
        val successCount: Long = 0
    )

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )

    sealed class ModuleEvent {
        object ModuleLoaded : ModuleEvent()
        object ModuleUnloaded : ModuleEvent()
        object SystemStartup : ModuleEvent()
        object SystemShutdown : ModuleEvent()
        data class CommandExecuted(val command: String, val success: Boolean) : ModuleEvent()
        data class ConfigurationChanged(val newConfig: ModuleConfiguration) : ModuleEvent()
    }
}

/**
 * Abstract base class that provides common functionality for modules
 */
abstract class AbstractBaseModule : BaseModule {

    protected var context: Context? = null
    protected var lastUsedTime: Long = 0
    protected var configuration = BaseModule.ModuleConfiguration()
    protected var statistics = BaseModule.ModuleStatistics()
    protected var isInitialized = false

    override fun initialize(context: Context?) {
        this.context = context
        this.isInitialized = true
        this.lastUsedTime = System.currentTimeMillis()
        onInitialize()
    }

    /**
     * Override this method for custom initialization
     */
    protected open fun onInitialize() {
        // Default implementation does nothing
    }

    override fun canHandleCommand(command: String): Boolean {
        return getSupportedCommands().contains(command.lowercase())
    }

    override suspend fun executeCommand(
        command: String,
        parameters: Map<String, String>,
        sender: String
    ): String {
        if (!isInitialized) {
            throw IllegalStateException("Module not initialized")
        }

        if (!canHandleCommand(command)) {
            throw IllegalArgumentException("Command not supported: $command")
        }

        val validationResult = validateParameters(command, parameters)
        if (!validationResult.isValid) {
            throw IllegalArgumentException("Invalid parameters: ${validationResult.errorMessage}")
        }

        lastUsedTime = System.currentTimeMillis()
        
        return try {
            val result = onExecuteCommand(command, parameters, sender)
            updateStatistics(command, true)
            onEvent(BaseModule.ModuleEvent.CommandExecuted(command, true))
            result
        } catch (e: Exception) {
            updateStatistics(command, false)
            onEvent(BaseModule.ModuleEvent.CommandExecuted(command, false))
            throw e
        }
    }

    /**
     * Override this method to implement command execution
     */
    protected abstract suspend fun onExecuteCommand(
        command: String,
        parameters: Map<String, String>,
        sender: String
    ): String

    override fun getCommandHelp(command: String): String {
        return "Help for command '$command' not available"
    }

    override fun getAllHelp(): String {
        val commands = getSupportedCommands()
        return buildString {
            appendLine("${getName()} v${getVersion()}")
            appendLine(getDescription())
            appendLine()
            appendLine("Supported commands:")
            commands.forEach { command ->
                appendLine("- $command: ${getCommandHelp(command)}")
            }
        }
    }

    override fun getLastUsedTime(): Long = lastUsedTime

    override fun getConfiguration(): BaseModule.ModuleConfiguration = configuration

    override fun updateConfiguration(config: BaseModule.ModuleConfiguration): Boolean {
        return try {
            this.configuration = config
            onEvent(BaseModule.ModuleEvent.ConfigurationChanged(config))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getStatistics(): BaseModule.ModuleStatistics = statistics

    override fun isEnabled(): Boolean = configuration.isEnabled

    override fun setEnabled(enabled: Boolean): Boolean {
        configuration = configuration.copy(isEnabled = enabled)
        return true
    }

    override fun getPriority(): Int = configuration.priority

    override fun onEvent(event: BaseModule.ModuleEvent) {
        // Default implementation does nothing
    }

    override fun cleanup() {
        isInitialized = false
        context = null
        onCleanup()
    }

    /**
     * Override this method for custom cleanup
     */
    protected open fun onCleanup() {
        // Default implementation does nothing
    }

    /**
     * Update module statistics
     */
    private fun updateStatistics(command: String, success: Boolean) {
        val currentTime = System.currentTimeMillis()
        val executionTime = currentTime - lastUsedTime
        
        statistics = statistics.copy(
            commandsExecuted = statistics.commandsExecuted + 1,
            lastExecutionTime = currentTime,
            averageExecutionTime = if (statistics.commandsExecuted > 0) {
                (statistics.averageExecutionTime * (statistics.commandsExecuted - 1) + executionTime) / statistics.commandsExecuted
            } else {
                executionTime
            },
            errorCount = if (success) statistics.errorCount else statistics.errorCount + 1,
            successCount = if (success) statistics.successCount + 1 else statistics.successCount
        )
    }

    /**
     * Helper method to get context safely
     */
    protected fun requireContext(): Context {
        return context ?: throw IllegalStateException("Module context not available")
    }

    /**
     * Helper method to check if module is initialized
     */
    protected fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Module not initialized")
        }
    }
}
