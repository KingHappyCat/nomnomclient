package net.staro.nomnom.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.command.NomnomCommand;
import net.staro.nomnom.command.arguments.FriendArgument;
import net.staro.nomnom.command.arguments.PlayerArgument;

public class FriendCommand extends NomnomCommand {
    private final PlayerArgument playerArgument;

    public FriendCommand(Nomnom nomnom) {
        super(nomnom, "friend", "Manage friends");
        playerArgument = new PlayerArgument(nomnom.mc());
    }

    @Override
    public void build(Nomnom nomnom, LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(
                context -> {
                    nomnom.chat().send("Please enter the correct sequence: +player <add/del/list/clear> <nickname>");
                    return COMPLETED;
                }).then(
                literal("add")
                        .then(argument("player", playerArgument)
                                .executes(context -> {
                                    var nickname = context.getArgument("player", String.class);
                                    if (!nomnom.friendManager().getFriends().contains(nickname)) {
                                        nomnom.friendManager().addFriend(nickname);
                                        nomnom.chat().send(Text.literal("")
                                                .append(Text.literal(nickname).formatted(Formatting.BOLD).formatted(Formatting.AQUA))
                                                .append(Text.literal(" is a friend now").formatted(Formatting.WHITE)));
                                    } else {
                                        nomnom.chat().send(Text.literal("")
                                                .append(Text.literal(nickname).formatted(Formatting.BOLD).formatted(Formatting.AQUA))
                                                .append(Text.literal(" is already a friend").formatted(Formatting.WHITE)));
                                    }

                                    return COMPLETED;
                                }))
        ).then(
                literal("del")
                        .then(argument("player", new FriendArgument(nomnom))
                                .executes(context -> {
                                    var nickname = context.getArgument("player", String.class);
                                    nomnom.friendManager().removeFriend(nickname);
                                    nomnom.chat().send(Text.literal("")
                                            .append(Text.literal(nickname).formatted(Formatting.BOLD).formatted(Formatting.RED))
                                            .append(Text.literal(" is not a friend anymore").formatted(Formatting.WHITE)));
                                    return COMPLETED;
                                }))
        ).then(
                literal("list")
                        .executes(context -> {
                            var friendList = new StringBuilder();
                            var friends = nomnom.friendManager().getFriends();
                            for (int i = 0; i < friends.size(); i++) {
                                friendList.append(friends.get(i));
                                if (i < friends.size() - 1) {
                                    friendList.append(", ");
                                }
                            }

                            nomnom.chat().send(Text.literal("")
                                    .append(Text.literal("Friend list: ").formatted(Formatting.BOLD).formatted(Formatting.AQUA))
                                    .append(Text.literal(friendList.toString()).formatted(Formatting.WHITE)));

                            return COMPLETED;
                        })
        ).then(
                literal("clear")
                        .executes(context -> {
                            nomnom.friendManager().getFriends().clear();
                            return COMPLETED;
                        })
        );
    }

}
