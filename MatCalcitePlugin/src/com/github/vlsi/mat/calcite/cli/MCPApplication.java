package com.github.vlsi.mat.calcite.cli;

import sqlline.Application;
import sqlline.CommandHandler;
import sqlline.MCPSqlLine;
import sqlline.SqlLine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MCPApplication extends Application {
  private static final Set<String> ALLOWED_SQLLINE_COMMANDS = new HashSet<String>() {{
    add("quit");
    add("history");
    add("verbose");
    add("run");
    add("list");
    add("all");
    add("go");
    add("script");
    add("record");
    add("brief");
    add("close");
    add("closeall");
    add("outputformat");
    add("set");
    add("help");
    add("reset");
    add("save");
    add("rerun");
    add("prompthandler");
  }};

  @Override
  public Collection<CommandHandler> getCommandHandlers(SqlLine sqlLine) {
    Collection<CommandHandler> handlers = new ArrayList<>();
    for (CommandHandler commandHandler : super.getCommandHandlers(sqlLine)) {
      if (ALLOWED_SQLLINE_COMMANDS.contains(commandHandler.getName())) {
        handlers.add(commandHandler);
      }
    }
    handlers.add(new OpenDumpCommandHandler((MCPSqlLine) sqlLine, new DumpFileNameCompleter(), "Open heap dump",
        "open_dump"));
    return handlers;
  }
}
