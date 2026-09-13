package eu.cronmoth.createentityaddon.rendering.displayboard;

import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.ResourceModelRenderer;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import eu.cronmoth.createentityaddon.rendering.displayboard.entitymodel.DisplayBoardEntity;
import eu.cronmoth.createentityaddon.rendering.text.ComponentText;
import eu.cronmoth.createentityaddon.rendering.text.FontAtlas;
import eu.cronmoth.createentityaddon.rendering.tracks.ObjMesh;
import eu.cronmoth.createentityaddon.rendering.tracks.ObjMeshRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class DisplayBoardTextRenderer implements BlockRenderer {

    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("createentityaddon", "display_board_text"),
            DisplayBoardTextRenderer::new
    );

    private static final boolean DEBUG = Boolean.getBoolean("cea.trackdebug");


    private static final float SCREEN_Z = 0.5f - 3f / 16f + 1f / 32f;

    private static final float CELL_SIZE = 0.25f;

    private static final float ROW_HEIGHT = 0.5f;

    private static final float LINE_Y_NUDGE = -4.5f / 32f / 2f + 1f / 16f;

    private final ResourceModelRenderer modelRenderer;
    private final TextureGallery textureGallery;

    private BlockNeighborhood block;

    public DisplayBoardTextRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.textureGallery = textureGallery;
        this.modelRenderer = new ResourceModelRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView tileModel, Color blockColor) {
        this.block = block;

        modelRenderer.render(block, variant, tileModel.initialize(), blockColor);

        if (!(block.getBlockEntity() instanceof DisplayBoardEntity entity) || !entity.isController()) return;

        Map<String, String> props = block.getBlockState().getProperties();
        String facing = props.getOrDefault("facing", "north");
        float yawDeg = switch (facing) {
            case "east" -> 90f;
            case "south" -> 180f;
            case "west" -> 270f;
            default -> 0f; // north
        };

        boolean alongX = facing.equals("north") || facing.equals("south");
        int wallDx = alongX ? 1 : 0;
        int wallDz = alongX ? 0 : 1;

        float poseYawDeg = yawDeg + (alongX ? 180f : 0f);

        int xSize = Math.max(1, entity.getXSize());
        float extendSign = 1f;
        if (xSize > 1) {
            boolean plusIsBoard = isDisplayBoard(block, wallDx, wallDz);
            boolean minusIsBoard = isDisplayBoard(block, -wallDx, -wallDz);
            if (!plusIsBoard && minusIsBoard) extendSign = -1f;
        }
        float assemblyMin = extendSign >= 0 ? 0f : -(xSize - 1);
        float assemblyMax = extendSign >= 0 ? xSize : 1f;
        float assemblyCenter = (assemblyMin + assemblyMax) / 2f;

        int rowCount = Math.max(1, entity.getYSize()) * 2;

        FontAtlas atlas = FontAtlas.get();
        LightData ld = block.getLightData();
        int[] light = {ld.getSkyLight(), ld.getBlockLight()};
        int[] tex = {textureGallery.get(FontAtlas.PATH)};

        for (int row = 0; row < rowCount; row++) {
            List<FontAtlas.Section> sections = buildRowSections(entity.getDisplay(row));
            if (DEBUG) Logger.global.logInfo("[cea] display_board " + block.getX() + "," + block.getY() + "," + block.getZ()
                    + " facing=" + facing + " yaw=" + yawDeg + " poseYaw=" + poseYawDeg + " xSize=" + xSize + " ySize=" + entity.getYSize()
                    + " extendSign=" + extendSign + " row=" + row + " sections=" + sections);
            emitLine(tileModel, atlas, sections, row, poseYawDeg, wallDx, wallDz, assemblyCenter, light, tex);
        }
    }

    private void emitLine(TileModelView tileModel, FontAtlas atlas, List<FontAtlas.Section> sections, int rowIndex,
                           float poseYawDeg, int wallDx, int wallDz, float assemblyCenter,
                           int[] light, int[] tex) {
        if (sections.isEmpty()) return;
        ObjMesh mesh = atlas.buildRow(sections, CELL_SIZE);
        if (mesh == null) return;

        float rowCenterY = 1f - (rowIndex + 0.5f) * ROW_HEIGHT + LINE_Y_NUDGE;

        float worldX = wallDx != 0 ? assemblyCenter : 0.5f;
        float worldZ = wallDz != 0 ? assemblyCenter : 0.5f;

        MatrixM4f pose = new MatrixM4f().identity()
                .translate(0f, 0f, SCREEN_Z)
                .rotate(0f, poseYawDeg, 0f)
                .translate(worldX, rowCenterY, worldZ);

        if (DEBUG) Logger.global.logInfo("[cea] emitLine row=" + rowIndex + " sections=" + sections
                + " poseYawDeg=" + poseYawDeg + " wallDx=" + wallDx + " wallDz=" + wallDz
                + " assemblyCenter=" + assemblyCenter
                + " worldX=" + worldX + " worldZ=" + worldZ + " rowCenterY=" + rowCenterY
                + " blockPos=" + block.getX() + "," + block.getY() + "," + block.getZ());

        ObjMeshRenderer.emit(tileModel, mesh, pose, light, tex, 1f, 1f, 1f, 1f);
    }

    private static boolean isDisplayBoard(BlockNeighborhood block, int dx, int dz) {
        ExtendedBlock nb = block.getNeighborBlock(dx, 0, dz);
        return nb != null && "create:display_board".equals(nb.getBlockState().getId().getFormatted());
    }

    private static List<FontAtlas.Section> buildRowSections(DisplayBoardEntity.DisplaySlot slot) {
        if (slot == null || slot.getSections() == null) return List.of();
        List<FontAtlas.Section> sections = new ArrayList<>();
        for (DisplayBoardEntity.DisplaySection section : slot.getSections()) {
            if (section == null) continue;
            sections.add(new FontAtlas.Section(formatSectionText(section), section.getWidth(),
                    section.isWide(), section.isSingleFlap(), section.isGap()));
        }
        return sections;
    }

    private static String formatSectionText(DisplayBoardEntity.DisplaySection section) {
        String text = ComponentText.resolveText(section.getText());
        if (section.isSingleFlap()) return text;

        float cellPx = section.isWide() ? FontAtlas.WIDE_MONOSPACE_PX : FontAtlas.MONOSPACE_PX;
        int charCount = Math.max(1, Math.round(section.getWidth() / cellPx));
        if (section.isRightAligned()) text = text.trim();

        text = text.toUpperCase(Locale.ROOT);
        if (text.length() > charCount) text = text.substring(0, charCount);
        String pad = " ".repeat(charCount - text.length());
        return section.isRightAligned() ? pad + text : text + pad;
    }
}
