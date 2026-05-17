package de.jamsintown.announcement;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public class AnnouncementResult {

    public int sent;
    public int failed;
    public List<String> errors;

    public AnnouncementResult() {}

    public AnnouncementResult(int sent, int failed, List<String> errors) {
        this.sent = sent;
        this.failed = failed;
        this.errors = errors;
    }
}
