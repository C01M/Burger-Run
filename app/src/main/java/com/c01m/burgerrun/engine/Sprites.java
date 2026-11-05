package com.c01m.burgerrun.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bakes vector art into shelf-packed texture atlases on first use. Each page keeps its Pixmap as
 * managed texture data, so libGDX can restore the atlas after the GL context is lost.
 * Pixels are premultiplied, which is why the batch uses (ONE, ONE_MINUS_SRC_ALPHA) blending.
 */
public final class Sprites {
    public interface Painter { void paint(Canvas c); }

    private static final int PAGE = 2048, PAD = 2;

    private static final class Page {
        Pixmap pixmap;
        Texture texture;
        int x = PAD, y = PAD, shelf;
    }

    private final List<Page> pages = new ArrayList<>();
    private final Map<String, TextureRegion> cache = new HashMap<>();

    /** w/h are logical units; scale is texture pixels per logical unit. */
    public TextureRegion get(String key, float w, float h, float scale, Painter painter) {
        TextureRegion cached = cache.get(key);
        if (cached != null) return cached;
        int pw = Math.max(1, (int) Math.ceil(w * scale)), ph = Math.max(1, (int) Math.ceil(h * scale));
        Bitmap bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(scale, scale);
        painter.paint(c);
        TextureRegion region = upload(bmp);
        bmp.recycle();
        cache.put(key, region);
        return region;
    }

    public TextureRegion get(String key, float w, float h, Painter painter) {
        return get(key, w, h, 2, painter);
    }

    private TextureRegion upload(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        Page page = pages.isEmpty() ? newPage() : pages.get(pages.size() - 1);
        if (page.x + w + PAD > PAGE) { page.x = PAD; page.y += page.shelf + PAD; page.shelf = 0; }
        if (page.y + h + PAD > PAGE) page = newPage();

        Pixmap px = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        px.setBlending(Pixmap.Blending.None);
        ByteBuffer buf = px.getPixels();
        buf.position(0);
        bmp.copyPixelsToBuffer(buf);
        buf.position(0);
        page.pixmap.drawPixmap(px, page.x, page.y);
        // Texture.draw() rejects managed textures, so upload the sub-image directly
        page.texture.bind();
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1);
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, page.x, page.y, w, h, px.getGLFormat(), px.getGLType(), px.getPixels());
        px.dispose();

        TextureRegion region = new TextureRegion(page.texture, page.x, page.y, w, h);
        region.flip(false, true); // the camera is y-down
        page.x += w + PAD;
        page.shelf = Math.max(page.shelf, h);
        return region;
    }

    private Page newPage() {
        Page p = new Page();
        p.pixmap = new Pixmap(PAGE, PAGE, Pixmap.Format.RGBA8888);
        p.pixmap.setBlending(Pixmap.Blending.None);
        p.texture = new Texture(new PixmapTextureData(p.pixmap, null, false, false, true));
        p.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pages.add(p);
        return p;
    }

    public void dispose() {
        for (Page p : pages) { p.texture.dispose(); p.pixmap.dispose(); }
        pages.clear();
        cache.clear();
    }
}
