package fr.barodine.anael.discord.roleattributor;

import fr.barodine.anael.discord.launcher.AbstractBaseListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.RestAction;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RoleAttributorListener extends AbstractBaseListener {
    public RoleAttributorListener(long idBot, @Nonnull JDA jda) {
        super(idBot, jda);
    }

    // Attributs
    private boolean ready = false;
    private RestAction<Message> listenedMessage;
    private Map<String, Role> emojiToRole = new HashMap<>();

    // Méthodes
    private void getReady() {
        this.ready = true;

        String textChannelId = this.getVariable("TEXT_CHANNEL_ID");
        if (textChannelId == null) this.log("Error: Listened text channel ID environment variable is undefined or null");

        String messageId = this.getVariable("MESSAGE_ID");
        if (messageId == null) this.log("Error: Listened message ID environment variable is undefined or null");

        if (textChannelId == null || messageId == null) {
            this.log("Please specify text channel ID & message ID environment variables");
            this.getJda().shutdownNow();
            return;
        }

        // Trouver le message.
        try {
            TextChannel textChannel = this.getJda().getTextChannelById(textChannelId);

            if (textChannel == null) {
                this.log("Error: Text channel of ID " + textChannelId + " not found");
                this.getJda().shutdownNow();
                return;
            }

            this.listenedMessage = textChannel.retrieveMessageById(messageId);

            try {
                this.listenedMessage.complete();
            } catch (ErrorResponseException errorResponseException) {
                log("Error: Message of ID " + messageId + " not found");
            }
        } catch (NumberFormatException numberFormatException) {
            log("Error: Text channel ID or message ID is not a valid snowflake value, expecting a type long value");
            this.getJda().shutdownNow();
            return;
        }

        // Trouver les emojis.
        this.log("Bot is ready; starting emoji search");
        this.findEmojis();
        this.reloadMessageReactions();
    }

    private void findEmojis() {
        // Obtention du message.
        this.listenedMessage.queue(message -> {
            // Trouver les rôles.
            Map<String, Role> roleNameToRole = new HashMap<>();
            for (Role role : message.getGuild().getRoles()) {
                roleNameToRole.put(role.getName().toLowerCase(Locale.ROOT), role);
            }

            // Trouver les émojis.
            this.emojiToRole = new HashMap<>();

            boolean passing = true;
            for (String line : message.getContentRaw().toLowerCase(Locale.ROOT).split((System.getProperty("line.separator")))) {
                if (passing) {
                    if (line.equals("--")) {
                        passing = false;
                    }
                } else {
                    String[] emojiRoleName = line.split(" ", 2);
                    if (roleNameToRole.containsKey(emojiRoleName[1])) {
                        this.emojiToRole.put(emojiRoleName[0], roleNameToRole.get(emojiRoleName[1]));
                    }
                }
            }

            this.log("Emojis found in the message: " + this.emojiToRole.toString());
        });
    }

    private void reloadMessageReactions() {
        // Obtention du message.
        this.listenedMessage.queue(message -> {
            // Retrait de toutes les réactions du bot.
            RestAction<Void> restAction = null;

            for (MessageReaction messageReaction : message.getReactions()) {
                restAction = restAction == null ? messageReaction.removeReaction() : restAction.and(messageReaction.removeReaction());
            }

            if (restAction != null) {
                restAction.queue(unused -> {
                    // Ajout de nouveau de toutes les réactions.
                    RestAction<Void> restActionAfterQueue = null;

                    for (String emoji : this.emojiToRole.keySet()) {
                        restActionAfterQueue = restActionAfterQueue == null ? message.addReaction(emoji) : restActionAfterQueue.and(message.addReaction(emoji));
                    }

                    if (restActionAfterQueue != null) {
                        restActionAfterQueue.queue();
                    }
                });
            }
        });
    }

    // Événements
    @Override
    public void onReady(@Nonnull final ReadyEvent event) {
        this.getReady();
    }

    @Override
    public void onGuildMessageReactionAdd(@Nonnull final GuildMessageReactionAddEvent event) {
        if (!this.ready) {
            log("Warning: Bot was not ready when GuildMessageReactionAddEvent was detected; getting ready");
            this.getReady();
        }

        if (event.getMessageId().equals(this.getVariable("MESSAGE_ID")) && !event.getUser().isBot()) {
            // Est-ce que c'est un émoji à suivre ?
            String receivedEmoji = null;
            if (event.getReactionEmote().isEmoji() && this.emojiToRole.containsKey(event.getReactionEmote().getEmoji())) {
                receivedEmoji = event.getReactionEmote().getEmoji();
                Role roleToAdd = event.getGuild().getRoleById(this.emojiToRole.get(receivedEmoji).getId());
                if (roleToAdd == null) {
                    log("Error: Role " + this.emojiToRole.get(receivedEmoji) + " has not been found, maybe has it been deleted? Reloading emojis");
                    this.findEmojis();
                } else {
                    event
                            .getGuild()
                            .addRoleToMember(event.getMember(), roleToAdd)
                            .queue();
                }
            }

            // Log.
            this.log(
                    "User " + event.getUser() + " reacted '" + event.getReactionEmote().getEmoji() + "'" +
                            (
                                    receivedEmoji != null ?
                                            " giving role " + event.getGuild().getRoleById(this.emojiToRole.get(receivedEmoji).getId()) :
                                            ""
                            )
            );

            // On enlève toutes les réactions de l'utilisateur sauf celle que l'on vient éventuellement de recevoir.
            final String receivedEmojiFinal = receivedEmoji;
            this.listenedMessage.queue(message -> {
                RestAction<Void> restAction = null;

                for (MessageReaction messageReaction : message.getReactions()) {
                    if (
                            receivedEmojiFinal == null ||
                                    !messageReaction.getReactionEmote().isEmoji() ||
                                    !messageReaction.getReactionEmote().getEmoji().equals(receivedEmojiFinal)
                    ) {
                        restAction =
                                restAction == null ?
                                        messageReaction.removeReaction(event.getUser()) :
                                        restAction.and(messageReaction.removeReaction(event.getUser()));
                    }
                }

                if (restAction != null) {
                    restAction.queue();
                }
            });
        }
    }

    @Override
    public void onGuildMessageReactionRemove(@Nonnull final GuildMessageReactionRemoveEvent event) {
        if (!this.ready) {
            log("Warning: Bot was not ready when GuildMessageReactionRemoveEvent was detected; getting ready");
            this.getReady();
        }

        if (event.getMessageId().equals(this.getVariable("MESSAGE_ID")) && event.getMember() != null && event.getUser() != null && !event.getUser().isBot()) {
            // Est-ce que c'est un émoji à suivre ?
            String receivedEmoji;
            if (event.getReactionEmote().isEmoji() && this.emojiToRole.containsKey(event.getReactionEmote().getEmoji())) {
                receivedEmoji = event.getReactionEmote().getEmoji();
                Role roleToRemove = event.getGuild().getRoleById(this.emojiToRole.get(receivedEmoji).getId());
                if (roleToRemove == null) {
                    log("Error: Role " + this.emojiToRole.get(receivedEmoji) + " has not been found, maybe has it been deleted? Reloading emojis");
                    this.findEmojis();
                    this.reloadMessageReactions();
                } else {
                    event
                            .getGuild()
                            .removeRoleFromMember(event.getMember(), roleToRemove)
                            .queue();
                }
            }
        }
    }

    @Override
    public void onGuildMessageUpdate(@Nonnull final GuildMessageUpdateEvent event) {
        if (!this.ready) {
            log("Warning: Bot was not ready when GuildMessageUpdateEvent was detected; getting ready");
            this.getReady();
        }

        if (event.getMessageId().equals(this.getVariable("MESSAGE_ID")) && !event.getAuthor().isBot()) {
            this.log("Listened message updated; starting emoji search");
            this.findEmojis();
        }
    }
}
