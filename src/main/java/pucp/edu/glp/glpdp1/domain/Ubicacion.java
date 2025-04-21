package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Ubicacion {
    private int x;
    private int y;

    public Ubicacion(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
