package de.jamsintown.announcement;

import java.util.List;

public class AnnouncementRequest {

    public enum RecipientFilter { ALL, GROUP, SPECIFIC, EXTERNAL }

    public String subject;
    public String body;
    public RecipientFilter recipientFilter = RecipientFilter.ALL;
    public Long groupId;
    public List<Long> userIds;
    public List<String> externalEmails;

    public String attachmentFilename;
    public String attachmentContent; // base64-encoded
    public Long attachmentGroupId;   // generate PDF from group
}
