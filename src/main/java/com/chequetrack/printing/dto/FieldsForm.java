package com.chequetrack.printing.dto;

import com.chequetrack.printing.domain.FieldCode;
import com.chequetrack.printing.domain.FontFamily;
import com.chequetrack.printing.domain.TextAlign;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FieldsForm {

    @Valid
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row {
        @NotNull
        private FieldCode fieldCode;
        @NotNull(message = "Enter X.")
        private Double x;
        @NotNull(message = "Enter Y.")
        private Double y;
        @NotNull(message = "Enter the width.")
        private Double width;
        @NotNull
        private FontFamily fontFamily;
        @NotNull(message = "Enter the font size.")
        private Double fontSize;
        private boolean bold;
        @NotNull
        private TextAlign align;
        private Double charSpacing;
        private Double line2X;
        private Double lineGap;
        private Double angle;
        private boolean printed;
    }
}
