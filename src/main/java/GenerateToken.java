package main.java;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Semaphore;

import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class GenerateToken implements HttpHandler {
    private String authorizationCode = null;
    private Semaphore semaphore = new Semaphore(0);
    private HttpServer server;

    // method to generate access token
    public String generateAccessToken() throws IOException, URISyntaxException, InterruptedException {
        Config config = new Config();

        // Construct the authorization URL
        String authUrl = buildAuthorizationUrl(config);

        // Open the browser for user authorization
        openBrowser(authUrl);

        // Start a simple HTTP server to handle the callback
        startHttpServer();

        // Wait for the authorization code to be received
        waitForAuthorizationCode();

        // Now you can use the authorization code to obtain the access token
        if (authorizationCode != null) {
            String clientId = config.clientId;
            String clientSecret = config.clientSecret;
            String accessToken = obtainAccessToken(clientId, clientSecret, config.redirectUri);
            server.stop(0);
            return accessToken;
        } else {
            return null;
        }
    }

    // method to construct the authorization URL
    private String buildAuthorizationUrl(Config config) {
        return "https://accounts.google.com/o/oauth2/v2/auth" + "?client_id=" + config.clientId + "&redirect_uri="
                + config.redirectUri + "&response_type=code" + "&scope=" + config.scope;
    }

    // method to open the user's browser for authorization
    private void openBrowser(String authUrl) throws IOException, URISyntaxException {
        Desktop.getDesktop().browse(new URI(authUrl));
    }

    // method to start an HTTP server
    private void startHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/callback", this);
        server.start();
    }

    // method to obtain the access token
    private String obtainAccessToken(String clientId, String clientSecret, String redirectUri) throws IOException {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        URL url = new URL(tokenUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        String parameters = "code=" + authorizationCode + "&client_id=" + clientId + "&client_secret=" + clientSecret
                + "&redirect_uri=" + redirectUri + "&grant_type=authorization_code";

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = parameters.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine);
                }
                JSONObject jsonResponse = new JSONObject(response.toString());
                String accessToken = jsonResponse.getString("access_token");
                return accessToken;
            }
        } else {
            // Handle the error or exception
            System.out.println("Error obtaining access token. Response code: " + responseCode);
        }
        return null;
    }

    // method to wait for the authorization code
    private void waitForAuthorizationCode() throws InterruptedException {
        semaphore.acquire();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            // Parse the query parameters to get the authorization code
            parseAuthorizationCode(query);

            // Send a response back to the browser
            sendResponse(exchange);
        } else {
            System.out.println("Callback URL does not contain query parameters.");
        }
    }

    // method to parse the authorization code
    private void parseAuthorizationCode(String query) {
        String[] queryParams = query.split("&");
        for (String param : queryParams) {
            if (param.startsWith("code=")) {
                authorizationCode = param.substring(5); // "code=" is 5 characters
                semaphore.release(); // Release the semaphore to signal that the code is available
                break;
            }
        }
    }

    // method to send a response back to the browser
    private void sendResponse(HttpExchange exchange) throws IOException {
        String response = "Authorization Code Received. You can close this window.";
        exchange.sendResponseHeaders(200, response.length());
        exchange.getResponseBody().write(response.getBytes());
        exchange.getResponseBody().close();
    }
}
