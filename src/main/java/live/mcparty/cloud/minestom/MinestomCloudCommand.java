package live.mcparty.cloud.minestom;

import cloud.commandframework.exceptions.*;
import kotlin.collections.ArraysKt;
import live.mcparty.cloud.minestom.arg.ArgumentDynamicWord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.CommandExecutor;
import net.minestom.server.command.builder.CommandSyntax;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;

public class MinestomCloudCommand<C> extends Command {

    private static final Component MESSAGE_INTERNAL_ERROR = Component.text("An internal error occurred while attempting to perform this command.", NamedTextColor.RED);
    private static final Component MESSAGE_NO_PERMS = Component.text("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", NamedTextColor.RED);
    private static final Component MESSAGE_UNKNOWN_COMMAND = Component.text("Unknown command. Type \"/help\" for help.", NamedTextColor.RED);

    private final MinestomCommandManager<C> manager;
    private final CommandExecutor emptyExecutor = (sender, args) -> {
    };
    private boolean isAmbiguous = false;

    public MinestomCloudCommand(cloud.commandframework.Command<C> cloudCommand, MinestomCommandManager<C> manager,
                                String name, String... aliases) {
        super(name, aliases);
        this.manager = manager;

        if (cloudCommand.isHidden()) {
            setCondition((sender, commandString) -> commandString != null);
        }

        registerCommandArguments(cloudCommand);
    }

    @NotNull
    private static String[] getArgumentNamesFromArguments(Argument<?>[] arguments) {
        return Arrays.stream(arguments).map(Argument::getId).toArray(String[]::new);
    }

    public void registerCommandArguments(cloud.commandframework.Command<?> cloudCommand) {
        //Do not register arguments if command is ambiguous
        if (isAmbiguous) return;
        setDefaultExecutor(emptyExecutor);

        var arguments =
                cloudCommand.getArguments().stream()
                        .skip(1)
                        .map(CloudInteropHelper::convertCloudArgumentToMinestom)
                        .toArray(Argument[]::new);
        for (Argument<?> arg : arguments) {
            if (!(arg instanceof ArgumentDynamicWord))
                continue;

            arg.setSuggestionCallback((sender, context, suggestion) -> {
                for (String suggested : manager.suggest(manager.mapCommandSender(sender), CloudInteropHelper.removeSlashPrefix(context.getInput()))) {
                    suggestion.addEntry(new SuggestionEntry(suggested));
                }
            });
        }

        var containsSyntax =
                getSyntaxes().stream().anyMatch(syntax -> Arrays.equals(getArgumentNamesFromArguments(arguments),
                        getArgumentNamesFromArguments(syntax.getArguments())));

        var syntaxes = (List<CommandSyntax>) getSyntaxes();

        if (!containsSyntax && arguments.length != 0) {
            addSyntax(emptyExecutor, arguments);

            var toMove = syntaxes.stream().filter(it ->
                    Arrays.stream(it.getArguments()).allMatch(arg -> arg instanceof ArgumentDynamicWord)
            ).toList();

            syntaxes.removeAll(toMove);
            syntaxes.addAll(0, toMove);

        }
        fixSyntaxArguments(syntaxes);
    }

    private void fixSyntaxArguments(List<CommandSyntax> syntaxes) {
        if (isAmbiguous) return;

        isAmbiguous = syntaxes.stream().anyMatch(it -> ArraysKt.indexOfFirst(it.getArguments(),
                arg -> arg instanceof ArgumentDynamicWord) == 0);

        if (isAmbiguous) {
            syntaxes.clear();
            ArgumentStringArray arg = new ArgumentStringArray("args");
            arg.setSuggestionCallback((sender, context, suggestion) -> {
                for (String suggested : manager.suggest(manager.mapCommandSender(sender), CloudInteropHelper.removeSlashPrefix(context.getInput()))) {
                    suggestion.addEntry(new SuggestionEntry(suggested));
                }
            });
            addSyntax(emptyExecutor, arg);
        }
    }

    @Override
    public void globalListener(@NotNull CommandSender commandSender, @NotNull CommandContext context, @NotNull String command) {
        var input = CloudInteropHelper.removeSlashPrefix(command);
        final C sender = this.manager.mapCommandSender(commandSender);
        this.manager.executeCommand(
                        sender,
                        input
                )
                .whenComplete((commandResult, throwable) -> {
                    if (throwable != null) {
                        if (throwable instanceof CompletionException) {
                            throwable = throwable.getCause();
                        }
                        final Throwable finalThrowable = throwable;
                        if (throwable instanceof InvalidSyntaxException) {
                            this.manager.handleException(sender,
                                    InvalidSyntaxException.class,
                                    (InvalidSyntaxException) throwable, (c, e) ->
                                            commandSender.sendMessage(Component.text("Invalid Command Syntax. Correct command syntax is: ", NamedTextColor.RED)
                                                    .append(Component.text("/" + ((InvalidSyntaxException) finalThrowable).getCorrectSyntax(), NamedTextColor.GRAY)))
                            );
                        } else if (throwable instanceof InvalidCommandSenderException) {
                            this.manager.handleException(sender,
                                    InvalidCommandSenderException.class,
                                    (InvalidCommandSenderException) throwable, (c, e) ->
                                            commandSender.sendMessage(finalThrowable.getMessage())
                            );
                        } else if (throwable instanceof NoPermissionException) {
                            this.manager.handleException(sender,
                                    NoPermissionException.class,
                                    (NoPermissionException) throwable, (c, e) ->
                                            commandSender.sendMessage(MESSAGE_NO_PERMS)
                            );
                        } else if (throwable instanceof NoSuchCommandException) {
                            this.manager.handleException(sender,
                                    NoSuchCommandException.class,
                                    (NoSuchCommandException) throwable, (c, e) ->
                                            commandSender.sendMessage(MESSAGE_UNKNOWN_COMMAND)
                            );
                        } else if (throwable instanceof ArgumentParseException) {
                            this.manager.handleException(sender,
                                    ArgumentParseException.class,
                                    (ArgumentParseException) throwable, (c, e) ->
                                            commandSender.sendMessage(Component.text("Invalid Command Argument: ", NamedTextColor.RED)
                                                    .append(Component.text(finalThrowable.getCause().getMessage(), NamedTextColor.GRAY)))
                            );
                        } else if (throwable instanceof CommandExecutionException) {
                            this.manager.handleException(sender,
                                    CommandExecutionException.class,
                                    (CommandExecutionException) throwable, (c, e) -> {
                                        commandSender.sendMessage(MESSAGE_INTERNAL_ERROR);
                                        MinecraftServer.LOGGER.error(
                                                "Exception executing command handler",
                                                finalThrowable.getCause()
                                        );
                                    }
                            );
                        } else {
                            commandSender.sendMessage(MESSAGE_INTERNAL_ERROR);
                            MinecraftServer.LOGGER.error(
                                    "An unhandled exception was thrown during command execution",
                                    throwable
                            );
                        }
                    }
                });

    }
}
