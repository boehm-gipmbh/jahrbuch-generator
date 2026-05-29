package de.jamsintown.pdf;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import de.jamsintown.bild.Bild;
import de.jamsintown.comment.Comment;
import de.jamsintown.reaction.Reaction;
import de.jamsintown.story.Story;
import de.jamsintown.text.Text;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ApplicationScoped
public class PdfService {

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String capturesPath;

    private final UserService userService;
    private final Vertx vertx;
    private final ObjectMapper objectMapper;

    /** Pfad zur extrahierten DejaVuSans-Temp-Datei — einmal gesetzt, nie wieder geändert. */
    private static volatile java.nio.file.Path fontTempPath = null;

    private static PdfFont makeUnicodeFont() {
        try {
            java.nio.file.Path path = fontTempPath;
            if (path == null) {
                try (var in = PdfService.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
                    if (in == null) {
                        log.warn("DejaVuSans.ttf nicht gefunden im Classpath");
                        return null;
                    }
                    path = java.nio.file.Files.createTempFile("DejaVuSans", ".ttf");
                    java.nio.file.Files.write(path, in.readAllBytes());
                    path.toFile().deleteOnExit();
                    fontTempPath = path;
                    log.info("DejaVuSans.ttf extrahiert nach {}", path);
                }
            }
            PdfFont font = PdfFontFactory.createFont(path.toString(), PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
            font.setSubset(false);
            return font;
        } catch (Exception e) {
            log.warn("DejaVuSans konnte nicht geladen werden: {}", e.getMessage());
            return null;
        }
    }

    @Inject
    public PdfService(UserService userService, Vertx vertx, ObjectMapper objectMapper) {
        this.userService = userService;
        this.vertx = vertx;
        this.objectMapper = objectMapper;
    }

    @WithSession
    Uni<PdfSettings> loadPdfSettings(Long groupId) {
        return Gruppe.<Gruppe>findById(groupId)
            .map(gruppe -> {
                if (gruppe == null || gruppe.pdfSettings == null) return PdfSettings.defaults();
                try {
                    return objectMapper.readValue(gruppe.pdfSettings, PdfSettings.class);
                } catch (Exception e) {
                    log.warn("Konnte PDF-Einstellungen nicht parsen für Gruppe {}: {}", groupId, e.getMessage());
                    return PdfSettings.defaults();
                }
            });
    }

    public Uni<byte[]> generateForGroup(Long groupId) {
        return generateForGroup(groupId, null);
    }

    public Uni<byte[]> generateForGroup(Long groupId, PdfOptions options) {
        return loadPdfSettings(groupId)
            .chain(settings -> generateForGroup(groupId, options, settings));
    }

    public Uni<byte[]> generateForGroup(Long groupId, PdfOptions options, PdfSettings settings) {
        return loadAllStoryData(groupId, options)
            .chain(storyDataList -> Uni.createFrom()
                .item(() -> renderPdf(storyDataList, options, false, settings))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
    }

    /** Generates a PDF without membership check \u2014 for admin use only. Uses compact image quality to stay within email attachment limits. */
    public Uni<byte[]> generateForGroupAsAdmin(Long groupId, PdfOptions options) {
        return loadPdfSettings(groupId)
            .chain(settings -> generateForGroupAsAdmin(groupId, options, settings));
    }

    public Uni<byte[]> generateForGroupAsAdmin(Long groupId, PdfOptions options, PdfSettings settings) {
        return loadAllStoryDataNoCheck(groupId, options)
            .chain(storyDataList -> {
                io.vertx.core.Context eventLoopCtx = vertx.getOrCreateContext();
                return Uni.createFrom()
                    .item(() -> renderPdf(storyDataList, options, true, settings))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                    .emitOn(cmd -> eventLoopCtx.runOnContext(ignored -> cmd.run()));
            });
    }

    @WithSession
    Uni<List<StoryData>> loadAllStoryData(Long groupId, PdfOptions options) {
        return userService.getCurrentUser()
            .chain(user -> Gruppe.<Gruppe>findById(groupId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden: " + groupId))
                .onItem().invoke(gruppe -> {
                    boolean isMember = user.groups != null && user.groups.stream().anyMatch(g -> groupId.equals(g.id));
                    boolean isManager = (user.managedGroup != null && groupId.equals(user.managedGroup.id))
                        || (user.managedGroups != null && user.managedGroups.stream().anyMatch(g -> groupId.equals(g.id)));
                    if (!isMember && !isManager) {
                        throw new UnauthorizedException("Kein Zugriff auf Gruppe: " + groupId);
                    }
                }))
            .chain(gruppe -> loadStoriesOrdered(gruppe, options)
                .chain(stories -> Multi.createFrom().iterable(stories)
                    .onItem().transformToUniAndConcatenate(s -> loadStoryData(s, options))
                    .collect().asList())
                .chain(storyDataList -> appendPendingData(storyDataList, gruppe, options)));
    }

    @WithSession
    Uni<List<StoryData>> loadAllStoryDataNoCheck(Long groupId, PdfOptions options) {
        return Gruppe.<Gruppe>findById(groupId)
            .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden: " + groupId))
            .chain(gruppe -> loadStoriesOrdered(gruppe, options)
                .chain(stories -> Multi.createFrom().iterable(stories)
                    .onItem().transformToUniAndConcatenate(s -> loadStoryData(s, options))
                    .collect().asList())
                .chain(storyDataList -> appendPendingData(storyDataList, gruppe, options)));
    }

    private Uni<List<Story>> loadStoriesOrdered(Gruppe gruppe, PdfOptions options) {
        if (options != null && options.storyIds() != null) {
            if (options.storyIds().isEmpty()) {
                return Uni.createFrom().item(List.of());
            }
            return Story.<Story>find("group = ?1 AND id IN ?2", gruppe, options.storyIds())
                .list()
                .map(stories -> {
                    Map<Long, Story> byId = stories.stream().collect(Collectors.toMap(s -> s.id, s -> s));
                    return options.storyIds().stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                });
        }
        return Story.<Story>find("group = ?1", Sort.by("created"), gruppe).list();
    }

    private Uni<List<StoryData>> appendPendingData(List<StoryData> storyDataList, Gruppe gruppe, PdfOptions options) {
        if (options == null || (!options.includePendingBilder() && !options.includePendingTexte())) {
            return Uni.createFrom().item(storyDataList);
        }

        Uni<List<Bild>> bilderUni = options.includePendingBilder()
            ? Bild.<Bild>find("group = ?1 AND story IS NULL AND deleted = false", Sort.by("created"), gruppe).list()
            : Uni.createFrom().item(List.of());

        return bilderUni.chain(bilder -> {
            Uni<List<Text>> texteUni = options.includePendingTexte()
                ? Text.<Text>find("group = ?1 AND story IS NULL AND deleted = false", Sort.by("created"), gruppe).list()
                : Uni.createFrom().item(List.of());
            return texteUni.map(texte -> {
                if (bilder.isEmpty() && texte.isEmpty()) return storyDataList;
                List<StoryData> result = new ArrayList<>(storyDataList);
                result.add(new StoryData(null, bilder, texte, Map.of(), Map.of()));
                return result;
            });
        });
    }

    private Uni<StoryData> loadStoryData(Story story) {
        return loadStoryData(story, null);
    }

    private Uni<StoryData> loadStoryData(Story story, PdfOptions options) {
        return Bild.<Bild>find(
            "story = ?1 AND deleted = false",
            Sort.by("storyColumn").and("storyPosition"),
            story
        ).list()
        .chain(bilder -> Text.<Text>find(
            "story = ?1 AND deleted = false",
            Sort.by("storyColumn").and("storyPosition"),
            story
        ).list()
        .chain(texte -> loadItemStats(bilder, texte, options)
            .map(stats -> new StoryData(story, bilder, texte, stats[0], stats[1]))));
    }

    /** Returns [bildStats, textStats] */
    private Uni<Map<Long, ItemStats>[]> loadItemStats(List<Bild> bilder, List<Text> texte, PdfOptions options) {
        boolean wantReactions = options == null || options.includeReactions();
        boolean wantComments  = options == null || options.includeComments();
        int depth    = (options != null && options.commentDepth() > 0) ? options.commentDepth() : 1;
        int maxItems = options != null ? options.commentMaxPerItem() : 5;

        if (!wantReactions && !wantComments) {
            @SuppressWarnings("unchecked")
            Map<Long, ItemStats>[] empty = new Map[]{Map.of(), Map.of()};
            return Uni.createFrom().item(empty);
        }

        List<Long> bildIds = bilder.stream().map(b -> b.id).filter(Objects::nonNull).toList();
        List<Long> textIds = texte.stream().map(t -> t.id).filter(Objects::nonNull).toList();

        Uni<Map<Long, ItemStats>> bildStatsUni = buildStats(Reaction.TargetType.BILD, bildIds, wantReactions, wantComments, depth, maxItems);
        Uni<Map<Long, ItemStats>> textStatsUni = buildStats(Reaction.TargetType.TEXT, textIds, wantReactions, wantComments, depth, maxItems);

        return bildStatsUni.chain(bildStats ->
            textStatsUni.map(textStats -> {
                @SuppressWarnings("unchecked")
                Map<Long, ItemStats>[] result = new Map[]{bildStats, textStats};
                return result;
            }));
    }

    private Uni<Map<Long, ItemStats>> buildStats(
            Reaction.TargetType targetType, List<Long> ids,
            boolean wantReactions, boolean wantComments, int depth, int maxItems) {

        if (ids.isEmpty()) return Uni.createFrom().item(Map.of());

        Uni<Map<Long, long[]>> reactionsUni = wantReactions
            ? Reaction.<Reaction>list(
                "targetType = ?1 AND targetId IN ?2 AND reactionType IN ?3",
                targetType, ids, List.of(Reaction.ReactionType.LIKE, Reaction.ReactionType.VOTE))
              .map(list -> {
                  Map<Long, long[]> m = new HashMap<>();
                  for (Reaction r : list) {
                      long[] counts = m.computeIfAbsent(r.targetId, k -> new long[2]);
                      if (r.reactionType == Reaction.ReactionType.LIKE)  counts[0]++;
                      if (r.reactionType == Reaction.ReactionType.VOTE)  counts[1]++;
                  }
                  return m;
              })
            : Uni.createFrom().item(Map.of());

        Uni<Map<Long, List<Comment>>> commentsUni = wantComments
            ? Comment.<Comment>list(
                "SELECT c FROM Comment c JOIN FETCH c.user WHERE c.targetType = ?1 AND c.targetId IN ?2 AND c.deletedAt IS NULL ORDER BY c.createdAt",
                targetType, ids)
              .map(list -> list.stream().collect(Collectors.groupingBy(c -> c.targetId)))
            : Uni.createFrom().item(Map.of());

        return reactionsUni.chain(reactions ->
            commentsUni.map(commentsMap -> {
                Map<Long, ItemStats> result = new HashMap<>();
                for (Long id : ids) {
                    long[] counts = reactions.getOrDefault(id, new long[2]);
                    List<Comment> allComments = commentsMap.getOrDefault(id, List.of());

                    // Build comment tree respecting depth
                    List<String[]> commentLines = buildCommentLines(allComments, depth, maxItems);
                    result.put(id, new ItemStats(counts[0], counts[1], commentLines));
                }
                return result;
            }));
    }

    /** Builds flat list of [authorName, text, indent] respecting depth and maxItems.
     *  Depth 1: top-level only. Depth 2: top-level + direct replies. */
    private List<String[]> buildCommentLines(List<Comment> comments, int depth, int max) {
        List<Comment> topLevel = comments.stream()
            .filter(c -> c.parentId == null)
            .toList();
        List<String[]> lines = new ArrayList<>();
        for (Comment c : topLevel) {
            if (max > 0 && lines.size() >= max) break;
            lines.add(new String[]{c.user != null ? c.user.name : "?", c.content, "0"});
            if (depth >= 2) {
                List<Comment> replies = comments.stream()
                    .filter(r -> r.parentId != null && r.parentId.equals(c.id))
                    .toList();
                for (Comment r : replies) {
                    if (max > 0 && lines.size() >= max) break;
                    lines.add(new String[]{r.user != null ? r.user.name : "?", r.content, "1"});
                }
            }
        }
        return lines;
    }

    byte[] renderPdf(List<StoryData> stories, PdfOptions options) {
        return renderPdf(stories, options, false, PdfSettings.defaults());
    }

    byte[] renderPdf(List<StoryData> stories, PdfOptions options, boolean compact) {
        return renderPdf(stories, options, compact, PdfSettings.defaults());
    }

    byte[] renderPdf(List<StoryData> stories, PdfOptions options, boolean compact, PdfSettings settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            WriterProperties writerProps = new WriterProperties();
            if (settings.pdfPassword() != null && !settings.pdfPassword().isBlank()) {
                writerProps.setStandardEncryption(
                    settings.pdfPassword().getBytes(),
                    null,
                    EncryptionConstants.ALLOW_PRINTING,
                    EncryptionConstants.ENCRYPTION_AES_128
                );
            }
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out, writerProps));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(40, 40, 50, 40);
            PdfFont unicodeFont = makeUnicodeFont();
            if (unicodeFont != null) {
                doc.setFont(unicodeFont);
            }

            if (options != null && options.pageNumbers()) {
                pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageNumberHandler());
            }
            if (options != null && options.passepartoutStyle() != null && !options.passepartoutStyle().isBlank() && !"none".equals(options.passepartoutStyle())) {
                pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PassepartoutHandler(options.passepartoutStyle()));
            }

