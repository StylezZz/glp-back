package pucp.edu.glp.glpdp1.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pucp.edu.glp.glpdp1.algorithms.GA;
import pucp.edu.glp.glpdp1.algorithms.GeneticoResolver;

@RestController
@RequestMapping("/api/genetic")
public class GeneticController {

    private final GeneticoResolver resolver;

    public GeneticController(GeneticoResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping(path = "/run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GA.Individual> run(
            @RequestPart("pedidos")  MultipartFile pedidosFile,
            @RequestPart("bloqueos") MultipartFile bloqueosFile,
            @RequestPart("averias")  MultipartFile averiasFile
    ) throws Exception {
        byte[] pedidos   = pedidosFile.getBytes();
        byte[] bloqueos  = bloqueosFile.getBytes();
        byte[] averias   = averiasFile.getBytes();

        GA.Individual solution = resolver.solve(pedidos, bloqueos, averias);
        return ResponseEntity.ok(solution);
    }
}
