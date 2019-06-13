package com.github.vlsi.mat.calcite.cli;

import org.jline.builtins.Completers;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

public class DumpFileNameCompleter extends Completers.FileNameCompleter {
  private static final PathMatcher DUMP_FILE_MATCHER =
      FileSystems.getDefault().getPathMatcher("glob:**.{bin,hprof}");

  @Override
  protected boolean accept(Path path) {
    return super.accept(path)
        && (Files.isDirectory(path) || DUMP_FILE_MATCHER.matches(path));
  }
}
