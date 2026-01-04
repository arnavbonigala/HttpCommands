package com.arnavbonigala.commands;

import com.arnavbonigala.config.HttpCommandsConfig;
import com.arnavbonigala.http.HttpService;
import com.arnavbonigala.http.RateLimiter;
import com.arnavbonigala.Httpcommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.http.HttpResponse;
import java.util.UUID;

public class HttpCommands {
    private static HttpService httpService;
    private static RateLimiter rateLimiter;
    private static HttpCommandsConfig config;
    
    private static boolean hasOpPermission(CommandSourceStack source) {
        // Command blocks and console (non-player sources) are allowed
        if (source.getEntity() == null) {
            Httpcommands.LOGGER.debug("Permission check: non-player source, allowing");
            return true;
        }
        // For players, check if they are OP
        if (source.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = source.getServer();
            var ops = server.getPlayerList().getOps();
            UUID playerId = player.getUUID();
            
            Httpcommands.LOGGER.debug("Permission check: checking player {} (UUID: {})", player.getName().getString(), playerId);
            Httpcommands.LOGGER.debug("Permission check: server is singleplayer: {}", server.isSingleplayer());
            Httpcommands.LOGGER.debug("Permission check: ops list size: {}", ops.getEntries().size());
            
            // In singleplayer, if cheats are enabled, the player should have permission
            // Check if player is in ops list
            for (var entry : ops.getEntries()) {
                UUID entryId = entry.getUser().id();
                if (entryId.equals(playerId)) {
                    Httpcommands.LOGGER.debug("Permission check: player is OP, allowing");
                    return true;
                }
            }
            
            // If singleplayer and ops list is empty, allow (cheats might be enabled but ops.json not created yet)
            if (server.isSingleplayer() && ops.getEntries().isEmpty()) {
                Httpcommands.LOGGER.debug("Permission check: singleplayer with empty ops list, allowing (assuming cheats enabled)");
                return true;
            }
            
            Httpcommands.LOGGER.warn("Permission check: player {} is NOT in ops list. Ops list has {} entries", player.getName().getString(), ops.getEntries().size());
        }
        Httpcommands.LOGGER.debug("Permission check: denying access");
        return false;
    }
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        config = HttpCommandsConfig.getInstance();
        httpService = new HttpService(config);
        rateLimiter = new RateLimiter(config);
        
        dispatcher.register(Commands.literal("httpget")
            .requires(HttpCommands::hasOpPermission)
            .then(Commands.argument("url", StringArgumentType.greedyString())
                .executes(HttpCommands::executeGet)
            )
        );
        
        dispatcher.register(Commands.literal("httppost")
            .requires(HttpCommands::hasOpPermission)
            .then(Commands.argument("url", StringArgumentType.string())
                .then(Commands.argument("body", StringArgumentType.greedyString())
                    .executes(HttpCommands::executePost)
                )
            )
        );
        
        dispatcher.register(Commands.literal("httpcommands")
            .requires(HttpCommands::hasOpPermission)
            .then(Commands.literal("reload")
                .executes(HttpCommands::executeReload)
            )
        );
        
        com.arnavbonigala.Httpcommands.LOGGER.info("Registered commands: /httpget, /httppost, /httpcommands reload");
    }
    
    private static int executeGet(CommandContext<CommandSourceStack> context) {
        String url = StringArgumentType.getString(context, "url");
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        
        UUID sourceId = getSourceId(source);
        
        // Check rate limit
        if (!rateLimiter.canMakeRequest(sourceId)) {
            long remaining = rateLimiter.getRemainingCooldown(sourceId);
            String message = "Rate limit: Please wait " + remaining + " more second(s) before making another request.";
            broadcastMessage(server, message);
            return 0;
        }
        
        rateLimiter.startRequest(sourceId);
        
        httpService.getAsync(url)
            .thenAccept(response -> {
                rateLimiter.endRequest();
                handleResponse(server, response, url);
            })
            .exceptionally(throwable -> {
                rateLimiter.endRequest();
                handleError(server, throwable, url);
                return null;
            });
        
        return 1;
    }
    
    private static int executePost(CommandContext<CommandSourceStack> context) {
        String url = StringArgumentType.getString(context, "url");
        String body = StringArgumentType.getString(context, "body");
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        
        UUID sourceId = getSourceId(source);
        
        // Check rate limit
        if (!rateLimiter.canMakeRequest(sourceId)) {
            long remaining = rateLimiter.getRemainingCooldown(sourceId);
            String message = "Rate limit: Please wait " + remaining + " more second(s) before making another request.";
            broadcastMessage(server, message);
            return 0;
        }
        
        rateLimiter.startRequest(sourceId);
        
        httpService.postAsync(url, body)
            .thenAccept(response -> {
                rateLimiter.endRequest();
                handleResponse(server, response, url);
            })
            .exceptionally(throwable -> {
                rateLimiter.endRequest();
                handleError(server, throwable, url);
                return null;
            });
        
        return 1;
    }
    
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        
        HttpCommandsConfig.reload();
        config = HttpCommandsConfig.getInstance();
        httpService = new HttpService(config);
        rateLimiter = new RateLimiter(config);
        
        broadcastMessage(server, "HttpCommands config reloaded successfully.");
        return 1;
    }
    
    private static void handleResponse(MinecraftServer server, HttpResponse<String> response, String url) {
        String body = response.body();
        if (body == null) {
            body = "";
        }
        
        // Truncate response
        if (body.length() > config.maxResponseChars) {
            body = body.substring(0, config.maxResponseChars) + "... (truncated)";
        }
        
        // Format message
        String message;
        if (config.showStatusCode) {
            message = String.format("[HTTP %d] %s", response.statusCode(), body);
        } else {
            message = body;
        }
        
        broadcastMessage(server, message);
    }
    
    private static void handleError(MinecraftServer server, Throwable throwable, String url) {
        String errorMessage = "HTTP request failed: " + throwable.getMessage();
        if (errorMessage.length() > config.maxResponseChars) {
            errorMessage = errorMessage.substring(0, config.maxResponseChars) + "... (truncated)";
        }
        broadcastMessage(server, errorMessage);
    }
    
    private static void broadcastMessage(MinecraftServer server, String message) {
        // Schedule on server thread
        server.execute(() -> {
            Component text = Component.literal(message);
            server.getPlayerList().broadcastSystemMessage(text, false);
        });
    }
    
    private static UUID getSourceId(CommandSourceStack source) {
        // Use entity UUID if available, otherwise use a fixed UUID for command blocks/console
        if (source.getEntity() != null) {
            return source.getEntity().getUUID();
        }
        // Use a fixed UUID for command blocks/console sources
        return UUID.nameUUIDFromBytes("command_block".getBytes());
    }
}

