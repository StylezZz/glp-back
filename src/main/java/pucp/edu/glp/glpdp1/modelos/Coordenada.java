package pucp.edu.glp.glpdp1.modelos;

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
}
