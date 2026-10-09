package com.finndog.justenoughstructures.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The fluid renderer writes positions relative to the 16x16x16 section a block is in, so this puts
 * the section's corner back on.
 */
final class OffsetConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private double dx;
    private double dy;
    private double dz;

    OffsetConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    OffsetConsumer at(int x, int y, int z) {
        this.dx = x & ~15;
        this.dy = y & ~15;
        this.dz = z & ~15;
        return this;
    }

    //? if >=1.21 {
    /*@Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x + (float) dx, y + (float) dy, z + (float) dz);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
    *///?} else {
    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x + dx, y + dy, z + dz);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        delegate.color(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
    //?}
    //? if >=26.1 {
    /*// 26.1's fluid renderer hands over each vertex whole, which the builder writes in one go.
    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        delegate.addVertex(x + (float) dx, y + (float) dy, z + (float) dz, color, u, v, overlay, light, nx, ny, nz);
    }

    @Override
    public VertexConsumer setColor(int color) {
        delegate.setColor(color);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
    *///?}
    //? if >=26.3 {
    /*@Override
    public VertexConsumer setUv3(float u, float v) {
        delegate.setUv3(u, v);
        return this;
    }
    *///?}
}
