package ru.kelcuprum.waterplayer.frontend.gui.screens.playlist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import ru.kelcuprum.alinlib.AlinLib;
import ru.kelcuprum.alinlib.config.Localization;
import ru.kelcuprum.alinlib.gui.InterfaceUtils;
import ru.kelcuprum.alinlib.gui.components.ConfigureScrolWidget;
import ru.kelcuprum.alinlib.gui.components.builder.button.ButtonWithIconBuilder;
import ru.kelcuprum.alinlib.gui.components.buttons.ButtonSprite;
import ru.kelcuprum.alinlib.gui.components.buttons.base.Button;
import ru.kelcuprum.alinlib.gui.components.editbox.base.EditBoxString;
import ru.kelcuprum.alinlib.gui.components.text.TextBox;
import ru.kelcuprum.waterplayer.WaterPlayer;
import ru.kelcuprum.waterplayer.backend.WaterPlayerAPI;
import ru.kelcuprum.waterplayer.backend.playlist.Playlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ru.kelcuprum.alinlib.gui.InterfaceUtils.Icons.*;

public class PlaylistScreen extends Screen {
    private Playlist playlist;
    private final String playlistName;
    JsonObject jsonPlaylist = new JsonObject();
    private final Screen parent;

    public PlaylistScreen(Screen parent, String playlistName) {
        super(Component.translatable("waterplayer.playlist"));
        this.parent = parent;
        this.playlistName = playlistName;
    }

    private final InterfaceUtils.DesignType designType = InterfaceUtils.DesignType.FLAT;
    Path playlistFile;
    boolean isDeleted = false;
    boolean isCreatedLink = false;
    String link = "";
    boolean isEnable = false;
    boolean isInit = false;
    @Override
    protected void init() {
        assert this.minecraft != null;
        playlistFile = this.minecraft.gameDirectory.toPath().resolve("config/WaterPlayer/playlists/" + playlistName + ".json");
        try {
            playlist = new Playlist(playlistFile);
        } catch (Exception ex) {
            WaterPlayer.log(ex.getLocalizedMessage(), Level.ERROR);
        }
        initPanel();
        initList();
        if(!isInit) {
            isInit = true;
            new Thread(() -> isEnable = WaterPlayerAPI.isPlaylistUploadEnable()).start();
        }
    }
    public Button upload;
    public TextBox desc;
    public void initPanel() {
        int x = 5;
        int size = 180;
        addRenderableWidget(new TextBox(x, 15, size, 9, title, true));

        addRenderableWidget(new EditBoxString(x, 40, size, 20, false, playlist.title, designType, Component.translatable("waterplayer.playlist.title"), (s) -> {
            playlist.title = s;
            save();
        }));

        addRenderableWidget(new EditBoxString(x, 65, size, 20, false, playlist.author, designType, Component.translatable("waterplayer.playlist.author"), (s) -> {
            playlist.author = s;
            save();
        }));
        int y = 90;
        upload = addRenderableWidget(new Button(x, y, size, 20, Component.translatable(isCreatedLink ? "waterplayer.playlist.copy_link" : "waterplayer.playlist.upload"), (e) -> {
            if (isCreatedLink) {
                AlinLib.MINECRAFT.keyboardHandler.setClipboard(link);
                WaterPlayer.getToast().setMessage(Component.translatable("waterplayer.playlist.link_copied")).show(AlinLib.MINECRAFT.getToasts());
            } else {
                try {
                    link = WaterPlayerAPI.uploadPlaylist(playlist, playlist.fileName);
                    AlinLib.MINECRAFT.keyboardHandler.setClipboard(link);
                    isCreatedLink = true;
                    WaterPlayer.getToast().setMessage(Component.translatable("waterplayer.playlist.uploaded")).show(AlinLib.MINECRAFT.getToasts());
                    e.setMessage(Component.translatable("waterplayer.playlist.copy_link"));
                } catch (Exception ex) {
                    e.setActive(false);
                    WaterPlayer.log(ex.getMessage() == null ? e.getClass().getName() : ex.getMessage(), Level.ERROR);
                }
            }
        }).setActive(false));
        upload.visible = false;

        desc = addRenderableWidget(new TextBox(x, y, size, 20, Localization.toText(String.format(WaterPlayer.localization.getLocalization("playlist.description"), playlistName)), true));
        addRenderableWidget(new Button(x, height - 30, size - 75, 20, designType, CommonComponents.GUI_BACK, (e) -> onClose()));
        addRenderableWidget(new ButtonSprite(x + size - 70, height - 30, 20, 20, designType, InterfaceUtils.getResourceLocation("waterplayer", "textures/player/play.png"), Localization.getText("waterplayer.playlist.play"), (e) -> {
            save();
            WaterPlayer.player.loadMusic(String.format("playlist:%s", playlistName), true);
            onClose();
        }));
        addRenderableWidget(new ButtonSprite(x + size - 45, height - 30, 20, 20, designType, InterfaceUtils.getResourceLocation("waterplayer", "textures/player/reset_queue.png"), Localization.getText("waterplayer.playlist.remove"), (e) -> {
            isDeleted = true;
            playlistFile.toFile().delete();
            onClose();
        }));
        addRenderableWidget(new ButtonSprite(x + size - 20, height - 30, 20, 20, designType, RESET, Localization.getText("waterplayer.playlist.reload"), (e) -> {
            save();
            rebuildWidgets();
        }));
    }

