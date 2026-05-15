package de.jamsintown.announcement;

import java.util.List;

public class AnnouncementRequest {

    public enum RecipientFilter { ALL, GROUP, SPECIFIC }

    public String subject;
    public String body;
    public RecipientFilter recipientFilter = RecipientFilter.ALL;
    public Long groupId;
    public List<Long> userIds;
}
