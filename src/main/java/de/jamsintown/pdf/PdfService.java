package de.jamsintown.pdf;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
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
import java.util.Random;
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

    /** Generates a PDF without membership check — for admin use only. */
    public Uni<byte[]> generateForGroupAsAdmin(Long groupId, PdfOptions options) {
        return loadAllStoryDataNoCheck(groupId, options)
            .chain(storyDataList -> Uni.createFrom()
                .item(() -> renderPdf(storyDataList, options))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
            .emitOn(Infrastructure.getDefaultExecutor());
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

    @WithSession
    Uni<List<StoryData>> loadAllStoryDataNoCheck(Long groupId, PdfOptions options) {
        return Gruppe.<Gruppe>findById(groupId)
            .onItem().ifNull().failWith(() -> new NotFoundException("Gruppe nicht gefunden: " + groupId))
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
                renderStory(doc, stories.get(i), i, options);
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

    // Pastel palette — 8 colors, one per story (cycling)
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
        renderStory(doc, sd, 0);
    }

    private void renderStory(Document doc, StoryData sd, int storyIndex) {
        renderStory(doc, sd, storyIndex, null);
    }

    private void renderStory(Document doc, StoryData sd, int storyIndex, PdfOptions options) {
        Story story = sd.story();
        String layout = story != null ? story.layout : null;

        if ("scrapbook".equals(layout)) {
            DeviceRgb color = STORY_COLORS[storyIndex % STORY_COLORS.length];
            String title = story != null ? story.name : "Sonstige";
            String subtitle = story != null ? story.description : null;
            renderStoryHeader(doc, title, subtitle, color);
            renderScrapbook(doc, sd, color);
        } else if ("grid".equals(layout)) {
            String title = story != null ? story.name : "Sonstige";
            doc.add(new Paragraph(title).setFontSize(18).setBold().setMarginBottom(4));
            if (story != null && story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description).setFontSize(10).setItalic().setMarginBottom(8));
            }
            renderThreeColumn(doc, sd);
        } else if ("1col".equals(layout)) {
            String title = story != null ? story.name : "Sonstige";
            doc.add(new Paragraph(title).setFontSize(18).setBold().setMarginBottom(4));
            if (story != null && story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description).setFontSize(10).setItalic().setMarginBottom(8));
            }
            renderOneColumn(doc, sd);
        } else {
            // Default: 2-column classic (layout == "2col" or null)
            String title = story != null ? story.name : "Sonstige";
            doc.add(new Paragraph(title).setFontSize(18).setBold().setMarginBottom(4));
            if (story != null && story.description != null && !story.description.isBlank()) {
                doc.add(new Paragraph(story.description).setFontSize(10).setItalic().setMarginBottom(8));
            }
            renderTwoColumn(doc, sd);
        }
    }

    private void renderStoryHeader(Document doc, String title, String subtitle, DeviceRgb color) {
        // Colored title band
        Table header = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
        Cell titleCell = new Cell().setBorder(null)
            .setBackgroundColor(color)
            .setPadding(12);
        titleCell.add(new Paragraph(title)
            .setFontSize(22).setBold()
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
            .setMargin(0));
        if (subtitle != null && !subtitle.isBlank()) {
            titleCell.add(new Paragraph(subtitle)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(0.9f, 0.9f, 0.9f))
                .setMarginTop(3).setMarginBottom(0));
        }
        header.addCell(titleCell);
        doc.add(header);
        doc.add(new Paragraph("").setMarginBottom(10));
    }

    private void renderScrapbook(Document doc, StoryData sd, DeviceRgb accentColor) {
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
            doc.add(buildPolaroidDiv(heroes.get(i), UnitValue.createPercentValue(94), i, true));
        }

        // All remaining items (images + texts) sorted by storyPosition into a single
        // 2-column newspaper grid: each item occupies one slot, alternating left/right.
        // Text items wrap within column width; iText handles A4 page breaks naturally.
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
                target.add(buildPolaroidDiv(item.bild(), UnitValue.createPercentValue(88), heroes.size() + i, false));
            } else {
                target.add(buildTextDiv(item.text()).setMarginTop(4).setMarginBottom(8));
            }
        }
        grid.addCell(left);
        grid.addCell(right);
        doc.add(grid);
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

    private Div buildPolaroidDiv(Bild bild, UnitValue width, int seed, boolean hero) {
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
            byte[] imageBytes = loadScaledImageBytes(path);
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
            frame.add(new Paragraph(title)
                .setFontSize(hero ? 9 : 7).setItalic()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(4).setMarginBottom(0));
        }
        wrapper.add(frame);
        return wrapper;
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

    record StoryData(Story story, List<Bild> bilder, List<Text> texte) {}
}
