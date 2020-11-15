package org.ser.plainserver;

import org.junit.Test;

import java.io.File;
import java.util.Scanner;

public class SimpleServerTest {

    private static final String ROOT_DIRECTORY = "/src/main/resources/static";

    @Test
    public void shouldFindTheFile() throws Exception {
        var file = new File(new File("").getAbsolutePath() + ROOT_DIRECTORY + "/index.html");
        System.out.println(file);
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                System.out.println(scanner.nextLine());
            }
        }

    }

}