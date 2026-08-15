package com.vorconcierge.ragcore.security;

/**
 * Fixed, platform-defined set of capability bits. Organizations cannot invent new
 * capabilities — every bit corresponds to a real enforced code path, so a new capability
 * always requires a code change regardless of whether roles are configurable. What IS
 * per-organization and dynamic: role names, hierarchy levels, department structure, and
 * which of these fixed bits a given role is granted (see the roles table).
 */
public enum Capability {
    UPLOAD_DOCUMENTS(1L),
    MANAGE_USERS(1L << 1),
    MANAGE_DEPARTMENTS(1L << 2),
    MANAGE_BILLING(1L << 3),
    VIEW_ALL_ORG_CHATS(1L << 4),
    MANAGE_ROLES(1L << 5),
    INVITE_USERS(1L << 6),
    VIEW_ANALYTICS(1L << 7);

    private final long bit;

    Capability(long bit) {
        this.bit = bit;
    }

    public long getBit() {
        return bit;
    }

    public static long allBits() {
        long mask = 0;
        for (Capability c : values()) {
            mask |= c.getBit();
        }
        return mask;
    }

    public static boolean has(long mask, Capability capability) {
        return (mask & capability.getBit()) != 0;
    }
}
