# HttpCommands

A Fabric mod for Minecraft that adds HTTP request commands for singleplayer worlds.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api)
2. Place `httpcommands-1.0.0.jar` in your `.minecraft/mods/` folder

## Commands

- `/httpget <url>` - Make a GET request
- `/httppost <url> <body>` - Make a POST request
- `/httpcommands reload` - Reload configuration

**Requirements:** OP permission (or use command blocks)

## Configuration

Config file: `.minecraft/config/httpcommands.json`

Default settings:
- `showStatusCode`: false
- `maxResponseChars`: 400
- `postContentType`: "application/json"
- `connectTimeoutMs`: 5000
- `requestTimeoutMs`: 10000
- `cooldownSeconds`: 5
- `maxConcurrentRequests`: 4
- `allowLocalTargets`: false

## Building

```bash
./gradlew build
```

The mod jar will be in `build/libs/`

