package de.jamsintown.announcement;

import de.jamsintown.pdf.PdfOptions;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
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
    public PdfOptions pdfOptions;    // optional PDF generation options for GROUP_PDF mode
}
