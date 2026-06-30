package com.example.demo.service;

import java.io.IOException;
import java.io.InputStream;

public final class CommandLogStream implements AutoCloseable {

    private final InputStream inputStream;
    private final AutoCloseable closeAction;

    public CommandLogStream(InputStream inputStream, AutoCloseable closeAction) {
        this.inputStream = inputStream;
        this.closeAction = closeAction;
    }

    public InputStream inputStream() {
        return inputStream;
    }

    @Override
    public void close() throws IOException {
        try {
            closeAction.close();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
