# Proto Kotlin Navigation

IntelliJ plugin that enables Cmd+Click (Ctrl+Click on Linux/Windows) navigation from any Kotlin file directly to `.proto` source definitions when referencing proto-generated classes, fields, or gRPC methods.

IntelliJ's built-in Protocol Buffers plugin provides this for Java but not Kotlin. This plugin fills that gap.

## Features

- Navigate from generated Kotlin message classes to their `.proto` message definitions
- Navigate from generated field accessors (getters/setters) to their `.proto` field definitions
- Navigate from generated gRPC stub methods to their `.proto` rpc definitions
- Navigate from generated enum classes to their `.proto` enum definitions
- Works with both source and decompiled (JAR) generated code
- Supports both K1 and K2 Kotlin plugin modes

## How It Works

When you Cmd+Click on a symbol in Kotlin code, the plugin:

1. Resolves the reference to the underlying Java/Kotlin PSI element
2. Detects whether the target is proto-generated (via annotations, superclass hierarchy, file path, or `// source:` comments)
3. Maps the generated element back to its proto name (e.g., `getIdempotencyKey` -> `idempotency_key`)
4. Locates the corresponding `.proto` file in the project
5. Navigates to the exact element within the `.proto` file

## Requirements

- IntelliJ IDEA 2024.2+
- Kotlin plugin

## Building

```sh
./gradlew build
```

## Installation

Build the plugin and install the resulting ZIP from `build/distributions/`:

**Settings** > **Plugins** > **Install Plugin from Disk...**
