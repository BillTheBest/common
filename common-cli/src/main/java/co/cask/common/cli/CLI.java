/*
 * Copyright © 2012-2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.common.cli;

import co.cask.common.cli.completers.DefaultStringsCompleter;
import co.cask.common.cli.completers.PrefixCompleter;
import co.cask.common.cli.exception.CLIExceptionHandler;
import co.cask.common.cli.exception.InvalidCommandException;
import co.cask.common.cli.internal.TreeNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import jline.console.ConsoleReader;
import jline.console.UserInterruptException;
import jline.console.completer.AggregateCompleter;
import jline.console.completer.Completer;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Provides a command-line interface (CLI) with auto-completion,
 * interactive and non-interactive modes, and other typical shell features.
 * </p>
 *
 * <p>
 * {@link #commands} contains all of the available commands, and {@link #completers}
 * contains the available completers per argument type. For example, if we have a command
 * with the pattern "start flow <flow-id>" and a completer keyed by "flow-id" in the {@link #completers} map,
 * then when the user enters "start flow" and then hits TAB, the completer will be activated to provide
 * auto-completion.
 * </p>
 *
 * @param <T> type of {@link Command} that this {@link CLI} will use
 */
public class CLI<T extends Command> {

  private final CommandSet<T> commands;
  private final CompleterSet completers;
  private final ConsoleReader reader;

  private CLIExceptionHandler<Exception> exceptionHandler = new CLIExceptionHandler<Exception>() {
    @Override
    public void handleException(PrintStream output, Exception exception) {
      output.println("Error: " + exception.getMessage());
    }
  };

  /**
   * @param commands the commands to use
   * @param completers the completers to use
   * @throws IOException if unable to construct the {@link ConsoleReader}.
   */
  public CLI(Iterable<T> commands, Map<String, Completer> completers) throws IOException {
    this.commands = new CommandSet<T>(commands);
    this.completers = new CompleterSet(completers);
    this.reader = new ConsoleReader();
    this.reader.setPrompt("cli> ");
  }

  /**
   * @param commands the commands to use
   * @throws IOException if unable to construct the {@link ConsoleReader}.
   */
  public CLI(T... commands) throws IOException {
    this(ImmutableList.copyOf(commands), ImmutableMap.<String, Completer>of());
  }

  /**
   * @return the {@link ConsoleReader} that is being used to read input.
   */
  public ConsoleReader getReader() {
    return reader;
  }

  /**
   * Executes a command given some input.
   *
   * @param input the input
   * @param output the {@link PrintStream} to write messages to
   */
  public void execute(String input, PrintStream output) throws InvalidCommandException {
    CommandMatch match = commands.findMatch(input);
    try {
      match.getCommand().execute(match.getArguments(), output);
    } catch (Exception e) {
      exceptionHandler.handleException(output, e);
    }
  }

  /**
   * Starts interactive mode, which provides a shell to enter multiple commands and use auto-completion.
   *
   * @param output {@link java.io.PrintStream} to write to
   * @throws java.io.IOException if there's an issue in reading the input
   */
  public void startInteractiveMode(PrintStream output) throws IOException {
    this.reader.setHandleUserInterrupt(true);

    List<Completer> completerList = generateCompleters();
    for (Completer completer : completerList) {
      reader.addCompleter(completer);
    }

    while (true) {
      String line;

      try {
        line = reader.readLine();
      } catch (UserInterruptException e) {
        continue;
      }

      if (line == null) {
        output.println();
        break;
      }

      if (line.length() > 0) {
        String command = line.trim();
        try {
          execute(command, output);
        } catch (Exception e) {
          exceptionHandler.handleException(output, e);
        }
        output.println();
      }
    }
  }

