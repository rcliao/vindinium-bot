package com.eric.vindiniumclient.bot.advanced;

import com.eric.vindiniumclient.dto.GameState;

/**
 * Represents a mine on the map
 */
public class Mine {

    // Mines don't move, but the owners change
    private final GameState.Position position;
    private GameState.Hero owner;

    public Mine(GameState.Position position, GameState.Hero owner) {
        this.position = position;
        this.owner = owner;
    }

    public GameState.Position getPosition() {
        return position;
    }

    public GameState.Hero getOwner() {
        return owner;
    }

    public void setOwner(GameState.Hero hero) { this.owner = hero; }

    @Override
    public String toString() {
        return "Mine{" +
            "position=" + position +
            ", owner=" + owner +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mine)) return false;

        Mine mine = (Mine) o;

        if (!getPosition().equals(mine.getPosition())) return false;
        return !(getOwner() != null ? !getOwner().equals(mine.getOwner()) : mine.getOwner() != null);

    }

    @Override
    public int hashCode() {
        int result = getPosition().hashCode();
        result = 31 * result + (getOwner() != null ? getOwner().hashCode() : 0);
        return result;
    }
}
