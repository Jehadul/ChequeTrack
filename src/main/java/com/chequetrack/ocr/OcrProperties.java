package com.chequetrack.ocr;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(boolean enabled,
                            @NotBlank String command,
                            // "eng" (built in), or a MICR E-13B model such as "e13b" / "mcr" placed in tessdata
                            @NotBlank String language,
                            // folder with the .traineddata files; blank = Tesseract's own
                            String tessdataDir,
                            @Min(1) int timeoutSeconds,
                            @Min(1) int maxConcurrent) {
}
