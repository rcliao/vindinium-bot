package com.eric.vindiniumclient.bot.advanced.oneForAll;

import com.eric.vindiniumclient.bot.BotMove;
import com.eric.vindiniumclient.bot.BotUtils;
import com.eric.vindiniumclient.bot.advanced.*;
import com.eric.vindiniumclient.dto.GameState;
import com.google.api.client.util.Maps;
import com.google.api.client.util.Sets;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.EvictingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class OneForAll implements AdvancedBot {
    private final Double FACTOR = 2.0;
    private final Double BASE_VALUE = 1000.0;
    private final Map<Mine, Double> mineAccum = Maps.newHashMap();
    private final Map<Pub, Double> pubAccum = Maps.newHashMap();
    private EvictingQueue<GameState.Position> lastPositions = EvictingQueue.create(2);

    private static final Logger logger = LogManager.getLogger(OneForAll.class);

    @Override
    public BotMove move(AdvancedGameState gameState) {
        Stopwatch watch = Stopwatch.createStarted();

        Map<GameState.Position, Double> valueMap = Maps.newHashMap();

        if (gameState.getMe().getLife() < 60) {
            pubAccum.keySet()
                .stream()
                .forEach(pub -> {
					pubAccum.put(pub, 0.9);
                });
        }

        // if the mine is contested, reset the accumulator
        mineAccum.keySet()
            .stream()
            .forEach(oldMine -> {
                gameState.getMines()
                    .values()
                    .stream()
                    .forEach(newMine -> {
                        if (newMine.getPosition().equals(oldMine.getPosition()) &&
                            oldMine.getOwner() != null &&
                            newMine.getOwner() != null &&
                            newMine.getOwner().getId() != oldMine.getOwner().getId()) {
                            mineAccum.put(oldMine, 0.9);
                        }
                    });
            });

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
                            (60.0 - gameState.getMe().getLife() > 0.0) ?
                                ((100.0 - gameState.getMe().getLife()) / 100.0) :
                                (gameState.getMe().getMineCount() / (gameState.getMines().size() / 1.5))
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
                if (hero.getLife() < 30) {
                    valueMap.put(hero.getSpawnPos(), -Double.MAX_VALUE);
                }

                Vertex v = gameState.getBoardGraph().get(hero.getPos());
                boolean winnable = hero.getLife() < gameState.getMe().getLife() &&
                    hero.getLife() / 20 < getDistance(hero.getPos(), getClosetTavern(gameState, hero).get());

                double value = (winnable) ?
                    BASE_VALUE * hero.getMineCount() * (hero.getLife() / 20.0) :
                    BASE_VALUE * -1.0 * gameState.getMe().getMineCount() * (gameState.getMe().getLife() / 20.0);

                diffuseMap(gameState, valueMap, Sets.newHashSet(), v, value, 6);
            });

        gameState.getHeroesByPosition()
            .values()
            .stream()
            .filter(hero -> hero.getName().equals(gameState.getMe().getName()) && hero.getId() != gameState.getMe().getId())
            .forEach(hero -> {
                Vertex v = gameState.getBoardGraph().get(hero.getPos());

                diffuseMap(gameState, valueMap, Sets.newHashSet(), v, -BASE_VALUE * 2, 1);
            });

        logger.info("OneForAll bot diffused hero values " + watch.elapsed(TimeUnit.MILLISECONDS));

        printValueMap(valueMap, gameState.getGameState().getGame().getBoard().getSize(), gameState.getMe().getPos());

        return findBestNextPath(gameState, valueMap);
    }

    private double getDistance(GameState.Position pos, GameState.Position closetTavern) {
        // TODO: maybe replace with BFS or Astar to find path and cost
        return Math.sqrt(Math.pow(pos.getX() - closetTavern.getX(), 2) + Math.pow(pos.getY() - closetTavern.getY(), 2));
    }

    private Optional<GameState.Position> getClosetTavern(AdvancedGameState gameState, GameState.Hero hero) {
        double value = Double.MIN_VALUE;
        Optional<GameState.Position> result = Optional.absent();

        for (Pub tavern: gameState.getPubs().values()) {
            double temp = getDistance(tavern.getPosition(), hero.getPos());
            if (value < temp) {
                value = temp;
                result = Optional.of(tavern.getPosition());
            }
        }

        return result;
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