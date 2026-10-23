package io.github.nullbrash.seepers.light;

/** Per-BlockState light overrides, implemented on BlockStateBase by a mixin and filled by {@link LightTable}. */
public interface LightOverrides {
    /** 0 = no override; otherwise {@link LightTable#pack} of the three per-dimension levels. */
    void seepers$setPackedLight(int packed);

    /** 0 = no override; otherwise light opacity + 1. */
    void seepers$setLightBlockOverride(int opacityPlusOne);
}