    private ConfigureScrolWidget scroller;
    private List<AbstractWidget> widgets = new ArrayList<>();
    private int lastSize = 0;

    public void initList() {
        widgets = new ArrayList<>();
        this.scroller = addRenderableWidget(new ConfigureScrolWidget(this.width - 8, 0, 4, this.height, Component.empty(), scroller -> {
            scroller.innerHeight = 5;
            for (AbstractWidget widget : widgets) {
                if (widget.visible) {
                    widget.setY((int) (scroller.innerHeight - scroller.scrollAmount()));
                    scroller.innerHeight += (widget.getHeight() + 5);
                } else widget.setY(-widget.getHeight());
            }
        }));
        int x = 195;
        widgets.add(new TextBox(x, 5, width - 200, 20, Component.translatable("waterplayer.playlist.urls"), true));
        int i = 0;
        for (JsonElement element : playlist.getUrlsJSON()) {
            String url = element.getAsString();
            int finalI = i;
            widgets.add(new EditBoxString(x, -20, width - 200, 20, false, url, designType, Component.literal(String.format("%s. ", i + 1)), (s) -> {
                playlist.setUrl(s, finalI);
                save();
            }));
            i++;
        }
        widgets.add(new ButtonWithIconBuilder(Component.translatable("waterplayer.playlist.add"), ADD, (e) -> {
            playlist.urls.add("https://c418.bandcamp.com/track/strad");
            save();
        }).setPosition(x, -20).setSize(width - 200, 20).build());
        addRenderableWidgets(widgets);
    }

    protected void addRenderableWidgets(@NotNull List<AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets) {
            this.addRenderableWidget(widget);
        }
    }

    private void save() {
        Minecraft CLIENT = Minecraft.getInstance();
        final Path configFile = CLIENT.gameDirectory.toPath().resolve("config/WaterPlayer/playlists/" + playlistName + ".json");
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, playlist.toJSON().toString());
        } catch (IOException e) {
            WaterPlayer.log(e.getLocalizedMessage(), Level.ERROR);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        super.renderBackground(guiGraphics);
        InterfaceUtils.renderLeftPanel(guiGraphics, 190, height);
    }
    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        try {
            renderBackground(guiGraphics);
            super.render(guiGraphics, i, j, f);
        } catch (Exception e) {
            WaterPlayer.log("Mojang, иди нахуй со своим "+ (e.getLocalizedMessage() == null ? e.getClass().getName() : e.getLocalizedMessage()));
        }
    }

    @Override
    public void tick() {
        if (scroller != null) scroller.onScroll.accept(scroller);
        if(isEnable){
            desc.setY(115);
            upload.setActive(true);
            upload.visible = true;
        } else {
            upload.setActive(false);
            upload.visible = false;
            desc.setY(90);
        }
        if (lastSize != playlist.getUrlsJSON().size()) {
            if (getFocused() == null || !(getFocused().isFocused() && (getFocused() instanceof EditBox))) {
                lastSize = playlist.getUrlsJSON().size();
                rebuildWidgetsList();
            }
        }
        super.tick();
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (i == GLFW.GLFW_KEY_ESCAPE) {
            if (getFocused() != null && getFocused().isFocused()) {
                getFocused().setFocused(false);
                return true;
            }
        }
        return super.keyPressed(i, j, k);
    }

    protected void rebuildWidgetsList() {
        removeWidget(scroller);
        scroller = null;
        for (AbstractWidget widget : widgets) {
            removeWidget(widget);
        }
        initList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        boolean scr = super.mouseScrolled(mouseX, mouseY, scrollY);
        if (!scr && scroller != null) {
            scr = scroller.mouseScrolled(mouseX, mouseY, scrollY);
        }
        return scr;
    }

    @Override
    public void onFilesDrop(List<Path> list) {
        if (list.size() == 1) playlist.addUrl(list.get(0).toString());
        else AlinLib.MINECRAFT.setScreen(new ConfirmAddedFiles(list, this, playlist));
    }

    public void onClose() {
        if (!isDeleted) save();
        assert this.minecraft != null;
        this.minecraft.setScreen(parent);
    }
}
