package ru.javaboys.vibe_data.util;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;

public class SqlExceptionUtil {

    public static boolean isCausedByTimeoutException(DataAccessException exception) {
        if (exception.getCause() instanceof SQLException sqlE) {
            if (sqlE.getMessage().contains("Query exceeded maximum time limit of")) {
                return true;
            }
        }
        return false;
    }

}
