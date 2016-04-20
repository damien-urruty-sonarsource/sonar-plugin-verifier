package com.jetbrains.pluginverifier;

import com.jetbrains.pluginverifier.commands.CheckIdeCommand;
import com.jetbrains.pluginverifier.commands.CheckPluginCommand;
import com.jetbrains.pluginverifier.commands.CompareResultsCommand;
import com.jetbrains.pluginverifier.commands.NewProblemsCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CommandHolder {

  private static final VerifierCommand defaultCommand = new CheckPluginCommand();

  private static final Map<String, VerifierCommand> COMMAND_MAP = new HashMap<String, VerifierCommand>();

  static {
    for (VerifierCommand c : new VerifierCommand[]{new CheckIdeCommand(), new CompareResultsCommand(), new NewProblemsCommand()}) {
      COMMAND_MAP.put(c.getName(), c);
    }

    COMMAND_MAP.put(defaultCommand.getName(), defaultCommand);
  }

  @Nullable
  public static VerifierCommand getCommand(@NotNull String commandName) {
    return COMMAND_MAP.get(commandName);
  }

  public static Map<String, VerifierCommand> getCommandMap() {
    return COMMAND_MAP;
  }

  public static VerifierCommand getDefaultCommand() {
    return defaultCommand;
  }
}
