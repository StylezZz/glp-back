package pucp.edu.glp.glpdp1.models;

import java.util.Objects;

public class Coordenada {
    private int x;
    private int y;

    public Coordenada(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public double distancia(Coordenada coordenada){
        return Math.abs(this.x - coordenada.getX()) + Math.abs(this.y - coordenada.getY());
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof Coordenada)) return false;
        Coordenada otra = (Coordenada) obj;
        return this.x == otra.getX() && this.y == otra.getY();
    }

    @Override
    public int hashCode(){
        return Objects.hash(x,y);
    }

    @Override
    public String toString(){
        return "("+x+","+y+")";
    }
}
