package de.jamsintown.pdf;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
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
import de.jamsintown.story.Story;
import de.jamsintown.text.Text;
import de.jamsintown.user.Gruppe;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ApplicationScoped
public class PdfService {

    @ConfigProperty(name = "jahrbuch.captures.path", defaultValue = "/tmp/captures/")
    String capturesPath;

    private final UserService userService;

    @Inject
    public PdfService(UserService userService) {
        this.userService = userService;
    }

    public Uni<byte[]> generateForGroup(Long groupId) {
        return generateForGroup(groupId, null);
    }

    public Uni<byte[]> generateForGroup(Long groupId, PdfOptions options) {
        return loadAllStoryData(groupId, options)
            .chain(storyDataList -> Uni.createFrom()
                .item(() -> renderPdf(storyDataList, options))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
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
                    .onItem().transformToUniAndConcatenate(this::loadStoryData)
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
                result.add(new StoryData(null, bilder, texte));
                return result;
            });
        });
    }

    private Uni<StoryData> loadStoryData(Story story) {
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
        .map(texte -> new StoryData(story, bilder, texte)));
    }

    byte[] renderPdf(List<StoryData> stories, PdfOptions options) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(40, 40, 50, 40);

            if (options != null && options.pageNumbers()) {
                pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageNumberHandler());
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
                renderStory(doc, stories.get(i));
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

    private void renderStory(Document doc, StoryData sd) {
        Story story = sd.story();

        if (story == null) {
            doc.add(new Paragraph("Sonstige")
                .setFontSize(18)
                .setBold()
                .setMarginBottom(4));
        } else {
            doc.add(new Paragraph(story.name)
                .setFontSize(18)
                .setBold()
                .setMarginBottom(4));

            if (story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description)
                    .setFontSize(10)
                    .setItalic()
                    .setMarginBottom(8));
            }
        }

        String layout = story != null ? story.layout : null;
        if ("grid".equals(layout)) {
            renderThreeColumn(doc, sd);
        } else if ("1col".equals(layout)) {
            renderOneColumn(doc, sd);
        } else {
            renderTwoColumn(doc, sd);
        }
    }

    private void renderOneColumn(Document doc, StoryData sd) {
        record Item(int pos, boolean isBild, Bild bild, Text text) {}
        List<Item> items = Stream.concat(
            sd.bilder().stream().map(b -> new Item(b.storyPosition != null ? b.storyPosition : 0, true, b, null)),
            sd.texte().stream().map(t -> new Item(t.storyPosition != null ? t.storyPosition : 0, false, null, t))
        ).sorted(Comparator.comparingInt(Item::pos)).toList();

        for (Item item : items) {
            if (item.isBild()) {
                doc.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100)));
            } else {
                doc.add(buildTextDiv(item.text()));
            }
        }
    }

    private void renderTwoColumn(Document doc, StoryData sd) {
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
            if (item.isBild()) leftCell.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100)));
            else leftCell.add(buildTextDiv(item.text()));
        }

        Cell rightCell = new Cell().setBorder(null).setPaddingLeft(3).setVerticalAlignment(VerticalAlignment.TOP);
        for (Item item : col1) {
            if (item.isBild()) rightCell.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100)));
            else rightCell.add(buildTextDiv(item.text()));
        }

        table.addCell(leftCell);
        table.addCell(rightCell);
        doc.add(table);
    }

    private void renderThreeColumn(Document doc, StoryData sd) {
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
                if (item.isBild()) cell.add(buildBildDiv(item.bild(), UnitValue.createPercentValue(100)));
                else cell.add(buildTextDiv(item.text()));
            }
            table.addCell(cell);
        }
        doc.add(table);
    }

    private byte[] loadScaledImageBytes(String path) {
        try {
            Process p = new ProcessBuilder(
                "convert", path, "-resize", "1200x1200>", "-quality", "80", "jpeg:-"
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
        Div div = new Div().setMarginBottom(6);
        String path = capturesPath + bild.getPfad().replaceFirst("^/", "");
        try {
            byte[] imageBytes = loadScaledImageBytes(path);
            Image img = imageBytes != null
                ? new Image(ImageDataFactory.create(imageBytes)).setWidth(width)
                : new Image(ImageDataFactory.create(path)).setWidth(width);
            div.add(img);
            String title = bild.getTitle();
            if (title != null && !title.isBlank()) {
                div.add(new Paragraph(title).setFontSize(8).setItalic().setTextAlignment(TextAlignment.CENTER).setMarginTop(2));
            }
        } catch (Exception e) {
            log.warn("Bild nicht gefunden: {}", path);
            div.add(new Paragraph("[Bild nicht gefunden: " + bild.getPfad() + "]").setFontSize(8));
        }
        return div;
    }

    private Div buildTextDiv(Text text) {
        Div div = new Div().setMarginBottom(6);
        if (text.title != null && !text.title.isBlank()) {
            div.add(new Paragraph(text.title).setFontSize(12).setBold());
        }
        if (text.description != null && !text.description.isBlank()) {
            div.add(new Paragraph(text.description).setFontSize(10).setTextAlignment(TextAlignment.JUSTIFIED));
        }
        return div;
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

    record StoryData(Story story, List<Bild> bilder, List<Text> texte) {}
}
