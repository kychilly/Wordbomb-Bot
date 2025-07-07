package com.kychilly.DiscordBot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.IOException;

public interface Command {
    void execute(SlashCommandInteractionEvent event) throws IOException;
}
