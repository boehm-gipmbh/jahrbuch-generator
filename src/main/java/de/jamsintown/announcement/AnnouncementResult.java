package de.jamsintown.announcement;

import java.util.List;

public class AnnouncementResult {

    public int sent;
    public int failed;
    public List<String> errors;

    public AnnouncementResult(int sent, int failed, List<String> errors) {
        this.sent = sent;
        this.failed = failed;
        this.errors = errors;
    }
}
