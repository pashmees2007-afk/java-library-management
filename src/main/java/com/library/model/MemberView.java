package com.library.model;

/** A member together with the counts shown in the members table. */
public record MemberView(Member member, int onLoan, double dues) {

    public int id() {
        return member.id();
    }

    public String name() {
        return member.name();
    }

    public boolean atLimit() {
        return onLoan >= member.borrowingLimit();
    }
}
