package com.example.demo.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface CommandExecutor {

    CommandExecutionResult execute(List<String> command, File directory) throws IOException, InterruptedException;

    default CommandExecutionResult execute(List<String> command, File directory, Map<String, String> environment)
            throws IOException, InterruptedException {
        return execute(command, directory);
    }

    void start(List<String> command, File directory, File logFile) throws IOException;

    CommandLogStream stream(List<String> command, File directory) throws IOException;
}
