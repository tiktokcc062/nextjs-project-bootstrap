package com.aman.agent.modules

import android.content.Context
import android.util.Log
import com.aman.agent.modules.base.BaseModule
import com.aman.agent.security.SecurityManager
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ModuleManager - Handles dynamic loading and management of AMAN modules
 * Provides secure module loading with sandbox testing and integrity verification
 */
class ModuleManager(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    companion object {
        private const val TAG = "ModuleManager"
        private const val MODULES_DIR = "modules"
        private const val TEMP_DIR = "temp_modules"
        private const val MODULE_TIMEOUT = 30000L // 30 seconds
        private const val MAX_MODULES = 10
    }

    private val loadedModules = ConcurrentHashMap<String, LoadedModule>()
    private val moduleSandbox = ModuleSandbox(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class LoadedModule(
        val name: String,
        val version: String,
        val instance: BaseModule,
        val classLoader: ClassLoader,
        val loadTime: Long,
        val checksum: String
    )

    /**
     * Load module from URL with security verification
     */
    suspend fun loadModule(moduleName: String, moduleUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Loading module: $moduleName from $moduleUrl")

            // Security checks
            if (!securityManager.isEnhancedSecurityEnabled()) {
                if (loadedModules.size >= MAX_MODULES) {
                    throw ModuleException("Maximum number of modules reached")
                }
            }

            // Download module
            val moduleFile = downloadModule(moduleUrl, moduleName)
            
            // Verify module integrity
            val checksum = calculateChecksum(moduleFile)
            
            // Test module in sandbox
            val testResult = moduleSandbox.testModule(moduleFile)
            if (!testResult.isValid) {
                throw ModuleException("Module failed sandbox test: ${testResult.error}")
            }

            // Load module class
            val moduleInstance = loadModuleClass(moduleFile, moduleName)
            
            // Initialize module
            moduleInstance.initialize(context)
            
            // Store loaded module
            val loadedModule = LoadedModule(
                name = moduleName,
                version = moduleInstance.getVersion(),
                instance = moduleInstance,
                classLoader = moduleInstance.javaClass.classLoader!!,
                loadTime = System.currentTimeMillis(),
                checksum = checksum
            )
            
            loadedModules[moduleName] = loadedModule
            
            Log.i(TAG, "Module loaded successfully: $moduleName v${loadedModule.version}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load module: $moduleName", e)
            throw ModuleException("Module loading failed: ${e.message}")
        }
    }

    /**
     * Download module from URL
     */
    private suspend fun downloadModule(url: String, moduleName: String): File = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "AMAN-Agent/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw ModuleException("Failed to download module: HTTP ${response.code}")
            }

            val responseBody = response.body ?: throw ModuleException("Empty response body")
            
            // Create modules directory
            val modulesDir = File(context.filesDir, MODULES_DIR)
            if (!modulesDir.exists()) {
                modulesDir.mkdirs()
            }

            // Save module file
            val moduleFile = File(modulesDir, "$moduleName.dex")
            FileOutputStream(moduleFile).use { output ->
                responseBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Module downloaded: ${moduleFile.absolutePath}")
            moduleFile

        } catch (e: Exception) {
            Log.e(TAG, "Module download failed", e)
            throw ModuleException("Download failed: ${e.message}")
        }
    }

    /**
     * Load module class using DexClassLoader
     */
    private fun loadModuleClass(moduleFile: File, moduleName: String): BaseModule {
        try {
            // Create optimized directory
            val optimizedDir = File(context.filesDir, "dex_opt")
            if (!optimizedDir.exists()) {
                optimizedDir.mkdirs()
            }

            // Create class loader
            val classLoader = DexClassLoader(
                moduleFile.absolutePath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )

            // Load module class (convention: com.aman.modules.ModuleName)
            val className = "com.aman.modules.${moduleName.capitalize()}Module"
            val moduleClass = classLoader.loadClass(className)
            
            // Verify it implements BaseModule
            if (!BaseModule::class.java.isAssignableFrom(moduleClass)) {
                throw ModuleException("Module does not implement BaseModule interface")
            }

            // Create instance
            val moduleInstance = moduleClass.getDeclaredConstructor().newInstance() as BaseModule
            
            Log.i(TAG, "Module class loaded: $className")
            return moduleInstance

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load module class", e)
            throw ModuleException("Class loading failed: ${e.message}")
        }
    }

    /**
     * Execute command on loaded module
     */
    suspend fun executeModuleCommand(
        command: String,
        parameters: Map<String, String>,
        sender: String
    ): String = withContext(Dispatchers.Default) {
        try {
            // Find module that can handle this command
            val module = findModuleForCommand(command)
                ?: throw ModuleException("No module found for command: $command")

            // Execute with timeout
            withTimeout(MODULE_TIMEOUT) {
                module.instance.executeCommand(command, parameters, sender)
            }

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Module command timeout: $command")
            throw ModuleException("Command execution timeout")
        } catch (e: Exception) {
            Log.e(TAG, "Module command execution failed", e)
            throw ModuleException("Command execution failed: ${e.message}")
        }
    }

    /**
     * Find module that can handle a command
     */
    private fun findModuleForCommand(command: String): LoadedModule? {
        return loadedModules.values.find { module ->
            try {
                module.instance.canHandleCommand(command)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking module capability", e)
                false
            }
        }
    }

    /**
     * Unload module
     */
    suspend fun unloadModule(moduleName: String): Boolean {
        return try {
            val module = loadedModules[moduleName]
            if (module != null) {
                // Cleanup module
                module.instance.cleanup()
                
                // Remove from loaded modules
                loadedModules.remove(moduleName)
                
                Log.i(TAG, "Module unloaded: $moduleName")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unload module: $moduleName", e)
            false
        }
    }

    /**
     * Get loaded modules info
     */
    fun getLoadedModules(): List<ModuleInfo> {
        return loadedModules.values.map { module ->
            ModuleInfo(
                name = module.name,
                version = module.version,
                loadTime = module.loadTime,
                commands = try {
                    module.instance.getSupportedCommands()
                } catch (e: Exception) {
                    emptyList()
                }
            )
        }
    }

    /**
     * Get loaded module count
     */
    fun getLoadedModuleCount(): Int = loadedModules.size

    /**
     * Check for module updates
     */
    suspend fun checkForModuleUpdates() {
        managerScope.launch {
            try {
                loadedModules.values.forEach { module ->
                    try {
                        // Check if module has update capability
                        if (module.instance.hasUpdateCapability()) {
                            val updateInfo = module.instance.checkForUpdates()
                            if (updateInfo.hasUpdate) {
                                Log.i(TAG, "Update available for module: ${module.name}")
                                // Could implement auto-update logic here
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking updates for module: ${module.name}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking module updates", e)
            }
        }
    }

    /**
     * Clean up unused modules
     */
    suspend fun cleanupUnusedModules() {
        managerScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val unusedThreshold = 24 * 60 * 60 * 1000L // 24 hours

                loadedModules.values.toList().forEach { module ->
                    try {
                        val lastUsed = module.instance.getLastUsedTime()
                        if (currentTime - lastUsed > unusedThreshold) {
                            Log.i(TAG, "Cleaning up unused module: ${module.name}")
                            unloadModule(module.name)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cleaning up module: ${module.name}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during module cleanup", e)
            }
        }
    }

    /**
     * Calculate file checksum
     */
    private fun calculateChecksum(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating checksum", e)
            ""
        }
    }

    /**
     * Verify module integrity
     */
    private fun verifyModuleIntegrity(moduleName: String): Boolean {
        return try {
            val module = loadedModules[moduleName] ?: return false
            val moduleFile = File(context.filesDir, "$MODULES_DIR/$moduleName.dex")
            
            if (!moduleFile.exists()) return false
            
            val currentChecksum = calculateChecksum(moduleFile)
            currentChecksum == module.checksum
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying module integrity", e)
            false
        }
    }

    /**
     * Get module statistics
     */
    fun getModuleStatistics(): ModuleStatistics {
        return ModuleStatistics(
            totalModules = loadedModules.size,
            totalCommands = loadedModules.values.sumOf { module ->
                try {
                    module.instance.getSupportedCommands().size
                } catch (e: Exception) {
                    0
                }
            },
            memoryUsage = estimateMemoryUsage(),
            oldestModule = loadedModules.values.minByOrNull { it.loadTime }?.name,
            newestModule = loadedModules.values.maxByOrNull { it.loadTime }?.name
        )
    }

    /**
     * Estimate memory usage of loaded modules
     */
    private fun estimateMemoryUsage(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            // This is a rough estimate - actual module memory usage would be complex to calculate
            usedMemory / 1024 / 1024 // Convert to MB
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            managerScope.cancel()
            
            // Cleanup all loaded modules
            loadedModules.values.forEach { module ->
                try {
                    module.instance.cleanup()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up module: ${module.name}", e)
                }
            }
            
            loadedModules.clear()
            httpClient.dispatcher.executorService.shutdown()
            
            Log.i(TAG, "Module manager cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during module manager cleanup", e)
        }
    }

    // Data classes

    data class ModuleInfo(
        val name: String,
        val version: String,
        val loadTime: Long,
        val commands: List<String>
    )

    data class ModuleStatistics(
        val totalModules: Int,
        val totalCommands: Int,
        val memoryUsage: Long,
        val oldestModule: String?,
        val newestModule: String?
    )

    /**
     * Custom exception for module operations
     */
    class ModuleException(message: String) : Exception(message)
}
