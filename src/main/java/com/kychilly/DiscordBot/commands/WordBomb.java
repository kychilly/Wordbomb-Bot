package com.kychilly.DiscordBot.commands;

import com.kychilly.DiscordBot.KychillyBot;
import com.kychilly.DiscordBot.classes.WordBombPlayer;
import com.kychilly.DiscordBot.listeners.WordBombButtonListener;
import com.kychilly.DiscordBot.listeners.WordBombMessageListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;

public class WordBomb implements Command {

    public final static HashSet<String> activeChannelIDs = new HashSet<>();

    private HashSet<String> dictionary;
    private ArrayList<String> prompts;

    public final ArrayList<WordBombPlayer> players = new ArrayList<>();
    public final HashSet<String> usedWords = new HashSet<>();
    public int currentPlayerIndex;
    public User host;
    public MessageChannel channel;


    public final int STARTING_LIVES = 3;
    private final int TURN_TIME = 10;
    private int DIFFICULTY_CODE;
    private int LANGUAGE_CODE;
    private final InputStream img = KychillyBot.class.getResourceAsStream("images/wordbomb.png");

    private String defaultDescription;

    public static CommandData getCommandData() {
        return Commands.slash("wordbomb", "Play WordBomb on Discord!")
                .addOptions(
                        (new OptionData(OptionType.INTEGER, "difficulty", "Difficulty of the letter sequences given")
                                .addChoice("Easy", 1)
                                .addChoice("Medium", 2)
                                .addChoice("Hard", 3)
                                .setRequired(true)
                        ),
                        (new OptionData(OptionType.INTEGER, "language", "Language to play WordBomb in (default: English)")
                                .addChoice("English", 0)
                                .addChoice("Spanish", 1)
                                .setRequired(false)
                        )
                );
    }



    public String getDifficulty() {
        if (DIFFICULTY_CODE == 1) {
            return "Easy";
        } else if (DIFFICULTY_CODE == 2) {
            return "Medium";
        } else {
            return "Hard";
        }
    }