            if (stories.isEmpty()) {
                doc.add(new Paragraph("Keine Stories vorhanden."));
                doc.close();
                return out.toByteArray();
            }

            if (options != null && options.coverPage()) {
                String title = options.coverTitle() != null && !options.coverTitle().isBlank()
                    ? options.coverTitle() : "Jahrbuch";
                renderCoverPage(doc, title);
                doc.add(new AreaBreak());
            }

            for (int i = 0; i < stories.size(); i++) {
                renderStory(doc, stories.get(i), i, options, compact, settings);
                if (i < stories.size() - 1) {
                    doc.add(new AreaBreak());
                }
            }

            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF-Generierung fehlgeschlagen", e);
        }
        return out.toByteArray();
    }

    private void renderCoverPage(Document doc, String title) {
        float pageHeight = doc.getPdfDocument().getDefaultPageSize().getHeight();
        doc.add(new Paragraph(title)
            .setFontSize(36)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(pageHeight / 2 - 80));
    }

    // Pastel palette \u2014 8 colors, one per story (cycling)
    private static final DeviceRgb[] STORY_COLORS = {
        new DeviceRgb(0.36f, 0.54f, 0.66f), // steel blue
        new DeviceRgb(0.55f, 0.35f, 0.53f), // mauve
        new DeviceRgb(0.22f, 0.55f, 0.47f), // teal
        new DeviceRgb(0.76f, 0.40f, 0.20f), // terracotta
        new DeviceRgb(0.35f, 0.47f, 0.62f), // slate
        new DeviceRgb(0.60f, 0.38f, 0.30f), // brick
        new DeviceRgb(0.28f, 0.52f, 0.40f), // forest
        new DeviceRgb(0.50f, 0.42f, 0.60f), // lavender
    };

    private void renderStory(Document doc, StoryData sd) {
        renderStory(doc, sd, 0, null, false, PdfSettings.defaults());
    }

    private void renderStory(Document doc, StoryData sd, int storyIndex) {
        renderStory(doc, sd, storyIndex, null, false, PdfSettings.defaults());
    }

    private void renderStory(Document doc, StoryData sd, int storyIndex, PdfOptions options, boolean compact, PdfSettings settings) {
        Story story = sd.story();
        String layout = story != null ? story.layout : null;

        if ("scrapbook".equals(layout)) {
            DeviceRgb color = STORY_COLORS[storyIndex % STORY_COLORS.length];
            String title = story != null ? story.name : "Sonstige";
            String subtitle = story != null ? story.description : null;
            renderStoryHeader(doc, title, subtitle, color, settings);
            renderScrapbook(doc, sd, color, compact, settings);
        } else if ("grid".equals(layout)) {
            String title = story != null ? story.name : "Sonstige";
            doc.add(new Paragraph(title).setFontSize(settings.storyHeaderTitleSize()).setBold().setMarginBottom(4));
            if (story != null && story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description).setFontSize(settings.storyHeaderSubtitleSize()).setItalic().setMarginBottom(8));
            }
            renderThreeColumn(doc, sd, compact);
        } else if ("1col".equals(layout)) {
            String title = story != null ? story.name : "Sonstige";
            doc.add(new Paragraph(title).setFontSize(settings.storyHeaderTitleSize()).setBold().setMarginBottom(4));
            if (story != null && story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description).setFontSize(settings.storyHeaderSubtitleSize()).setItalic().setMarginBottom(8));
            }
            renderOneColumn(doc, sd, compact);
        } else {
            // Default: 2-column classic (layout == "2col" or null)
            String title = story != null ? story.name : "Sonstige";
            doc.add(new Paragraph(title).setFontSize(settings.storyHeaderTitleSize()).setBold().setMarginBottom(4));
            if (story != null && story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description).setFontSize(settings.storyHeaderSubtitleSize()).setItalic().setMarginBottom(8));
            }
            renderTwoColumn(doc, sd, compact);
        }
    }

    private void renderStoryHeader(Document doc, String title, String subtitle, DeviceRgb color, PdfSettings settings) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
        Cell titleCell = new Cell().setBorder(null)
            .setBackgroundColor(color)
            .setPadding(12);
        titleCell.add(new Paragraph(title)
            .setFontSize(settings.storyHeaderTitleSize()).setBold()
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
            .setMargin(0));
        if (subtitle != null && !subtitle.isBlank()) {
            titleCell.add(new Paragraph(subtitle)
                .setFontSize(settings.storyHeaderSubtitleSize())
                .setFontColor(new DeviceRgb(0.9f, 0.9f, 0.9f))
                .setMarginTop(3).setMarginBottom(0));
        }
        header.addCell(titleCell);
        doc.add(header);
        doc.add(new Paragraph("").setMarginBottom(10));
    }

    private void renderScrapbook(Document doc, StoryData sd, DeviceRgb accentColor) {
        renderScrapbook(doc, sd, accentColor, false, PdfSettings.defaults());
    }

    private void renderScrapbook(Document doc, StoryData sd, DeviceRgb accentColor, boolean compact, PdfSettings settings) {
        List<Bild> bilder = sd.bilder();
        List<Text> texte = sd.texte();

        if (bilder.isEmpty() && texte.isEmpty()) return;

        // Hauptbilder as full-width heroes; fall back to first image if none marked
        List<Bild> heroes = bilder.stream().filter(b -> b.hauptbild).toList();
        List<Bild> restBilder;
        if (heroes.isEmpty() && !bilder.isEmpty()) {
            heroes = List.of(bilder.get(0));
            restBilder = bilder.subList(1, bilder.size());
        } else {
            restBilder = bilder.stream().filter(b -> !b.hauptbild).toList();
        }

        for (int i = 0; i < heroes.size(); i++) {
            doc.add(buildPolaroidDiv(heroes.get(i), UnitValue.createPercentValue(94), i, true, compact,
                stats(sd.bildStats(), heroes.get(i).id), settings));
        }

        record Item(int pos, boolean isBild, Bild bild, Text text) {}
        List<Item> flow = Stream.concat(
            restBilder.stream().map(b -> new Item(b.storyPosition != null ? b.storyPosition : 0, true, b, null)),
            texte.stream().map(t -> new Item(t.storyPosition != null ? t.storyPosition : 0, false, null, t))
        ).sorted(Comparator.comparingInt(Item::pos)).toList();

        if (flow.isEmpty()) return;

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
            .useAllAvailableWidth().setMarginTop(8);
        Cell left = new Cell().setBorder(null).setPaddingRight(8).setVerticalAlignment(VerticalAlignment.TOP);
        Cell right = new Cell().setBorder(null).setPaddingLeft(8).setVerticalAlignment(VerticalAlignment.TOP);

        for (int i = 0; i < flow.size(); i++) {
            Item item = flow.get(i);
            Cell target = (i % 2 == 0) ? left : right;
            if (item.isBild()) {
                target.add(buildPolaroidDiv(item.bild(), UnitValue.createPercentValue(88), heroes.size() + i, false, compact,
                    stats(sd.bildStats(), item.bild().id), settings));
            } else {
                target.add(buildTextDiv(item.text(), stats(sd.textStats(), item.text().id), settings).setMarginTop(4).setMarginBottom(8));
            }
        }
        grid.addCell(left);
        grid.addCell(right);
        doc.add(grid);
    }

    private void renderOneColumn(Document doc, StoryData sd) {
        renderOneColumn(doc, sd, false);
    }

    private void renderOneColumn(Document doc, StoryData sd, boolean compact) {
        record Item(int pos, boolean isBild, Bild bild, Text text) {}
        List<Item> items = Stream.concat(
            sd.bilder().stream().map(b -> new Item(b.storyPosition != null ? b.storyPosition : 0, true, b, null)),
            sd.texte().stream().map(t -> new Item(t.storyPosition != null ? t.storyPosition : 0, false, null, t))
        ).sorted(Comparator.comparingInt(Item::pos)).toList();

        for (Item item : items) {
            if (item.isBild()) {
                doc.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100), compact, stats(sd.bildStats(), item.bild().id)));
            } else {
                doc.add(buildTextDiv(item.text(), stats(sd.textStats(), item.text().id)));
            }
        }
    }

    private void renderTwoColumn(Document doc, StoryData sd) {
        renderTwoColumn(doc, sd, false);
    }

    private void renderTwoColumn(Document doc, StoryData sd, boolean compact) {
        record Item(int pos, boolean isBild, Bild bild, Text text) {}

        List<Item> col0 = Stream.concat(
            sd.bilder().stream().filter(b -> b.storyColumn == null || b.storyColumn == 0)
                .map(b -> new Item(b.storyPosition != null ? b.storyPosition : 0, true, b, null)),
            sd.texte().stream().filter(t -> t.storyColumn == null || t.storyColumn == 0)
                .map(t -> new Item(t.storyPosition != null ? t.storyPosition : 0, false, null, t))
        ).sorted(Comparator.comparingInt(Item::pos)).toList();

        List<Item> col1 = Stream.concat(
            sd.bilder().stream().filter(b -> b.storyColumn != null && b.storyColumn == 1)
                .map(b -> new Item(b.storyPosition != null ? b.storyPosition : 0, true, b, null)),
            sd.texte().stream().filter(t -> t.storyColumn != null && t.storyColumn == 1)
                .map(t -> new Item(t.storyPosition != null ? t.storyPosition : 0, false, null, t))
        ).sorted(Comparator.comparingInt(Item::pos)).toList();

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(8);

        Cell leftCell = new Cell().setBorder(null).setPaddingRight(3).setVerticalAlignment(VerticalAlignment.TOP);
        for (Item item : col0) {
            if (item.isBild()) leftCell.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100), compact, stats(sd.bildStats(), item.bild().id)));
            else leftCell.add(buildTextDiv(item.text(), stats(sd.textStats(), item.text().id)));
        }

        Cell rightCell = new Cell().setBorder(null).setPaddingLeft(3).setVerticalAlignment(VerticalAlignment.TOP);
        for (Item item : col1) {
            if (item.isBild()) rightCell.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100), compact, stats(sd.bildStats(), item.bild().id)));
            else rightCell.add(buildTextDiv(item.text(), stats(sd.textStats(), item.text().id)));
        }

        table.addCell(leftCell);
        table.addCell(rightCell);
        doc.add(table);
    }

    private void renderThreeColumn(Document doc, StoryData sd) {
        renderThreeColumn(doc, sd, false);
    }

    private void renderThreeColumn(Document doc, StoryData sd, boolean compact) {
        record Item(int pos, boolean isBild, Bild bild, Text text) {}

        java.util.function.IntFunction<List<Item>> colItems = c -> Stream.concat(
            sd.bilder().stream().filter(b -> c == 0 ? (b.storyColumn == null || b.storyColumn == 0) : b.storyColumn != null && b.storyColumn == c)
                .map(b -> new Item(b.storyPosition != null ? b.storyPosition : 0, true, b, null)),
            sd.texte().stream().filter(t -> c == 0 ? (t.storyColumn == null || t.storyColumn == 0) : t.storyColumn != null && t.storyColumn == c)
                .map(t -> new Item(t.storyPosition != null ? t.storyPosition : 0, false, null, t))
        ).sorted(Comparator.comparingInt(Item::pos)).toList();

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth().setMarginBottom(8);

        for (int c = 0; c < 3; c++) {
            float padLeft = c > 0 ? 3 : 0;
            float padRight = c < 2 ? 3 : 0;
            Cell cell = new Cell().setBorder(null).setPaddingLeft(padLeft).setPaddingRight(padRight)
                .setVerticalAlignment(VerticalAlignment.TOP);
            for (Item item : colItems.apply(c)) {
                if (item.isBild()) cell.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100), compact, stats(sd.bildStats(), item.bild().id)));
                else cell.add(buildTextDiv(item.text(), stats(sd.textStats(), item.text().id)));
            }
            table.addCell(cell);
        }
        doc.add(table);
    }

    private byte[] loadScaledImageBytes(String path) {
        return loadScaledImageBytes(path, "1200x1200>", 80);
    }

    private byte[] loadScaledImageBytes(String path, String resize, int quality) {
        try {
            Process p = new ProcessBuilder(
                "convert", path, "-resize", resize, "-quality", String.valueOf(quality), "jpeg:-"
            ).start();
            byte[] bytes = p.getInputStream().readAllBytes();
            int exit = p.waitFor();
            if (exit != 0 || bytes.length == 0) {
                log.warn("ImageMagick konnte Bild nicht skalieren (exit {}): {}", exit, path);
                return null;
            }
            return bytes;
        } catch (Exception e) {
            log.warn("Bildskalierung fehlgeschlagen: {}", path);
            return null;
        }
    }

    private Div buildBildDiv(Bild bild, UnitValue width) {
        return buildBildDiv(bild, width, false, null, PdfSettings.defaults());
    }

    private Div buildBildDiv(Bild bild, UnitValue width, boolean compact) {
        return buildBildDiv(bild, width, compact, null, PdfSettings.defaults());
    }

    private Div buildBildDiv(Bild bild, UnitValue width, boolean compact, ItemStats stats) {
        return buildBildDiv(bild, width, compact, stats, PdfSettings.defaults());
    }

    private Div buildBildDiv(Bild bild, UnitValue width, boolean compact, ItemStats stats, PdfSettings settings) {
        Div div = new Div().setMarginBottom(6);
        String path = capturesPath + bild.getPfad().replaceFirst("^/", "");
        try {
            byte[] imageBytes = compact
                ? loadScaledImageBytes(path, "600x600>", 55)
                : loadScaledImageBytes(path);
            Image img = imageBytes != null
                ? new Image(ImageDataFactory.create(imageBytes)).setWidth(width)
                : new Image(ImageDataFactory.create(path)).setWidth(width);
            div.add(img);
            String title = bild.getTitle();
            if (title != null && !title.isBlank()) {
                div.add(new Paragraph(title).setFontSize(settings.imageCaptionSize()).setItalic().setTextAlignment(TextAlignment.CENTER).setMarginTop(2));
            }
        } catch (Exception e) {
            log.warn("Bild nicht gefunden: {}", path);
            div.add(new Paragraph("[Bild nicht gefunden: " + bild.getPfad() + "]").setFontSize(8));
        }
        appendStats(div, stats, settings);
        return div;
    }

    private Div buildPolaroidDiv(Bild bild, UnitValue width, int seed, boolean hero) {
        return buildPolaroidDiv(bild, width, seed, hero, false, null, PdfSettings.defaults());
    }

    private Div buildPolaroidDiv(Bild bild, UnitValue width, int seed, boolean hero, boolean compact) {
        return buildPolaroidDiv(bild, width, seed, hero, compact, null, PdfSettings.defaults());
    }

    private Div buildPolaroidDiv(Bild bild, UnitValue width, int seed, boolean hero, boolean compact, ItemStats stats) {
        return buildPolaroidDiv(bild, width, seed, hero, compact, stats, PdfSettings.defaults());
    }

    private Div buildPolaroidDiv(Bild bild, UnitValue width, int seed, boolean hero, boolean compact, ItemStats stats, PdfSettings settings) {
        double maxDeg = hero ? 2.5 : 4.0;
        double angle = ((new Random((bild.id != null ? bild.id : 0L) + seed).nextDouble() * 2 - 1) * maxDeg) * Math.PI / 180.0;

        // Outer wrapper absorbs the rotation's extra visual spread
        Div wrapper = new Div()
            .setWidth(width)
            .setMarginBottom(hero ? 14 : 10)
            .setMarginLeft(hero ? 8 : 4)
            .setMarginRight(hero ? 8 : 4);

        Div frame = new Div()
            .setWidth(UnitValue.createPercentValue(100))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
            .setPaddingTop(6).setPaddingLeft(6).setPaddingRight(6).setPaddingBottom(14)
            .setRotationAngle(angle);

        String path = capturesPath + bild.getPfad().replaceFirst("^/", "");
        try {
            byte[] imageBytes = compact
                ? loadScaledImageBytes(path, "600x600>", 55)
                : loadScaledImageBytes(path);
            Image img = imageBytes != null
                ? new Image(ImageDataFactory.create(imageBytes)).setWidth(UnitValue.createPercentValue(100))
                : new Image(ImageDataFactory.create(path)).setWidth(UnitValue.createPercentValue(100));
            img.setMaxHeight(hero ? 320 : 180);
            frame.add(img);
        } catch (Exception e) {
            log.warn("Bild nicht gefunden: {}", path);
            frame.add(new Paragraph("[Bild nicht gefunden]").setFontSize(8));
        }

        String title = bild.getTitle();
        if (title != null && !title.isBlank()) {
            float captionSize = hero ? settings.imageCaptionSize() + 1 : settings.imageCaptionSize();
            frame.add(new Paragraph(title)
                .setFontSize(captionSize).setItalic()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(4).setMarginBottom(0));
        }
        wrapper.add(frame);
        appendStats(wrapper, stats, settings);
        return wrapper;
    }

    private Div buildTextDiv(Text text) {
        return buildTextDiv(text, null, PdfSettings.defaults());
    }

    private Div buildTextDiv(Text text, ItemStats stats) {
        return buildTextDiv(text, stats, PdfSettings.defaults());
    }

    private Div buildTextDiv(Text text, ItemStats stats, PdfSettings settings) {
        Div div = new Div().setMarginBottom(6);
        if (text.title != null && !text.title.isBlank()) {
            div.add(new Paragraph(text.title).setFontSize(settings.textTitleSize()).setBold());
        }
        if (text.description != null && !text.description.isBlank()) {
            div.add(new Paragraph(text.description).setFontSize(settings.textDescriptionSize()).setTextAlignment(TextAlignment.JUSTIFIED));
        }
        appendStats(div, stats, settings);
        return div;
    }

    private static ItemStats stats(Map<Long, ItemStats> map, Long id) {
        return (map != null && id != null) ? map.get(id) : null;
    }

    private static final DeviceRgb REPLY_COLOR = new DeviceRgb(0.55f, 0.55f, 0.55f);

    /** Schriftgröße und Farbe skalieren mit der Gesamtzahl der Reaktionen. */
    private static float reactionFontSize(long total) {
        if (total >= 10) return 22f;
        if (total >= 6)  return 20f;
        if (total >= 3)  return 18f;
        return 16f;
    }

    private static DeviceRgb reactionColor(long total) {
        if (total >= 10) return new DeviceRgb(0.72f, 0.05f, 0.05f); // Dunkelrot
        if (total >= 6)  return new DeviceRgb(0.88f, 0.10f, 0.10f); // Kräftiges Rot
        if (total >= 3)  return new DeviceRgb(0.90f, 0.25f, 0.35f); // Leuchtendes Pink-Rot
        return new DeviceRgb(0.85f, 0.35f, 0.50f);                   // Mittleres Pink
    }

    private void appendStats(Div div, ItemStats stats) {
        appendStats(div, stats, PdfSettings.defaults());
    }

    private void appendStats(Div div, ItemStats stats, PdfSettings settings) {
        if (stats == null) return;
        if (stats.likes() > 0 || stats.votes() > 0) {
            long total = stats.likes() + stats.votes();
            float fontSize = reactionFontSize(total);
            DeviceRgb color = reactionColor(total);
            StringBuilder sb = new StringBuilder();
            if (stats.likes() > 0) sb.append("\u2665 ").append(stats.likes());
            if (stats.votes() > 0) {
                if (!sb.isEmpty()) sb.append("   ");
                sb.append("\u2605 ").append(stats.votes());
            }
            div.add(new Paragraph(sb.toString())
                .setFontSize(fontSize).setFontColor(color)
                .setMarginTop(2).setMarginBottom(stats.comments().isEmpty() ? 0 : 2));
        }
        for (String[] line : stats.comments()) {
            boolean isReply = "1".equals(line[2]);
            String text = "\u201E" + truncate(line[1], 80) + "\u201C \u2014 " + line[0];
            div.add(new Paragraph(text)
                .setFontSize(isReply ? settings.commentReplySize() : settings.commentTopLevelSize())
                .setItalic()
                .setFontColor(REPLY_COLOR)
                .setMarginLeft(isReply ? 10 : 0)
                .setMarginTop(2).setMarginBottom(0));
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static class PageNumberHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            PdfDocument pdf = docEvent.getDocument();
            int pageNum = pdf.getPageNumber(page);
            com.itextpdf.kernel.geom.Rectangle rect = page.getPageSize();
            try (Canvas canvas = new Canvas(page, rect)) {
                canvas.showTextAligned(
                    new Paragraph(String.valueOf(pageNum)).setFontSize(9),
                    rect.getWidth() / 2,
                    20,
                    TextAlignment.CENTER
                );
            }
        }
    }

    private static class PassepartoutHandler implements IEventHandler {
        private final String style;

        PassepartoutHandler(String style) {
            this.style = style;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            PdfDocument pdf = docEvent.getDocument();
            com.itextpdf.kernel.geom.Rectangle r = page.getPageSize();
            float w = r.getWidth(), h = r.getHeight();

            DeviceRgb primary, fill;
            float bw, ol, cs, innerLineWidth, fillOpacity, strokeOpacity;
            boolean diamonds, squares, doubleInner;
            int flowerPetals;
            boolean midSideFlowers;

            switch (style) {
                case "silber" -> {
                    primary = new DeviceRgb(0.60f, 0.60f, 0.64f);
                    fill    = new DeviceRgb(0.94f, 0.94f, 0.96f);
                    bw = 26f; ol = 8f; cs = 4.5f;
                    innerLineWidth = 1.8f; fillOpacity = 0.15f; strokeOpacity = 0.65f;
                    diamonds = false; squares = true; doubleInner = false;
                    flowerPetals = 0; midSideFlowers = false;
                }
                case "vintage" -> {
                    primary = new DeviceRgb(0.55f, 0.32f, 0.10f);
                    fill    = new DeviceRgb(0.98f, 0.94f, 0.87f);
                    bw = 34f; ol = 8f; cs = 5.5f;
                    innerLineWidth = 0.8f; fillOpacity = 0.20f; strokeOpacity = 0.62f;
                    diamonds = false; squares = false; doubleInner = true;
                    flowerPetals = 4; midSideFlowers = true;
                }
                case "festlich" -> {
                    primary = new DeviceRgb(0.43f, 0.04f, 0.08f);
                    fill    = new DeviceRgb(0.99f, 0.91f, 0.92f);
                    bw = 38f; ol = 8f; cs = 6.0f;
                    innerLineWidth = 1.8f; fillOpacity = 0.14f; strokeOpacity = 0.68f;
                    diamonds = false; squares = false; doubleInner = false;
                    flowerPetals = 5; midSideFlowers = true;
                }
                default -> { // "gold"
                    primary = new DeviceRgb(0.74f, 0.56f, 0.08f);
                    fill    = new DeviceRgb(0.99f, 0.97f, 0.89f);
                    bw = 26f; ol = 8f; cs = 4.5f;
                    innerLineWidth = 1.2f; fillOpacity = 0.18f; strokeOpacity = 0.65f;
                    diamonds = true; squares = false; doubleInner = false;
                    flowerPetals = 0; midSideFlowers = false;
                }
            }

            PdfCanvas cv = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);
            try {
                // semi-transparent fill on border strip
                cv.saveState();
                cv.setExtGState(new PdfExtGState().setFillOpacity(fillOpacity));
                cv.setFillColor(fill);
                cv.rectangle(0, h - bw, w, bw).fill();
                cv.rectangle(0, 0, w, bw).fill();
                cv.rectangle(0, bw, bw, h - 2 * bw).fill();
                cv.rectangle(w - bw, bw, bw, h - 2 * bw).fill();
                cv.restoreState();

                // outer thin line
                if (!doubleInner) {
                    cv.saveState();
                    cv.setExtGState(new PdfExtGState().setStrokeOpacity(strokeOpacity * 0.7f));
                    cv.setStrokeColor(primary);
                    cv.setLineWidth(0.5f);
                    cv.rectangle(ol, ol, w - 2 * ol, h - 2 * ol).stroke();
                    cv.restoreState();
                }

                // inner border + ornaments
                cv.saveState();
                cv.setExtGState(new PdfExtGState().setFillOpacity(strokeOpacity).setStrokeOpacity(strokeOpacity));
                cv.setStrokeColor(primary);
                cv.setFillColor(primary);
                cv.setLineWidth(innerLineWidth);
                cv.rectangle(bw, bw, w - 2 * bw, h - 2 * bw).stroke();

                if (doubleInner) {
                    cv.setLineWidth(0.5f);
                    cv.rectangle(bw + 4f, bw + 4f, w - 2 * (bw + 4f), h - 2 * (bw + 4f)).stroke();
                }

                float[][] corners = {{bw, bw}, {w - bw, bw}, {bw, h - bw}, {w - bw, h - bw}};

                if (diamonds) {
                    for (float[] c : corners) {
                        cv.moveTo(c[0], c[1] - cs).lineTo(c[0] + cs, c[1])
                          .lineTo(c[0], c[1] + cs).lineTo(c[0] - cs, c[1]).closePath();
                    }
                    cv.fillStroke();
                } else if (squares) {
                    float hs = cs * 0.75f;
                    for (float[] c : corners) cv.rectangle(c[0] - hs, c[1] - hs, hs * 2, hs * 2);
                    cv.fillStroke();
                } else if (flowerPetals > 0) {
                    for (float[] c : corners) drawFlower(cv, c[0], c[1], cs, flowerPetals);
                    if (midSideFlowers) {
                        float sr = cs * 0.65f;
                        drawFlower(cv, w / 2, bw / 2,      sr, flowerPetals);
                        drawFlower(cv, w / 2, h - bw / 2,  sr, flowerPetals);
                        drawFlower(cv, bw / 2, h / 2,      sr, flowerPetals);
                        drawFlower(cv, w - bw / 2, h / 2,  sr, flowerPetals);
                    }
                }

                cv.restoreState();
            } finally {
                cv.release();
            }
        }

        private static void drawFlower(PdfCanvas cv, float cx, float cy, float r, int petals) {
            for (int i = 0; i < petals; i++) {
                double angle = Math.PI * 2.0 * i / petals - Math.PI / 2;
                float pcx = cx + (float)(Math.cos(angle) * r * 0.72f);
                float pcy = cy + (float)(Math.sin(angle) * r * 0.72f);
                cv.circle(pcx, pcy, r * 0.52f);
            }
            cv.circle(cx, cy, r * 0.36f);
            cv.fill();
        }
    }

    record ItemStats(long likes, long votes, List<String[]> comments) {}

    record StoryData(
        Story story,
        List<Bild> bilder,
        List<Text> texte,
        Map<Long, ItemStats> bildStats,
        Map<Long, ItemStats> textStats
    ) {}
}
