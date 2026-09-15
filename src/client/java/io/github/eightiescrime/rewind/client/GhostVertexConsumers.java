package io.github.eightiescrime.rewind.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Перекрашивает всё, что через него рисуется, в один полупрозрачный тон.
 *
 * <p>Своего шейдера и своего слоя рендера мод не заводит: силуэт получается
 * подменой цвета вершин, а прозрачность — тем, что модель рисуется по
 * прозрачному пути (см. {@link RewindEffects}).
 */
final class GhostVertexConsumers implements VertexConsumerProvider {

    private final VertexConsumerProvider delegate;
    private final int red;
    private final int green;
    private final int blue;
    private final int alpha;

    GhostVertexConsumers(VertexConsumerProvider delegate, int rgb, float alpha) {
        this.delegate = delegate;
        this.red = (rgb >> 16) & 0xFF;
        this.green = (rgb >> 8) & 0xFF;
        this.blue = rgb & 0xFF;
        this.alpha = (int) (Math.clamp(alpha, 0.0f, 1.0f) * 255);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new Ghost(delegate.getBuffer(layer));
    }

    private final class Ghost implements VertexConsumer {
        private final VertexConsumer delegate;

        private Ghost(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            // цвет модели игнорируется целиком: силуэт не должен быть узнаваем
            // по скину, иначе это уже не эхо, а второй игрок
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }
}
