package com.arnavbonigala;

import com.arnavbonigala.commands.HttpCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Httpcommands implements ModInitializer {
	public static final String MOD_ID = "httpcommands";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LOGGER.info("Registering HttpCommands...");
			HttpCommands.register(dispatcher);
			LOGGER.info("HttpCommands registered successfully!");
		});

		LOGGER.info("HttpCommands mod initialized!");
	}
}