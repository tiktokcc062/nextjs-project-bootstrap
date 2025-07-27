package com.aman.agent.modules

import android.content.Context
import android.util.Log
import com.aman.agent.modules.base.BaseModule
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import java.io.File
import java.security.Permission
import java.security.SecurityManager
import java.util.concurrent.TimeUnit

/**
 * ModuleSandbox - Provides secure testing environment for modules
 * Tests modules in isolation before allowing them to be loaded
 */
class ModuleSandbox(private val context: Context) {

    companion object {
        private const val TAG = "ModuleSandbox"
        private const val SANDBOX_TIMEOUT = 15000L // 15 seconds
        private const val MAX_MEMORY_MB = 50 // 50MB limit for sandbox
    }

    data class SandboxTestResult(
        val isValid: Boolean,
        val error: String? = null,
        val warnings: List<String> = emptyList(),
        val memoryUsage: Long = 0,
        val executionTime: Long = 0
    )

    /**
     * Test module in secure sandbox environment
     */
    suspend fun testModule(moduleFile: File): SandboxTestResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()
        
        try {
            Log.i(TAG, "Testing module in sandbox: ${moduleFile.name}")

            // Create isolated environment
            val sandboxResult = withTimeout(SANDBOX_TIMEOUT) {
                runInSandbox(moduleFile, warnings)
            }

            val executionTime = System.currentTimeMillis() - startTime
            
            SandboxTestResult(
                isValid = sandboxResult.success,
                error = sandboxResult.error,
                warnings = warnings,
                memoryUsage = sandboxResult.memoryUsed,
                executionTime = executionTime
            )

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Sandbox test timeout for: ${moduleFile.name}")
            SandboxTestResult(
                isValid = false,
                error = "Module test timeout - possible infinite loop or blocking operation",
                executionTime = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sandbox test failed", e)
            SandboxTestResult(
                isValid = false,
                error = "Sandbox test failed: ${e.message}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Run module test in isolated environment
     */
    private suspend fun runInSandbox(
        moduleFile: File,
        warnings: MutableList<String>
    ): SandboxResult = withContext(Dispatchers.Default) {
        
        val memoryBefore = getUsedMemory()
        
        try {
            // Create sandbox directory
            val sandboxDir = createSandboxDirectory()
            
            // Install custom security manager
            val originalSecurityManager = System.getSecurityManager()
            val sandboxSecurityManager = SandboxSecurityManager(warnings)
            
            try {
                // Set sandbox security manager
                System.setSecurityManager(sandboxSecurityManager)
                
                // Test module loading
                val testResult = testModuleLoading(moduleFile, sandboxDir, warnings)
                
                val memoryAfter = getUsedMemory()
                val memoryUsed = memoryAfter - memoryBefore
                
                // Check memory usage
                if (memoryUsed > MAX_MEMORY_MB * 1024 * 1024) {
                    warnings.add("High memory usage detected: ${memoryUsed / 1024 / 1024}MB")
                }
                
                SandboxResult(
                    success = testResult,
                    memoryUsed = memoryUsed,
                    error = null
                )
                
            } finally {
                // Restore original security manager
                System.setSecurityManager(originalSecurityManager)
                
                // Cleanup sandbox directory
                cleanupSandboxDirectory(sandboxDir)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sandbox execution failed", e)
            SandboxResult(
                success = false,
                memoryUsed = 0,
                error = e.message
            )
        }
    }

    /**
     * Test module loading and basic functionality
     */
    private fun testModuleLoading(
        moduleFile: File,
        sandboxDir: File,
        warnings: MutableList<String>
    ): Boolean {
        return try {
            // Create class loader in sandbox
            val classLoader = DexClassLoader(
                moduleFile.absolutePath,
                sandboxDir.absolutePath,
                null,
                context.classLoader
            )

            // Try to find and load module class
            val moduleClass = findModuleClass(classLoader, moduleFile.nameWithoutExtension)
            
            if (moduleClass == null) {
                warnings.add("No valid module class found")
                return false
            }

            // Verify interface implementation
            if (!BaseModule::class.java.isAssignableFrom(moduleClass)) {
                warnings.add("Module does not implement BaseModule interface")
                return false
            }

            // Test instantiation
            val moduleInstance = moduleClass.getDeclaredConstructor().newInstance() as BaseModule
            
            // Test basic methods
            testModuleMethods(moduleInstance, warnings)
            
            // Cleanup test instance
            try {
                moduleInstance.cleanup()
            } catch (e: Exception) {
                warnings.add("Module cleanup failed: ${e.message}")
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Module loading test failed", e)
            warnings.add("Module loading failed: ${e.message}")
            false
        }
    }

    /**
     * Find module class in the loaded DEX file
     */
    private fun findModuleClass(classLoader: ClassLoader, moduleName: String): Class<*>? {
        val possibleClassNames = listOf(
            "com.aman.modules.${moduleName.capitalize()}Module",
            "com.aman.modules.$moduleName",
            "${moduleName}Module",
            moduleName
        )

        for (className in possibleClassNames) {
            try {
                return classLoader.loadClass(className)
            } catch (e: ClassNotFoundException) {
                // Continue trying other names
            }
        }

        return null
    }

    /**
     * Test basic module methods
     */
    private fun testModuleMethods(module: BaseModule, warnings: MutableList<String>) {
        try {
            // Test initialization (with null context for safety)
            try {
                module.initialize(null)
            } catch (e: Exception) {
                warnings.add("Module initialization test failed: ${e.message}")
            }

            // Test version method
            try {
                val version = module.getVersion()
                if (version.isBlank()) {
                    warnings.add("Module version is empty")
                }
            } catch (e: Exception) {
                warnings.add("Module version method failed: ${e.message}")
            }

            // Test supported commands
            try {
                val commands = module.getSupportedCommands()
                if (commands.isEmpty()) {
                    warnings.add("Module supports no commands")
                }
            } catch (e: Exception) {
                warnings.add("Module getSupportedCommands failed: ${e.message}")
            }

            // Test command handling capability
            try {
                module.canHandleCommand("test")
            } catch (e: Exception) {
                warnings.add("Module canHandleCommand failed: ${e.message}")
            }

        } catch (e: Exception) {
            warnings.add("Module method testing failed: ${e.message}")
        }
    }

    /**
     * Create sandbox directory
     */
    private fun createSandboxDirectory(): File {
        val sandboxDir = File(context.filesDir, "sandbox_${System.currentTimeMillis()}")
        if (!sandboxDir.exists()) {
            sandboxDir.mkdirs()
        }
        return sandboxDir
    }

    /**
     * Cleanup sandbox directory
     */
    private fun cleanupSandboxDirectory(sandboxDir: File) {
        try {
            if (sandboxDir.exists()) {
                sandboxDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup sandbox directory", e)
        }
    }

    /**
     * Get current memory usage
     */
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Custom Security Manager for sandbox
     */
    private class SandboxSecurityManager(
        private val warnings: MutableList<String>
    ) : SecurityManager() {

        override fun checkPermission(perm: Permission?) {
            // Log permission requests but don't block them in sandbox
            perm?.let { permission ->
                when (permission.name) {
                    "java.net.SocketPermission" -> {
                        warnings.add("Module requests network access")
                    }
                    "java.io.FilePermission" -> {
                        warnings.add("Module requests file system access")
                    }
                    "java.lang.RuntimePermission" -> {
                        if (permission.actions?.contains("exitVM") == true) {
                            warnings.add("Module attempts to exit VM")
                            throw SecurityException("VM exit not allowed in sandbox")
                        }
                    }
                }
            }
        }

        override fun checkExit(status: Int) {
            warnings.add("Module attempts to exit with status: $status")
            throw SecurityException("System exit not allowed in sandbox")
        }

        override fun checkExec(cmd: String?) {
            warnings.add("Module attempts to execute command: $cmd")
            throw SecurityException("Command execution not allowed in sandbox")
        }

        override fun checkDelete(file: String?) {
            warnings.add("Module attempts to delete file: $file")
            // Allow in sandbox but log it
        }

        override fun checkWrite(file: String?) {
            warnings.add("Module attempts to write file: $file")
            // Allow in sandbox but log it
        }
    }

    /**
     * Validate module against security policies
     */
    fun validateModuleSecurity(moduleFile: File): SecurityValidationResult {
        return try {
            val validationResult = SecurityValidationResult()
            
            // Check file size (prevent extremely large modules)
            val fileSizeKB = moduleFile.length() / 1024
            if (fileSizeKB > 10240) { // 10MB limit
                validationResult.addWarning("Large module file: ${fileSizeKB}KB")
            }

            // Check file extension
            if (!moduleFile.name.endsWith(".dex") && !moduleFile.name.endsWith(".jar")) {
                validationResult.addError("Invalid module file type")
            }

            // Additional security checks could be added here
            // - Code signing verification
            // - Malware scanning
            // - API usage analysis

            validationResult

        } catch (e: Exception) {
            Log.e(TAG, "Security validation failed", e)
            SecurityValidationResult().apply {
                addError("Security validation failed: ${e.message}")
            }
        }
    }

    // Data classes

    private data class SandboxResult(
        val success: Boolean,
        val memoryUsed: Long,
        val error: String?
    )

    data class SecurityValidationResult(
        private val errors: MutableList<String> = mutableListOf(),
        private val warnings: MutableList<String> = mutableListOf()
    ) {
        fun addError(error: String) = errors.add(error)
        fun addWarning(warning: String) = warnings.add(warning)
        
        fun isValid(): Boolean = errors.isEmpty()
        fun getErrors(): List<String> = errors.toList()
        fun getWarnings(): List<String> = warnings.toList()
    }
}