  public void setExceptionHandler(CLIExceptionHandler<Exception> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  /**
   * Converts the list into an array
   *
   * @param list the list to be converted
   * @return converted list into an array
   */
  private String[] getArray(List<String> list) {
    if (list == null) {
      return null;
    }
    String[] array = new String[list.size()];
    for (int i = 0; i < list.size(); i++) {
      array[i] = list.get(i);
    }
    return array;
  }

  /**
   * Splits elements from list and array into array
   *
   * @param list the list
   * @param array the array
   * @return array split elements
   */
  private String[] getArray(List<String> list, String[] array) {
    if (list == null && array == null) {
      return null;
    }
    if (list == null) {
      return array;
    }
    if (array == null) {
      return getArray(list);
    }
    String[] argArray = getArray(list);
    String[] resultArray = new String[argArray.length + array.length];
    System.arraycopy(argArray, 0, resultArray, 0, argArray.length);
    System.arraycopy(array, 0, resultArray, argArray.length, array.length);
    return resultArray;
  }

  private List<Completer> generateCompleters() {
    TreeNode<String> commandTokenTree = new TreeNode<String>();

    for (Command command : commands) {
      String pattern = command.getPattern();
      String[] tokens = getArray(CommandMatch.Parser.parsePattern(pattern));

      generateCompleters(commandTokenTree, tokens);
    }

    return generateCompleters(null, commandTokenTree);
  }

  private TreeNode<String> generateCompleters(TreeNode<String> commandTokenTree, String[] tokens) {
    TreeNode<String> currentNode = commandTokenTree;
    int counter = 1;
    for (String token : tokens) {
      if (token.matches("\\[.+\\]")) {
        currentNode = generateCompleters(currentNode, getArray(CommandMatch.Parser.parsePattern(getEntry(token)),
                                                               Arrays.copyOfRange(tokens, counter, tokens.length)));
      } else {
        currentNode = currentNode.findOrCreateChild(token);
      }
      counter++;
    }

    return commandTokenTree;
  }

  private List<Completer> generateCompleters(String prefix, TreeNode<String> commandTokenTree) {
    List<Completer> completers = Lists.newArrayList();
    String name = commandTokenTree.getData();
    String childPrefix = (prefix == null || prefix.isEmpty() ? "" : prefix + " ") + (name == null ? "" : name);

    if (!commandTokenTree.getChildren().isEmpty()) {
      List<String> nonArgumentTokens = Lists.newArrayList();
      List<String> argumentTokens = Lists.newArrayList();
      for (TreeNode<String> child : commandTokenTree.getChildren()) {
        String childToken = child.getData();
        if (childToken.matches("<.+>")) {
          argumentTokens.add(childToken);
        } else {
          nonArgumentTokens.add(child.getData());
        }
      }

      for (String argumentToken : argumentTokens) {
        // chop off the < and >
        String completerType = getEntry(argumentToken);
        Completer argumentCompleter = getCompleterForType(completerType);
        if (argumentCompleter != null) {
          completers.add(prefixCompleterIfNeeded(childPrefix, argumentCompleter));
        }
      }

      completers.add(prefixCompleterIfNeeded(childPrefix, new DefaultStringsCompleter(nonArgumentTokens)));

      for (TreeNode<String> child : commandTokenTree.getChildren()) {
        completers.addAll(generateCompleters(childPrefix, child));
      }
    }

    return Lists.<Completer>newArrayList(new AggregateCompleter(completers));
  }

  /**
   * Retrieves entry from input {@link String}.
   * For example, for input "<some input>" returns "some input".
   *
   * @param input the input
   * @return entry {@link String}
   */
  private String getEntry(String input) {
    return input.substring(1, input.length() - 1);
  }

  private Completer prefixCompleterIfNeeded(String prefix, Completer completer) {
    if (prefix != null && !prefix.isEmpty()) {
      return new PrefixCompleter(prefix, completer);
    } else {
      return completer;
    }
  }

  private Completer getCompleterForType(String completerType) {
    return completers.getCompleter(completerType);
  }

}
