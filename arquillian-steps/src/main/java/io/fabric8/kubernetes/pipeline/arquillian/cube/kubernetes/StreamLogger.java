package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.api.Logger;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StreamLogger implements Logger {

    private final String INFO = "[INFO]";
    private final String ERROR = "[ERROR]";
    private final String WARNING = "[WARNING]";
    private final String STATUS = "[STATUS]";

    private final String LOGGING_FORMAT = "%s %s: %s";
    private final DateFormat DATE_FORMAT = new SimpleDateFormat("dd MM yyyy - HH:mm");

    private final PrintStream stream;

    public StreamLogger(PrintStream stream) {
        this.stream = stream;
    }

    @Override
    public void info(String s) {
        stream.println(String.format(LOGGING_FORMAT, INFO, DATE_FORMAT.format(new Date()), s));
    }

    @Override
    public void warn(String s) {
        stream.println(String.format(LOGGING_FORMAT, WARNING, DATE_FORMAT.format(new Date()), s));
    }

    @Override
    public void error(String s) {
        stream.println(String.format(LOGGING_FORMAT, ERROR, DATE_FORMAT.format(new Date()), s));
    }

    @Override
    public void status(String s) {
        stream.println(String.format(LOGGING_FORMAT, STATUS, DATE_FORMAT.format(new Date()), s));
    }

    @Override
    public org.arquillian.cube.kubernetes.api.Logger toImmutable() {
        return null;
    }
}

