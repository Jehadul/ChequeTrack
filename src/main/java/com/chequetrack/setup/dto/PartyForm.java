package com.chequetrack.setup.dto;

import com.chequetrack.setup.domain.PartyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartyForm {

    private Long id;
    private Long version;

    @NotNull(message = "Choose customer, supplier or both.")
    private PartyType partyType = PartyType.CUSTOMER;

    @NotBlank(message = "Enter a code.")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "Use up to 20 letters, digits, - or _.")
    private String code;

    @NotBlank(message = "Enter the name.")
    @Size(max = 150)
    private String name;

    @Size(max = 100)
    private String contactPerson;

    @Size(max = 50)
    private String phone;

    @Email(message = "Enter a valid email address.")
    @Size(max = 150)
    private String email;

    @Size(max = 300)
    private String address;

    @Pattern(regexp = "^$|\\d{9,15}", message = "TIN must be 9-15 digits.")
    private String tin;
}
