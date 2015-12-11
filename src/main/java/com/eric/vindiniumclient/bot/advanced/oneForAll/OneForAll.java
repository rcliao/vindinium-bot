package com.eric.vindiniumclient.bot.advanced.oneForAll;

import com.eric.vindiniumclient.bot.BotMove;
import com.eric.vindiniumclient.bot.BotUtils;
import com.eric.vindiniumclient.bot.advanced.AdvancedBot;
import com.eric.vindiniumclient.bot.advanced.AdvancedGameState;
import com.eric.vindiniumclient.bot.advanced.Mine;
import com.eric.vindiniumclient.bot.advanced.Vertex;
import com.eric.vindiniumclient.dto.GameState;
import com.google.api.client.util.Maps;
import com.google.api.client.util.Sets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.EvictingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class OneForAll implements AdvancedBot {
    private final Double FACTOR = 2.1;
    private final Double BASE_VALUE = 10000.0;
    private final Map<Mine, Double> mineAccum = Maps.newHashMap();
    private EvictingQueue<GameState.Position> lastPositions = EvictingQueue.create(2);

    private static final Logger logger = LogManager.getLogger(OneForAll.class);

    @Override
    public BotMove move(AdvancedGameState gameState) {
        Stopwatch watch = Stopwatch.createStarted();

        Map<GameState.Position, Double> valueMap = Maps.newHashMap();

        lastPositions.stream()
            .forEach(pos -> valueMap.put(pos, -BASE_VALUE));

        logger.info("OneForAll bot started");

        if (gameState.getMe().getLife() > 30) {
            gameState.getMines()
                .values()
                .stream()
                .filter(mine -> mine.getOwner() == null || !mine.getOwner().getName().equals(gameState.getMe().getName()))
                .forEach(mine -> {
                    mineAccum.put(mine, mineAccum.getOrDefault(mine, 0.9) + 0.1);
                    Vertex v = gameState.getBoardGraph().get(mine.getPosition());
                    diffuseMap(gameState, valueMap, Sets.newHashSet(), v, BASE_VALUE * mineAccum.getOrDefault(mine, 1.0), 30);
                });
        }

        logger.info("OneForAll bot diffused mine values " + watch.elapsed(TimeUnit.MILLISECONDS));

        gameState.getPubs()
            .values()
            .stream()
            .forEach(pub -> {
                Vertex v = gameState.getBoardGraph().get(pub.getPosition());
                // random formula to reason on mine
                Double value =
                    BASE_VALUE * gameState.getMe().getMineCount() *
                        (
                            (70.0 - gameState.getMe().getLife() > 0.0) ?
                                ((100.0 - gameState.getMe().getLife()) / 100.0) :
                                0.0
                        );
                diffuseMap(gameState, valueMap, Sets.newHashSet(), v, value, 30);
            });

        logger.info("OneForAll bot diffused tavern values " + watch.elapsed(TimeUnit.MILLISECONDS));

        // stay away or get close to enemy hero
        gameState.getHeroesByPosition()
            .values()
            .stream()
            .filter(hero -> !hero.getName().equals(gameState.getMe().getName()))
            .forEach(hero -> {
                // do not stand on the spawn point to avoid random death
                valueMap.put(hero.getSpawnPos(), -Double.MAX_VALUE);

                Vertex v = gameState.getBoardGraph().get(hero.getPos());
                boolean winnable = gameState.getMe().getLife() > 30 &&
                    hero.getLife() + 10 < gameState.getMe().getLife();
                // TODO: may need to take care of the tavern distance later

                double value = (winnable) ?
                    BASE_VALUE * hero.getMineCount() * ((gameState.getMe().getLife() - hero.getLife()) / 20.0) :
                    BASE_VALUE * -1.0 * gameState.getMe().getMineCount() * ((hero.getLife() - gameState.getMe().getLife()) / 20.0);

                diffuseMap(gameState, valueMap, Sets.newHashSet(), v, value, 5);
            });

        logger.info("OneForAll bot diffused hero values " + watch.elapsed(TimeUnit.MILLISECONDS));

        return findBestNextPath(gameState, valueMap);
    }

    private void printValueMap(Map<GameState.Position, Double> valueMap, int size, GameState.Position me) {
        for (int i = 0; i < size; i ++) {
            for (int j = 0; j < size; j ++) {
                if (valueMap.getOrDefault(new GameState.Position(i, j), 0.0) == - Double.MAX_VALUE) {
                    System.out.printf("%5.1f ", 0.0);
                } else {
                    System.out.printf("%5.1f ", valueMap.getOrDefault(new GameState.Position(i, j), 0.0));
                }

                if (new GameState.Position(i, j).equals(me)) {
                    System.out.print("*");
                }
            }

            System.out.println();
        }
    }

    private BotMove findBestNextPath(AdvancedGameState gameState, Map<GameState.Position, Double> valueMap) {
        Vertex vertex = gameState.getBoardGraph().get(gameState.getMe().getPos());
        double maxValue = -Double.MAX_VALUE;
        GameState.Position max = gameState.getMe().getPos();

            for (Vertex neighbor: vertex.getAdjacentVertices()) {
                GameState.Position pos = neighbor.getPosition();
                double value = valueMap.getOrDefault(pos, 0.0);
                if (value > maxValue) {
                    maxValue = value;
                    max = pos;
                }
            }

        if (gameState.getMines().get(max) != null) {
            mineAccum.put(gameState.getMines().get(max), 0.0);
        }

        lastPositions.add(max);
        logger.info("Find biggest neighbor " + max + " with value " + maxValue);
        return BotUtils.directionTowards(gameState.getMe().getPos(), max);
    }

    private void diffuseMap(
        AdvancedGameState gameState,
        Map<GameState.Position, Double> valueMap,
        Set<GameState.Position> visited,
        Vertex vertex,
        Double base,
        Integer distance
    ) {
        if (distance == 0) {
            return;
        }

        valueMap.put(
            vertex.getPosition(),
            valueMap.getOrDefault(vertex.getPosition(), 0.0) + base
        );
        visited.add(vertex.getPosition());

        Double value = base / FACTOR;

        for (Vertex neighbor: vertex.getAdjacentVertices()) {
            if (gameState.getMines().containsKey(neighbor.getPosition()) ||
                gameState.getPubs().containsKey(neighbor.getPosition()) ||
                visited.contains(neighbor.getPosition())) {
                continue;
            }

            // dummy logic to avoid dead end
            if (vertex.getAdjacentVertices().size() == 1) {
                diffuseMap(gameState, valueMap, visited, neighbor, value / 2.0, distance - 1);
            } else {
                diffuseMap(gameState, valueMap, visited, neighbor, value, distance - 1);
            }
        }
    }

    @Override
    public void setup() {

    }

    @Override
    public void shutdown() {

    }
}