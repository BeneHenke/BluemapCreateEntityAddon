package eu.cronmoth.createentityaddon;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Config {

    private boolean bakeSableSublevelsIntoMap = true;

    @Override
    public String toString() {
        return """
                # CreateEntityConfig
                # Determines whether the sublevels are baked into the map or saved in individual files to be served by the API.
                bakeSableSublevelsIntoMap: %s
                """.formatted(bakeSableSublevelsIntoMap);
    }
}