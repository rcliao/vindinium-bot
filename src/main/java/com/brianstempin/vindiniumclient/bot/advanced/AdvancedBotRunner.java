package com.brianstempin.vindiniumclient.bot.advanced;

import com.brianstempin.vindiniumclient.bot.BotMove;
import com.brianstempin.vindiniumclient.bot.advanced.AdvancedBot;
import com.brianstempin.vindiniumclient.bot.advanced.Mine;
import com.brianstempin.vindiniumclient.bot.advanced.Pub;
import com.brianstempin.vindiniumclient.bot.advanced.Vertex;
import com.brianstempin.vindiniumclient.dto.ApiKey;
import com.brianstempin.vindiniumclient.dto.GameState;
import com.brianstempin.vindiniumclient.dto.Move;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;

import java.util.LinkedList;
import java.util.Map;

/**
 * Created by bstempi on 9/28/14.
 */
public class AdvancedBotRunner implements Runnable {
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new GsonFactory();
    private static final HttpRequestFactory REQUEST_FACTORY =
            HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) {
                    request.setParser(new JsonObjectParser(JSON_FACTORY));
                }
            });

    private final ApiKey apiKey;
    private final Class<? extends AdvancedBot> botClass;
    private final GenericUrl gameUrl;
    private final AdvancedBot bot;

    // Effectively immutable.  They are mutable but not designed to be.
    // TODO The map itself should be immutable.  Consider using Guava
    private Map<GameState.Position, Vertex> immutableBoardGraph;

    public AdvancedBotRunner(ApiKey apiKey, Class<? extends AdvancedBot> botClass, GenericUrl gameUrl) throws
            IllegalAccessException, InstantiationException {
        this.apiKey = apiKey;
        this.botClass = botClass;
        this.gameUrl = gameUrl;
        this.bot = this.botClass.newInstance();
    }

    @Override
    public void run() {
        HttpContent content;
        HttpRequest request;
        HttpResponse response;
        GameState gameState;

        try {
            // Initial request
            content = new UrlEncodedContent(apiKey);
            request = REQUEST_FACTORY.buildPostRequest(gameUrl, content);
            request.setReadTimeout(0); // Wait forever to be assigned to a game
            response = request.execute();
            gameState = response.parseAs(GameState.class);

            AdvancedGameState advancedGameState = new AdvancedGameState(gameState);

            // Game loop
            while (!gameState.getGame().isFinished() && !gameState.getHero().isCrashed()) {
                BotMove direction = bot.move(advancedGameState);
                Move move = new Move(apiKey.getKey(), direction.toString());


                HttpContent turn = new UrlEncodedContent(move);
                HttpRequest turnRequest = REQUEST_FACTORY.buildPostRequest(new GenericUrl(gameState.getPlayUrl()), turn);
                HttpResponse turnResponse = turnRequest.execute();

                gameState = turnResponse.parseAs(GameState.class);
                advancedGameState = new AdvancedGameState(advancedGameState, gameState);
            }

        } catch (Exception e) {
            // TODO Log exception and end game
        }
    }
}