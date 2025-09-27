package ru.javaboys.utils;

import io.github.cdimascio.dotenv.Dotenv;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class DotenvTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) {
        Dotenv dotenv = Dotenv.load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    }
}

