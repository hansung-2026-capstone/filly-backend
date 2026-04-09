package net.coboogie.fillybackend.blip.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlipAnalyzeResponse {

    private String status;
    private Data data;

    @Getter
    @Setter
    public static class Data {
        private String caption;
        private String mood;
    }
}
