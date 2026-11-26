package com.appraisal.domain;

/** Container for small enums shared across the domain. */
public final class Enums {
    private Enums() {}

    /** COMMON = company-wide competencies (shared by all teams); ROLE = team-specific questions. */
    public enum Block { COMMON, ROLE }

    /** Who may answer a question. */
    public enum ReviewedBy { ALL, TL_MANAGER_ONLY }

    /** Application roles (a user can hold several). */
    public enum Role { ADMIN, MANAGER, TL, EMPLOYEE }

    /** The four review columns of the appraisal sheet. */
    public enum ReviewerRole { SELF, PEER, TL, MANAGER }

    public enum CycleStatus { DRAFT, OPEN, CLOSED }

    public enum ReviewStatus { DRAFT, SUBMITTED }

    public enum PickListType { SITUATION, BUSINESS_IMPACT }
}
