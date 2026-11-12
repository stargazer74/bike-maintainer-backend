package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where everything sits on a worksheet, so that the app can read a photo of the filled-in sheet without recognising
 * its layout (Epic #120): corner markers, QR code, the tables and the boxes for the handwritten values. The positions
 * depend only on what the QR code carries – template, layout version, cylinders and valves per cylinder – and never on
 * the vehicle's data or the language.
 *
 * <p>All coordinates are PDF points (1/72 inch) on an A4 page with the origin in the <b>top left</b> corner. The app has
 * a Kotlin twin of this class; both are checked against {@code worksheet-scan-layout-v1.json} (see
 * {@code docs/worksheet-scan-layout.md}). <b>Changing a position changes the layout version.</b>
 */
public final class WorksheetScanLayout {

    /** Version of the positions below, carried in the QR code. */
    public static final int VERSION = 1;

    public static final float PAGE_WIDTH = 595.2756f;
    public static final float PAGE_HEIGHT = 841.8898f;

    /** Solid black squares in the four corners, to straighten the photo. */
    public static final float MARKER_SIZE = 18f;
    public static final float MARKER_INSET = 20f;

    /** QR code to the right of the vehicle block, below the header rule. */
    public static final float QR_SIZE = 64f;
    public static final float QR_LEFT = PAGE_WIDTH - 50f - QR_SIZE;
    public static final float QR_TOP = 80f;

    /** One box per digit; integer and decimal boxes are separated by a gap holding the decimal separator. */
    public static final float DIGIT_WIDTH = 18f;
    public static final float DIGIT_HEIGHT = 24f;
    public static final float SEPARATOR_WIDTH = 8f;
    public static final float CHECKBOX_SIZE = 16f;

    static final float CONTENT_LEFT = 50f;
    static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * CONTENT_LEFT;
    static final float HEADING_HEIGHT = 20f;
    static final float HEADER_ROW_HEIGHT = 20f;
    static final float ROW_HEIGHT = 30f;
    static final float BLOCK_GAP = 12f;
    static final float LINE_HEIGHT = 24f;
    /** Inset of the boxes from the left border of their cell. */
    static final float CELL_INSET = 5f;
    /** Top of the first block: below the vehicle block and the QR code. */
    static final float CONTENT_TOP = 180f;
    /** Lowest a block may reach; the footer starts below. */
    static final float CONTENT_BOTTOM = 780f;
    /** Date, mileage, done by, notes and three note lines (not read by the app). */
    static final float FILL_IN_HEIGHT = 7 * LINE_HEIGHT;
    static final float FILL_IN_BOTTOM = 785f;

    /** Valve, target, actual, old shim, new shim, ok. */
    static final float[] VALVE_COLUMNS = {0.18f, 0.16f, 0.20f, 0.20f, 0.20f, 0.06f};
    /** Check point, target or limit, actual value or finding, ok. */
    static final float[] CHECK_COLUMNS = {0.36f, 0.20f, 0.32f, 0.12f};

    private WorksheetScanLayout() {
    }

    /** What a field holds: a number written digit by digit, or a box to tick. */
    public enum FieldKind {
        DIGITS, CHECKBOX
    }

    /**
     * One field the app reads. For {@link FieldKind#DIGITS}, ({@code left}, {@code top}) is the top left corner of the
     * first box; the boxes follow without gaps except one of {@link #SEPARATOR_WIDTH} after the integer digits. For
     * {@link FieldKind#CHECKBOX} it is the top left corner of the box ({@link #CHECKBOX_SIZE}).
     *
     * @param id e.g. {@code valves.2.EXHAUST.1.measured}, {@code brakes.padFront}, {@code chain.cleaned}
     */
    public record Field(String id, FieldKind kind, float left, float top, int integerDigits, int decimalDigits) {

        static Field digits(String id, float left, float top, int integerDigits, int decimalDigits) {
            return new Field(id, FieldKind.DIGITS, left, top, integerDigits, decimalDigits);
        }

        static Field checkbox(String id, float left, float top) {
            return new Field(id, FieldKind.CHECKBOX, left, top, 0, 0);
        }

        /** Width of all boxes including the separator gap. */
        public float width() {
            if (kind == FieldKind.CHECKBOX) {
                return CHECKBOX_SIZE;
            }
            return (integerDigits + decimalDigits) * DIGIT_WIDTH + (decimalDigits > 0 ? SEPARATOR_WIDTH : 0f);
        }

        public float height() {
            return kind == FieldKind.CHECKBOX ? CHECKBOX_SIZE : DIGIT_HEIGHT;
        }
    }

    /** The check points of the brake, chain and tyre sheets; the label key is {@code worksheet.<labelKey>}. */
    public enum CheckPoint {
        PAD_THICKNESS("brakes.padThickness"),
        DISC_THICKNESS("brakes.discThickness"),
        DISC_CONDITION("brakes.discCondition"),
        FLUID_LEVEL("brakes.fluidLevel"),
        FLUID_COLOUR("brakes.fluidColour"),
        FLUID_LAST_CHANGED("brakes.fluidLastChanged"),
        CHAIN_SLACK("chain.slack"),
        CHAIN_WEAR("chain.wear"),
        SPROCKET("chain.sprocket"),
        REAR_SPROCKET("chain.rearSprocket"),
        TIRE_PRESSURE("tires.pressure"),
        TREAD_DEPTH("tires.tread"),
        DOT("tires.dot"),
        TIRE_CONDITION("tires.condition");

        private final String labelKey;

        CheckPoint(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return "worksheet." + labelKey;
        }
    }

    /** Heading of a check table: front, rear, brake fluid, chain measurement, chain condition. */
    public enum Section {
        FRONT("front"), REAR("rear"), FLUID("brakes.fluid"), MEASUREMENT("chain.measurement"),
        CONDITION("chain.condition");

        private final String headingKey;

        Section(String headingKey) {
            this.headingKey = headingKey;
        }

        public String headingKey() {
            return "worksheet." + headingKey;
        }
    }

    /** Something drawn on a page, at {@code top}. */
    public sealed interface Block permits CheckboxLine, CheckTable, CylinderTable {

        float top();
    }

    /**
     * A box to tick followed by a text; {@code field} is set if the app reads it.
     *
     * @param labelKey e.g. {@code worksheet.chain.cleaned}
     */
    public record CheckboxLine(float top, String labelKey, Field field) implements Block {

        public float boxLeft() {
            return CONTENT_LEFT;
        }

        public float boxTop() {
            return top + (LINE_HEIGHT - CHECKBOX_SIZE) / 2;
        }
    }

    /** Heading, header row and one row per check point. */
    public record CheckTable(float top, Section section, List<CheckRow> rows) implements Block {

        public CheckTable {
            rows = List.copyOf(rows);
        }

        public float headerTop() {
            return top + HEADING_HEIGHT;
        }
    }

    /** One check point; {@code actual} is {@code null} for findings written as text. */
    public record CheckRow(float top, CheckPoint point, Field actual) {
    }

    /** Heading "cylinder n", header row and one row per valve; never split across pages. */
    public record CylinderTable(float top, int cylinder, List<ValveRow> rows) implements Block {

        public CylinderTable {
            rows = List.copyOf(rows);
        }

        public float headerTop() {
            return top + HEADING_HEIGHT;
        }
    }

    public record ValveRow(float top, ValveKind kind, int index, Field measured, Field shimOld, Field shimNew) {
    }

    /**
     * One printed page.
     *
     * @param fillInTop top of the fill-in fields (date, mileage, …) if they are on this page, else {@code null}
     */
    public record Page(int number, List<Block> blocks, Float fillInTop) {

        public Page {
            blocks = List.copyOf(blocks);
        }

        public List<Field> fields() {
            List<Field> fields = new ArrayList<>();
            for (Block block : blocks) {
                switch (block) {
                    case CheckboxLine line -> {
                        if (line.field() != null) {
                            fields.add(line.field());
                        }
                    }
                    case CheckTable table -> table.rows().stream()
                            .filter(row -> row.actual() != null)
                            .forEach(row -> fields.add(row.actual()));
                    case CylinderTable table -> table.rows().forEach(row -> {
                        fields.add(row.measured());
                        fields.add(row.shimOld());
                        fields.add(row.shimNew());
                    });
                }
            }
            return fields;
        }
    }

    /** Layout of a whole worksheet. */
    public record Sheet(WorksheetTemplate template, ValveLayout valves, List<Page> pages) {

        public Sheet {
            pages = List.copyOf(pages);
        }

        /**
         * Content of the QR code on {@code page}, e.g. {@code BMWS;v=1;t=VALVE_CLEARANCE;id=42;c=4;vpc=4;p=1/2}.
         */
        public String qrContent(long vehicleId, int page) {
            return content("BMWS", vehicleId, page);
        }

        /**
         * QR code of a sheet printed during the trial (backend#138), e.g. {@code BMWD;v=1;t=TIRES;p=1/1}: the app
         * recognises it, but without the vehicle ID it cannot assign the values and explains why instead.
         */
        public String demoQrContent(int page) {
            return content("BMWD", null, page);
        }

        private String content(String prefix, Long vehicleId, int page) {
            StringBuilder content = new StringBuilder(prefix).append(";v=").append(VERSION)
                    .append(";t=").append(template.name());
            if (vehicleId != null) {
                content.append(";id=").append(vehicleId);
            }
            if (valves != null) {
                content.append(";c=").append(valves.cylinders()).append(";vpc=").append(valves.valvesPerCylinder());
            }
            return content.append(";p=").append(page).append('/').append(pages.size()).toString();
        }
    }

    /** Top left corners of the four markers: top left, top right, bottom left, bottom right. */
    public static List<float[]> markers() {
        float far = MARKER_INSET + MARKER_SIZE;
        return List.of(
                new float[]{MARKER_INSET, MARKER_INSET},
                new float[]{PAGE_WIDTH - far, MARKER_INSET},
                new float[]{MARKER_INSET, PAGE_HEIGHT - far},
                new float[]{PAGE_WIDTH - far, PAGE_HEIGHT - far});
    }

    /** @param valves only for {@link WorksheetTemplate#VALVE_CLEARANCE}, ignored otherwise */
    public static Sheet of(WorksheetTemplate template, ValveLayout valves) {
        return switch (template) {
            case VALVE_CLEARANCE -> valveClearance(valves);
            case BRAKES -> singlePage(template, List.of(
                    table(Section.FRONT, digits(CheckPoint.PAD_THICKNESS, "brakes.padFront", 2, 2),
                            digits(CheckPoint.DISC_THICKNESS, "brakes.discFront", 2, 2), text(CheckPoint.DISC_CONDITION)),
                    table(Section.REAR, digits(CheckPoint.PAD_THICKNESS, "brakes.padRear", 2, 2),
                            digits(CheckPoint.DISC_THICKNESS, "brakes.discRear", 2, 2), text(CheckPoint.DISC_CONDITION)),
                    table(Section.FLUID, text(CheckPoint.FLUID_LEVEL), text(CheckPoint.FLUID_COLOUR),
                            text(CheckPoint.FLUID_LAST_CHANGED))));
            case CHAIN -> singlePage(template, List.of(
                    table(Section.MEASUREMENT, digits(CheckPoint.CHAIN_SLACK, "chain.slack", 2, 1),
                            digits(CheckPoint.CHAIN_WEAR, "chain.wearLength", 3, 1)),
                    table(Section.CONDITION, text(CheckPoint.SPROCKET), text(CheckPoint.REAR_SPROCKET)),
                    checkboxLine("worksheet.chain.cleaned", "chain.cleaned"),
                    checkboxLine("worksheet.chain.lubricated", "chain.lubricated")));
            case TIRES -> singlePage(template, List.of(tireTable(Section.FRONT, "Front"),
                    tireTable(Section.REAR, "Rear")));
        };
    }

    // ---- check sheets: everything on one page, blocks stacked from CONTENT_TOP ----

    /** A block before its vertical position is known. */
    private interface Pending {

        float height();

        Block at(float top);
    }

    private record PendingRow(CheckPoint point, String fieldId, int integerDigits, int decimalDigits) {
    }

    private static PendingRow digits(CheckPoint point, String fieldId, int integerDigits, int decimalDigits) {
        return new PendingRow(point, fieldId, integerDigits, decimalDigits);
    }

    private static PendingRow text(CheckPoint point) {
        return new PendingRow(point, null, 0, 0);
    }

    private static Pending tireTable(Section section, String side) {
        return table(section, digits(CheckPoint.TIRE_PRESSURE, "tires.pressure" + side, 1, 2),
                digits(CheckPoint.TREAD_DEPTH, "tires.tread" + side, 2, 1),
                digits(CheckPoint.DOT, "tires.dot" + side, 4, 0), text(CheckPoint.TIRE_CONDITION));
    }

    private static Pending table(Section section, PendingRow... rows) {
        return new Pending() {
            @Override
            public float height() {
                return tableHeight(rows.length);
            }

            @Override
            public Block at(float top) {
                float actualLeft = columnLeft(CHECK_COLUMNS, 2) + CELL_INSET;
                List<CheckRow> placed = new ArrayList<>();
                float rowTop = top + HEADING_HEIGHT + HEADER_ROW_HEIGHT;
                for (PendingRow row : rows) {
                    Field field = row.fieldId() == null ? null : Field.digits(row.fieldId(), actualLeft,
                            boxTop(rowTop), row.integerDigits(), row.decimalDigits());
                    placed.add(new CheckRow(rowTop, row.point(), field));
                    rowTop += ROW_HEIGHT;
                }
                return new CheckTable(top, section, placed);
            }
        };
    }

    private static Pending checkboxLine(String labelKey, String fieldId) {
        return new Pending() {
            @Override
            public float height() {
                return LINE_HEIGHT;
            }

            @Override
            public Block at(float top) {
                CheckboxLine line = new CheckboxLine(top, labelKey, null);
                return new CheckboxLine(top, labelKey, fieldId == null ? null
                        : Field.checkbox(fieldId, line.boxLeft(), line.boxTop()));
            }
        };
    }

    private static Sheet singlePage(WorksheetTemplate template, List<Pending> pending) {
        List<Block> blocks = new ArrayList<>();
        float top = CONTENT_TOP;
        for (Pending block : pending) {
            blocks.add(block.at(top));
            top += block.height();
        }
        return new Sheet(template, null, List.of(new Page(1, blocks, top)));
    }

    // ---- valve clearance: cylinder tables, as many per page as fit ----

    /** Engine cold, TDC checked: two lines above the first cylinder (not read by the app). */
    private static final float VALVE_TABLES_TOP = CONTENT_TOP + 2 * LINE_HEIGHT;

    private static Sheet valveClearance(ValveLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Valve layout is required for VALVE_CLEARANCE");
        }
        List<ValveSlot> slots = valveSlots(layout.valvesPerCylinder());
        float blockHeight = tableHeight(slots.size());
        int perPage = cylindersPerPage(layout.valvesPerCylinder());
        List<Page> pages = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        int pageNumber = 1;
        float top = VALVE_TABLES_TOP;
        int onPage = 0;
        for (int cylinder = 1; cylinder <= layout.cylinders(); cylinder++) {
            if (onPage == perPage) {
                pages.add(new Page(pageNumber++, blocks, null));
                blocks = new ArrayList<>();
                top = VALVE_TABLES_TOP;
                onPage = 0;
            }
            if (pageNumber == 1 && blocks.isEmpty()) {
                blocks.add(new CheckboxLine(CONTENT_TOP, "worksheet.valve.engineCold", null));
                blocks.add(new CheckboxLine(CONTENT_TOP + LINE_HEIGHT, "worksheet.valve.tdcChecked", null));
            }
            blocks.add(cylinderTable(cylinder, top, slots));
            top += blockHeight;
            onPage++;
        }
        if (top + FILL_IN_HEIGHT <= FILL_IN_BOTTOM) {
            pages.add(new Page(pageNumber, blocks, top));
        } else {
            pages.add(new Page(pageNumber, blocks, null));
            pages.add(new Page(pageNumber + 1, List.of(), CONTENT_TOP));
        }
        return new Sheet(WorksheetTemplate.VALVE_CLEARANCE, layout, pages);
    }

    /** How many cylinder tables fit between the first table and {@link #CONTENT_BOTTOM}, at least one. */
    static int cylindersPerPage(int valvesPerCylinder) {
        float blockHeight = tableHeight(valveSlots(valvesPerCylinder).size());
        return Math.max(1, (int) ((CONTENT_BOTTOM - VALVE_TABLES_TOP) / blockHeight));
    }

    private record ValveSlot(ValveKind kind, int index) {
    }

    /** 2 valves: intake, exhaust; 3: two intakes, one exhaust; 4: two of each. */
    private static List<ValveSlot> valveSlots(int valvesPerCylinder) {
        int intakes = valvesPerCylinder >= 3 ? 2 : 1;
        int exhausts = valvesPerCylinder >= 4 ? 2 : 1;
        List<ValveSlot> slots = new ArrayList<>();
        for (int index = 1; index <= intakes; index++) {
            slots.add(new ValveSlot(ValveKind.INTAKE, index));
        }
        for (int index = 1; index <= exhausts; index++) {
            slots.add(new ValveSlot(ValveKind.EXHAUST, index));
        }
        return slots;
    }

    private static CylinderTable cylinderTable(int cylinder, float top, List<ValveSlot> slots) {
        List<ValveRow> rows = new ArrayList<>();
        float rowTop = top + HEADING_HEIGHT + HEADER_ROW_HEIGHT;
        for (ValveSlot slot : slots) {
            String prefix = "valves." + cylinder + "." + slot.kind().name() + "." + slot.index() + ".";
            float boxTop = boxTop(rowTop);
            rows.add(new ValveRow(rowTop, slot.kind(), slot.index(),
                    Field.digits(prefix + "measured", columnLeft(VALVE_COLUMNS, 2) + CELL_INSET, boxTop, 1, 3),
                    Field.digits(prefix + "shimOld", columnLeft(VALVE_COLUMNS, 3) + CELL_INSET, boxTop, 1, 3),
                    Field.digits(prefix + "shimNew", columnLeft(VALVE_COLUMNS, 4) + CELL_INSET, boxTop, 1, 3)));
            rowTop += ROW_HEIGHT;
        }
        return new CylinderTable(top, cylinder, rows);
    }

    // ---- shared geometry ----

    /** Heading, header row, {@code rows} rows and the gap below. */
    private static float tableHeight(int rows) {
        return HEADING_HEIGHT + HEADER_ROW_HEIGHT + rows * ROW_HEIGHT + BLOCK_GAP;
    }

    /** Boxes are centred vertically in their row. */
    private static float boxTop(float rowTop) {
        return rowTop + (ROW_HEIGHT - DIGIT_HEIGHT) / 2;
    }

    static float columnLeft(float[] columns, int column) {
        float left = CONTENT_LEFT;
        for (int index = 0; index < column; index++) {
            left += CONTENT_WIDTH * columns[index];
        }
        return left;
    }

    /** E.g. {@code 12.5}: positions as written to the JSON contract, rounded to 1/100 point. */
    static String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
