package de.jamsintown.announcement;

public record Recipient(Long id, String name, String email) {

    public static Recipient fromEmail(String email) {
        return new Recipient(null, null, email);
    }
}
