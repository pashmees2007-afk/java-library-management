package com.library.model;

import java.time.LocalDate;

/** A library member. {@code maxBooks} overrides the membership type's limit when set. */
public record Member(
        int id,
        String code,
        String name,
        String email,
        String phone,
        String address,
        MembershipType type,
        LocalDate joinDate,
        boolean active,
        Integer maxBooks) {

    /** How many books this member may hold at once. */
    public int borrowingLimit() {
        return maxBooks != null ? maxBooks : type.defaultLimit();
    }

    /** Phone if known, otherwise email, otherwise a dash. */
    public String contact() {
        if (phone != null && !phone.isBlank()) {
            return phone;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "-";
    }

    public String display() {
        return code + "  " + name;
    }
}
