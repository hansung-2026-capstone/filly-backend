package net.coboogie.fillybackend.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AiAnalysisResponse {
    private String filename;
    private String caption;
}