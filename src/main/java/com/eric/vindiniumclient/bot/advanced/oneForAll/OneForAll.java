package com.eric.vindiniumclient.bot.advanced.oneForAll;

import com.eric.vindiniumclient.bot.BotMove;
import com.eric.vindiniumclient.bot.BotUtils;
import com.eric.vindiniumclient.bot.advanced.AdvancedBot;
import com.eric.vindiniumclient.bot.advanced.AdvancedGameState;
import com.eric.vindiniumclient.bot.advanced.Vertex;
import com.eric.vindiniumclient.dto.GameState;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class OneForAll implements AdvancedBot {
    private final Double FACTOR = .25;
    private final Double BASE_VALUE = 100.0;

    @Override
    public BotMove move(AdvancedGameState gameState) {
        Table<Integer, Integer, Double> valueMap = HashBasedTable.create();

        gameState.getMines()
            .values()
            .stream()
            .filter(mine -> mine.getOwner().getName().equals(gameState.getMe().getName()))
            .forEach(mine -> {
                Vertex v = gameState.getBoardGraph().get(mine.getPosition());
                diffuseMap(valueMap, v, BASE_VALUE, 15);
            });

        gameState.getPubs()
            .values()
            .stream()
            .forEach(pub -> {
                Vertex v = gameState.getBoardGraph().get(pub.getPosition());
                // random formula to reason on mine
                Double value = BASE_VALUE / 5 * gameState.getMe().getMineCount() * ((100 - gameState.getMe().getLife()) / 100);
                diffuseMap(valueMap, v, value, 10);
            });

        // stay away or get close to enemy hero
        gameState.getHeroesByPosition()
            .values()
            .stream()
            .filter(hero -> hero.getName().equals(gameState.getMe().getName()))
            .forEach(hero -> {
                Vertex v = gameState.getBoardGraph().get(hero.getPos());
                boolean winnable = hero.getLife() + 10 < gameState.getMe().getLife();
                // TODO: may need to take care of the tavern distance later

                double value = (winnable) ?
                    BASE_VALUE * hero.getMineCount() * ((100 - hero.getLife()) / 100) :
                    BASE_VALUE * -1 * gameState.getMe().getMineCount() * ((100 - gameState.getMe().getLife()) / 100);

                diffuseMap(valueMap, v, value, 5);
            });

        return findBestNextPath(gameState, valueMap);
    }

    private BotMove findBestNextPath(AdvancedGameState gameState, Table<Integer, Integer, Double> valueMap) {
        Vertex vertex = gameState.getBoardGraph().get(gameState.getMe().getPos());
        double maxValue = Double.MIN_VALUE;
        GameState.Position max = gameState.getMe().getPos();

        for (Vertex neighbor: vertex.getAdjacentVertices()) {
            GameState.Position pos = neighbor.getPosition();
            double value = valueMap.get(pos.getX(), pos.getY());
            if (value > maxValue) {
                maxValue = value;
                max = pos;
            }
        }

        return BotUtils.directionTowards(gameState.getMe().getPos(), max);
    }

    private void diffuseMap(
        Table<Integer, Integer, Double> valueMap,
        Vertex vertex,
        Double base,
        Integer distance
    ) {
        valueMap.put(
            vertex.getPosition().getX(),
            vertex.getPosition().getY(),
            valueMap.get(vertex.getPosition().getX(), vertex.getPosition().getY()) + base
        );

        double value = base;

        for (int i = 0; i < distance; i ++) {
            value *= FACTOR;

            for (Vertex neighbor: vertex.getAdjacentVertices()) {
                valueMap.put(
                    neighbor.getPosition().getX(),
                    vertex.getPosition().getY(),
                    valueMap.get(neighbor.getPosition().getX(), neighbor.getPosition().getY()) + value
                );
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