    public WordBombPlayer getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }
    public String languageName()
    {
        if (LANGUAGE_CODE == 0) {
            return "English";
        } else if (LANGUAGE_CODE == 1) {
            return "Spanish";
        }
        return "YOU HAVE A MASSIVE BUG";
    }
    public String playerList() {
        StringBuilder s = new StringBuilder();
        for (WordBombPlayer player : players) {
            s.append("\n").append(player.user.getAsMention());
        }
        return s.toString();
    }
    public EmbedBuilder getEmbed() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("WordBomb");
        eb.setThumbnail("https://static.wikia.nocookie.net/roblox/images/4/40/Wordbomb-icon.webp/revision/latest?cb=20240720022629");
        eb.setDescription(defaultDescription + playerList());
        eb.setFooter(host.getEffectiveName(), host.getAvatarUrl());
        return eb;
    }
    public ArrayList<String> decodeJSON(String filePath) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<String>>() {}.getType();

        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(WordBomb.class.getClassLoader().getResourceAsStream("com/github/KychillyBot/wordbomb/" + filePath + ".json")))) {
            return gson.fromJson(reader, listType);
        } catch (Exception ignored) {

            System.out.println("Error decoding JSON at " + filePath);
        }
        throw new RuntimeException(filePath + " invalid path");
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) throws IOException {

        if (activeChannelIDs.contains(event.getChannelId())) {
            event.reply("There is already a game going on in this channel!").setEphemeral(true).queue();
            return;
        }

        host = event.getUser();
        players.add(new WordBombPlayer(host, STARTING_LIVES));
        LANGUAGE_CODE = event.getOption("language") == null ? 0 : Objects.requireNonNull(event.getOption("language")).getAsInt();
        DIFFICULTY_CODE = event.getOption("difficulty") == null ? 1 : Objects.requireNonNull(event.getOption("difficulty")).getAsInt();
        defaultDescription = (
                "**Starting Lives:** " + STARTING_LIVES + "\n" +
                        "**Turn Time**: " + TURN_TIME + "\n" +
                        "**Language:** " + languageName() + "\n" +
                        "**Difficulty:** " + getDifficulty() + "\n" +
                        "**Players:**"
        );
        channel = event.getChannel();
        dictionary = (LANGUAGE_CODE == 0) ? new HashSet<>(decodeJSON("dictionary_en")) : new HashSet<>(decodeJSON("dictionary_es"));
        prompts = (DIFFICULTY_CODE == 1) ? decodeJSON("easy") : (DIFFICULTY_CODE == 2) ? decodeJSON("medium") : decodeJSON("hard");

        activeChannelIDs.add(channel.getId());

        WordBombButtonListener listener = new WordBombButtonListener(this);
        event.getJDA().addEventListener(listener);

        EmbedBuilder eb = getEmbed();
        event.deferReply().queue(hook -> {
            assert img != null;
            hook.sendMessageEmbeds(eb.build())
                    //.addFiles(FileUpload.fromData(img,"wordbomb.png"))
                    .addActionRow(
                            Button.success("start", "Start Game"),
                            Button.primary("join", "Join Game"),
                            Button.danger("leave", "Leave Game"),
                            Button.secondary("help", "Help")
                    )
                    .queue(message -> {
                        Timer timer = new Timer();
                        TimerTask expiration = new TimerTask() {
                            @Override
                            public void run() {
                                EmbedBuilder eb = new EmbedBuilder();
                                eb.setTitle("WordBomb");
                                eb.setColor(Color.red);
                                eb.setDescription("Timed out due to inactivity");
                                eb.setFooter(host.getEffectiveName(), host.getAvatarUrl());
                                message.editMessageEmbeds(eb.build())
                                        .setAttachments()
                                        .setComponents()
                                        .queue();
                                event.getJDA().removeEventListener(listener);
                                activeChannelIDs.remove(channel.getId());
                            }
                        };
                        timer.schedule(expiration, 300_000); // 5 minutes until expiration
                    });
        });

    }

    public String getRandomPrompt() {
        return prompts.get((int)(Math.random() * prompts.size()));
    }
    public boolean wordIsValid(String word) {
        return dictionary.contains(word);
    }
    public void endGame() {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.green);
        eb.setTitle(players.get(0).user.getEffectiveName() + ", you are the winner! :tada:");
        eb.setDescription("Congratulations " + players.get(0).user.getAsMention() + "!!!! You are a word master!!!");
        eb.setImage(players.get(0).user.getEffectiveAvatarUrl());
        channel.sendMessageEmbeds(eb.build()).queue();
        activeChannelIDs.remove(channel.getId());
    }

    public void passTurn() {
        if (players.size() == 1) {
            endGame();
            return;
        }
        if (++currentPlayerIndex == players.size()) {
            currentPlayerIndex = 0;
        }
        promptTurn();
    }
    public void promptTurn() {
        EmbedBuilder eb = new EmbedBuilder();
        WordBombPlayer player = players.get(currentPlayerIndex);
        String prompt = getRandomPrompt();
        eb.setTitle(prompt.toUpperCase());
        eb.setDescription("It's " + player.user.getAsMention() + "'s turn!\n\n" + player);
        eb.setThumbnail(player.user.getEffectiveAvatarUrl());
        channel.sendMessageEmbeds(eb.build()).queue();

        Timer timer = new Timer();
        WordBombMessageListener listener = new WordBombMessageListener(this, prompt, timer);
        TimerTask removeLife = new TimerTask() {
            @Override
            public void run() {
                EmbedBuilder embed = new EmbedBuilder();
                int remainingLives = player.removeLife();
                if (remainingLives > 0) {
                    embed.setColor(Color.orange);
                    if (remainingLives == 1) {
                        embed.setDescription(player.user.getAsMention() + " **failed!** 1 life left");
                    }
                    else {
                        embed.setDescription(player.user.getAsMention() + " **failed!** " + remainingLives + " lives left");
                    }
                    channel.sendMessageEmbeds(embed.build()).queue();
                }
                else {
                    embed.setColor(Color.red);
                    embed.setDescription(player.user.getAsMention() + " **is out!**");
                    players.remove(currentPlayerIndex--);
                    channel.sendMessageEmbeds(embed.build()).queue();
                }
                channel.getJDA().removeEventListener(listener);
                passTurn();
            }
        };
        channel.getJDA().addEventListener(listener);
        timer.schedule(removeLife, TURN_TIME * 1000);
    }


}