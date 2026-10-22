package com.library.db;

/** Member details as typed into the form; validated by the data layer. */
public record MemberDraft(
        String code,
        String name,
        String email,
        String phone,
        String address,
        String type,
        String joinDate,
        boolean active,
        String maxBooks) {

    /** A new member with just the essentials; the code is assigned automatically. */
    public static MemberDraft of(String name, String type, String email, String phone) {
        return new MemberDraft("", name, email, phone, "", type, "", true, "");
    }
}
