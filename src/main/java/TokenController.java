package main.java;

import java.io.IOException;
import java.net.URISyntaxException;

public class TokenController {

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        String token = new GenerateToken().generateAccessToken();
        System.out.println(token);
    }
}
