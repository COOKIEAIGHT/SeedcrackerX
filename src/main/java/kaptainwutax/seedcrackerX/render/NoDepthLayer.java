package kaptainwutax.seedcrackerX.render;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class NoDepthLayer {
    private NoDepthLayer() {
    }

    private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("seedcrackerx", "pipeline/lines_no_depth"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .build()
    );

    private static final OitPipelineSet LINES_NO_DEPTH_OIT_PIPELINE = OitPipelineSet.builder(
                    "seedcrackerx_no_depth",
                    RenderPipeline.builder(RenderPipelines.OIT_LINES_SNIPPET)
            )
            .withoutDepthTest()
            .build();

    /**
     * tell Iris (shaders) to draw our see-through outlines with its line shader.
     * Without this Iris logs "Missing program seedcrackerx:pipeline/lines_no_depth" and outlines can
     * go missing or look wrong with shaders on. Done by reflection so it's skipped if Iris isn't there.
     */
    public static void registerWithIris() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object lines = program.getField("LINES").get(null);
            api.getMethod("assignPipeline", RenderPipeline.class, program).invoke(instance, LINES_NO_DEPTH_PIPELINE, lines);
            org.slf4j.LoggerFactory.getLogger("seedcrackerx").info("Registered outline pipeline with Iris");
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("seedcrackerx").warn("Couldn't register outline pipeline with Iris: {}", t.toString());
        }
    }

    public static final RenderType LINES_NO_DEPTH_LAYER = RenderType.create("seedcrackerx_no_depth",
        RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
            .setOitPipelines(LINES_NO_DEPTH_OIT_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    );
}
